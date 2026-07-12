package com.avery.atrain.util;

/** 方塊座標打包工具，避免高頻路徑上的字串拼接與 GC 壓力 */
public final class BlockCoords {

    private static final long INVALID = Long.MIN_VALUE;

    private BlockCoords() {}

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) (y + 512) & 0x3FFL) << 28
                | ((long) z & 0x3FFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    public static int unpackY(long packed) {
        return (int) ((packed >> 28) & 0x3FFL) - 512;
    }

    public static int unpackZ(long packed) {
        return ((int) packed << 6) >> 6;
    }

    public static long fromDataString(String key) {
        if (key == null) return INVALID;
        String[] parts = key.split(",");
        if (parts.length != 3) return INVALID;
        try {
            return pack(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return INVALID;
        }
    }

    public static String toDataString(long packed) {
        return unpackX(packed) + "," + unpackY(packed) + "," + unpackZ(packed);
    }

    public static boolean isValid(long packed) {
        return packed != INVALID;
    }
}
