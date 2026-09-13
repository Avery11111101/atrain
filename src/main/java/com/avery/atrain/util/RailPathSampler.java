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

    public static List<Location> betweenStops(Stop from, Stop to, com.avery.atrain.manager.RouteManager routeManager) {
        return betweenStops(from, to, routeManager, com.avery.atrain.model.TravelDirection.FORWARD);
    }

    public static List<Location> betweenStops(Stop from, Stop to, com.avery.atrain.manager.RouteManager routeManager,
                                              com.avery.atrain.model.TravelDirection direction) {
        Location start = snapToRail(from.getRailLocation(direction));
        Location end = snapToRail(to.getRailLocation(direction));
        if (start == null || end == null) return List.of();

        List<Location> raw = null;

        if (routeManager != null && from != null && to != null) {
            List<Location> locs = routeManager.getRouteLocations(from.getId(), to.getId());
            if (locs != null && !locs.isEmpty()) {
                raw = locs;
            }
        }
        if (raw == null || raw.size() < 2) {
            raw = bfsAlongRails(start, end, 100000);
        }
        if (raw == null || raw.size() < 2) {
            raw = walkTowardTarget(start, end, 100000);
        }
        if (raw == null || raw.size() < 2) {
            raw = List.of(start, end);
        }

        return densifyAndSnap(raw);
    }

    private static Location snapToRail(Location loc) {
        if (loc == null) return null;
        // 避免在未加載區塊中同步尋找方塊，這會觸發區塊載入並可能導致主線程卡死（Watchdog 判定為 Infinite Loop）
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        if (!loc.getWorld().isChunkLoaded(chunkX, chunkZ)) {
            return loc.clone();
        }
        Block rail = RailUtil.findNearestRailBlock(loc, 4);
        if (rail != null) return RailUtil.cartPositionOnRail(rail);
        return loc.clone();
    }

    /**
     * 僅吸附鐵軌高度與爬坡斜率，保留連續的浮點 X/Z 座標。
     * 避免將礦車強制吸附到方塊中心 (+0.5) 導致離站與進站低速時每格停滯瞬移。
     */
    public static Location snapToRailHeight(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        if (!loc.getWorld().isChunkLoaded(chunkX, chunkZ)) {
            return loc.clone();
        }
        Block rail = RailUtil.findNearestRailBlock(loc, 4);
        if (rail != null) {
            double y = rail.getY();
            if (rail.getBlockData() instanceof org.bukkit.block.data.Rail r) {
                switch (r.getShape()) {
                    case ASCENDING_EAST -> y += Math.max(0.0, Math.min(1.0, loc.getX() - rail.getX()));
                    case ASCENDING_WEST -> y += Math.max(0.0, Math.min(1.0, rail.getX() + 1.0 - loc.getX()));
                    case ASCENDING_SOUTH -> y += Math.max(0.0, Math.min(1.0, loc.getZ() - rail.getZ()));
                    case ASCENDING_NORTH -> y += Math.max(0.0, Math.min(1.0, rail.getZ() + 1.0 - loc.getZ()));
                    default -> y += RailUtil.cartHeightOnRail(rail);
                }
            } else {
                y += RailUtil.cartHeightOnRail(rail);
            }
            Location out = loc.clone();
            out.setY(y);
            return out;
        }
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

    private static class Node implements Comparable<Node> {
        Block block;
        String key;
        double gCost;
        double fCost;

        Node(Block block, String key, double gCost, double fCost) {
            this.block = block;
            this.key = key;
            this.gCost = gCost;
            this.fCost = fCost;
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(this.fCost, o.fCost);
        }
    }

    private static List<Location> bfsAlongRails(Location start, Location end, int maxNodes) {
        Block startRail = RailUtil.findNearestRailBlock(start, 4);
        Block endRail = RailUtil.findNearestRailBlock(end, 4);
        if (startRail == null || endRail == null) return List.of();
        if (!startRail.getWorld().equals(endRail.getWorld())) return List.of();

        String goalKey = blockKey(endRail);
        Location endLoc = endRail.getLocation();

        Map<String, Block> parent = new HashMap<>();
        Map<String, Double> gCosts = new HashMap<>();
        java.util.PriorityQueue<Node> openSet = new java.util.PriorityQueue<>();
        Set<String> closedSet = new HashSet<>();

        String startKey = blockKey(startRail);
        gCosts.put(startKey, 0.0);
        openSet.add(new Node(startRail, startKey, 0.0, startRail.getLocation().distance(endLoc)));

        int expanded = 0;
        while (!openSet.isEmpty() && expanded++ < maxNodes) {
            Node cur = openSet.poll();
            
            if (cur.key.equals(goalKey)) {
                return blocksToLocations(reconstruct(parent, cur.block));
            }

            if (!closedSet.add(cur.key)) continue;

            for (Block nb : connectedRailNeighbors(cur.block)) {
                String nk = blockKey(nb);
                if (closedSet.contains(nk)) continue;

                double tentativeG = cur.gCost + cur.block.getLocation().distance(nb.getLocation());
                double existingG = gCosts.getOrDefault(nk, Double.MAX_VALUE);

                if (tentativeG < existingG) {
                    parent.put(nk, cur.block);
                    gCosts.put(nk, tentativeG);
                    double fCost = tentativeG + nb.getLocation().distance(endLoc);
                    openSet.add(new Node(nb, nk, tentativeG, fCost));
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
        Location first = snapToRailHeight(sparse.get(0));
        dense.add(first);

        for (int i = 0; i < sparse.size() - 1; i++) {
            Location a = sparse.get(i);
            Location b = sparse.get(i + 1);
            if (a == null || b == null) continue;

            double dist = a.distance(b);
            int steps = Math.max(1, (int) Math.ceil(dist / DENSIFY_STEP));
            // 限制最大步數，避免極遠距離（例如跨越數千格的直線飛行）產生過多節點導致記憶體與運算過載
            if (steps > 1000) steps = 1000;
            
            for (int s = 1; s <= steps; s++) {
                double t = s / (double) steps;
                Location mid = new Location(a.getWorld(),
                        a.getX() + (b.getX() - a.getX()) * t,
                        a.getY() + (b.getY() - a.getY()) * t,
                        a.getZ() + (b.getZ() - a.getZ()) * t);
                dense.add(snapToRailHeight(mid));
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

    /** 依路徑弧長比例取樣（0~1），輸出必吸附鐵軌高度，維持平滑連續的 X/Z 座標 */
    public static Location sampleAt(List<Location> path, double t) {
        if (path == null || path.isEmpty()) return null;
        if (path.size() == 1 || t <= 0) return snapToRailHeight(path.get(0).clone());
        if (t >= 1) return snapToRailHeight(path.get(path.size() - 1).clone());

        double total = 0;
        double[] segLen = new double[path.size() - 1];
        for (int i = 0; i < path.size() - 1; i++) {
            segLen[i] = path.get(i).distance(path.get(i + 1));
            total += segLen[i];
        }
        if (total < 0.001) return snapToRailHeight(path.get(path.size() - 1).clone());

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
                Location snapped = snapToRailHeight(out);
                if (dir.lengthSquared() > 0.0001) {
                    snapped.setDirection(dir);
                }
                return snapped;
            }
            acc += segLen[i];
        }
        return snapToRailHeight(path.get(path.size() - 1).clone());
    }

    public static double easeInOut(double t) {
        return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    }

    /**
     * 平滑 S 型梯形巡航過渡曲線 (Smooth S-Curve Cruise Profile)：
     * 1. 離站起步 (前 20%): 二次平滑加速至巡航速度，結合極小線性基底避免初始停滯。
     * 2. 中間巡航 (中段 60%): 維持高於平均速度之恆定高速巡航 (1.24x)，重現列車高速穿梭之「速度感」。
     * 3. 靠站減速 (後 20%): 平滑平緩減速至零，精準無頓挫停靠月台金磚。
     */
    public static double smoothTransit(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double a = 0.20; // 起步加速區間比例
        double d = 0.20; // 靠站減速區間比例
        double vc = 1.0 / (1.0 - (a + d) / 2.0); // 巡航速度係數 = 1.25

        double trap;
        if (clamped < a) {
            trap = (vc / (2.0 * a)) * clamped * clamped;
        } else if (clamped <= 1.0 - d) {
            trap = (vc * a / 2.0) + vc * (clamped - a);
        } else {
            double u = 1.0 - clamped;
            trap = 1.0 - (vc / (2.0 * d)) * u * u;
        }

        // 注入 5% 線性基底保證起步首 tick 即具備細微位移，95% 梯形高速巡航
        return 0.05 * clamped + 0.95 * trap;
    }
}
