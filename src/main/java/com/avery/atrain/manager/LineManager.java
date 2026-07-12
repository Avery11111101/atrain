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
            if (id.equals(stop.getDisplayLineId())) {
                stop.setDisplayLineId(null);
            }
            if (id.equals(stop.getReturnLineId())) {
                stop.setReturnLineId(null);
            }
        }
        plugin.getDataStore().save();
        return true;
    }

    public void addStopToLine(String lineId, String stopId, int index) {
        Line line = getLine(lineId);
        if (line == null) return;
        boolean wasInLine = line.getStopIds().contains(stopId);
        int sizeAfterRemove = line.getStopIds().size();
        if (wasInLine) sizeAfterRemove--;
        line.getStopIds().remove(stopId);
        boolean appendOnly = !wasInLine && (index < 0 || index >= sizeAfterRemove);
        if (index < 0 || index >= line.getStopIds().size()) {
            line.getStopIds().add(stopId);
        } else {
            line.getStopIds().add(index, stopId);
        }
        if (!appendOnly) {
            line.clearAllSegments();
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
        line.clearAllSegments();
        var stop = plugin.getStopManager().getStop(stopId);
        if (stop != null) {
            stop.getLineIds().remove(lineId);
            if (lineId.equals(stop.getDisplayLineId())) {
                stop.setDisplayLineId(null);
            }
        }
        plugin.getDataStore().save();
    }

    public Line createLineAuto(String displayName) {
        String id;
        do {
            id = "line_" + UUID.randomUUID().toString().substring(0, 8);
        } while (getLine(id) != null);
        String name = (displayName != null && !displayName.isBlank())
                ? displayName.trim()
                : id;
        createLine(id, name);
        return getLine(id);
    }

    public void renameLine(String lineId, String newName) {
        Line line = getLine(lineId);
        if (line == null || newName == null || newName.isBlank()) return;
        line.setDisplayName(newName.trim());
        plugin.getDataStore().save();
    }

    public void toggleCircular(String lineId) {
        Line line = getLine(lineId);
        if (line == null) return;
        line.setCircular(!line.isCircular());
        plugin.getDataStore().save();
    }

    public boolean moveStopInLine(String lineId, String stopId, int delta) {
        Line line = getLine(lineId);
        if (line == null) return false;
        List<String> ids = line.getStopIds();
        int idx = ids.indexOf(stopId);
        if (idx < 0) return false;
        int newIdx = idx + delta;
        if (newIdx < 0 || newIdx >= ids.size()) return false;
        String item = ids.remove(idx);
        ids.add(newIdx, item);
        line.clearAllSegments();
        plugin.getDataStore().save();
        return true;
    }

    public List<Line> getLinesAtStop(String stopId) {
        List<Line> result = new ArrayList<>();
        for (Line line : getAllLines()) {
            if (line.getStopIds().contains(stopId)) result.add(line);
        }
        return result;
    }
}
