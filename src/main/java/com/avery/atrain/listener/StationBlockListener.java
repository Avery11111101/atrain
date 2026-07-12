package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.StationUtil;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/** 鑽石顯示塊放置/破壞時自動更新鄰近站點 */
public class StationBlockListener implements Listener {

    private final AtrainPlugin plugin;

    public StationBlockListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!StationUtil.isDisplayBlock(event.getBlock().getType())) return;
        rescanNearbyStop(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!StationUtil.isDisplayBlock(event.getBlock().getType())) return;
        Block block = event.getBlock();
        Bukkit.getScheduler().runTask(plugin, () -> rescanNearbyStop(block));
    }

    private void rescanNearbyStop(Block block) {
        Stop stop = plugin.getStopManager().findStopAdjacentTo(block);
        if (stop != null) {
            plugin.getStopManager().rescanDisplayBlocks(stop);
        }
    }
}
