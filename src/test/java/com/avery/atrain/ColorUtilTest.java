package com.avery.atrain;

import com.avery.atrain.util.ColorUtil;
import de.bluecolored.bluemap.api.math.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ColorUtilTest {

    public static void main(String[] args) {
        ColorUtilTest test = new ColorUtilTest();
        test.testHex6Color();
        test.testVariousHexPrefixes();
        test.testLegacyMinecraftHex();
        test.testHex3();
        test.testLegacyMinecraftColorCodes();
        test.testInvalidFallback();
        System.out.println("ALL COLOR UTIL TESTS PASSED SUCCESSFULLY!");
    }
    @Test
    @DisplayName("測試 Hex 6位色碼解析 (#f1f5f5)")
    void testHex6Color() {
        int[] rgba = ColorUtil.parseRgba("#f1f5f5");
        assertEquals(0xf1, rgba[0]);
        assertEquals(0xf5, rgba[1]);
        assertEquals(0xf5, rgba[2]);
        assertEquals(255, rgba[3]);

        Color bmColor = ColorUtil.toBlueMapColor("#f1f5f5");
        assertEquals(0xf1, bmColor.getRed());
        assertEquals(0xf5, bmColor.getGreen());
        assertEquals(0xf5, bmColor.getBlue());
        assertEquals(255, bmColor.getAlpha());

        assertEquals("<#f1f5f5>", ColorUtil.toMiniMessageTag("#f1f5f5"));
        assertEquals("§x§f§1§f§5§f§5", ColorUtil.toLegacyPrefix("#f1f5f5"));
        assertTrue(ColorUtil.isValidColor("#f1f5f5"));
    }

    @Test
    @DisplayName("測試其他 Hex 前綴格式 (&#f1f5f5, §#f1f5f5, 0xf1f5f5, f1f5f5)")
    void testVariousHexPrefixes() {
        int[] expected = new int[]{0xf1, 0xf5, 0xf5, 255};

        assertArrayEquals(expected, ColorUtil.parseRgba("&#f1f5f5"));
        assertArrayEquals(expected, ColorUtil.parseRgba("§#f1f5f5"));
        assertArrayEquals(expected, ColorUtil.parseRgba("0xf1f5f5"));
        assertArrayEquals(expected, ColorUtil.parseRgba("f1f5f5"));
        assertArrayEquals(expected, ColorUtil.parseRgba("<#f1f5f5>"));

        assertTrue(ColorUtil.isValidColor("&#f1f5f5"));
        assertTrue(ColorUtil.isValidColor("§#f1f5f5"));
        assertTrue(ColorUtil.isValidColor("f1f5f5"));
    }

    @Test
    @DisplayName("測試 Minecraft 1.16+ 傳統 Hex 格式 (§x§f§1§f§5§f§5)")
    void testLegacyMinecraftHex() {
        int[] rgba = ColorUtil.parseRgba("§x§f§1§f§5§f§5");
        assertEquals(0xf1, rgba[0]);
        assertEquals(0xf5, rgba[1]);
        assertEquals(0xf5, rgba[2]);
        assertTrue(ColorUtil.isValidColor("§x§f§1§f§5§f§5"));
    }

    @Test
    @DisplayName("測試 3 位 Hex 縮寫 (#fff, #abc)")
    void testHex3() {
        int[] rgba = ColorUtil.parseRgba("#fff");
        assertEquals(255, rgba[0]);
        assertEquals(255, rgba[1]);
        assertEquals(255, rgba[2]);
        assertTrue(ColorUtil.isValidColor("#fff"));
        assertEquals("#ffffff", ColorUtil.normalizeColor("#fff"));
    }

    @Test
    @DisplayName("測試原版 Minecraft 色碼 (&a, §a, a, &c, red)")
    void testLegacyMinecraftColorCodes() {
        // §a 綠色 (85, 255, 85)
        int[] green = ColorUtil.parseRgba("§a");
        assertEquals(85, green[0]);
        assertEquals(255, green[1]);
        assertEquals(85, green[2]);

        int[] red = ColorUtil.parseRgba("&c");
        assertEquals(255, red[0]);
        assertEquals(85, red[1]);
        assertEquals(85, red[2]);

        assertTrue(ColorUtil.isValidColor("&a"));
        assertTrue(ColorUtil.isValidColor("§c"));
        assertTrue(ColorUtil.isValidColor("red"));
        assertTrue(ColorUtil.isValidColor("gold"));
    }

    @Test
    @DisplayName("測試無效輸入與回退")
    void testInvalidFallback() {
        assertFalse(ColorUtil.isValidColor("invalid_color_123"));
        assertFalse(ColorUtil.isValidColor(""));
        assertFalse(ColorUtil.isValidColor(null));

        int[] fallback = ColorUtil.parseRgba("invalid_string");
        assertEquals(100, fallback[0]);
        assertEquals(255, fallback[1]);
        assertEquals(100, fallback[2]);
    }
}
