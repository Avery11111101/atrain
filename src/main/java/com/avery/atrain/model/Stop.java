package com.avery.atrain.model;

import com.avery.atrain.util.RailUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.avery.atrain.util.BlockCoords;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 虛擬路線站點：金磚上鋪軌道即月台；站點資訊改為站在金磚上查看 */
public class Stop {
    private String id;
    private String displayName;
    private String world;
    /** 金磚座標（打包 long，避免高頻 contains 時的字串分配） */
    private final Set<Long> goldBlockKeys = new LinkedHashSet<>();
    /** 鑽石塊座標（資訊顯示用） */
    private final Set<Long> displayBlockKeys = new LinkedHashSet<>();
    private int dwellTimeTicks = 80;
    /** 資訊顯示：上一站（純文字，與路線無關） */
    private String infoPrev = "-";
    /** 資訊顯示：下一站（純文字，與路線無關） */
    private String infoNext = "-";
    /** 重點站名稱（轉乘/主要目的地提示） */
    private String keyStation = "-";
    /** 行駛方向（如：北上、南下、東向） */
    private String keyDirection = "-";
    /** 管理員專用備註（僅管理員看得到） */
    private String adminInfo = "";
    /** 用於自動顯示上下站的路線 ID（可選，預設取第一條所屬路線） */
    private String displayLineId;
    /** 站在回程月台時用於顯示上下站的路線 ID */
    private String returnLineId;
    /** 回程月台金磚座標（其餘金磚視為去程月台） */
    private final Set<Long> returnGoldBlockKeys = new LinkedHashSet<>();
    private List<String> lineIds = new ArrayList<>();
    /** 各路線由此站發車到下一站的行駛秒數（lineId -> 秒） */
    private final Map<String, Integer> lineTravelSeconds = new HashMap<>();

    public Stop() {}

    public Stop(String id, String displayName, String world) {
        this.id = id;
        this.displayName = displayName;
        this.world = world;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }
    public List<String> getGoldBlocks() {
        List<String> out = new ArrayList<>(goldBlockKeys.size());
        for (long packed : goldBlockKeys) out.add(BlockCoords.toDataString(packed));
        return out;
    }

    public void setGoldBlocks(List<String> goldBlocks) {
        goldBlockKeys.clear();
        if (goldBlocks == null) return;
        for (String key : goldBlocks) {
            long packed = BlockCoords.fromDataString(key);
            if (BlockCoords.isValid(packed)) goldBlockKeys.add(packed);
        }
    }

    public List<String> getDisplayBlocks() {
        List<String> out = new ArrayList<>(displayBlockKeys.size());
        for (long packed : displayBlockKeys) out.add(BlockCoords.toDataString(packed));
        return out;
    }

    public void setDisplayBlocks(List<String> displayBlocks) {
        displayBlockKeys.clear();
        if (displayBlocks == null) return;
        for (String key : displayBlocks) {
            long packed = BlockCoords.fromDataString(key);
            if (BlockCoords.isValid(packed)) displayBlockKeys.add(packed);
        }
    }
    public int getDwellTimeTicks() { return dwellTimeTicks; }
    public void setDwellTimeTicks(int dwellTimeTicks) { this.dwellTimeTicks = Math.max(0, dwellTimeTicks); }
    public String getInfoPrev() { return infoPrev != null && !infoPrev.isBlank() ? infoPrev : "-"; }
    public void setInfoPrev(String infoPrev) { this.infoPrev = infoPrev; }
    public String getInfoNext() { return infoNext != null && !infoNext.isBlank() ? infoNext : "-"; }
    public void setInfoNext(String infoNext) { this.infoNext = infoNext; }
    public String getKeyStation() { return blankToDash(keyStation); }
    public void setKeyStation(String keyStation) { this.keyStation = keyStation; }
    public String getKeyDirection() { return blankToDash(keyDirection); }
    public void setKeyDirection(String keyDirection) { this.keyDirection = keyDirection; }
    public String getAdminInfo() { return adminInfo != null ? adminInfo : ""; }
    public void setAdminInfo(String adminInfo) { this.adminInfo = adminInfo; }
    public boolean hasKeyInfo() {
        return !"-".equals(getKeyStation()) || !"-".equals(getKeyDirection());
    }
    public List<String> getLineIds() { return lineIds; }
    public String getDisplayLineId() { return displayLineId; }
    public void setDisplayLineId(String displayLineId) { this.displayLineId = displayLineId; }
    public String getReturnLineId() { return returnLineId; }
    public void setReturnLineId(String returnLineId) { this.returnLineId = returnLineId; }
    public List<String> getReturnGoldBlocks() {
        List<String> out = new ArrayList<>(returnGoldBlockKeys.size());
        for (long packed : returnGoldBlockKeys) out.add(BlockCoords.toDataString(packed));
        return out;
    }

