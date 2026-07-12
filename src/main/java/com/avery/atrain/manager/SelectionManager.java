package com.avery.atrain.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {
    private final Map<UUID, Location> corner1 = new HashMap<>();
    private final Map<UUID, Location> corner2 = new HashMap<>();

    public void setCorner1(Player player, Location loc) {
        corner1.put(player.getUniqueId(), loc.clone());
    }

    public void setCorner2(Player player, Location loc) {
        Location c1 = corner1.get(player.getUniqueId());
        if (c1 != null && c1.getWorld() != null && loc.getWorld() != null
                && !c1.getWorld().equals(loc.getWorld())) {
            return;
        }
        corner2.put(player.getUniqueId(), loc.clone());
    }

    public boolean isSameWorldAsCorner1(Player player, Location loc) {
        Location c1 = corner1.get(player.getUniqueId());
        if (c1 == null || c1.getWorld() == null || loc.getWorld() == null) return true;
        return c1.getWorld().equals(loc.getWorld());
    }

    public Location getCorner1(Player player) {
        return corner1.get(player.getUniqueId());
    }

    public Location getCorner2(Player player) {
        return corner2.get(player.getUniqueId());
    }

    public boolean hasBothCorners(Player player) {
        return corner1.containsKey(player.getUniqueId()) && corner2.containsKey(player.getUniqueId());
    }

    public void clear(Player player) {
        corner1.remove(player.getUniqueId());
        corner2.remove(player.getUniqueId());
    }
}
