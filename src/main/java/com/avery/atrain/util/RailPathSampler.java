package com.avery.atrain.util;

import com.avery.atrain.model.Line;
import com.avery.atrain.model.RoutePoint;
import com.avery.atrain.model.Stop;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 站點間路徑取樣：沿真實鐵軌 BFS，加密後每點吸附軌道高度，避免空中直線飛行。
 */
public final class RailPathSampler {

    private static final double DENSIFY_STEP = 0.35;

    private RailPathSampler() {}

    public static List<Location> betweenStops(Stop from, Stop to, Line line) {
        return betweenStops(from, to, line, com.avery.atrain.model.TravelDirection.FORWARD);
    }

    public static List<Location> betweenStops(Stop from, Stop to, Line line,
                                              com.avery.atrain.model.TravelDirection direction) {
        Location start = snapToRail(from.getRailLocation(direction));
        Location end = snapToRail(to.getRailLocation(direction));
        if (start == null || end == null) return List.of();

        List<Location> raw = null;

        if (line != null && from != null && to != null) {
            int segIdx = line.findSegmentIndex(from.getId(), to.getId(), direction);
            if (segIdx >= 0) {
                List<RoutePoint> segPts = line.getSegmentPoints(segIdx, direction);
                if (!segPts.isEmpty()) {
                    raw = segmentToLocations(segPts, start, end);
                }
            }
        }
        if ((raw == null || raw.size() < 2) && direction == com.avery.atrain.model.TravelDirection.FORWARD
                && line != null && !line.getRoutePoints().isEmpty()) {
            raw = sliceRoutePoints(line.getRoutePoints(), start, end);
        }
        if (raw == null || raw.size() < 2) {
            raw = bfsAlongRails(start, end, 4000);
        }
        if (raw == null || raw.size() < 2) {
            raw = walkTowardTarget(start, end, 4000);
        }
        if (raw == null || raw.size() < 2) {
            raw = List.of(start, end);
        }

        return densifyAndSnap(raw);
    }

    private static Location snapToRail(Location loc) {
        if (loc == null) return null;
        Block rail = RailUtil.findNearestRailBlock(loc, 4);
        if (rail != null) return RailUtil.cartPositionOnRail(rail);
        return loc.clone();
    }

    private static List<Location> segmentToLocations(List<RoutePoint> points, Location start, Location end) {
        List<Location> out = new ArrayList<>();
        out.add(snapToRail(start));
        for (RoutePoint p : points) {
            Location pt = p.toLocation();
            if (pt != null) out.add(snapToRail(pt));
        }
        out.add(snapToRail(end));
        return dedupe(out);
    }

    private static List<Location> sliceRoutePoints(List<RoutePoint> points, Location start, Location end) {
        if (points.isEmpty()) return List.of();
        int fromIdx = nearestIndex(points, start, 0);
        int toIdx = nearestIndex(points, end, fromIdx);
        if (fromIdx > toIdx) {
            int t = fromIdx;
            fromIdx = toIdx;
            toIdx = t;
        }
        List<Location> out = new ArrayList<>();
        out.add(snapToRail(start));
        for (int i = fromIdx; i <= toIdx; i++) {
            Location pt = points.get(i).toLocation();
            if (pt != null) out.add(snapToRail(pt));
        }
        out.add(snapToRail(end));
        return dedupe(out);
    }

