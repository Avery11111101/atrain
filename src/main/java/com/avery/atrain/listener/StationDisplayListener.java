package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 站在金磚/鑽石塊上顯示站點資訊（與自動停車、路線無關） */
public class StationDisplayListener implements Listener {

    private final AtrainPlugin plugin;
    private final Map<UUID, String> lastStopId = new ConcurrentHashMap<>();

    public StationDisplayListener(AtrainPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, 40L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (!plugin.getConfigManager().isStationDisplayEnabled()) return;

        Player player = event.getPlayer();
        Stop stop = plugin.getStopManager().getStopByDisplay(event.getTo());
        if (stop == null) {
            lastStopId.remove(player.getUniqueId());
            return;
        }
        if (stop.getId().equals(lastStopId.get(player.getUniqueId()))) return;
        lastStopId.put(player.getUniqueId(), stop.getId());
        sendDisplay(player, stop);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastStopId.remove(event.getPlayer().getUniqueId());
    }

    private void refreshAll() {
        if (!plugin.getConfigManager().isStationDisplayEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Stop stop = plugin.getStopManager().getStopByDisplay(player.getLocation());
            if (stop != null) {
                sendDisplay(player, stop);
            }
        }
    }

    private void sendDisplay(Player player, Stop stop) {
        var lang = plugin.getLanguageManager();
        StringBuilder text = new StringBuilder(lang.get(player, "station.display_info", Map.of(
                "prev", TextUtil.escapePlain(plugin.getStopManager().resolveDisplayPrev(stop, player.getLocation())),
                "current", TextUtil.escapePlain(stop.getDisplayName()),
                "next", TextUtil.escapePlain(plugin.getStopManager().resolveDisplayNext(stop, player.getLocation())))));

        if (stop.hasKeyInfo()) {
            text.append(" §8| ").append(lang.get(player, "station.key_info", Map.of(
                    "station", TextUtil.escapePlain(stop.getKeyStation()),
                    "direction", TextUtil.escapePlain(stop.getKeyDirection()))));
        }

        if (player.hasPermission("atrain.admin") && !stop.getAdminInfo().isBlank()) {
            text.append(" §8| ").append(lang.get(player, "station.admin_info", Map.of(
                    "info", TextUtil.escapePlain(stop.getAdminInfo()))));
        }

        player.sendActionBar(TextUtil.component(text.toString()));
    }
}
