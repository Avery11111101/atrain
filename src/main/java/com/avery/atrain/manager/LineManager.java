package com.avery.atrain.manager;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import org.bukkit.Location;

import java.util.*;

public class LineManager {
    private final AtrainPlugin plugin;

    public LineManager(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public Collection<Line> getAllLines() {
        return plugin.getDataStore().getLines().values();
    }

    public Line getLine(String id) {
        return id == null ? null : plugin.getDataStore().getLines().get(id);
    }

    public boolean createLine(String id, String displayName) {
        if (getLine(id) != null) return false;
        plugin.getDataStore().getLines().put(id, new Line(id, displayName));
        plugin.getDataStore().save();
        return true;
    }

    public boolean deleteLine(String id) {
        if (plugin.getDataStore().getLines().remove(id) == null) return false;
        for (var stop : plugin.getStopManager().getAllStops()) {
            stop.getLineIds().remove(id);
        }
        plugin.getDataStore().save();
        return true;
    }

    public void addStopToLine(String lineId, String stopId, int index) {
        Line line = getLine(lineId);
        if (line == null) return;
        line.getStopIds().remove(stopId);
        if (index < 0 || index >= line.getStopIds().size()) {
            line.getStopIds().add(stopId);
        } else {
            line.getStopIds().add(index, stopId);
        }
        var stop = plugin.getStopManager().getStop(stopId);
        if (stop != null && !stop.getLineIds().contains(lineId)) {
            stop.getLineIds().add(lineId);
        }
        plugin.getDataStore().save();
    }

    public void removeStopFromLine(String lineId, String stopId) {
        Line line = getLine(lineId);
        if (line == null) return;
        line.getStopIds().remove(stopId);
        var stop = plugin.getStopManager().getStop(stopId);
        if (stop != null) stop.getLineIds().remove(lineId);
        plugin.getDataStore().save();
    }

    public List<Line> getLinesAtStop(String stopId) {
        List<Line> result = new ArrayList<>();
        for (Line line : getAllLines()) {
            if (line.getStopIds().contains(stopId)) result.add(line);
        }
        return result;
    }
}
