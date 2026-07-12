package com.avery.atrain.manager;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatInputManager {

    public enum Type {
        STOP_NAME,
        STOP_INFO_PREV,
        STOP_INFO_NEXT,
        STOP_KEY_STATION,
        STOP_KEY_DIRECTION,
        STOP_ADMIN_INFO,
        LINE_CREATE,
        LINE_RENAME
    }

    public record Pending(Type type, String contextId) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public void setPending(Player player, Type type, String contextId) {
        pending.put(player.getUniqueId(), new Pending(type, contextId));
    }

    public Pending getPending(Player player) {
        return pending.get(player.getUniqueId());
    }

    public void clear(Player player) {
        pending.remove(player.getUniqueId());
    }

    public void clearAll() {
        pending.clear();
    }

    public boolean hasPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }
}
