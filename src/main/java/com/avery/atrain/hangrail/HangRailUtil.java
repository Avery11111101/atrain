package com.avery.atrain.hangrail;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 懸浮軌道偵測與方向計算（參考 TC-HangRail 邏輯，獨立實作）。
 */
public final class HangRailUtil {

    private HangRailUtil() {}

    public static boolean matches(Block block, HangRailType type) {
        return block != null && block.getType() == type.getBlock();
    }

    /** 在礦車位置附近尋找懸浮軌道方塊 */
    public static HangRailInfo findHangRail(Location cartLoc, List<HangRailType> types) {
        if (cartLoc.getWorld() == null || types.isEmpty()) return null;

        int x = cartLoc.getBlockX();
        int z = cartLoc.getBlockZ();
        int cy = cartLoc.getBlockY();

        for (HangRailType type : types) {
            if (type.isBelowRail()) {
                int railY = cy - type.getOffset();
                Block rail = cartLoc.getWorld().getBlockAt(x, railY, z);
                if (matches(rail, type)) {
                    return new HangRailInfo(rail, type);
                }
                Block slopeRail = cartLoc.getWorld().getBlockAt(x, railY + 1, z);
                if (matches(slopeRail, type) && findSlope(slopeRail, type) != null) {
                    return new HangRailInfo(slopeRail, type);
                }
            } else {
                int railY = cy - type.getOffset();
                Block rail = cartLoc.getWorld().getBlockAt(x, railY, z);
                if (matches(rail, type) && !hasRailVert(rail, BlockFace.UP, type)) {
                    return new HangRailInfo(rail, type);
                }
                Block slopeRail = cartLoc.getWorld().getBlockAt(x, railY - 1, z);
                if (matches(slopeRail, type) && findSlope(slopeRail, type) != null) {
                    return new HangRailInfo(slopeRail, type);
                }
            }
        }
        return null;
    }

    private static boolean hasRailVert(Block block, BlockFace face, HangRailType type) {
        Block next = block.getRelative(face);
        return matches(next, type);
    }

    /** 取得懸浮軌道連接方向 */
    public static List<Vector> getDirections(Block railBlock, HangRailType type) {
        List<Vector> dirs = new ArrayList<>();
        BlockFace slope = findSlope(railBlock, type);
        if (slope != null) {
            dirs.add(faceToVector(slope));
            dirs.add(faceToVector(slope.getOppositeFace()));
            return dirs;
        }

        if (isConnected(railBlock, type, BlockFace.NORTH)) dirs.add(new Vector(0, 0, -1));
        if (isConnected(railBlock, type, BlockFace.SOUTH)) dirs.add(new Vector(0, 0, 1));
        if (isConnected(railBlock, type, BlockFace.EAST)) dirs.add(new Vector(1, 0, 0));
        if (isConnected(railBlock, type, BlockFace.WEST)) dirs.add(new Vector(-1, 0, 0));

        if (dirs.isEmpty()) {
            dirs.add(new Vector(0, 0, 1));
            dirs.add(new Vector(0, 0, -1));
            dirs.add(new Vector(1, 0, 0));
            dirs.add(new Vector(-1, 0, 0));
        }
        return dirs;
    }

    public static BlockFace findSlope(Block railBlock, HangRailType type) {
        if (type.isBelowRail()) {
            Block below = railBlock.getRelative(BlockFace.DOWN);
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                if (isConnected(below, type, face) && !isRailVert(below, face, -1, type)) {
                    return face.getOppositeFace();
                }
            }
        } else {
            Block above = railBlock.getRelative(BlockFace.UP);
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                if (isConnected(above, type, face) && !isRailVert(above, face, 1, type)) {
                    return face;
                }
            }
        }
        return null;
    }

    private static boolean isRailVert(Block block, BlockFace offset, int dy, HangRailType type) {
        Block check = block.getWorld().getBlockAt(
                block.getX() + offset.getModX(),
                block.getY() + dy,
                block.getZ() + offset.getModZ());
        return matches(check, type);
    }

    private static boolean isConnected(Block block, HangRailType type, BlockFace face) {
        return matches(block.getRelative(face), type);
    }

    public static Vector pickBestDirection(Location cartLoc, HangRailInfo info, Vector targetDir) {
        List<Vector> options = getDirections(info.getRailBlock(), info.getType());
        if (targetDir != null && targetDir.lengthSquared() > 0.001) {
            Vector flat = targetDir.clone();
            flat.setY(0).normalize();
            Vector best = null;
            double bestDot = -2;
            for (Vector opt : options) {
                double dot = opt.dot(flat);
                if (dot > bestDot) {
                    bestDot = dot;
                    best = opt.clone();
                }
            }
            if (best != null) return best;
        }
        return options.isEmpty() ? null : options.get(0).clone();
    }

    public static Vector pickContinuation(Location cartLoc, HangRailInfo info, Vector velocity) {
        if (velocity != null && velocity.lengthSquared() > 0.001) {
            Vector flat = velocity.clone();
            flat.setY(0);
            if (flat.lengthSquared() > 0.001) {
                return pickBestDirection(cartLoc, info, flat);
            }
        }
        List<Vector> dirs = getDirections(info.getRailBlock(), info.getType());
        return dirs.isEmpty() ? null : dirs.get(0).clone();
    }

    public static Location getCartAnchor(HangRailInfo info) {
        Block rail = info.getRailBlock();
        HangRailType type = info.getType();
        double y = rail.getY() + 0.5 + type.getOffset();
        BlockFace slope = findSlope(rail, type);
        if (slope != null && type.isBelowRail()) {
            y -= 0.5;
        } else if (slope != null) {
            y += 0.5;
        }
        return new Location(rail.getWorld(), rail.getX() + 0.5, y, rail.getZ() + 0.5);
    }

    public static Location snapLocation(Location cartLoc, HangRailInfo info, Vector direction) {
        Location anchor = getCartAnchor(info);
        Location result = anchor.clone();
        result.setYaw(cartLoc.getYaw());
        result.setPitch(cartLoc.getPitch());

        if (direction != null && direction.lengthSquared() > 0.001) {
            Vector norm = direction.clone().normalize();
            double dx = cartLoc.getX() - anchor.getX();
            double dz = cartLoc.getZ() - anchor.getZ();
            double along = dx * norm.getX() + dz * norm.getZ();
            result.add(norm.getX() * along * 0.15, 0, norm.getZ() * along * 0.15);
        }
        return result;
    }

    private static Vector faceToVector(BlockFace face) {
        return switch (face) {
            case NORTH -> new Vector(0, 0, -1);
            case SOUTH -> new Vector(0, 0, 1);
            case EAST -> new Vector(1, 0, 0);
            case WEST -> new Vector(-1, 0, 0);
            default -> new Vector(0, 0, 1);
        };
    }
}
