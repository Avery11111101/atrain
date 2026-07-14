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

    /**
     * 鑽石塊已改用途為「礦車調速方塊」（見 SpeedBlockManager），
     * 不再作為站點顯示塊——站點資訊改為站在金磚上查看。
     * 保留此方法回傳 false，讓舊有的顯示掃描自動失效。
     */
    public static boolean isDisplayBlock(Material type) {
        return false;
    }

    public static boolean isSpeedBlock(Material type) {
        return type == Material.DIAMOND_BLOCK;
    }

    /** 鐵軌正下方是否為鑽石調速方塊 */
    public static boolean hasSpeedBlockBelow(Block rail) {
        if (rail == null || !isAnyRail(rail.getType())) return false;
        return isSpeedBlock(rail.getRelative(BlockFace.DOWN).getType());
    }

    /**
     * 從鐵軌所在柱向下搜尋調速鑽石塊（最多 {@code maxDepth} 格）。
     * 允許金磚等方塊夾在鐵軌與鑽石之間。
     */
    public static Block findSpeedBlockBelow(Block rail, int maxDepth) {
        if (rail == null) return null;
        Block cur = rail;
        for (int i = 0; i < maxDepth; i++) {
            cur = cur.getRelative(BlockFace.DOWN);
            if (isSpeedBlock(cur.getType())) return cur;
            if (cur.getType().isAir()) break;
        }
        return null;
    }

    public static Block findSpeedBlockBelow(Block rail) {
        return findSpeedBlockBelow(rail, 5);
    }

    /** 該柱從鑽石塊往上是否找得到鐵軌（含正上方與爬升軌相鄰格） */
    public static boolean hasRailAbove(Block diamond) {
        if (diamond == null || !isSpeedBlock(diamond.getType())) return false;
        Block above = diamond.getRelative(BlockFace.UP);
        if (isAnyRail(above.getType())) return true;
        // 爬升軌：鐵軌可能在斜上方同一柱
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block adj = above.getRelative(face);
            if (isAnyRail(adj.getType())) return true;
        }
        return false;
    }

    /**
     * 從點擊的方塊解析調速鑽石塊：可直接點鑽石塊，或點其上方（含間隔）的鐵軌。
     */
    public static Block resolveSpeedBlock(Block clicked) {
        if (clicked == null) return null;
        if (isSpeedBlock(clicked.getType())) return clicked;
        if (isAnyRail(clicked.getType())) {
            return findSpeedBlockBelow(clicked);
        }
        return null;
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
            boolean hasRail = false;
            Block above = b;
            for (int d = 1; d <= 4; d++) {
                above = above.getRelative(BlockFace.UP);
                if (isAnyRail(above.getType())) {
                    hasRail = true;
                    break;
                }
            }
            if (!hasRail) continue;
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
