package com.avery.atrain.util;

import de.bluecolored.bluemap.api.math.Color;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 顏色工具類別：支援十六進位色碼 (#RRGGBB, #RGB, &#RRGGBB, §#RRGGBB, §x§r... 等)、
 * Minecraft 傳統色碼與 BlueMap Color 轉換。
 */
public final class ColorUtil {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^[0-9a-fA-F]{6}$");
    private static final Pattern HEX_3_PATTERN = Pattern.compile("^[0-9a-fA-F]{3}$");
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("[§&]x(?:[§&]([0-9a-fA-F])){6}", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINIMESSAGE_COLOR_TAG = Pattern.compile("^<#([0-9a-fA-F]{6})>$", Pattern.CASE_INSENSITIVE);

    private static final Map<Character, int[]> MC_COLORS = new HashMap<>();
    private static final Map<String, int[]> NAMED_COLORS = new HashMap<>();
    private static final Map<Character, String> MC_TO_MINIMESSAGE = new HashMap<>();

    static {
        // Minecraft 16 原版色碼對應 RGB
        registerMcColor('0', 0, 0, 0, "<black>");
        registerMcColor('1', 0, 0, 170, "<dark_blue>");
        registerMcColor('2', 0, 170, 0, "<dark_green>");
        registerMcColor('3', 0, 170, 170, "<dark_aqua>");
        registerMcColor('4', 170, 0, 0, "<dark_red>");
        registerMcColor('5', 170, 0, 170, "<dark_purple>");
        registerMcColor('6', 255, 170, 0, "<gold>");
        registerMcColor('7', 170, 170, 170, "<gray>");
        registerMcColor('8', 85, 85, 85, "<dark_gray>");
        registerMcColor('9', 85, 85, 255, "<blue>");
        registerMcColor('a', 85, 255, 85, "<green>");
        registerMcColor('b', 85, 255, 255, "<aqua>");
        registerMcColor('c', 255, 85, 85, "<red>");
        registerMcColor('d', 255, 85, 255, "<light_purple>");
        registerMcColor('e', 255, 255, 85, "<yellow>");
        registerMcColor('f', 255, 255, 255, "<white>");

        // 常見顏色名稱
        NAMED_COLORS.put("black", new int[]{0, 0, 0, 255});
        NAMED_COLORS.put("dark_blue", new int[]{0, 0, 170, 255});
        NAMED_COLORS.put("dark_green", new int[]{0, 170, 0, 255});
        NAMED_COLORS.put("dark_aqua", new int[]{0, 170, 170, 255});
        NAMED_COLORS.put("dark_red", new int[]{170, 0, 0, 255});
        NAMED_COLORS.put("dark_purple", new int[]{170, 0, 170, 255});
        NAMED_COLORS.put("purple", new int[]{170, 0, 170, 255});
        NAMED_COLORS.put("gold", new int[]{255, 170, 0, 255});
        NAMED_COLORS.put("orange", new int[]{255, 170, 0, 255});
        NAMED_COLORS.put("gray", new int[]{170, 170, 170, 255});
        NAMED_COLORS.put("grey", new int[]{170, 170, 170, 255});
        NAMED_COLORS.put("dark_gray", new int[]{85, 85, 85, 255});
        NAMED_COLORS.put("dark_grey", new int[]{85, 85, 85, 255});
        NAMED_COLORS.put("blue", new int[]{85, 85, 255, 255});
        NAMED_COLORS.put("green", new int[]{85, 255, 85, 255});
        NAMED_COLORS.put("aqua", new int[]{85, 255, 255, 255});
        NAMED_COLORS.put("cyan", new int[]{85, 255, 255, 255});
        NAMED_COLORS.put("red", new int[]{255, 85, 85, 255});
        NAMED_COLORS.put("light_purple", new int[]{255, 85, 255, 255});
        NAMED_COLORS.put("pink", new int[]{255, 85, 255, 255});
        NAMED_COLORS.put("yellow", new int[]{255, 255, 85, 255});
        NAMED_COLORS.put("white", new int[]{255, 255, 255, 255});
    }

    private static void registerMcColor(char code, int r, int g, int b, String miniTag) {
        MC_COLORS.put(code, new int[]{r, g, b, 255});
        MC_TO_MINIMESSAGE.put(code, miniTag);
    }

    private ColorUtil() {}

    /**
     * 解析任意顏色字串為 RGBA 陣列 [r, g, b, a]。若解析失敗回傳預設亮綠色 [100, 255, 100, 255]。
     */
    public static int[] parseRgba(String colorStr) {
        if (colorStr == null || colorStr.isBlank()) {
            return new int[]{100, 255, 100, 255};
        }
        String clean = colorStr.trim();

        // 1. 處理 §x§r§r§g§g§b§b 格式
        Matcher legMatcher = LEGACY_HEX_PATTERN.matcher(clean);
        if (legMatcher.find()) {
            String hex = clean.replaceAll("[§&xX]", "");
            if (hex.length() == 6) {
                try {
                    int rgb = Integer.parseInt(hex, 16);
                    return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255};
                } catch (NumberFormatException ignored) {}
            }
        }

        // 2. 移除周圍符號與 MiniMessage 標籤 (<#f1f5f5>, <color:#f1f5f5>)
        if (clean.startsWith("<") && clean.endsWith(">")) {
            clean = clean.substring(1, clean.length() - 1);
            if (clean.toLowerCase(Locale.ROOT).startsWith("color:")) {
                clean = clean.substring(6);
            }
        }

        // 3. 處理 &#RRGGBB 或 §#RRGGBB
        if (clean.startsWith("&#") || clean.startsWith("§#")) {
            clean = clean.substring(1);
        }

        // 4. 處理 0x 或 0X
        if (clean.toLowerCase(Locale.ROOT).startsWith("0x")) {
            clean = clean.substring(2);
        }

        // 5. 處理 #RRGGBB 或 #RGB 或 #AARRGGBB
        if (clean.startsWith("#")) {
            String hex = clean.substring(1);
            if (HEX_COLOR_PATTERN.matcher(hex).matches()) {
                try {
                    int rgb = Integer.parseInt(hex, 16);
                    return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255};
                } catch (NumberFormatException ignored) {}
            } else if (HEX_3_PATTERN.matcher(hex).matches()) {
                // #RGB -> #RRGGBB
                char r = hex.charAt(0);
                char g = hex.charAt(1);
                char b = hex.charAt(2);
                String expanded = "" + r + r + g + g + b + b;
                try {
                    int rgb = Integer.parseInt(expanded, 16);
                    return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255};
                } catch (NumberFormatException ignored) {}
            } else if (hex.length() == 8) {
                // #AARRGGBB
                try {
                    long val = Long.parseLong(hex, 16);
                    int a = (int) ((val >> 24) & 0xFF);
                    int r = (int) ((val >> 16) & 0xFF);
                    int g = (int) ((val >> 8) & 0xFF);
                    int b = (int) (val & 0xFF);
                    return new int[]{r, g, b, a};
                } catch (NumberFormatException ignored) {}
            }
        }

        // 6. 純 6 位 Hex 字串 (f1f5f5)
        if (HEX_COLOR_PATTERN.matcher(clean).matches()) {
            try {
                int rgb = Integer.parseInt(clean, 16);
                return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255};
            } catch (NumberFormatException ignored) {}
        }

        // 7. Minecraft 原版色碼 (&a, §a, a)
        char lastChar = Character.toLowerCase(clean.charAt(clean.length() - 1));
        if (MC_COLORS.containsKey(lastChar) && (clean.length() <= 2 || clean.startsWith("§") || clean.startsWith("&"))) {
            return MC_COLORS.get(lastChar);
        }

        // 8. 具名顏色名稱 (red, green, dark_blue 等)
        String lower = clean.toLowerCase(Locale.ROOT);
        if (NAMED_COLORS.containsKey(lower)) {
            return NAMED_COLORS.get(lower);
        }

        // 預設回退亮綠色
        return new int[]{100, 255, 100, 255};
    }

    /**
     * 將顏色字串轉為 BlueMap Color 物件。
     */
    public static Color toBlueMapColor(String colorStr) {
        int[] rgba = parseRgba(colorStr);
        return new Color(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    /**
     * 檢查輸入的字串是否為合法的顏色設定（Hex 或 MC 原版色碼/名稱）。
     */
    public static boolean isValidColor(String input) {
        if (input == null || input.isBlank()) return false;
        String clean = input.trim();

        if (clean.startsWith("#")) {
            String hex = clean.substring(1);
            return HEX_COLOR_PATTERN.matcher(hex).matches() || HEX_3_PATTERN.matcher(hex).matches();
        }
        if (clean.startsWith("&#") || clean.startsWith("§#")) {
            String hex = clean.substring(2);
            return HEX_COLOR_PATTERN.matcher(hex).matches() || HEX_3_PATTERN.matcher(hex).matches();
        }
        if (HEX_COLOR_PATTERN.matcher(clean).matches()) {
            return true;
        }
        if (LEGACY_HEX_PATTERN.matcher(clean).matches()) {
            return true;
        }
        if ((clean.startsWith("&") || clean.startsWith("§")) && clean.length() == 2) {
            char c = Character.toLowerCase(clean.charAt(1));
            return MC_COLORS.containsKey(c);
        }
        if (clean.length() == 1) {
            return MC_COLORS.containsKey(Character.toLowerCase(clean.charAt(0)));
        }
        return NAMED_COLORS.containsKey(clean.toLowerCase(Locale.ROOT));
    }

    /**
     * 正規化顏色輸入字串（例如將 #F1F5F5 轉為 #f1f5f5，&a 轉為 §a）。
     */
    public static String normalizeColor(String input) {
        if (input == null || input.isBlank()) return "§a";
        String clean = input.trim();

        if (clean.startsWith("&#") || clean.startsWith("§#")) {
            clean = clean.substring(1);
        }
        if (clean.startsWith("#")) {
            String hex = clean.substring(1).toLowerCase(Locale.ROOT);
            if (HEX_3_PATTERN.matcher(hex).matches()) {
                char r = hex.charAt(0);
                char g = hex.charAt(1);
                char b = hex.charAt(2);
                return "#" + r + r + g + g + b + b;
            }
            return "#" + hex;
        }
        if (HEX_COLOR_PATTERN.matcher(clean).matches()) {
            return "#" + clean.toLowerCase(Locale.ROOT);
        }
        if (clean.startsWith("&") && clean.length() == 2) {
            return "§" + Character.toLowerCase(clean.charAt(1));
        }
        if (clean.length() == 1 && MC_COLORS.containsKey(Character.toLowerCase(clean.charAt(0)))) {
            return "§" + Character.toLowerCase(clean.charAt(0));
        }
        return clean;
    }

    /**
     * 轉換成 MiniMessage 顏色標籤（如 <#f1f5f5> 或 <green>）。
     */
    public static String toMiniMessageTag(String colorStr) {
        if (colorStr == null || colorStr.isBlank()) return "<green>";
        String clean = colorStr.trim();

        if (clean.startsWith("#")) {
            String hex = clean.substring(1);
            if (hex.length() == 3) {
                char r = hex.charAt(0);
                char g = hex.charAt(1);
                char b = hex.charAt(2);
                hex = "" + r + r + g + g + b + b;
            }
            return "<#" + hex + ">";
        }
        if (clean.startsWith("&#") || clean.startsWith("§#")) {
            return "<#" + clean.substring(2) + ">";
        }
        if (HEX_COLOR_PATTERN.matcher(clean).matches()) {
            return "<#" + clean + ">";
        }
        if (clean.startsWith("§") || clean.startsWith("&")) {
            char code = Character.toLowerCase(clean.charAt(clean.length() - 1));
            if (MC_TO_MINIMESSAGE.containsKey(code)) {
                return MC_TO_MINIMESSAGE.get(code);
            }
        }
        if (clean.length() == 1) {
            char code = Character.toLowerCase(clean.charAt(0));
            if (MC_TO_MINIMESSAGE.containsKey(code)) {
                return MC_TO_MINIMESSAGE.get(code);
            }
        }
        return "<" + clean.toLowerCase(Locale.ROOT) + ">";
    }

    /**
     * 轉換成 Minecraft 傳統/相容字串前綴（例如 §x§f§1§f§5§f§5 或 §a）。
     */
    public static String toLegacyPrefix(String colorStr) {
        if (colorStr == null || colorStr.isBlank()) return "§a";
        String clean = colorStr.trim();
        if (clean.startsWith("§") && clean.length() == 2) return clean;
        if (clean.startsWith("&") && clean.length() == 2) return "§" + clean.charAt(1);

        int[] rgba = parseRgba(colorStr);
        String hex = String.format("%02x%02x%02x", rgba[0], rgba[1], rgba[2]);
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            sb.append('§').append(c);
        }
        return sb.toString();
    }
}