    public void setReturnGoldBlocks(List<String> keys) {
        returnGoldBlockKeys.clear();
        if (keys == null) return;
        for (String key : keys) {
            long packed = BlockCoords.fromDataString(key);
            if (BlockCoords.isValid(packed)) returnGoldBlockKeys.add(packed);
        }
    }

    public boolean hasAnyGoldBlock() {
        return !goldBlockKeys.isEmpty();
    }

    public boolean ownsGoldKey(String key) {
        long packed = BlockCoords.fromDataString(key);
        if (!BlockCoords.isValid(packed)) return false;
        return goldBlockKeys.contains(packed) || returnGoldBlockKeys.contains(packed);
    }

    public boolean removeGoldKey(String key) {
        long packed = BlockCoords.fromDataString(key);
        if (!BlockCoords.isValid(packed)) return false;
        return goldBlockKeys.remove(packed) | returnGoldBlockKeys.remove(packed);
    }

    public void addReturnGoldKey(String key) {
        long packed = BlockCoords.fromDataString(key);
        if (BlockCoords.isValid(packed)) returnGoldBlockKeys.add(packed);
    }

    /** 玩家是否站在去程月台 */
    public boolean isOnForwardPlatform(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        return isOnPlatformGold(loc, forwardGoldKeys());
    }

    /** 玩家是否站在回程月台（僅比對已註冊金磚，不讀取世界方塊） */
    public boolean isReturnPlatformAt(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        if (returnGoldBlockKeys.isEmpty()) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return hasReturnGoldBlock(x, y, z) || hasReturnGoldBlock(x, y - 1, z);
    }

    /** 玩家是否站在回程月台 */
    public boolean isOnReturnPlatform(Location loc) {
        return isReturnPlatformAt(loc);
    }

    /** 依行駛方向取得月台鐵軌上的礦車停靠點 */
    public Location getRailLocation(TravelDirection direction) {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        List<String> keys = direction == TravelDirection.REVERSE && !returnGoldBlockKeys.isEmpty()
                ? getReturnGoldBlocks()
                : forwardGoldKeys();
        if (keys.isEmpty()) return null;
        for (String k : keys) {
            Location rail = railLocationForGoldKey(w, k);
            if (rail != null) return rail;
        }
        return null;
    }

    private List<String> forwardGoldKeys() {
        List<String> forward = new ArrayList<>();
        for (long packed : goldBlockKeys) {
            if (!returnGoldBlockKeys.contains(packed)) forward.add(BlockCoords.toDataString(packed));
        }
        if (forward.isEmpty()) return getGoldBlocks();
        return forward;
    }

    private boolean isOnPlatformGold(Location loc, List<String> platformKeys) {
        if (platformKeys.isEmpty()) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        String k1 = key(x, y, z);
        String k2 = key(x, y - 1, z);
        if (platformKeys.contains(k1) || platformKeys.contains(k2)) return true;
        Block rail = RailUtil.findRailBlock(loc);
        if (rail != null) {
            Block below = rail.getRelative(BlockFace.DOWN);
            for (int d = 0; d <= 4; d++) {
                String bk = key(below.getX(), below.getY(), below.getZ());
                if (platformKeys.contains(bk)) return true;
                below = below.getRelative(BlockFace.DOWN);
            }
        }
        return false;
    }

