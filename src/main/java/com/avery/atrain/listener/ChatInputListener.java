package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.manager.ChatInputManager;
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

    private final AtrainPlugin plugin;

    public ChatInputListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        ChatInputManager.Pending pending = plugin.getChatInputManager().getPending(event.getPlayer());
        if (pending == null) return;

        event.setCancelled(true);
        event.viewers().clear();

        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();
        Player player = event.getPlayer();

        plugin.getServer().getScheduler().runTask(plugin,
                () -> handleInput(player, pending, message));
    }

    private void handleInput(Player player, ChatInputManager.Pending pending, String message) {
        if (isCancel(message)) {
            plugin.getChatInputManager().clear(player);
            TextUtil.send(player, plugin.getLanguageManager().get(player, "input.cancelled"));
            reopen(player, pending);
            return;
        }

        switch (pending.type()) {
            case STOP_NAME -> applyStopName(player, pending, message);
            case STOP_INFO_PREV -> applyInfoPrev(player, pending, message);
            case STOP_INFO_NEXT -> applyInfoNext(player, pending, message);
            case STOP_KEY_STATION -> applyKeyStation(player, pending, message);
            case STOP_KEY_DIRECTION -> applyKeyDirection(player, pending, message);
            case STOP_ADMIN_INFO -> applyAdminInfo(player, pending, message);
            case LINE_CREATE -> applyLineCreate(player, message);
            case LINE_RENAME -> applyLineRename(player, pending, message);
            case LINE_COLOR -> applyLineColor(player, pending, message);
            case SPEED_BLOCK_SPEED -> applySpeedBlockSpeed(player, pending, message);
        }
    }

    private void applySpeedBlockSpeed(Player player, ChatInputManager.Pending pending, String message) {
        if (!player.hasPermission("atrain.speed.edit")) {
            plugin.getChatInputManager().clear(player);
            TextUtil.send(player, plugin.getLanguageManager().get(player, "error.no_permission"));
            return;
        }
        String blockKey = pending.contextId();
        var sb = plugin.getSpeedBlockManager().getByKey(blockKey);
        if (sb == null) {
            plugin.getChatInputManager().clear(player);
            TextUtil.send(player, plugin.getLanguageManager().get(player, "speed_block.not_found"));
            return;
        }
        double speed;
        try {
            speed = Double.parseDouble(message.trim());
        } catch (NumberFormatException e) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "speed_block.speed_invalid"));
            return;
        }
        if (speed < 0.05 || speed > 2.0) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "speed_block.speed_range"));
            return;
        }
        plugin.getSpeedBlockManager().updateSpeed(blockKey, speed);
        plugin.getChatInputManager().clear(player);
        TextUtil.send(player, plugin.getLanguageManager().get(player, "speed_block.speed_set",
                Map.of("speed", String.format("%.2f", speed))));
        plugin.getGuiManager().openSpeedBlockEdit(player, blockKey);
    }

    private void applyLineCreate(Player player, String message) {
        if (message.isBlank()) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "line.name_empty"));
            return;
        }
        var line = plugin.getLineManager().createLineAuto(message.trim());
        plugin.getChatInputManager().clear(player);
        TextUtil.send(player, plugin.getLanguageManager().get(player, "line.created", Map.of("id", line.getDisplayName())));
        plugin.getGuiManager().openLineDetail(player, line.getId(), 0);
    }

    private void applyLineRename(Player player, ChatInputManager.Pending pending, String message) {
        if (message.isBlank()) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "line.name_empty"));
            return;
        }
        String lineId = pending.contextId();
        var line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            plugin.getChatInputManager().clear(player);
            TextUtil.send(player, plugin.getLanguageManager().get(player, "line.not_found", Map.of("id", lineId)));
            return;
        }
        plugin.getLineManager().renameLine(lineId, message.trim());
        plugin.getChatInputManager().clear(player);
        TextUtil.send(player, plugin.getLanguageManager().get(player, "line.renamed", Map.of("name", message.trim())));
        plugin.getGuiManager().openLineDetail(player, lineId, 0);
    }

    private void applyLineColor(Player player, ChatInputManager.Pending pending, String message) {
        String lineId = pending.contextId();
        var line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            plugin.getChatInputManager().clear(player);
            TextUtil.send(player, plugin.getLanguageManager().get(player, "line.not_found", Map.of("id", lineId)));
            return;
        }
        String input = message.trim();
        if (input.isBlank() || !com.avery.atrain.util.ColorUtil.isValidColor(input)) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "line.color_invalid"));
            return;
        }
        String normalized = com.avery.atrain.util.ColorUtil.normalizeColor(input);
        line.setColor(normalized);
        plugin.getDataStore().save();
        plugin.getBlueMapManager().updateMap();
        plugin.getChatInputManager().clear(player);
        TextUtil.send(player, plugin.getLanguageManager().get(player, "line.color_set", Map.of("color", line.getFormattedColor() + normalized)));
        plugin.getGuiManager().openLineDetail(player, lineId, 0);
    }

    private void applyStopName(Player player, ChatInputManager.Pending pending, String message) {
        Stop stop = requireStop(player, pending);
        if (stop == null) return;
        if (message.isBlank()) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "stop.name_empty"));
            return;
        }
        stop.setDisplayName(message.trim());
        saveAndFinish(player, pending, "stop.name_set", Map.of("name", stop.getDisplayName()));
    }

    private void applyInfoPrev(Player player, ChatInputManager.Pending pending, String message) {
        Stop stop = requireStop(player, pending);
        if (stop == null) return;
        stop.setInfoPrev(message.trim().isEmpty() ? "-" : message.trim());
        saveAndFinish(player, pending, "stop.info_prev_set", Map.of("name", stop.getInfoPrev()));
    }

    private void applyInfoNext(Player player, ChatInputManager.Pending pending, String message) {
        Stop stop = requireStop(player, pending);
        if (stop == null) return;
        stop.setInfoNext(message.trim().isEmpty() ? "-" : message.trim());
        saveAndFinish(player, pending, "stop.info_next_set", Map.of("name", stop.getInfoNext()));
    }

    private void applyKeyStation(Player player, ChatInputManager.Pending pending, String message) {
        plugin.getChatInputManager().clear(player);
    }

    private void applyKeyDirection(Player player, ChatInputManager.Pending pending, String message) {
        Stop stop = requireStop(player, pending);
        if (stop == null) return;
        stop.setKeyDirection(message.trim().isEmpty() ? "-" : message.trim());
        saveAndFinish(player, pending, "stop.key_direction_set", Map.of("name", stop.getKeyDirection()));
    }

    private void applyAdminInfo(Player player, ChatInputManager.Pending pending, String message) {
        if (!player.hasPermission("atrain.admin")) {
            plugin.getChatInputManager().clear(player);
            TextUtil.send(player, plugin.getLanguageManager().get(player, "error.no_permission"));
            return;
        }
        Stop stop = requireStop(player, pending);
        if (stop == null) return;
        stop.setAdminInfo(message.trim());
        saveAndFinish(player, pending, "stop.admin_info_set", Map.of("info", stop.getAdminInfo().isBlank() ? "-" : stop.getAdminInfo()));
    }

    private Stop requireStop(Player player, ChatInputManager.Pending pending) {
        var lang = plugin.getLanguageManager();
        Stop stop = plugin.getStopManager().getStop(pending.contextId());
        if (stop == null) {
            plugin.getChatInputManager().clear(player);
            TextUtil.send(player, lang.get(player, "stop.not_found", Map.of("id", pending.contextId())));
        }
        return stop;
    }

    private void saveAndFinish(Player player, ChatInputManager.Pending pending, String key, Map<String, String> ph) {
        plugin.getDataStore().save();
        plugin.getChatInputManager().clear(player);
        TextUtil.send(player, plugin.getLanguageManager().get(player, key, ph));
        plugin.getGuiManager().openStationEdit(player, pending.contextId());
    }

    private void reopen(Player player, ChatInputManager.Pending pending) {
        if (pending.contextId() == null) {
            if (pending.type() == ChatInputManager.Type.LINE_CREATE) {
                plugin.getGuiManager().openLineList(player, 0);
            }
            return;
        }
        if (pending.type() == ChatInputManager.Type.LINE_RENAME || pending.type() == ChatInputManager.Type.LINE_COLOR) {
            plugin.getGuiManager().openLineDetail(player, pending.contextId(), 0);
            return;
        }
        if (pending.type() == ChatInputManager.Type.SPEED_BLOCK_SPEED) {
            plugin.getGuiManager().openSpeedBlockEdit(player, pending.contextId());
            return;
        }
        if (plugin.getStopManager().getStop(pending.contextId()) != null) {
            plugin.getGuiManager().openStationEdit(player, pending.contextId());
        }
    }

    private boolean isCancel(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return "cancel".equals(lower) || "取消".equals(message) || "キャンセル".equals(message);
    }
}
