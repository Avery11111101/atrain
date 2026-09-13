package com.avery.atrain.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Map;

public final class TextUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private static final java.util.regex.Pattern LEGACY_HEX_REGEX = java.util.regex.Pattern.compile("[§&]x[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern HASH_HEX_REGEX = java.util.regex.Pattern.compile("[§&]#([0-9a-fA-F]{6})", java.util.regex.Pattern.CASE_INSENSITIVE);

    private TextUtil() {}

    /** 將混用的 § 色碼與 Hex 色碼轉成 MiniMessage，避免解析失敗後標籤原樣顯示 */
    public static String preprocessForMiniMessage(String text) {
        if (text == null || text.isEmpty()) return text;
        String s = text;
        if (s.contains("§") || s.contains("&")) {
            s = LEGACY_HEX_REGEX.matcher(s).replaceAll("<#$1$2$3$4$5$6>");
            s = HASH_HEX_REGEX.matcher(s).replaceAll("<#$1>");
            s = s
                    .replace("§l", "<bold>").replace("§L", "<bold>")
                    .replace("&l", "<bold>").replace("&L", "<bold>")
                    .replace("§o", "<italic>").replace("§O", "<italic>")
                    .replace("&o", "<italic>").replace("&O", "<italic>")
                    .replace("§n", "<underlined>").replace("§N", "<underlined>")
                    .replace("&n", "<underlined>").replace("&N", "<underlined>")
                    .replace("§m", "<strikethrough>").replace("§M", "<strikethrough>")
                    .replace("&m", "<strikethrough>").replace("&M", "<strikethrough>")
                    .replace("§k", "<obfuscated>").replace("§K", "<obfuscated>")
                    .replace("&k", "<obfuscated>").replace("&K", "<obfuscated>")
                    .replace("§r", "<reset>").replace("§R", "<reset>")
                    .replace("&r", "<reset>").replace("&R", "<reset>")
                    .replace("§0", "<black>").replace("&0", "<black>")
                    .replace("§1", "<dark_blue>").replace("&1", "<dark_blue>")
                    .replace("§2", "<dark_green>").replace("&2", "<dark_green>")
                    .replace("§3", "<dark_aqua>").replace("&3", "<dark_aqua>")
                    .replace("§4", "<dark_red>").replace("&4", "<dark_red>")
                    .replace("§5", "<dark_purple>").replace("&5", "<dark_purple>")
                    .replace("§6", "<gold>").replace("&6", "<gold>")
                    .replace("§7", "<gray>").replace("&7", "<gray>")
                    .replace("§8", "<dark_gray>").replace("&8", "<dark_gray>")
                    .replace("§9", "<blue>").replace("&9", "<blue>")
                    .replace("§a", "<green>").replace("&a", "<green>")
                    .replace("§b", "<aqua>").replace("&b", "<aqua>")
                    .replace("§c", "<red>").replace("&c", "<red>")
                    .replace("§d", "<light_purple>").replace("&d", "<light_purple>")
                    .replace("§e", "<yellow>").replace("&e", "<yellow>")
                    .replace("§f", "<white>").replace("&f", "<white>");
        }
        return s;
    }

    /** 語系模板著色（可含 MiniMessage） */
    public static String colorize(String text) {
        if (text == null) return "";
        String normalized = preprocessForMiniMessage(text);
        if (normalized.contains("<")) {
            try {
                return LEGACY.serialize(MINI.deserialize(normalized));
            } catch (Exception e) {
                return escapePlain(text);
            }
        }
        return normalized.replace('&', '§');
    }

    /** 玩家輸入的純文字，避免被當成 MiniMessage / 色碼破壞 GUI */
    public static String escapePlain(String text) {
        if (text == null) return "";
        return text.replace("&", "＆").replace("<", "‹").replace(">", "›");
    }

    public static void send(Player player, String message) {
        if (message == null || message.isEmpty()) return;
        String normalized = preprocessForMiniMessage(message);
        if (normalized.contains("<") && normalized.contains(">")) {
            try {
                player.sendMessage(MINI.deserialize(normalized));
                return;
            } catch (Exception ignored) {
                // fall through
            }
        }
        player.sendMessage(colorize(normalized));
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
        String normalized = preprocessForMiniMessage(message);
        if (normalized.contains("<")) {
            try {
                return MINI.deserialize(normalized);
            } catch (Exception e) {
                return LEGACY.deserialize(escapePlain(message));
            }
        }
        return LEGACY.deserialize(colorize(normalized));
    }
}
