package com.avery.atrain.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Map;

public final class TextUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private TextUtil() {}

    public static String colorize(String text) {
        if (text == null) return "";
        if (text.contains("<")) {
            return LEGACY.serialize(MINI.deserialize(text));
        }
        return text.replace('&', '§');
    }

    public static void send(Player player, String message) {
        if (message == null || message.isEmpty()) return;
        if (message.contains("<")) {
            player.sendMessage(MINI.deserialize(message));
        } else {
            player.sendMessage(colorize(message));
        }
    }

    public static String format(String template, Map<String, String> placeholders) {
        String result = template;
        for (var e : placeholders.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue());
        }
        return result;
    }

    public static Component component(String message) {
        if (message.contains("<")) return MINI.deserialize(message);
        return LEGACY.deserialize(colorize(message));
    }
}
