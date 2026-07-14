package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 站在金磚月台上顯示站點資訊（與自動停車、路線無關） */
public class StationDisplayListener implements Listener {

    private final AtrainPlugin plugin;
    private final Map<UUID, String> lastStopId = new ConcurrentHashMap<>();

    public StationDisplayListener(AtrainPlugin plugin) {
        this.plugin = plugin;
        scheduleRefreshTask();
    }

    private void scheduleRefreshTask() {
        long interval = plugin.getConfigManager().getStationDisplayIntervalTicks();
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, interval, interval);
    }

    /** 僅在跨方塊移動時檢查，避免轉頭/微調視角每秒觸發 20+ 次 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (!plugin.getConfigManager().isStationDisplayEnabled()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        updateDisplay(event.getPlayer(), to);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastStopId.remove(event.getPlayer().getUniqueId());
    }

    private void refreshAll() {
        if (!plugin.getConfigManager().isStationDisplayEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateDisplay(player, player.getLocation());
        }
    }

    private void updateDisplay(Player player, Location at) {
        Stop stop = plugin.getStopManager().getStopByDisplay(at);
        if (stop == null) {
            if (lastStopId.remove(player.getUniqueId()) != null) {
                player.sendActionBar(net.kyori.adventure.text.Component.empty());
            }
            return;
        }
        String cacheKey = stop.getId() + "|" + (stop.isOnReturnPlatformOnly(at) ? "R" : "F");
        if (cacheKey.equals(lastStopId.get(player.getUniqueId()))) return;
        lastStopId.put(player.getUniqueId(), cacheKey);
        sendDisplay(player, stop, at);
    }

    private void sendDisplay(Player player, Stop stop, Location at) {
        if (!plugin.getConfigManager().isActionbarEnabled()) return;
        var lang = plugin.getLanguageManager();
        var stopMgr = plugin.getStopManager();
        String prev = stopMgr.resolveDisplayPrev(stop, at);
        String next = stopMgr.resolveDisplayNext(stop, at);
        boolean hasPrev = stopMgr.hasDisplayPrev(stop, at);
        boolean hasNext = stopMgr.hasDisplayNext(stop, at);

        StringBuilder text = new StringBuilder();
        if (!stop.getReturnGoldBlocks().isEmpty()) {
            boolean onReturn = stop.isOnReturnPlatformOnly(at);
            String dirKey = onReturn ? "route.direction_reverse" : "route.direction_forward";
            text.append("§7(").append(lang.get(player, dirKey)).append(") ");
        }

        String displayKey;
        Map<String, String> placeholders;
        if (hasPrev && hasNext) {
            displayKey = "station.display_info";
            placeholders = Map.of(
                    "prev", TextUtil.escapePlain(prev),
                    "current", TextUtil.escapePlain(stop.getDisplayName()),
                    "next", TextUtil.escapePlain(next));
        } else if (hasPrev) {
            displayKey = "station.display_info_no_next";
            placeholders = Map.of(
                    "prev", TextUtil.escapePlain(prev),
                    "current", TextUtil.escapePlain(stop.getDisplayName()));
        } else if (hasNext) {
            displayKey = "station.display_info_no_prev";
            placeholders = Map.of(
                    "current", TextUtil.escapePlain(stop.getDisplayName()),
                    "next", TextUtil.escapePlain(next));
        } else {
            displayKey = "station.display_info_current_only";
            placeholders = Map.of("current", TextUtil.escapePlain(stop.getDisplayName()));
        }
        text.append(lang.get(player, displayKey, placeholders));

        if (stop.hasKeyInfo()) {
            java.util.List<String> ksNames = plugin.getStopManager().getKeyStationDisplayNames(stop);
            String ksLore = ksNames.isEmpty() ? "-" : String.join(", ", ksNames);
            text.append(" §8| ").append(lang.get(player, "station.key_info", Map.of(
                    "station", TextUtil.escapePlain(ksLore),
                    "direction", TextUtil.escapePlain(stop.getKeyDirection()))));
        }

        if (player.hasPermission("atrain.admin") && !stop.getAdminInfo().isBlank()) {
            text.append(" §8| ").append(lang.get(player, "station.admin_info", Map.of(
                    "info", TextUtil.escapePlain(stop.getAdminInfo()))));
        }

        player.sendActionBar(TextUtil.component(text.toString()));
    }
}