    private static int nearestIndex(List<RoutePoint> points, Location loc, int minIndex) {
        int best = minIndex;
        double bestD = Double.MAX_VALUE;
        for (int i = minIndex; i < points.size(); i++) {
            RoutePoint p = points.get(i);
            if (!p.getWorld().equals(loc.getWorld().getName())) continue;
            double d = p.distanceSquared(loc);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static List<Location> bfsAlongRails(Location start, Location end, int maxNodes) {
        Block startRail = RailUtil.findNearestRailBlock(start, 4);
        Block endRail = RailUtil.findNearestRailBlock(end, 4);
        if (startRail == null || endRail == null) return List.of();
        if (!startRail.getWorld().equals(endRail.getWorld())) return List.of();

        String goalKey = blockKey(endRail);
        Map<String, Block> parent = new HashMap<>();
        Queue<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        queue.add(startRail);
        visited.add(blockKey(startRail));
        parent.put(blockKey(startRail), null);

        int expanded = 0;
        while (!queue.isEmpty() && expanded++ < maxNodes) {
            Block cur = queue.poll();
            if (blockKey(cur).equals(goalKey)) {
                return blocksToLocations(reconstruct(parent, cur));
            }
            for (Block nb : connectedRailNeighbors(cur)) {
                String nk = blockKey(nb);
                if (visited.add(nk)) {
                    parent.put(nk, cur);
                    queue.add(nb);
                }
            }
        }
        return List.of();
    }

    /** 沿軌道拓撲朝目標貪婪行走（BFS 失敗時的備援） */
    private static List<Location> walkTowardTarget(Location start, Location end, int maxSteps) {
        Block cur = RailUtil.findNearestRailBlock(start, 4);
        Block goal = RailUtil.findNearestRailBlock(end, 4);
        if (cur == null || goal == null) return List.of();

        List<Location> path = new ArrayList<>();
        path.add(RailUtil.cartPositionOnRail(cur));
        Set<String> visited = new HashSet<>();
        visited.add(blockKey(cur));

        Vector toGoal = end.toVector().subtract(start.toVector());
        toGoal.setY(0);

        for (int i = 0; i < maxSteps; i++) {
            if (blockKey(cur).equals(blockKey(goal))) break;
            Vector stepDir = toGoal.clone();
            if (stepDir.lengthSquared() < 0.01) break;
            Block next = RailUtil.walkNextRail(cur, stepDir.normalize());
            if (next == null || !visited.add(blockKey(next))) break;
            cur = next;
            path.add(RailUtil.cartPositionOnRail(cur));
            toGoal = end.toVector().subtract(path.get(path.size() - 1).toVector());
            toGoal.setY(0);
        }
        if (!blockKey(cur).equals(blockKey(goal))) {
            path.add(RailUtil.cartPositionOnRail(goal));
        }
        return path;
    }

    private static List<Block> reconstruct(Map<String, Block> parent, Block end) {
        List<Block> chain = new ArrayList<>();
        Block cur = end;
        while (cur != null) {
            chain.add(0, cur);
            cur = parent.get(blockKey(cur));
        }
        return chain;
    }

    private static List<Location> blocksToLocations(List<Block> rails) {
        List<Location> out = new ArrayList<>();
        for (Block rail : rails) {
            Location pos = RailUtil.cartPositionOnRail(rail);
            if (pos != null) out.add(pos);
        }
        return out;
    }

    /** 僅取與當前軌道幾何相連的鄰格（轉彎/爬升），避免跳層 */
    private static List<Block> connectedRailNeighbors(Block rail) {
        List<Block> out = new ArrayList<>();
        if (rail == null) return out;
        Location center = RailUtil.cartPositionOnRail(rail);
        if (center == null) return out;

        for (Vector dir : RailUtil.getRailDirections(center)) {
            Block next = RailUtil.walkNextRail(rail, dir);
            if (next != null && !next.equals(rail)) out.add(next);
        }

        // 爬升/下降相鄰柱
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (Math.abs(dx) + Math.abs(dz) > 1) continue;
                    Block rel = rail.getRelative(dx, dy, dz);
                    if (RailUtil.isRailMaterial(rel.getType()) && !rel.equals(rail)) {
                        out.add(rel);
                    }
                }
            }
        }
        return out;
    }

    /**
     * 路徑加密：每 {@value DENSIFY_STEP} 格插點，並吸附到最近鐵軌高度。
     */
    private static List<Location> densifyAndSnap(List<Location> sparse) {
        if (sparse.isEmpty()) return sparse;
        List<Location> dense = new ArrayList<>();
        Location first = snapToRail(sparse.get(0));
        dense.add(first);

        for (int i = 0; i < sparse.size() - 1; i++) {
            Location a = snapToRail(sparse.get(i));
            Location b = snapToRail(sparse.get(i + 1));
            if (a == null || b == null) continue;

            double dist = a.distance(b);
            int steps = Math.max(1, (int) Math.ceil(dist / DENSIFY_STEP));
            for (int s = 1; s <= steps; s++) {
                double t = s / (double) steps;
                Location mid = new Location(a.getWorld(),
                        a.getX() + (b.getX() - a.getX()) * t,
                        a.getY() + (b.getY() - a.getY()) * t,
                        a.getZ() + (b.getZ() - a.getZ()) * t);
                dense.add(snapToRail(mid));
            }
        }
        return dedupe(dense);
    }

    private static List<Location> dedupe(List<Location> in) {
        List<Location> out = new ArrayList<>();
        Location last = null;
        for (Location loc : in) {
            if (loc == null) continue;
            if (last != null && last.distanceSquared(loc) < 0.02) continue;
            out.add(loc);
            last = loc;
        }
        return out;
    }

    private static String blockKey(Block b) {
        return b.getWorld().getName() + "@" + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    /** 依路徑弧長比例取樣（0~1），輸出必吸附鐵軌 */
    public static Location sampleAt(List<Location> path, double t) {
        if (path == null || path.isEmpty()) return null;
        if (path.size() == 1 || t <= 0) return snapToRail(path.get(0).clone());
        if (t >= 1) return snapToRail(path.get(path.size() - 1).clone());

        double total = 0;
        double[] segLen = new double[path.size() - 1];
        for (int i = 0; i < path.size() - 1; i++) {
            segLen[i] = path.get(i).distance(path.get(i + 1));
            total += segLen[i];
        }
        if (total < 0.001) return snapToRail(path.get(path.size() - 1).clone());

        double want = t * total;
        double acc = 0;
        for (int i = 0; i < segLen.length; i++) {
            if (acc + segLen[i] >= want) {
                double local = segLen[i] < 0.001 ? 0 : (want - acc) / segLen[i];
                Location a = path.get(i);
                Location b = path.get(i + 1);
                Location out = new Location(a.getWorld(),
                        a.getX() + (b.getX() - a.getX()) * local,
                        a.getY() + (b.getY() - a.getY()) * local,
                        a.getZ() + (b.getZ() - a.getZ()) * local);
                Vector dir = b.toVector().subtract(a.toVector());
                Location snapped = snapToRail(out);
                if (dir.lengthSquared() > 0.0001) {
                    snapped.setDirection(dir);
                }
                return snapped;
            }
            acc += segLen[i];
        }
        return snapToRail(path.get(path.size() - 1).clone());
    }

    public static double easeInOut(double t) {
        return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    }
}
