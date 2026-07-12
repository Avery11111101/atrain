package com.avery.atrain.hangrail;

import org.bukkit.Material;

/**
 * 懸浮軌道類型設定（對應 TC-HangRail 的 types 配置）。
 */
public class HangRailType {
    private final Material block;
    private final int offset;

    public HangRailType(Material block, int offset) {
        this.block = block;
        this.offset = offset;
    }

    public Material getBlock() { return block; }
    public int getOffset() { return offset; }
    public boolean isBelowRail() { return offset < 0; }

    public static HangRailType ironBarsDefault() {
        return new HangRailType(Material.IRON_BARS, -2);
    }
}
