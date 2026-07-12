package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.util.StationUtil;
import com.avery.atrain.util.TextUtil;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/** 調速方塊放置註冊；已註冊金磚/鑽石塊破壞保護（僅管理員可拆） */
public class StationBlockListener implements Listener {

    private final AtrainPlugin plugin;

    public StationBlockListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        var cfg = plugin.getConfigManager();
        var sbMgr = plugin.getSpeedBlockManager();

        // 放置鑽石塊：上方已有鐵軌則註冊
        if (StationUtil.isSpeedBlock(block.getType()) && StationUtil.hasRailAbove(block)) {
            sbMgr.getOrCreate(block, cfg.getSpeedBlockDefault(), cfg.getSpeedBlockDefaultRamp());
            return;
        }

        // 放置鐵軌：下方柱內有鑽石塊則註冊（先放鑽石、後鋪軌也能用）
        if (StationUtil.isAnyRail(block.getType())) {
            Block diamond = StationUtil.findSpeedBlockBelow(block);
            if (diamond != null) {
                sbMgr.getOrCreate(diamond, cfg.getSpeedBlockDefault(), cfg.getSpeedBlockDefaultRamp());
            }
        }
    }

    /** 已註冊的金磚站點、調速鑽石塊僅允許具 atrain.block.break 者破壞 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakProtect(BlockBreakEvent event) {
        if (!(event.getPlayer() instanceof org.bukkit.entity.Player player)) return;
        Block block = event.getBlock();

        boolean protectedGold = StationUtil.isGoldBlock(block.getType())
                && plugin.getStopManager().isProtectedGold(block);
        boolean protectedSpeed = StationUtil.isSpeedBlock(block.getType())
                && plugin.getSpeedBlockManager().getAt(block) != null;

        if (!protectedGold && !protectedSpeed) return;

        if (!player.hasPermission("atrain.block.break")) {
            event.setCancelled(true);
            TextUtil.send(player, plugin.getLanguageManager().get(player, "error.block_protected"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakCleanup(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (StationUtil.isSpeedBlock(block.getType())) {
            plugin.getSpeedBlockManager().removeAt(block);
            return;
        }

        if (StationUtil.isGoldBlock(block.getType())) {
            plugin.getStopManager().unregisterGoldBlock(block);
        }
    }
}
