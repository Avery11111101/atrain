package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.SpeedBlockInteract;
import com.avery.atrain.util.StationUtil;
import com.avery.atrain.util.TextUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Map;

/** 金磚站點：蹲下右鍵編輯；一般右鍵召喚原版礦車。軌下鑽石塊：右鍵調速。 */
public class PlayerInteractListener implements Listener {

    private final AtrainPlugin plugin;

    public PlayerInteractListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    /** 調速方塊：最高優先級，即使事件已被取消也處理 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSpeedBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = SpeedBlockInteract.resolveClickedBlock(event);
        if (clicked == null) return;

        // 鑽石塊或鐵軌（且下方柱內有鑽石）才介入
        boolean relevant = StationUtil.isSpeedBlock(clicked.getType())
                || StationUtil.isAnyRail(clicked.getType());
        if (!relevant) return;

        if (StationUtil.resolveSpeedBlock(clicked) == null
                && !StationUtil.isSpeedBlock(clicked.getType())) {
            return; // 一般鐵軌，下方無調速鑽石
        }

        SpeedBlockInteract.claimInteraction(event);
        SpeedBlockInteract.tryOpenEditor(plugin, event.getPlayer(), clicked);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStationInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (StationUtil.resolveSpeedBlock(block) != null) return;

        if (!StationUtil.isGoldStationRail(block)) return;

        Player player = event.getPlayer();
        if (player.isSneaking()) {
            if (plugin.getRouteRecordingManager() != null
                    && plugin.getRouteRecordingManager().isRecording(player)) {
                handleCartSpawn(event, player, block);
                return;
            }
            handleStationEdit(event, player, block);
        } else {
            // 若玩家手持礦車，則不攔截，讓原版行為發生（放置普通礦車）
            org.bukkit.inventory.ItemStack item = event.getItem();
            if (item != null && item.getType().name().contains("MINECART")) {
                return;
            }
            handleCartSpawn(event, player, block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof org.bukkit.entity.minecart.RideableMinecart cart)) return;
        Player player = event.getPlayer();
        if (plugin.getRouteRecordingManager() != null && plugin.getRouteRecordingManager().isRecording(player)) {
            org.bukkit.block.Block rail = com.avery.atrain.util.RailUtil.findRailBlock(cart.getLocation());
            if (rail != null) {
                boolean success = plugin.getRouteRecordingManager().spawnRecordingCart(player, rail);
                if (!success || plugin.getConfigManager().isCartSpawnAutoMount()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private void handleCartSpawn(PlayerInteractEvent event, Player player, Block block) {
        if (plugin.getChatInputManager().hasPending(player)) return;
        event.setCancelled(true);
        plugin.getCartSpawnManager().trySpawn(player, block);
    }

    private void handleStationEdit(PlayerInteractEvent event, Player player, Block block) {
        if (!player.hasPermission("atrain.station.edit")) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "error.no_permission"));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        var bindMgr = plugin.getBindPlatformManager();
        if (bindMgr.isBinding(player)) {
            String targetId = bindMgr.getBindTarget(player);
            bindMgr.clear(player);
            if (targetId != null && plugin.getStopManager().bindReturnPlatform(targetId, block)) {
                TextUtil.send(player, plugin.getLanguageManager().get(player, "stop.return_bound",
                        Map.of("id", targetId)));
                plugin.getGuiManager().openStationEdit(player, targetId);
            } else {
                TextUtil.send(player, plugin.getLanguageManager().get(player, "stop.return_bind_failed"));
            }
            return;
        }

        Block gold = StationUtil.resolveGoldBlock(block);
        if (gold == null) return;

        var stopMgr = plugin.getStopManager();
        String defaultName = plugin.getLanguageManager().get(player, "stop.default_name",
                Map.of("id", gold.getX() + "_" + gold.getZ()));
        boolean existed = stopMgr.findStopOwningGold(gold) != null;
        var result = stopMgr.registerFromBlock(block, defaultName);
        if (result == null || result.stop() == null) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "stop.need_rail_above"));
            return;
        }
        Stop stop = result.stop();
        if (!existed) {
            String msgKey = result.created() ? "stop.created" : "stop.merged";
            TextUtil.send(player, plugin.getLanguageManager().get(player, msgKey,
                    Map.of("id", stop.getId())));
        }
        plugin.getGuiManager().openStationEdit(player, stop.getId());
    }
}
