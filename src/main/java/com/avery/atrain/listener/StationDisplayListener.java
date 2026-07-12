package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;

/** 站在鑽石塊或金磚月台上顯示上一站 / 本站 / 下一站 */
public class StationDisplayListener implements Listener {

    private final AtrainPlugin plugin;

    public StationDisplayListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        Stop stop = plugin.getStopManager().getStopByDisplay(event.getTo());
        if (stop == null) return;

        Line line = plugin.getLineManager().getLinesAtStop(stop.getId()).stream().findFirst().orElse(null);
        if (line == null) return;

        var lang = plugin.getLanguageManager();
        String prev = plugin.getStopManager().getPrevStopName(line, stop.getId());
        String next = plugin.getStopManager().getNextStopName(line, stop.getId());
        String msg = lang.get(player, "station.display_info", Map.of(
                "prev", prev,
                "current", stop.getDisplayName(),
                "next", next,
                "line", line.getFormattedName()));
        Component component = LegacyComponentSerializer.legacySection().deserialize(msg);
        player.sendActionBar(component);
    }
}
