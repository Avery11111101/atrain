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

    /** 語系模板著色（可含 MiniMessage） */
    public static String colorize(String text) {
        if (text == null) return "";
        if (text.contains("<")) {
            try {
                return LEGACY.serialize(MINI.deserialize(text));
            } catch (Exception e) {
                return escapePlain(text);
            }
        }
        return text.replace('&', '§');
    }

    /** 玩家輸入的純文字，避免被當成 MiniMessage / 色碼破壞 GUI */
    public static String escapePlain(String text) {
        if (text == null) return "";
        return text.replace("&", "＆").replace("<", "‹").replace(">", "›");
    }

    public static void send(Player player, String message) {
        if (message == null || message.isEmpty()) return;
        if (message.contains("<") && message.contains(">")) {
            try {
                player.sendMessage(MINI.deserialize(message));
                return;
            } catch (Exception ignored) {
                // fall through
            }
        }
        player.sendMessage(colorize(message));
    }

    public static String format(String template, Map<String, String> placeholders) {
        String result = template;
        for (var e : placeholders.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue());
        }
        return result;
    }

    public static Component component(String message) {
        if (message == null) return Component.empty();
        if (message.contains("<")) {
            try {
                return MINI.deserialize(message);
            } catch (Exception e) {
                return LEGACY.deserialize(escapePlain(message));
            }
        }
        return LEGACY.deserialize(colorize(message));
    }
}
