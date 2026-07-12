package com.avery.atrain.manager;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Stop;
import org.bukkit.Location;

import java.util.*;

public class StopManager {
    private final AtrainPlugin plugin;

    public StopManager(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public Collection<Stop> getAllStops() {
        return plugin.getDataStore().getStops().values();
    }

    public Stop getStop(String id) {
        return id == null ? null : plugin.getDataStore().getStops().get(id);
    }

    public boolean createStop(String id, String displayName, Location c1, Location c2) {
        if (getStop(id) != null) return false;
        plugin.getDataStore().getStops().put(id, new Stop(id, displayName, c1, c2));
        plugin.getDataStore().save();
        return true;
    }

    public boolean deleteStop(String id) {
        if (plugin.getDataStore().getStops().remove(id) == null) return false;
        for (var line : plugin.getLineManager().getAllLines()) {
            line.getStopIds().remove(id);
        }
        plugin.getDataStore().save();
        return true;
    }

    public Stop getStopContaining(Location loc) {
        if (loc == null) return null;
        for (Stop stop : getAllStops()) {
            if (stop.contains(loc)) return stop;
        }
        return null;
    }

    public void setStopPoint(String stopId, Location loc) {
        setBoardPoint(stopId, com.avery.atrain.model.PlatformSide.FORWARD, loc);
    }

    public void setBoardPoint(String stopId, com.avery.atrain.model.PlatformSide side, Location loc) {
        Stop stop = getStop(stopId);
        if (stop == null) return;
        stop.setBoardPoint(side, loc);
        plugin.getDataStore().save();
    }

    public com.avery.atrain.util.StopPlatformUtil.ConflictLevel getPlatformConflict(String stopId) {
        Stop stop = getStop(stopId);
        return stop == null ? com.avery.atrain.util.StopPlatformUtil.ConflictLevel.OK
                : com.avery.atrain.util.StopPlatformUtil.checkConflict(stop);
    }
}
