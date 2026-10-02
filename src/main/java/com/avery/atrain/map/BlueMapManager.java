package com.avery.atrain.map;

import com.avery.atrain.AtrainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * 地圖管理器（BlueMap 整合管理）。
 * 本類別不直接引用任何 BlueMap API 類別，確保在未安裝 BlueMap 的伺服器上能安全載入與執行，
 * 徹底杜絕 NoClassDefFoundError。
 */
public class BlueMapManager {

    private final AtrainPlugin plugin;
    private final MapHook hook;
    private final boolean available;

    public BlueMapManager(AtrainPlugin plugin) {
        this.plugin = plugin;
        this.available = checkBlueMap();
        if (this.available) {
            MapHook createdHook = null;
            try {
                createdHook = new BlueMapHook(plugin);
                createdHook.register();
                plugin.getLogger().info("已成功啟用 BlueMap 地圖標記整合。");
            } catch (Throwable t) {
                plugin.getLogger().warning("初始化 BlueMap 整合時發生錯誤: " + t.getMessage());
                createdHook = null;
            }
            this.hook = createdHook;
        } else {
            this.hook = null;
            plugin.getLogger().info("未偵測到 BlueMap 插件或其 API，已停用地圖標記功能。");
        }
    }

    /**
     * 檢查伺服器是否安裝了 BlueMap 且能安全載入其 API。
     */
    private boolean checkBlueMap() {
        try {
            Plugin bmPlugin = Bukkit.getPluginManager().getPlugin("BlueMap");
            if (bmPlugin == null) {
                return false;
            }
            Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * BlueMap 是否可用且已就緒。
     */
    public boolean isAvailable() {
        return available && hook != null && hook.isApiEnabled();
    }

    /**
     * 安全請求更新地圖標記（若未安裝 BlueMap 則自動略過）。
     */
    public void updateMap() {
        if (hook != null) {
            try {
                hook.updateMap();
            } catch (Throwable t) {
                plugin.getLogger().warning("更新 BlueMap 標記時發生錯誤: " + t.getMessage());
            }
        }
    }

    /**
     * 停用並清理地圖掛鉤。
     */
    public void disable() {
        if (hook != null) {
            try {
                hook.disable();
            } catch (Throwable t) {
                // 停用時安全略過
            }
        }
    }
}
