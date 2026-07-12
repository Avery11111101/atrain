package com.avery.atrain.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.RedstoneRail;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 軌道方向偵測與路徑輔助工具。
 * 核心修正：以軌道切線方向取代歷史移動方向，避免轉彎撞牆回彈。
 */
public final class RailUtil {

    private RailUtil() {}

    public static boolean isOnRail(Location loc) {
        return findRailBlock(loc) != null;
    }

    public static boolean isRailMaterial(Material type) {
        String n = type.name();
        return n.contains("RAIL");
    }

    /** 取得礦車腳下軌道的可能行進方向（含轉彎分叉） */
    public static List<Vector> getRailDirections(Location loc) {
        List<Vector> dirs = new ArrayList<>();
        Block railBlock = findRailBlock(loc);
        if (railBlock == null) return dirs;

        if (railBlock.getBlockData() instanceof Rail rail) {
            for (BlockFace face : getConnectedFaces(rail)) {
                Vector v = faceToVector(face);
                if (v != null) dirs.add(v);
            }
            if (dirs.isEmpty()) {
                var shape = rail.getShape();
                switch (shape) {
                    case NORTH_SOUTH -> { dirs.add(new Vector(0, 0, 1)); dirs.add(new Vector(0, 0, -1)); }
                    case EAST_WEST -> { dirs.add(new Vector(1, 0, 0)); dirs.add(new Vector(-1, 0, 0)); }
                    case ASCENDING_NORTH -> dirs.add(new Vector(0, 0, -1));
                    case ASCENDING_SOUTH -> dirs.add(new Vector(0, 0, 1));
                    case ASCENDING_EAST -> dirs.add(new Vector(1, 0, 0));
                    case ASCENDING_WEST -> dirs.add(new Vector(-1, 0, 0));
                    default -> {
                        dirs.add(new Vector(0, 0, 1));
                        dirs.add(new Vector(0, 0, -1));
                        dirs.add(new Vector(1, 0, 0));
                        dirs.add(new Vector(-1, 0, 0));
                    }
                }
            }
        }
        return dirs;
    }

    private static List<BlockFace> getConnectedFaces(Rail rail) {
        List<BlockFace> faces = new ArrayList<>();
        var shape = rail.getShape();
        switch (shape) {
            case NORTH_SOUTH -> { faces.add(BlockFace.NORTH); faces.add(BlockFace.SOUTH); }
            case EAST_WEST -> { faces.add(BlockFace.EAST); faces.add(BlockFace.WEST); }
            case ASCENDING_NORTH -> faces.add(BlockFace.NORTH);
            case ASCENDING_SOUTH -> faces.add(BlockFace.SOUTH);
            case ASCENDING_EAST -> faces.add(BlockFace.EAST);
            case ASCENDING_WEST -> faces.add(BlockFace.WEST);
            case SOUTH_EAST -> { faces.add(BlockFace.SOUTH); faces.add(BlockFace.EAST); }
            case SOUTH_WEST -> { faces.add(BlockFace.SOUTH); faces.add(BlockFace.WEST); }
            case NORTH_EAST -> { faces.add(BlockFace.NORTH); faces.add(BlockFace.EAST); }
            case NORTH_WEST -> { faces.add(BlockFace.NORTH); faces.add(BlockFace.WEST); }
            default -> { faces.add(BlockFace.NORTH); faces.add(BlockFace.SOUTH); }
        }
        return faces;
    }

    private static Vector faceToVector(BlockFace face) {
        return switch (face) {
            case NORTH -> new Vector(0, 0, -1);
            case SOUTH -> new Vector(0, 0, 1);
            case EAST -> new Vector(1, 0, 0);
            case WEST -> new Vector(-1, 0, 0);
            default -> null;
        };
    }

    public static Block findRailBlock(Location loc) {
        if (loc == null) return null;
        Block at = loc.getBlock();
        if (isRailMaterial(at.getType())) return at;
        Block below = at.getRelative(BlockFace.DOWN);
        if (isRailMaterial(below.getType())) return below;
        Block below2 = below.getRelative(BlockFace.DOWN);
        if (isRailMaterial(below2.getType())) return below2;
        return null;
    }

