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

        Location centerLoc = RailUtil.cartPositionOnRail(rail);
        if (centerLoc == null) centerLoc = rail.getLocation().add(0.5, 0.5, 0.5);
        double radius = cfg.getCartSpawnRadius();

        // 1. Check if the exact station rail has an available cart
        for (var entity : world.getNearbyEntities(centerLoc, radius, radius, radius)) {
            if (!(entity instanceof Minecart existing) || !existing.isValid() || existing.isDead()) continue;
            if (existing instanceof RideableMinecart rideable) {
                if (rideable.getPassengers().isEmpty() && !pendingMounts.contains(rideable.getUniqueId())) {
                    TextUtil.send(player, lang.get(player, "cart.already_nearby"));
                    if (cfg.isCartSpawnAutoMount()) {
                        mountNextTick(player, rideable);
                    }
                    return false;
                }
            }
        }

        // 2. Find an empty rail nearby to spawn
        Block emptyRail = findEmptyRailConnectedTo(rail, world, radius);
        if (emptyRail == null) {
            TextUtil.send(player, lang.get(player, "cart.already_nearby"));
            return false;
        }

        Location spawnLoc = emptyRail.getLocation().add(0.5, RailUtil.cartHeightOnRail(emptyRail) + 0.0625, 0.5);

        RideableMinecart cart = world.spawn(spawnLoc, RideableMinecart.class, entity -> {
            entity.setMaxSpeed((float) cfg.getCartSpeed());
            plugin.markAsManagedCart(entity);
        });

        lastSpawnTick.put(player.getUniqueId(), now);
        scheduleEmptyDespawn(cart);
        TextUtil.send(player, lang.get(player, "cart.spawned", Map.of(
                "stop", TextUtil.escapePlain(stop.getDisplayName()))));

        if (!RailUtil.isPoweredRail(spawnLoc)) {
            TextUtil.send(player, lang.get(player, "cart.need_power"));
        }

        if (cfg.isCartSpawnAutoMount()) {
            mountNextTick(player, cart);
        }
        return true;
    }

    private Block findEmptyRailConnectedTo(Block startRail, World world, double radius) {
        java.util.Set<Block> visited = new java.util.HashSet<>();
        java.util.Queue<Block> queue = new java.util.ArrayDeque<>();
        queue.add(startRail);
        visited.add(startRail);

        while (!queue.isEmpty() && visited.size() < 30) {
            Block curr = queue.poll();
            Location loc = RailUtil.cartPositionOnRail(curr);
            if (loc == null) loc = curr.getLocation().add(0.5, 0.5, 0.5);

            boolean occupied = false;
            for (var entity : world.getNearbyEntities(loc, radius, radius, radius)) {
                if (entity instanceof Minecart m && m.isValid() && !m.isDead()) {
                    occupied = true;
                    break;
                }
            }
            if (!occupied) {
                return curr;
            }

            for (org.bukkit.util.Vector dir : RailUtil.getRailDirections(curr.getLocation())) {
                Block next = RailUtil.walkNextRail(curr, dir);
                if (next != null && visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return null;
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
