package com.avery.atrain.hangrail;

import com.avery.atrain.AtrainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Minecart;
import org.bukkit.scheduler.BukkitTask;

/**
 * 懸浮軌道每 tick 主動吊住任務。
 * 取代原本依賴 {@code VehicleMoveEvent} 的做法——事件僅在礦車移動時觸發，
 * 靜止或剛掉落的礦車會抓不到而墜落。改為每 tick 輪詢即可穩定吊住。
 */
public class HangRailTask {

    private final AtrainPlugin plugin;
    private final HangRailHandler handler;
    private BukkitTask task;

    public HangRailTask(AtrainPlugin plugin, HangRailHandler handler) {
        this.plugin = plugin;
        this.handler = handler;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!plugin.getConfigManager().isHangRailEnabled()) {
            // 功能被關閉（含 GUI 切換）時，還原先前被吊住礦車的重力，避免永久漂浮
            handler.restoreAll();
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
                if (!cart.isValid() || cart.isDead()) continue;
                handler.hold(cart);
            }
        }
    }
}