    /** 軌道方塊唯一鍵（進入新格偵測用） */
    public static String blockKey(Block block) {
        if (block == null || block.getWorld() == null) return null;
        return block.getWorld().getName() + "@" + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    /**
     * 沿行進方向取得下一格軌道（跟隨轉彎/爬升，不用水平主軸硬步進）。
     */
    public static Block walkNextRail(Block current, Vector travelDir) {
        if (current == null || travelDir == null) return null;
        Vector t = travelDir.clone();
        if (t.lengthSquared() < 0.0001) return null;
        t.normalize();

        Block best = null;
        double bestDot = 0.25;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (dx != 0 && dz != 0) continue;
                    Block rel = current.getRelative(dx, dy, dz);
                    Block rail = railAtColumn(rel);
                    if (rail == null || rail.equals(current)) continue;
                    Vector step = new Vector(
                            rail.getX() - current.getX(),
                            rail.getY() - current.getY(),
                            rail.getZ() - current.getZ());
                    if (step.lengthSquared() < 0.01) continue;
                    step.normalize();
                    double dot = step.dot(t);
                    if (dot > bestDot) {
                        bestDot = dot;
                        best = rail;
                    }
                }
            }
        }
        return best;
    }

    /** 沿軌道路徑往前探查（含轉彎/坡道） */
    public static List<Block> walkRailPath(Block start, Vector direction, int maxSteps) {
        List<Block> path = new ArrayList<>();
        if (start == null || direction == null || maxSteps <= 0) return path;
        Block current = start;
        Vector dir = direction.clone();
        for (int i = 0; i < maxSteps; i++) {
            Block next = walkNextRail(current, dir);
            if (next == null) break;
            path.add(next);
            Vector step = new Vector(
                    next.getX() - current.getX(),
                    next.getY() - current.getY(),
                    next.getZ() - current.getZ());
            if (step.lengthSquared() > 0.01) dir = step;
            current = next;
        }
        return path;
    }

    /**
     * 取得沿軌道行進的 3D 單位向量（含爬升 Y 分量），供每 tick 施加速度。
     */
    public static Vector getRailMovementVector(Location loc, Vector basis) {
        Vector horiz = pickForwardDirection(loc, basis);
        if (horiz == null || horiz.lengthSquared() < 0.0001) return null;
        Block rail = findRailBlock(loc);
        if (rail != null && rail.getBlockData() instanceof Rail r) {
            Vector move = horiz.clone();
            switch (r.getShape()) {
                case ASCENDING_NORTH, ASCENDING_SOUTH, ASCENDING_EAST, ASCENDING_WEST -> move.setY(1);
                default -> move.setY(0);
            }
            if (move.lengthSquared() > 0.0001) return move.normalize();
        }
        horiz.setY(0);
        return horiz.normalize();
    }

    /** 選擇最符合目標方向的軌道分支 */
    public static Vector pickBestRailDirection(Location loc, Vector targetDir) {
        if (targetDir == null || targetDir.lengthSquared() < 0.001) return null;
        Vector flat = targetDir.clone();
        flat.setY(0);
        if (flat.lengthSquared() < 0.001) return null;
        flat.normalize();

        List<Vector> options = getRailDirections(loc);
        if (options.isEmpty()) return flat;

        Vector best = null;
        double bestDot = -2;
        for (Vector opt : options) {
            double dot = opt.dot(flat);
            if (dot > bestDot) {
                bestDot = dot;
                best = opt.clone();
            }
        }
        return best != null ? best : flat;
    }

    /** 該位置的軌道是否為轉彎（不含上下坡） */
    public static boolean isCurveRail(Location loc) {
        return isCurveShape(findRailBlock(loc));
    }

    private static boolean isCurveShape(Block rail) {
        if (rail == null || !(rail.getBlockData() instanceof Rail r)) return false;
        return switch (r.getShape()) {
            case SOUTH_EAST, SOUTH_WEST, NORTH_EAST, NORTH_WEST -> true;
            default -> false;
        };
    }

    /**
     * 依進入方向選擇軌道出口（轉彎時排除倒退分支，避免抽動）。
     */
    public static Vector pickForwardDirection(Location loc, Vector incoming) {
        List<Vector> options = getRailDirections(loc);
        if (options.isEmpty()) return null;

        if (incoming == null || incoming.lengthSquared() < 0.0001) {
            return options.get(0).clone();
        }
        Vector flat = incoming.clone();
        flat.setY(0);
        if (flat.lengthSquared() < 0.0001) return options.get(0).clone();
        flat.normalize();

        Vector best = null;
        double bestDot = -2;
        for (Vector opt : options) {
            Vector o = opt.clone();
            o.setY(0);
            if (o.lengthSquared() < 0.0001) continue;
            o.normalize();
            double dot = o.dot(flat);
            if (dot > bestDot) {
                bestDot = dot;
                best = opt.clone();
            }
        }
        if (best != null && bestDot > -0.1) return best;
        return options.get(0).clone();
    }

    /** 依目前速度選擇延續方向的軌道分支 */
    public static Vector pickContinuationDirection(Location loc, Vector currentVelocity) {
        if (currentVelocity != null && currentVelocity.lengthSquared() > 0.001) {
            Vector picked = pickForwardDirection(loc, currentVelocity);
            if (picked != null) return picked;
        }
        List<Vector> dirs = getRailDirections(loc);
        return dirs.isEmpty() ? null : dirs.get(0).clone();
    }

    /**
     * 沿軌道路徑檢查前方是否被實體阻擋（車身空間 = 軌道上方一格）。
     * 會追蹤高度變化，只看自身路徑，避免立體交叉誤判上/下層結構為障礙。
     */
    public static boolean isRailPathBlocked(Location loc, Vector direction, int lookahead) {
        if (direction == null) return false;
        Vector flat = direction.clone();
        flat.setY(0);
        if (flat.lengthSquared() < 0.001) return false;
        Vector step = dominantAxis(flat.normalize());

        Block current = findRailBlock(loc);
        if (current == null) return false;
        for (int i = 1; i <= lookahead; i++) {
            Block base = current.getRelative(
                    (int) step.getX(), (int) step.getY(), (int) step.getZ());
            Block next = railAtColumn(base);
            if (next == null) {
                // 軌道到盡頭：正前方車身高度有實體牆才算阻擋
                return base.getType().isSolid() && !isRailMaterial(base.getType());
            }
            Block body = next.getRelative(BlockFace.UP);
            if (body.getType().isSolid() && !isRailMaterial(body.getType())) return true;
            current = next;
        }
        return false;
    }

    public static boolean hasObstructionAhead(Location loc, Vector direction, double distance) {
        if (direction == null) return false;
        Vector norm = direction.clone();
        if (norm.lengthSquared() < 0.001) return false;
        norm.normalize();

        Location check = loc.clone().add(norm.clone().multiply(distance));
        Block block = check.getBlock();
        if (block.getType().isSolid() && !isRailMaterial(block.getType())) return true;

        Block above = block.getRelative(BlockFace.UP);
        Block atEye = loc.clone().add(0, 0.5, 0).add(norm.clone().multiply(distance)).getBlock();
        return (atEye.getType().isSolid() && !isRailMaterial(atEye.getType()));
    }

    /** 該位置的軌道是否為爬升軌（上下坡），需保留原版 Y 分量以正常爬坡 */
    public static boolean isAscendingRail(Location loc) {
        Block rail = findRailBlock(loc);
        if (rail == null) return false;
        if (!(rail.getBlockData() instanceof Rail r)) return false;
        return switch (r.getShape()) {
            case ASCENDING_NORTH, ASCENDING_SOUTH, ASCENDING_EAST, ASCENDING_WEST -> true;
            default -> false;
        };
    }

    /** 該位置的軌道是否為轉彎（非直線）或上下坡（爬升軌） */
    public static boolean isCurveOrSlopeRail(Location loc) {
        return isCurveOrSlopeShape(findRailBlock(loc));
    }

    private static boolean isCurveOrSlopeShape(Block rail) {
        if (rail == null || !(rail.getBlockData() instanceof Rail r)) return false;
        return switch (r.getShape()) {
            case SOUTH_EAST, SOUTH_WEST, NORTH_EAST, NORTH_WEST,
                 ASCENDING_NORTH, ASCENDING_SOUTH, ASCENDING_EAST, ASCENDING_WEST -> true;
            default -> false;
        };
    }
    private static Vector dominantAxis(Vector v) {
        if (Math.abs(v.getX()) >= Math.abs(v.getZ())) {
            return new Vector(Math.signum(v.getX()), 0, 0);
        }
        return new Vector(0, 0, Math.signum(v.getZ()));
    }

    /** 在指定水平柱位找同層/上一層/下一層的軌道（處理爬升軌高度變化） */
    private static Block railAtColumn(Block base) {
        if (isRailMaterial(base.getType())) return base;
        Block up = base.getRelative(BlockFace.UP);
        if (isRailMaterial(up.getType())) return up;
        Block down = base.getRelative(BlockFace.DOWN);
        if (isRailMaterial(down.getType())) return down;
        return null;
    }

    /**
     * 從 loc 沿 direction「順著軌道路徑」逐格往前探查，若途中出現轉彎/上下坡則回傳 true。
     * 會追蹤爬升軌造成的高度變化，且僅檢查自身路徑上的軌道，不會誤判上/下層立體交叉軌。
     */
    public static boolean curveOrSlopeAhead(Location loc, Vector direction, int lookahead) {
        Block start = findRailBlock(loc);
        if (start == null || direction == null) return false;
        List<Block> path = walkRailPath(start, direction, lookahead);
        for (Block rail : path) {
            if (isCurveOrSlopeShape(rail)) return true;
        }
        return false;
    }

    /** 該位置腳下是否為「已通電的充能鐵軌（POWERED_RAIL）」——用於全速加速判定 */
    public static boolean isOnActivePoweredRail(Location loc) {
        Block rail = findRailBlock(loc);
        if (rail == null || rail.getType() != Material.POWERED_RAIL) return false;
        return rail.getBlockData() instanceof RedstoneRail rr && rr.isPowered();
    }

    public static boolean isPoweredRail(Location loc) {
        Block rail = findRailBlock(loc);
        if (rail == null) return false;
        if (rail.getBlockData() instanceof RedstoneRail rr) {
            return rr.isPowered();
        }
        return rail.getBlockData() instanceof Rail;
    }

    public static Location snapToRailCenter(Location loc) {
        Block rail = findRailBlock(loc);
        if (rail == null) return loc;
        return cartPositionOnRail(rail);
    }

    /** 礦車在軌道上的標準座標（中心對齊軌道格，高度貼軌） */
    public static Location cartPositionOnRail(Block rail) {
        if (rail == null || rail.getWorld() == null) return null;
        return new Location(rail.getWorld(),
                rail.getX() + 0.5,
                rail.getY() + cartHeightOnRail(rail),
                rail.getZ() + 0.5);
    }

    /** 礦車相對軌道方塊底部的 Y 偏移（爬升軌略高） */
    public static double cartHeightOnRail(Block rail) {
        if (rail == null || !(rail.getBlockData() instanceof Rail r)) return 0.0;
        return switch (r.getShape()) {
            case ASCENDING_NORTH, ASCENDING_SOUTH, ASCENDING_EAST, ASCENDING_WEST -> 0.5;
            default -> 0.0;
        };
    }

    /** 在附近搜尋最近的鐵軌方塊（含上下與相鄰柱） */
    public static Block findNearestRailBlock(Location loc, int verticalRange) {
        if (loc == null || loc.getWorld() == null) return null;
        Block direct = findRailBlock(loc);
        if (direct != null) return direct;

        World w = loc.getWorld();
        int bx = loc.getBlockX(), bz = loc.getBlockZ();
        int baseY = loc.getBlockY();
        Block best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dy = verticalRange; dy >= -verticalRange; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = w.getBlockAt(bx + dx, baseY + dy, bz + dz);
                    if (!isRailMaterial(b.getType())) continue;
                    double d = cartPositionOnRail(b).distanceSquared(loc);
                    if (d < bestDist) {
                        bestDist = d;
                        best = b;
                    }
                }
            }
        }
        return best;
    }
}
