package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.PlatformSide;
import com.avery.atrain.model.Stop;
import com.avery.atrain.model.TravelDirection;
import com.avery.atrain.train.TrainMovementTask;
import com.avery.atrain.util.StopPlatformUtil;
import com.avery.atrain.util.TextUtil;
import com.avery.atrain.util.TrackUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerInteractListener implements Listener {

    private final AtrainPlugin plugin;
    private final Set<UUID> pendingSpawn = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> pendingSpawnTasks = new ConcurrentHashMap<>();

    public PlayerInteractListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (isGoldenAxe(event)) {
            if (!player.hasPermission("atrain.create")) {
                TextUtil.send(player, plugin.getLanguageManager().get(player, "error.no_permission"));
                event.setCancelled(true);
                return;
            }
            handleWand(player, block.getLocation());
            event.setCancelled(true);
            return;
        }

        if (!TrackUtil.isBoardableBlock(plugin, block)) return;

        Stop stop = plugin.getStopManager().getStopContaining(block.getLocation());
        if (stop == null) return;

        List<Line> lines = plugin.getLineManager().getLinesAtStop(stop.getId());
        if (lines.isEmpty()) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "ride.no_lines_at_stop",
                    Map.of("stop", stop.getDisplayName())));
            return;
        }

        PlatformSide platform = StopPlatformUtil.detectPlatform(stop, block.getLocation());
        if (platform == null) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "stop.platform_unset"));
            return;
        }

        TravelDirection direction = platform.toTravelDirection();
        if (stop.getBoardPoint(platform) == null) {
            TextUtil.send(player, plugin.getLanguageManager().get(player,
                    platform == PlatformSide.FORWARD ? "stop.no_forward_point" : "stop.no_return_point"));
            return;
        }

        event.setCancelled(true);
        List<Line> viable = lines.stream()
                .filter(line -> line.getNextStopId(stop.getId(), direction) != null)
                .toList();
        if (viable.isEmpty()) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "ride.no_direction",
                    Map.of("dir", plugin.getLanguageManager().get(player,
                            platform == PlatformSide.FORWARD ? "stop.platform_forward" : "stop.platform_return"))));
            return;
        }

        if (viable.size() == 1) {
            spawnAndRide(player, stop, viable.get(0), block.getLocation(), direction);
        } else {
            plugin.getGuiManager().openLineChoice(player, stop, viable, platform, block.getLocation());
        }
    }

    private boolean isGoldenAxe(PlayerInteractEvent event) {
        ItemStack used = event.getItem();
        if (used != null && used.getType() == Material.GOLDEN_AXE) return true;
        EquipmentSlot hand = event.getHand();
        if (hand == null) return false;
        ItemStack inHand = event.getPlayer().getInventory().getItem(hand);
        return inHand != null && inHand.getType() == Material.GOLDEN_AXE;
    }

    private void handleWand(Player player, Location loc) {
        var sel = plugin.getSelectionManager();
        var lang = plugin.getLanguageManager();
        if (sel.getCorner1(player) == null) {
            sel.setCorner1(player, loc);
            TextUtil.send(player, lang.get(player, "selection.corner1"));
        } else if (sel.getCorner2(player) == null) {
            if (!sel.isSameWorldAsCorner1(player, loc)) {
                TextUtil.send(player, lang.get(player, "selection.different_world"));
                return;
            }
            sel.setCorner2(player, loc);
            TextUtil.send(player, lang.get(player, "selection.corner2"));
        } else {
            sel.clear(player);
            sel.setCorner1(player, loc);
            TextUtil.send(player, lang.get(player, "selection.corner1_reset"));
        }
    }

    public void spawnAndRide(Player player, Stop stop, Line line, Location railLoc) {
        spawnAndRide(player, stop, line, railLoc, TravelDirection.FORWARD);
    }

    public void spawnAndRide(Player player, Stop stop, Line line, Location railLoc, TravelDirection direction) {
        var lang = plugin.getLanguageManager();
        if (!player.hasPermission("atrain.use")) {
            TextUtil.send(player, lang.get(player, "error.no_permission"));
            return;
        }
        if (pendingSpawn.contains(player.getUniqueId())) {
            TextUtil.send(player, lang.get(player, "ride.already_spawning"));
            return;
        }

        if (line.getStopIds().isEmpty()) {
            TextUtil.send(player, lang.get(player, "ride.no_stops"));
            return;
        }
        if (line.getRoutePoints().isEmpty()) {
            TextUtil.send(player, lang.get(player, "ride.no_route"));
            return;
        }
        if (line.getNextStopId(stop.getId(), direction) == null) {
            TextUtil.send(player, lang.get(player, "ride.no_direction",
                    Map.of("dir", lang.get(player, direction == TravelDirection.FORWARD
                            ? "stop.platform_forward" : "stop.platform_return"))));
            return;
        }

        PlatformSide side = direction == TravelDirection.REVERSE ? PlatformSide.RETURN : PlatformSide.FORWARD;
        Location spawn = stop.getBoardPoint(side);
        if (spawn == null) spawn = railLoc;

        pendingSpawn.add(player.getUniqueId());
        int delay = plugin.getConfigManager().getSpawnDelay();
        String dirLabel = lang.get(player, direction == TravelDirection.FORWARD
                ? "ride.direction_forward" : "ride.direction_reverse");
        TextUtil.send(player, lang.get(player, "ride.spawning_dir", Map.of(
                "line", line.getFormattedName(),
                "stop", stop.getDisplayName(),
                "dir", dirLabel)));

        Location finalSpawn = spawn;
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingSpawn.remove(player.getUniqueId());
            pendingSpawnTasks.remove(player.getUniqueId());
            if (!player.isOnline()) return;

            if (finalSpawn.getWorld() == null) {
                TextUtil.send(player, lang.get(player, "error.world_not_loaded"));
                return;
            }

            Minecart cart = finalSpawn.getWorld().spawn(finalSpawn, Minecart.class, c -> {
                c.setMaxSpeed(line.getMaxSpeed());
                c.setSlowWhenEmpty(false);
                var pdc = c.getPersistentDataContainer();
                pdc.set(plugin.getMinecartKey(), PersistentDataType.BYTE, (byte) 1);
                pdc.set(plugin.getLineKey(), PersistentDataType.STRING, line.getId());
                pdc.set(plugin.getStopKey(), PersistentDataType.STRING, stop.getId());
            });
            cart.addPassenger(player);
            new TrainMovementTask(plugin, cart, player, line.getId(), stop.getId(), direction);
        }, delay);
        pendingSpawnTasks.put(player.getUniqueId(), task);
    }

    public void clearPlayerState(java.util.UUID playerId) {
        pendingSpawn.remove(playerId);
        BukkitTask task = pendingSpawnTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    public void clearAllPendingSpawn() {
        pendingSpawn.clear();
        for (BukkitTask task : pendingSpawnTasks.values()) {
            if (task != null) task.cancel();
        }
        pendingSpawnTasks.clear();
    }
}
