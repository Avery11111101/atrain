package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.manager.ChatInputManager;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.TextUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Locale;
import java.util.Map;

public class ChatInputListener implements Listener {

    public static final double MIN_LINE_SPEED = 0.1;
    public static final double MAX_LINE_SPEED = 0.8;

    private final AtrainPlugin plugin;

    public ChatInputListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ChatInputManager.Pending pending = plugin.getChatInputManager().getPending(player);
        if (pending == null) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        plugin.getServer().getScheduler().runTask(plugin,
                () -> handleInput(player, pending, message));
    }

    private void handleInput(Player player, ChatInputManager.Pending pending, String message) {
        if (isCancel(message)) {
            plugin.getChatInputManager().clear(player);
            TextUtil.send(player, plugin.getLanguageManager().get(player, "input.cancelled"));
            reopenAfterCancel(player, pending);
            return;
        }

        switch (pending.type()) {
            case LINE_SPEED -> handleLineSpeed(player, pending, message);
            case STOP_NAME -> handleStopName(player, pending, message);
        }
    }

    private void handleLineSpeed(Player player, ChatInputManager.Pending pending, String message) {
        var lang = plugin.getLanguageManager();
        String lineId = pending.contextId();
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            plugin.getChatInputManager().clear(player);
            TextUtil.send(player, lang.get(player, "line.not_found", Map.of("id", lineId)));
            return;
        }

        Double speed = parseSpeed(message);
        if (speed == null || speed < MIN_LINE_SPEED || speed > MAX_LINE_SPEED) {
            TextUtil.send(player, lang.get(player, "line.speed_invalid", Map.of(
                    "min", formatSpeed(MIN_LINE_SPEED),
                    "max", formatSpeed(MAX_LINE_SPEED))));
            return;
        }

        line.setMaxSpeed(speed);
        plugin.getDataStore().save();
        plugin.getChatInputManager().clear(player);
        TextUtil.send(player, lang.get(player, "line.speed_set", Map.of("speed", formatSpeed(speed))));
        plugin.getGuiManager().openLineDetail(player, lineId);
    }

    private void handleStopName(Player player, ChatInputManager.Pending pending, String message) {
        var lang = plugin.getLanguageManager();
        String stopId = pending.contextId();
        Stop stop = plugin.getStopManager().getStop(stopId);
        if (stop == null) {
            plugin.getChatInputManager().clear(player);
            TextUtil.send(player, lang.get(player, "stop.not_found", Map.of("id", stopId)));
            return;
        }
        if (message.isBlank()) {
            TextUtil.send(player, lang.get(player, "stop.name_empty"));
            return;
        }

        stop.setDisplayName(message.trim());
        plugin.getDataStore().save();
        plugin.getChatInputManager().clear(player);
        TextUtil.send(player, lang.get(player, "stop.name_set", Map.of("name", stop.getDisplayName())));
        plugin.getGuiManager().openStationEdit(player, stopId);
    }

    private void reopenAfterCancel(Player player, ChatInputManager.Pending pending) {
        if (pending == null) return;
        switch (pending.type()) {
            case LINE_SPEED -> {
                if (pending.contextId() != null && plugin.getLineManager().getLine(pending.contextId()) != null) {
                    plugin.getGuiManager().openLineDetail(player, pending.contextId());
                }
            }
            case STOP_NAME -> {
                if (pending.contextId() != null && plugin.getStopManager().getStop(pending.contextId()) != null) {
                    plugin.getGuiManager().openStationEdit(player, pending.contextId());
                }
            }
        }
    }

    private boolean isCancel(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return "cancel".equals(lower) || "取消".equals(message) || "キャンセル".equals(message);
    }

    private Double parseSpeed(String raw) {
        try {
            double value = Double.parseDouble(raw.replace(',', '.'));
            return Math.round(value * 1000.0) / 1000.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatSpeed(double speed) {
        return String.format(Locale.US, "%.2f", speed);
    }
}
