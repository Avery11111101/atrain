package com.avery.atrain.util;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.SpeedBlock;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.RayTraceResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 調速方塊互動：解析目標方塊、開啟 GUI（下一 tick，相容 Paper 1.21） */
public final class SpeedBlockInteract {

    private static final Map<UUID, Long> openedThisTick = new ConcurrentHashMap<>();

    private SpeedBlockInteract() {}

    /** 從互動事件或視線射線取得被點擊的方塊 */
    public static Block resolveClickedBlock(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block != null) return block;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return null;
        RayTraceResult hit = event.getPlayer().rayTraceBlocks(6);
        return hit != null ? hit.getHitBlock() : null;
    }

    /** 玩家視線前方方塊（供 /train speed 使用） */
    public static Block rayTarget(Player player, int range) {
        RayTraceResult hit = player.rayTraceBlocks(range);
        return hit != null ? hit.getHitBlock() : null;
    }

    /**
     * 嘗試對調速方塊開啟編輯 GUI。
     * @return 是否成功排程開啟
     */
    public static boolean tryOpenEditor(AtrainPlugin plugin, Player player, Block clicked) {
        if (clicked == null) return false;

        // 同 tick 去重（主手/副手各觸發一次時只開一次）
        long tick = plugin.getServer().getCurrentTick();
        if (openedThisTick.put(player.getUniqueId(), tick) == tick) return true;

        Block speedBlock = StationUtil.resolveSpeedBlock(clicked);
        if (speedBlock == null) {
            if (StationUtil.isSpeedBlock(clicked.getType())) {
                TextUtil.send(player, plugin.getLanguageManager().get(player, "speed_block.need_rail_above"));
            }
            return false;
        }

        if (!player.hasPermission("atrain.speed.edit")) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "error.no_permission"));
            return false;
        }

        var cfg = plugin.getConfigManager();
        SpeedBlock sb = plugin.getSpeedBlockManager().getOrCreate(
                speedBlock, cfg.getSpeedBlockDefault(), cfg.getSpeedBlockDefaultRamp());
        String blockKey = sb.key();

        player.sendActionBar(TextUtil.component(
                plugin.getLanguageManager().get(player, "speed_block.opening")));

        // Paper 1.21：同 tick 開背包常失敗，延到下一 tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.getGuiManager().openSpeedBlockEdit(player, blockKey);
        });
        return true;
    }

    /** 設定 Paper 互動結果，避免手持物品搶走右鍵 */
    public static void claimInteraction(PlayerInteractEvent event) {
        try {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.ALLOW);
        } catch (NoSuchMethodError ignored) {
            // 舊版 API 無此方法
        }
        event.setCancelled(true);
    }
}
