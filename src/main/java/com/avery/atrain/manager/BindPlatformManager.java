package com.avery.atrain.manager;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 站點編輯：綁定去程與回程金磚月台 */
public class BindPlatformManager {

    private final Map<UUID, String> bindTargetStop = new ConcurrentHashMap<>();

    public void startBind(Player player, String stopId) {
        bindTargetStop.put(player.getUniqueId(), stopId);
    }

    public String getBindTarget(Player player) {
        return bindTargetStop.get(player.getUniqueId());
    }

    public boolean isBinding(Player player) {
        return bindTargetStop.containsKey(player.getUniqueId());
    }

    public void clear(Player player) {
        bindTargetStop.remove(player.getUniqueId());
    }

    public void clearAll() {
        bindTargetStop.clear();
    }
}
