package com.avery.atrain.util;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.hangrail.HangRailInfo;
import com.avery.atrain.hangrail.HangRailUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * 判斷位置是否在可運行軌道上（一般軌道 + 懸浮軌道）。
 */
public final class TrackUtil {

    private TrackUtil() {}

    public static boolean isOnTrack(AtrainPlugin plugin, Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (RailUtil.isOnRail(loc)) return true;
        if (!plugin.getConfigManager().isHangRailEnabled()) return false;
        return HangRailUtil.findHangRail(loc, plugin.getConfigManager().getHangRailTypes()) != null;
    }

    public static boolean isBoardableBlock(AtrainPlugin plugin, Block block) {
        if (block == null) return false;
        String name = block.getType().name();
        if (name.contains("RAIL") && RailUtil.isPoweredRail(block.getLocation())) return true;
        if (!plugin.getConfigManager().isHangRailEnabled()) return false;
        return HangRailUtil.findHangRail(block.getLocation(), plugin.getConfigManager().getHangRailTypes()) != null;
    }

    public static Location sampleLocation(AtrainPlugin plugin, Location loc) {
        if (RailUtil.isOnRail(loc)) return RailUtil.snapToRailCenter(loc);
        if (plugin.getConfigManager().isHangRailEnabled()) {
            HangRailInfo hang = HangRailUtil.findHangRail(loc, plugin.getConfigManager().getHangRailTypes());
            if (hang != null) return HangRailUtil.getCartAnchor(hang);
        }
        return loc;
    }
}
