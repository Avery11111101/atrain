package com.avery.atrain.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Line {
    private String id;
    private String displayName;
    private String color;
    private double maxSpeed;
    private boolean circular;
    private List<String> stopIds = new ArrayList<>();


    public Line() {}

    public Line(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.color = "§a";
        this.maxSpeed = 0.35;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public double getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(double maxSpeed) { this.maxSpeed = maxSpeed; }
    public boolean isCircular() { return circular; }
    public void setCircular(boolean circular) { this.circular = circular; }
    public List<String> getStopIds() { return stopIds; }
    public void setStopIds(List<String> stopIds) { this.stopIds = stopIds; }


    public int getSegmentCount() {
        int n = stopIds.size();
        if (n < 2) return 0;
        return circular ? n : n - 1;
    }

    public void updateStopsAndPreserveSegments(List<String> newStopIds) {
        setStopIds(newStopIds);
    }

    public int findSegmentIndex(String fromStopId, String toStopId) {
        return findSegmentIndex(fromStopId, toStopId, TravelDirection.FORWARD);
    }

    public int findSegmentIndex(String fromStopId, String toStopId, TravelDirection direction) {
        int from = stopIds.indexOf(fromStopId);
        int to = stopIds.indexOf(toStopId);
        if (from < 0 || to < 0) return -1;
        if (direction == TravelDirection.FORWARD) {
            if (to == from + 1) return from;
            if (circular && from == stopIds.size() - 1 && to == 0) return from;
            return -1;
        }
        if (from == to + 1) return stopIds.size() - 1 - from;
        if (circular && from == 0 && to == stopIds.size() - 1) {
            return stopIds.size() - 1;
        }
        return -1;
    }

    public String getSegmentFromStopId(int segmentIndex) {
        return getSegmentFromStopId(segmentIndex, TravelDirection.FORWARD);
    }

    public String getSegmentToStopId(int segmentIndex) {
        return getSegmentToStopId(segmentIndex, TravelDirection.FORWARD);
    }

    public String getSegmentFromStopId(int segmentIndex, TravelDirection direction) {
        if (segmentIndex < 0 || segmentIndex >= getSegmentCount()) return null;
        if (direction == TravelDirection.FORWARD) {
            return stopIds.get(segmentIndex);
        }
        if (segmentIndex < stopIds.size() - 1) {
            int fromIdx = stopIds.size() - 1 - segmentIndex;
            return fromIdx >= 0 && fromIdx < stopIds.size() ? stopIds.get(fromIdx) : null;
        }
        if (circular && segmentIndex == stopIds.size() - 1) {
            return stopIds.get(0);
        }
        return null;
    }

    public String getSegmentToStopId(int segmentIndex, TravelDirection direction) {
        if (segmentIndex < 0 || segmentIndex >= getSegmentCount()) return null;
        if (direction == TravelDirection.FORWARD) {
            if (segmentIndex < stopIds.size() - 1) {
                return stopIds.get(segmentIndex + 1);
            }
            if (circular && segmentIndex == stopIds.size() - 1) {
                return stopIds.get(0);
            }
            return null;
        }
        if (segmentIndex < stopIds.size() - 1) {
            int toIdx = stopIds.size() - 2 - segmentIndex;
            return toIdx >= 0 ? stopIds.get(toIdx) : null;
        }
        if (circular && segmentIndex == stopIds.size() - 1) {
            return stopIds.get(stopIds.size() - 1);
        }
        return null;
    }

    public String getNextStopId(String currentStopId) {
        return getNextStopId(currentStopId, TravelDirection.FORWARD);
    }

    public String getNextStopId(String currentStopId, TravelDirection direction) {
        int idx = stopIds.indexOf(currentStopId);
        if (idx < 0) return null;
        if (direction == TravelDirection.FORWARD) {
            if (idx + 1 < stopIds.size()) return stopIds.get(idx + 1);
            return circular && !stopIds.isEmpty() ? stopIds.get(0) : null;
        }
        if (idx > 0) return stopIds.get(idx - 1);
        return circular && !stopIds.isEmpty() ? stopIds.get(stopIds.size() - 1) : null;
    }

    public String getFormattedName() {
        return (color != null ? color : "§a") + displayName;
    }

}
