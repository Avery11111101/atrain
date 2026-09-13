package com.avery.atrain.service;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;

import java.util.*;

/**
 * 路線規劃服務：提供跨路線、轉乘站與環狀線的最優路徑搜尋與搭乘指引
 */
public class RoutePlannerService {

    private final AtrainPlugin plugin;

    public RoutePlannerService(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public record RouteStep(
            String lineId,
            String lineDisplayName,
            String lineColor,
            String fromStopId,
            String fromStopName,
            String toStopId,
            String toStopName,
            List<String> intermediateStopNames,
            int stopCount,
            boolean isTransferNext
    ) {}

    public record RoutePlan(
            String originStopId,
            String originStopName,
            String destStopId,
            String destStopName,
            List<RouteStep> steps,
            int totalStops,
            int totalTransfers,
            boolean found
    ) {}

    /**
     * 搜尋從起點站到終點站的最佳搭乘指引（最少轉乘優先，其次最少站數）
     */
    public RoutePlan calculateRoute(String originStopId, String destStopId) {
        Stop origin = plugin.getStopManager().getStop(originStopId);
        Stop dest = plugin.getStopManager().getStop(destStopId);

        if (origin == null || dest == null || originStopId.equals(destStopId)) {
            return new RoutePlan(
                    originStopId, origin != null ? origin.getDisplayName() : originStopId,
                    destStopId, dest != null ? dest.getDisplayName() : destStopId,
                    List.of(), 0, 0, false
            );
        }

        PriorityQueue<RecordState> queue = new PriorityQueue<>(Comparator
                .comparingInt((RecordState s) -> s.transfers)
                .thenComparingInt(s -> s.totalStops));

        Map<String, Integer> visited = new HashMap<>();

        queue.add(new RecordState(originStopId, null, 0, 0, new ArrayList<>()));

        RecordState bestTargetState = null;

        while (!queue.isEmpty()) {
            RecordState curr = queue.poll();

            if (curr.stopId.equals(destStopId)) {
                bestTargetState = curr;
                break;
            }

            String stateKey = curr.stopId + "@" + (curr.lineId == null ? "" : curr.lineId);
            if (visited.containsKey(stateKey) && visited.get(stateKey) <= curr.transfers) {
                continue;
            }
            visited.put(stateKey, curr.transfers);

            Stop currentStop = plugin.getStopManager().getStop(curr.stopId);
            if (currentStop != null) {
                List<Line> linesAtStop = plugin.getLineManager().getLinesAtStop(curr.stopId);
                for (Line line : linesAtStop) {
                    if (curr.lineId != null && line.getId().equals(curr.lineId)) continue;
                    exploreLineFromStop(curr, line, queue, visited);
                }
            }
        }

        if (bestTargetState == null || bestTargetState.pathHistory.isEmpty()) {
            return new RoutePlan(
                    originStopId, origin.getDisplayName(),
                    destStopId, dest.getDisplayName(),
                    List.of(), 0, 0, false
            );
        }

        List<RouteStep> steps = new ArrayList<>();
        int totalStopsSum = 0;
        int transfersCount = Math.max(0, bestTargetState.transfers - 1);

        List<RawEdge> edges = bestTargetState.pathHistory;
        for (int i = 0; i < edges.size(); i++) {
            RawEdge edge = edges.get(i);
            Line line = plugin.getLineManager().getLine(edge.lineId);
            String lineName = line != null ? line.getDisplayName() : edge.lineId;
            String lineColor = line != null ? line.getFormattedColor() : "§a";

            Stop fromStop = plugin.getStopManager().getStop(edge.fromStopId);
            Stop toStop = plugin.getStopManager().getStop(edge.toStopId);

            List<String> interNames = new ArrayList<>();
            for (String sId : edge.intermediateStopIds) {
                Stop s = plugin.getStopManager().getStop(sId);
                interNames.add(s != null ? s.getDisplayName() : sId);
            }

            int segmentStops = edge.intermediateStopIds.size() + 1;
            totalStopsSum += segmentStops;

            boolean isTransfer = (i < edges.size() - 1);

            steps.add(new RouteStep(
                    edge.lineId,
                    lineName,
                    lineColor,
                    edge.fromStopId,
                    fromStop != null ? fromStop.getDisplayName() : edge.fromStopId,
                    edge.toStopId,
                    toStop != null ? toStop.getDisplayName() : edge.toStopId,
                    interNames,
                    segmentStops,
                    isTransfer
            ));
        }

        return new RoutePlan(
                originStopId, origin.getDisplayName(),
                destStopId, dest.getDisplayName(),
                steps, totalStopsSum, transfersCount, true
        );
    }

    private void exploreLineFromStop(RecordState curr, Line line, PriorityQueue<RecordState> queue, Map<String, Integer> visited) {
        List<String> stops = line.getStopIds();
        int startIndex = stops.indexOf(curr.stopId);
        if (startIndex < 0) return;

        int size = stops.size();

        // 正向搜尋 (Forward direction)
        for (int step = 1; step < (line.isCircular() ? size : size - startIndex); step++) {
            int targetIndex = (startIndex + step) % size;
            String targetStopId = stops.get(targetIndex);

            List<String> interStops = new ArrayList<>();
            for (int k = 1; k < step; k++) {
                interStops.add(stops.get((startIndex + k) % size));
            }

            List<RawEdge> newHistory = new ArrayList<>(curr.pathHistory);
            newHistory.add(new RawEdge(line.getId(), curr.stopId, targetStopId, interStops));

            int newTransfers = (curr.lineId == null) ? 1 : curr.transfers + 1;
            queue.add(new RecordState(targetStopId, line.getId(), curr.totalStops + step, newTransfers, newHistory));
        }

        // 反向搜尋 (Reverse direction)
        for (int step = 1; step < (line.isCircular() ? size : startIndex + 1); step++) {
            int targetIndex = (startIndex - step + size * 10) % size;
            String targetStopId = stops.get(targetIndex);

            List<String> interStops = new ArrayList<>();
            for (int k = 1; k < step; k++) {
                interStops.add(stops.get((startIndex - k + size * 10) % size));
            }

            List<RawEdge> newHistory = new ArrayList<>(curr.pathHistory);
            newHistory.add(new RawEdge(line.getId(), curr.stopId, targetStopId, interStops));

            int newTransfers = (curr.lineId == null) ? 1 : curr.transfers + 1;
            queue.add(new RecordState(targetStopId, line.getId(), curr.totalStops + step, newTransfers, newHistory));
        }
    }

    private record RawEdge(
            String lineId,
            String fromStopId,
            String toStopId,
            List<String> intermediateStopIds
    ) {}

    private record RecordState(
            String stopId,
            String lineId,
            int totalStops,
            int transfers,
            List<RawEdge> pathHistory
    ) {}
}
