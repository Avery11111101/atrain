package com.avery.atrain.model;

import java.util.ArrayList;
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
    /** 合併後的去程全線軌跡（相容舊版） */
    private List<RoutePoint> routePoints = new ArrayList<>();
    /** 去程：站點 i → 站點 i+1 */
    private Map<Integer, List<RoutePoint>> forwardRouteSegments = new LinkedHashMap<>();
    /** 回程：站點反序，段 0 = 最末站 → 倒數第二站 */
    private Map<Integer, List<RoutePoint>> reverseRouteSegments = new LinkedHashMap<>();

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

    public Map<Integer, List<RoutePoint>> getForwardRouteSegments() { return forwardRouteSegments; }
    public void setForwardRouteSegments(Map<Integer, List<RoutePoint>> segments) {
        this.forwardRouteSegments = segments != null ? new LinkedHashMap<>(segments) : new LinkedHashMap<>();
        rebuildRoutePoints();
    }

    public Map<Integer, List<RoutePoint>> getReverseRouteSegments() { return reverseRouteSegments; }
    public void setReverseRouteSegments(Map<Integer, List<RoutePoint>> segments) {
        this.reverseRouteSegments = segments != null ? new LinkedHashMap<>(segments) : new LinkedHashMap<>();
    }

    /** 相容舊 API：等同去程分段 */
    public Map<Integer, List<RoutePoint>> getRouteSegments() { return forwardRouteSegments; }
    public void setRouteSegments(Map<Integer, List<RoutePoint>> routeSegments) {
        setForwardRouteSegments(routeSegments);
    }

    private Map<Integer, List<RoutePoint>> segmentsFor(TravelDirection direction) {
        return direction == TravelDirection.REVERSE ? reverseRouteSegments : forwardRouteSegments;
    }

    public int getSegmentCount() {
        int n = stopIds.size();
        if (n < 2) return 0;
        return circular ? n : n - 1;
    }

    public List<RoutePoint> getSegmentPoints(int segmentIndex) {
        return getSegmentPoints(segmentIndex, TravelDirection.FORWARD);
    }

    public List<RoutePoint> getSegmentPoints(int segmentIndex, TravelDirection direction) {
        List<RoutePoint> pts = segmentsFor(direction).get(segmentIndex);
        return pts != null ? pts : List.of();
    }

    public boolean hasSegment(int segmentIndex) {
        return hasSegment(segmentIndex, TravelDirection.FORWARD);
    }

    public boolean hasSegment(int segmentIndex, TravelDirection direction) {
        List<RoutePoint> pts = segmentsFor(direction).get(segmentIndex);
        return pts != null && !pts.isEmpty();
    }

    public int getRecordedSegmentCount() {
        return getRecordedSegmentCount(TravelDirection.FORWARD);
    }

    public int getRecordedSegmentCount(TravelDirection direction) {
        int n = 0;
        for (int i = 0; i < getSegmentCount(); i++) {
            if (hasSegment(i, direction)) n++;
        }
        return n;
    }

    public void setSegmentPoints(int segmentIndex, List<RoutePoint> points) {
        setSegmentPoints(segmentIndex, TravelDirection.FORWARD, points);
    }

    public void setSegmentPoints(int segmentIndex, TravelDirection direction, List<RoutePoint> points) {
        Map<Integer, List<RoutePoint>> map = segmentsFor(direction);
        if (points == null || points.isEmpty()) {
            map.remove(segmentIndex);
        } else {
            map.put(segmentIndex, new ArrayList<>(points));
        }
        if (direction == TravelDirection.FORWARD) {
            rebuildRoutePoints();
        }
    }

    public void clearSegment(int segmentIndex) {
        clearSegment(segmentIndex, TravelDirection.FORWARD);
    }

    public void clearSegment(int segmentIndex, TravelDirection direction) {
        segmentsFor(direction).remove(segmentIndex);
        if (direction == TravelDirection.FORWARD) {
            rebuildRoutePoints();
        }
    }

    public void rebuildRoutePoints() {
        routePoints.clear();
        for (int i = 0; i < getSegmentCount(); i++) {
            List<RoutePoint> seg = forwardRouteSegments.get(i);
            if (seg != null && !seg.isEmpty()) {
                routePoints.addAll(seg);
            }
        }
    }

    /** 站點順序變更後清除所有錄製軌跡（分段索引與站序綁定） */
    public void clearAllSegments() {
        forwardRouteSegments.clear();
        reverseRouteSegments.clear();
        routePoints.clear();
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

    /**
     * 兩站之間用於路徑導引的軌跡點（依行駛方向）。
     * 優先使用分段錄製資料；去程無分段時回退至合併後的舊版 routePoints。
     */
    public List<RoutePoint> getGuidancePoints(String fromStopId, String toStopId, TravelDirection direction) {
        TravelDirection dir = direction != null ? direction : TravelDirection.FORWARD;
        int segIdx = findSegmentIndex(fromStopId, toStopId, dir);
        if (segIdx >= 0) {
            List<RoutePoint> seg = getSegmentPoints(segIdx, dir);
            if (!seg.isEmpty()) return seg;
        }
        if (dir == TravelDirection.FORWARD && !routePoints.isEmpty()) {
            return routePoints;
        }
        return List.of();
    }
}
