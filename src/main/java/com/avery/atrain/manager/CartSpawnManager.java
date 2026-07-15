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

    private record SpawnRequest(Player player, Block clickedRail) {}
    private final Map<String, java.util.Queue<SpawnRequest>> spawnQueues = new ConcurrentHashMap<>();
    private final Map<String, Long> lastOccupiedTick = new ConcurrentHashMap<>(); // 紀錄最後一次被佔用的 Tick

    public CartSpawnManager(AtrainPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::processQueues, 1L, 1L);
    }

    private void processQueues() {
        long now = plugin.getServer().getCurrentTick();
        for (var entry : spawnQueues.entrySet()) {
            String stopId = entry.getKey();
            var queue = entry.getValue();
            if (queue.isEmpty()) continue;

            SpawnRequest req = queue.peek();
            if (!req.player().isOnline()) {
                queue.poll();
                continue;
            }

            Block rail = StationUtil.resolveRailBlock(req.clickedRail());
            if (rail == null) {
                queue.poll();
                continue;
            }

            Location centerLoc = RailUtil.cartPositionOnRail(rail);
            if (centerLoc == null) centerLoc = rail.getLocation().add(0.5, 0.5, 0.5);
            double radius = plugin.getConfigManager().getCartSpawnRadius();

            boolean occupied = false;
            World world = rail.getWorld();
            for (var entity : world.getNearbyEntities(centerLoc, radius, radius, radius)) {
                if (entity instanceof Minecart m && m.isValid() && !m.isDead()) {
                    occupied = true;
                    break;
                }
            }

            if (occupied) {
                lastOccupiedTick.put(stopId, now); // 只要有車，就更新最後佔用時間
            } else {
                long lastOcc = lastOccupiedTick.getOrDefault(stopId, 0L);
                // 必須距離最後一次被佔用超過 20 Ticks (1秒)，才允許發下一班車
                if (now - lastOcc >= 20) {
                    queue.poll();
                    performSpawn(req.player(), rail, plugin.getStopManager().getStop(stopId));
                    lastOccupiedTick.put(stopId, now); // 發車瞬間也算佔用，重置計時
                }
            }
        }
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

        java.util.Queue<SpawnRequest> q = spawnQueues.computeIfAbsent(stop.getId(), k -> new java.util.concurrent.ConcurrentLinkedQueue<>());

        for (SpawnRequest req : q) {
            if (req.player().getUniqueId().equals(player.getUniqueId())) {
                TextUtil.send(player, lang.get(player, "cart.queued", Map.of("pos", String.valueOf(q.size()))));
                return false;
            }
        }

        boolean isOccupied = false;
        boolean hasExistingEmpty = false;
        RideableMinecart existingEmpty = null;
        for (var entity : world.getNearbyEntities(centerLoc, radius, radius, radius)) {
            if (!(entity instanceof Minecart existing) || !existing.isValid() || existing.isDead()) continue;
            isOccupied = true;
            if (existing instanceof RideableMinecart rideable && rideable.getPassengers().isEmpty() && !pendingMounts.contains(rideable.getUniqueId())) {
                hasExistingEmpty = true;
                existingEmpty = rideable;
                // 不 break，因為我們還要確認是否有其他礦車佔用，不過這邊找到了空車，
                // 如果我們只想上這個空車，其實可以 break。
                // 為了安全起見，isOccupied 會讓它知道站上有車。
            }
        }

        if (hasExistingEmpty && q.isEmpty() && now - lastOccupiedTick.getOrDefault(stop.getId(), 0L) >= 20) {
            TextUtil.send(player, lang.get(player, "cart.already_nearby"));
            if (cfg.isCartSpawnAutoMount()) {
                mountNextTick(player, existingEmpty);
                lastSpawnTick.put(player.getUniqueId(), now);
            }
            return false;
        }

        q.add(new SpawnRequest(player, clicked));
        lastSpawnTick.put(player.getUniqueId(), now);

        if (q.size() > 1 || isOccupied || now - lastOccupiedTick.getOrDefault(stop.getId(), 0L) < 20) {
            TextUtil.send(player, lang.get(player, "cart.queued", Map.of("pos", String.valueOf(q.size()))));
        }

        return true;
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
