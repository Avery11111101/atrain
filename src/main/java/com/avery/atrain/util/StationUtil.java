package com.avery.atrain.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

public final class StationUtil {

    private StationUtil() {}

    public static boolean isGoldBlock(Material type) {
        return type == Material.GOLD_BLOCK;
    }

    public static boolean isDisplayBlock(Material type) {
        return type == Material.DIAMOND_BLOCK;
    }

    public static boolean isAnyRail(Material type) {
        return type != null && type.name().contains("RAIL");
    }

    /** 金磚正上方有任意鐵軌 */
    public static boolean isGoldStationRail(Block railOrBelow) {
        if (railOrBelow == null) return false;
        Block rail = railOrBelow;
        Block below = rail.getRelative(BlockFace.DOWN);
        if (isAnyRail(railOrBelow.getType()) && isGoldBlock(below.getType())) return true;
        if (isGoldBlock(railOrBelow.getType())) {
            Block above = railOrBelow.getRelative(BlockFace.UP);
            return isAnyRail(above.getType());
        }
        return false;
    }

    public static Block resolveRailBlock(Block clicked) {
        if (clicked == null) return null;
        if (isAnyRail(clicked.getType())) return clicked;
        if (isGoldBlock(clicked.getType())) {
            Block above = clicked.getRelative(BlockFace.UP);
            if (isAnyRail(above.getType())) return above;
        }
        return null;
    }

    public static Block resolveGoldBlock(Block clicked) {
        if (clicked == null) return null;
        if (isGoldBlock(clicked.getType())) return clicked;
        if (isAnyRail(clicked.getType())) {
            Block below = clicked.getRelative(BlockFace.DOWN);
            if (isGoldBlock(below.getType())) return below;
        }
        return null;
    }

    /** 從起始金磚 BFS 掃描相連金磚（四向），僅納入上方有鐵軌者 */
    public static Set<String> scanConnectedGoldPlatform(Block startGold) {
        Set<String> result = new LinkedHashSet<>();
        if (startGold == null || !isGoldBlock(startGold.getType())) return result;

        Queue<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(startGold);

        while (!queue.isEmpty()) {
            Block b = queue.poll();
            String key = com.avery.atrain.model.Stop.key(b.getX(), b.getY(), b.getZ());
            if (!visited.add(key)) continue;
            if (!isGoldBlock(b.getType())) continue;
            Block rail = b.getRelative(BlockFace.UP);
            if (!isAnyRail(rail.getType())) continue;
            result.add(key);
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                queue.add(b.getRelative(face));
            }
        }
        return result;
    }

    /** 掃描與金磚平台相鄰（含上下一格）的鑽石塊 */
    public static Set<String> scanAdjacentDisplayBlocks(Set<String> goldKeys, String worldName) {
        Set<String> diamonds = new LinkedHashSet<>();
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) return diamonds;

        for (String gk : goldKeys) {
            int[] p = com.avery.atrain.model.Stop.parseKey(gk);
            if (p == null) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block b = world.getBlockAt(p[0] + dx, p[1] + dy, p[2] + dz);
                        if (isDisplayBlock(b.getType())) {
                            diamonds.add(com.avery.atrain.model.Stop.key(b.getX(), b.getY(), b.getZ()));
                        }
                    }
                }
            }
        }
        return diamonds;
    }

    public static Location railCenter(Block rail) {
        if (rail == null) return null;
        return RailUtil.snapToRailCenter(rail.getLocation().add(0.5, 0, 0.5));
    }
}
