package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.StationUtil;
import com.avery.atrain.util.TextUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;

/** 金磚站點：蹲下右鍵編輯；一般右鍵召喚原版礦車 */
public class PlayerInteractListener implements Listener {

    private final AtrainPlugin plugin;

    public PlayerInteractListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || !StationUtil.isGoldStationRail(block)) return;

        Player player = event.getPlayer();
        if (player.isSneaking()) {
            handleStationEdit(event, player, block);
        } else {
            handleCartSpawn(event, player, block);
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
