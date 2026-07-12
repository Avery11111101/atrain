package com.avery.atrain.manager;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.StationUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

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

    public Stop getStopAtRail(Location loc) {
        if (loc == null) return null;
        for (Stop stop : getAllStops()) {
            if (stop.containsRail(loc)) return stop;
        }
        return null;
    }

    public Stop getStopByDisplay(Location loc) {
        if (loc == null) return null;
        Location feet = loc.clone();
        for (Stop stop : getAllStops()) {
            if (stop.containsInfoLocation(feet)) return stop;
        }
        return null;
    }

    public Stop findStopOwningGold(Block goldBlock) {
        if (goldBlock == null) return null;
        String key = Stop.key(goldBlock.getX(), goldBlock.getY(), goldBlock.getZ());
        for (Stop stop : getAllStops()) {
            if (stop.getGoldBlocks().contains(key)) return stop;
        }
        return null;
    }

    /** 從點擊的金磚/軌道掃描並建立或更新站點 */
    public Stop registerFromBlock(Block clicked, String defaultName) {
        Block gold = StationUtil.resolveGoldBlock(clicked);
        if (gold == null) return null;

        Stop existing = findStopOwningGold(gold);
        if (existing != null) return existing;

        Set<String> goldKeys = StationUtil.scanConnectedGoldPlatform(gold);
        if (goldKeys.isEmpty()) return null;

        String world = gold.getWorld().getName();
        Set<String> displayKeys = StationUtil.scanAdjacentDisplayBlocks(goldKeys, world);

        String id = "stop_" + UUID.randomUUID().toString().substring(0, 8);
        Stop stop = new Stop(id, defaultName, world);
        stop.setGoldBlocks(new ArrayList<>(goldKeys));
        stop.setDisplayBlocks(new ArrayList<>(displayKeys));
        stop.setDwellTimeTicks(plugin.getConfigManager().getDefaultDwellTime());

        plugin.getDataStore().getStops().put(id, stop);
        plugin.getDataStore().save();
        return stop;
    }

    public boolean createStop(Stop stop) {
        if (stop == null || getStop(stop.getId()) != null) return false;
        plugin.getDataStore().getStops().put(stop.getId(), stop);
        plugin.getDataStore().save();
        return true;
    }

    public boolean deleteStop(String id) {
        if (plugin.getDataStore().getStops().remove(id) == null) return false;
        for (Line line : plugin.getLineManager().getAllLines()) {
            line.getStopIds().remove(id);
        }
        plugin.getDataStore().save();
        return true;
    }

    public void rescanDisplayBlocks(Stop stop) {
        if (stop == null) return;
        Set<String> displays = StationUtil.scanAdjacentDisplayBlocks(
                new LinkedHashSet<>(stop.getGoldBlocks()), stop.getWorld());
        stop.setDisplayBlocks(new ArrayList<>(displays));
        plugin.getDataStore().save();
    }

    public String getPrevStopName(Line line, String stopId) {
        if (line == null) return "-";
        int idx = line.getStopIds().indexOf(stopId);
        if (idx < 0) return "-";
        if (idx == 0) {
            if (line.isCircular() && !line.getStopIds().isEmpty()) {
                return nameOf(line.getStopIds().get(line.getStopIds().size() - 1));
            }
            return "-";
        }
        return nameOf(line.getStopIds().get(idx - 1));
    }

    public String getNextStopName(Line line, String stopId) {
        if (line == null) return "-";
        int idx = line.getStopIds().indexOf(stopId);
        if (idx < 0) return "-";
        if (idx + 1 < line.getStopIds().size()) return nameOf(line.getStopIds().get(idx + 1));
        if (line.isCircular() && !line.getStopIds().isEmpty()) return nameOf(line.getStopIds().get(0));
        return "終點";
    }

    private String nameOf(String stopId) {
        Stop s = getStop(stopId);
        return s != null ? s.getDisplayName() : stopId;
    }
}
