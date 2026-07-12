package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.model.TravelDirection;
import com.avery.atrain.train.TrainMovementTask;
import com.avery.atrain.util.StationUtil;
import com.avery.atrain.util.TextUtil;
import com.avery.atrain.util.TrackUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();

        if (!StationUtil.isGoldStationRail(block) && StationUtil.resolveGoldBlock(block) == null) return;

        if (player.isSneaking()) {
            if (!player.hasPermission("atrain.station.edit")) {
                TextUtil.send(player, plugin.getLanguageManager().get(player, "error.no_permission"));
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            Block gold = StationUtil.resolveGoldBlock(block);
            if (gold == null) return;
            Stop stop = plugin.getStopManager().findStopOwningGold(gold);
            if (stop == null) {
                stop = plugin.getStopManager().registerFromBlock(block,
                        plugin.getLanguageManager().get(player, "stop.default_name",
                                Map.of("id", gold.getX() + "_" + gold.getZ())));
                TextUtil.send(player, plugin.getLanguageManager().get(player, "stop.created", Map.of("id", stop.getId())));
            }
            plugin.getGuiManager().openStationEdit(player, stop.getId());
            return;
        }

        Block rail = StationUtil.resolveRailBlock(block);
        if (rail == null) return;
        Stop stop = plugin.getStopManager().getStopAtRail(rail.getLocation());
        if (stop == null) return;

        List<Line> lines = plugin.getLineManager().getLinesAtStop(stop.getId());
        if (lines.isEmpty()) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "ride.no_lines_at_stop",
                    Map.of("stop", stop.getDisplayName())));
            return;
        }

        event.setCancelled(true);
        List<Line> viable = lines.stream()
                .filter(line -> line.getNextStopId(stop.getId()) != null)
                .toList();
        if (viable.isEmpty()) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "ride.no_next_stop"));
            return;
        }

        if (viable.size() == 1) {
            spawnAndRide(player, stop, viable.get(0), rail.getLocation());
        } else {
            plugin.getGuiManager().openLineChoice(player, stop, viable, rail.getLocation());
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
        if (line.getNextStopId(stop.getId(), direction) == null) {
            TextUtil.send(player, lang.get(player, "ride.no_next_stop"));
            return;
        }

        Location spawn = TrackUtil.sampleLocation(plugin, railLoc);
        pendingSpawn.add(player.getUniqueId());
        int delay = plugin.getConfigManager().getSpawnDelay();
        TextUtil.send(player, lang.get(player, "ride.spawning", Map.of(
                "line", line.getFormattedName(),
                "stop", stop.getDisplayName())));

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

    public void clearPlayerState(UUID playerId) {
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
