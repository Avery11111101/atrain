package com.avery.atrain.map;

/**
 * 地圖標記掛鉤介面。
 * 隔離具體地圖插件依賴（例如 BlueMap），避免在未安裝地圖插件時觸發 NoClassDefFoundError。
 */
public interface MapHook {
    /**
     * 註冊地圖 API 監聽器。
     */
    void register();

    /**
     * 更新地圖標記（站點與路線）。
     */
    void updateMap();

    /**
     * 停用並清理地圖掛鉤。
     */
    void disable();

    /**
     * 地圖 API 目前是否已就緒且可用。
     */
    boolean isApiEnabled();
}
