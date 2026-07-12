package com.avery.atrain.hangrail;

import org.bukkit.block.Block;

public class HangRailInfo {
    private final Block railBlock;
    private final HangRailType type;

    public HangRailInfo(Block railBlock, HangRailType type) {
        this.railBlock = railBlock;
        this.type = type;
    }

    public Block getRailBlock() { return railBlock; }
    public HangRailType getType() { return type; }
}