    private Location railLocationForGoldKey(World w, String goldKey) {
        int[] p = parseKey(goldKey);
        if (p == null) return null;
        Block gold = w.getBlockAt(p[0], p[1], p[2]);
        Block above = gold.getRelative(BlockFace.UP);
        if (RailUtil.isRailMaterial(above.getType())) {
            return RailUtil.cartPositionOnRail(above);
        }
        for (int dy = 1; dy <= 3; dy++) {
            Block up = gold.getRelative(0, dy, 0);
            if (RailUtil.isRailMaterial(up.getType())) {
                return RailUtil.cartPositionOnRail(up);
            }
        }
        Block nearest = RailUtil.findNearestRailBlock(
                new Location(w, p[0] + 0.5, p[1] + 1.0, p[2] + 0.5), 3);
        if (nearest != null) return RailUtil.cartPositionOnRail(nearest);
        return new Location(w, p[0] + 0.5, p[1] + 1.0, p[2] + 0.5);
    }

    private static String blankToDash(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }
    public void setLineIds(List<String> lineIds) { this.lineIds = lineIds != null ? lineIds : new ArrayList<>(); }

    public Map<String, Integer> getLineTravelSeconds() { return lineTravelSeconds; }

    public void setLineTravelSeconds(Map<String, Integer> map) {
        lineTravelSeconds.clear();
        if (map != null) lineTravelSeconds.putAll(map);
    }

    /** 由此站沿指定路線到下一站的行駛秒數；未設則用預設值 */
    public int getTravelSecondsToNext(String lineId, int defaultSeconds) {
        if (lineId == null) return defaultSeconds;
        Integer v = lineTravelSeconds.get(lineId);
        return v != null && v > 0 ? v : defaultSeconds;
    }

    public void setTravelSecondsToNext(String lineId, int seconds) {
        if (lineId == null) return;
        lineTravelSeconds.put(lineId, Math.max(5, Math.min(seconds, 600)));
    }

    public static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public static int[] parseKey(String key) {
        String[] p = key.split(",");
        if (p.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean hasGoldBlock(int x, int y, int z) {
        return goldBlockKeys.contains(BlockCoords.pack(x, y, z));
    }

    public boolean hasDisplayBlock(int x, int y, int z) {
        return displayBlockKeys.contains(BlockCoords.pack(x, y, z));
    }

    public boolean containsDisplay(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        return hasDisplayBlock(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** 玩家站立位置是否應顯示站點資訊（金磚月台） */
    public boolean containsInfoLocation(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        if (hasGoldBlock(x, y, z) || hasReturnGoldBlock(x, y, z)) return true;
        return hasGoldBlock(x, y - 1, z) || hasReturnGoldBlock(x, y - 1, z);
    }

    public boolean hasReturnGoldBlock(int x, int y, int z) {
        return returnGoldBlockKeys.contains(BlockCoords.pack(x, y, z));
    }

    public boolean containsRail(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        if (hasGoldBlock(x, y - 1, z) || hasReturnGoldBlock(x, y - 1, z)) return true;
        if (hasGoldBlock(x, y, z) || hasReturnGoldBlock(x, y, z)) return true;
        org.bukkit.block.Block rail = com.avery.atrain.util.RailUtil.findRailBlock(loc);
        if (rail != null) {
            org.bukkit.block.Block below = rail.getRelative(org.bukkit.block.BlockFace.DOWN);
            if (hasGoldBlock(below.getX(), below.getY(), below.getZ())
                    || hasReturnGoldBlock(below.getX(), below.getY(), below.getZ())) return true;
            for (int d = 2; d <= 4; d++) {
                below = below.getRelative(org.bukkit.block.BlockFace.DOWN);
                if (hasGoldBlock(below.getX(), below.getY(), below.getZ())
                        || hasReturnGoldBlock(below.getX(), below.getY(), below.getZ())) return true;
            }
        }
        return false;
    }

    /** 去程月台鐵軌位置（相容舊 API） */
    public Location getPrimaryRailLocation() {
        return getRailLocation(TravelDirection.FORWARD);
    }

    public void addGoldBlock(int x, int y, int z) {
        goldBlockKeys.add(BlockCoords.pack(x, y, z));
    }

    public void addDisplayBlock(int x, int y, int z) {
        displayBlockKeys.add(BlockCoords.pack(x, y, z));
    }

    public Set<Location> getGoldLocations(World w) {
        Set<Location> out = new HashSet<>();
        if (w == null) return out;
        for (long packed : goldBlockKeys) {
            out.add(new Location(w, BlockCoords.unpackX(packed), BlockCoords.unpackY(packed), BlockCoords.unpackZ(packed)));
        }
        return out;
    }
}
