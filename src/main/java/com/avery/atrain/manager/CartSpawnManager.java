package com.avery.atrain.manager;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.RailUtil;
import com.avery.atrain.util.StationUtil;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;

import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 在站點生成原版可乘坐礦車，移動交由原版動力軌物理處理 */
public class CartSpawnManager {

    private final AtrainPlugin plugin;
    private final Map<UUID, Long> lastSpawnTick = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> despawnTaskIds = new ConcurrentHashMap<>();

    private final Set<UUID> pendingMounts = ConcurrentHashMap.newKeySet();

    private record SpawnRequest(Player player, Block clickedRail) {}
    private final Map<String, java.util.Queue<SpawnRequest>> spawnQueues = new ConcurrentHashMap<>();
    private final Map<String, Long> lastOccupiedTick = new ConcurrentHashMap<>(); // 紀錄最後一次被佔用的 Tick

    public CartSpawnManager(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean trySpawn(Player player, Block clicked) {
        if (plugin.getRouteRecordingManager() != null
                && plugin.getRouteRecordingManager().isRecording(player)) {
            return plugin.getRouteRecordingManager().spawnRecordingCart(player, clicked);
        }

        var lang = plugin.getLanguageManager();
        var cfg = plugin.getConfigManager();

        if (!cfg.isCartSpawnEnabled()) {
            TextUtil.send(player, lang.get(player, "cart.spawn_disabled"));
            return false;
        }
        if (!player.hasPermission("atrain.cart.spawn")) {
            TextUtil.send(player, lang.get(player, "error.no_permission"));
            return false;
        }

        Block rail = StationUtil.resolveRailBlock(clicked);
        if (rail == null) {
            TextUtil.send(player, lang.get(player, "cart.no_rail"));
            return false;
        }

        Stop stop = plugin.getStopManager().getStopAtRail(rail.getLocation());
        if (stop == null) {
            TextUtil.send(player, lang.get(player, "cart.need_station"));
            return false;
        }

        long now = plugin.getServer().getCurrentTick();
        long last = lastSpawnTick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < cfg.getCartSpawnCooldownTicks()) {
            TextUtil.send(player, lang.get(player, "cart.cooldown"));
            return false;
        }

        World world = rail.getWorld();
        if (world == null) {
            TextUtil.send(player, lang.get(player, "error.world_not_loaded"));
            return false;
        }

        // 搜尋可用、未被佔用之鐵軌格，防止礦車重疊
        Block spawnRail = findAvailableRailBlock(rail, stop);

        performSpawn(player, spawnRail, stop);
        lastSpawnTick.put(player.getUniqueId(), now);
        return true;
    }

    /** 搜尋未被礦車佔用的鐵軌格，避免重疊 */
    public Block findAvailableRailBlock(Block clickedRail, Stop stop) {
        if (!isRailOccupiedByCart(clickedRail)) {
            return clickedRail;
        }

        World world = clickedRail.getWorld();
        if (stop != null) {
            Set<Location> goldLocs = stop.getGoldLocations(world);
            for (Location gLoc : goldLocs) {
                Block r = RailUtil.findRailBlock(gLoc.clone().add(0, 1, 0));
                if (r == null) {
                    r = RailUtil.findRailBlock(gLoc);
                }
                if (r != null && !isRailOccupiedByCart(r)) {
                    return r;
                }
            }
        }

        List<Vector> dirs = RailUtil.getRailDirections(clickedRail.getLocation());
        if (!dirs.isEmpty()) {
            for (Vector dir : dirs) {
                List<Block> path = RailUtil.walkRailPath(clickedRail, dir, 5);
                for (Block p : path) {
                    if (!isRailOccupiedByCart(p)) {
                        return p;
                    }
                }
                List<Block> pathRev = RailUtil.walkRailPath(clickedRail, dir.clone().multiply(-1), 5);
                for (Block p : pathRev) {
                    if (!isRailOccupiedByCart(p)) {
                        return p;
                    }
                }
            }
        }

        return clickedRail;
    }

    private boolean isRailOccupiedByCart(Block rail) {
        if (rail == null || rail.getWorld() == null) return false;
        Location centerLoc = RailUtil.cartPositionOnRail(rail);
        if (centerLoc == null) centerLoc = rail.getLocation().add(0.5, 0.5, 0.5);
        for (var entity : rail.getWorld().getNearbyEntities(centerLoc, 0.7, 0.7, 0.7)) {
            if (entity instanceof Minecart m && m.isValid() && !m.isDead()) {
                return true;
            }
        }
        return false;
    }

    private void performSpawn(Player player, Block rail, Stop stop) {
        World world = rail.getWorld();
        var cfg = plugin.getConfigManager();
        Location spawnLoc = rail.getLocation().add(0.5, RailUtil.cartHeightOnRail(rail) + 0.0625, 0.5);

        RideableMinecart cart = world.spawn(spawnLoc, RideableMinecart.class, entity -> {
            entity.setMaxSpeed((float) cfg.getCartSpeed());
            plugin.markAsManagedCart(entity);
        });

        scheduleEmptyDespawn(cart);
        if (stop != null) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "cart.spawned", Map.of(
                    "stop", TextUtil.escapePlain(stop.getDisplayName()))));
        }

        if (!RailUtil.isPoweredRail(spawnLoc)) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "cart.need_power"));
        }

        if (cfg.isCartSpawnAutoMount()) {
            mountNextTick(player, cart);
        }
    }

    private void mountNextTick(Player player, RideableMinecart cart) {
        pendingMounts.add(cart.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingMounts.remove(cart.getUniqueId());
            if (!cart.isValid() || cart.isDead()) return;
            if (player.isInsideVehicle()) return;
            cart.addPassenger(player);
            cancelDespawn(cart.getUniqueId());
        });
    }

    /** 玩家下車後立即移除礦車（若已無其他乘客） */
    public void despawnWhenEmpty(RideableMinecart cart) {
        if (cart == null || cart.isDead()) return;
        cancelDespawn(cart.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!cart.isValid() || cart.isDead()) return;
            if (!cart.getPassengers().isEmpty()) return;
            cart.remove();
        });
    }

    /** 無乘客時延遲刪除礦車（預設 1 秒） */
    public void scheduleEmptyDespawn(Minecart cart) {
        if (cart == null || cart.isDead()) return;
        cancelDespawn(cart.getUniqueId());
        int delay = plugin.getConfigManager().getDespawnDelay();
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            despawnTaskIds.remove(cart.getUniqueId());
            if (!cart.isValid() || cart.isDead()) return;
            if (!cart.getPassengers().isEmpty()) return;
            if (!isManagedStationCart(cart)) return;
            cart.remove();
        }, delay).getTaskId();
        despawnTaskIds.put(cart.getUniqueId(), taskId);
    }

    public void cancelDespawn(UUID cartId) {
        if (cartId == null) return;
        Integer taskId = despawnTaskIds.remove(cartId);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    public boolean isManagedStationCart(Minecart cart) {
        if (cart == null) return false;
        return plugin.getStopManager().getStopAtRail(cart.getLocation()) != null;
    }

    public void clearCooldown(Player player) {
        if (player != null) lastSpawnTick.remove(player.getUniqueId());
    }
}
