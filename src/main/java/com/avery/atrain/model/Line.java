package com.avery.atrain.model;

import java.util.ArrayList;
import java.util.List;

public class Line {
    private String id;
    private String displayName;
    private String color;
    private double maxSpeed;
    private boolean circular;
    private List<String> stopIds = new ArrayList<>();
    private List<RoutePoint> routePoints = new ArrayList<>();

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
    public List<RoutePoint> getRoutePoints() { return routePoints; }
    public void setRoutePoints(List<RoutePoint> routePoints) { this.routePoints = routePoints; }

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
