package com.avery.atrain.util;

import org.bukkit.Location;
import org.bukkit.Material;
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
        Block below = loc.getBlock().getRelative(BlockFace.DOWN);
        return isRailMaterial(below.getType());
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
        Block below = loc.getBlock().getRelative(BlockFace.DOWN);
        if (isRailMaterial(below.getType())) return below;
        if (isRailMaterial(loc.getBlock().getType())) return loc.getBlock();
        return null;
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

    /** 依目前速度選擇延續方向的軌道分支 */
    public static Vector pickContinuationDirection(Location loc, Vector currentVelocity) {
        if (currentVelocity != null && currentVelocity.lengthSquared() > 0.001) {
            Vector flat = currentVelocity.clone();
            flat.setY(0);
            if (flat.lengthSquared() > 0.001) {
                return pickBestRailDirection(loc, flat);
            }
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

    /** 取水平主軸單位方向，避免斜向探查誤跨到鄰軌 */
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
        if (direction == null || direction.lengthSquared() < 0.001) return false;
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
            if (next == null) return false; // 路徑到此結束
            if (isCurveOrSlopeShape(next)) return true;
            current = next;
        }
        return false;
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
        return rail.getLocation().add(0.5, 0, 0.5);
    }
}
