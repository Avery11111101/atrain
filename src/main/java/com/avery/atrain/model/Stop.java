package com.avery.atrain.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 虛擬路線站點：金磚上鋪軌道即月台，鄰近鑽石塊顯示站名資訊 */
public class Stop {
    private String id;
    private String displayName;
    private String world;
    /** 金磚座標 "x,y,z" */
    private final List<String> goldBlocks = new ArrayList<>();
    /** 鑽石塊座標 "x,y,z"（資訊顯示用） */
    private final List<String> displayBlocks = new ArrayList<>();
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
    private final List<String> returnGoldBlocks = new ArrayList<>();
    private List<String> lineIds = new ArrayList<>();

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
    public List<String> getGoldBlocks() { return goldBlocks; }
    public void setGoldBlocks(List<String> goldBlocks) {
        this.goldBlocks.clear();
        if (goldBlocks != null) this.goldBlocks.addAll(goldBlocks);
    }
    public List<String> getDisplayBlocks() { return displayBlocks; }
    public void setDisplayBlocks(List<String> displayBlocks) {
        this.displayBlocks.clear();
        if (displayBlocks != null) this.displayBlocks.addAll(displayBlocks);
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
    public List<String> getReturnGoldBlocks() { return returnGoldBlocks; }
    public void setReturnGoldBlocks(List<String> keys) {
        returnGoldBlocks.clear();
        if (keys != null) returnGoldBlocks.addAll(keys);
    }

    /** 玩家是否站在回程月台（金磚或鑽石顯示塊） */
    public boolean isOnReturnPlatform(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        if (returnGoldBlocks.isEmpty()) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        String k1 = key(x, y, z);
        String k2 = key(x, y - 1, z);
        return returnGoldBlocks.contains(k1) || returnGoldBlocks.contains(k2);
    }

    private static String blankToDash(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }
    public void setLineIds(List<String> lineIds) { this.lineIds = lineIds != null ? lineIds : new ArrayList<>(); }

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
        return goldBlocks.contains(key(x, y, z));
    }

    public boolean hasDisplayBlock(int x, int y, int z) {
        return displayBlocks.contains(key(x, y, z));
    }

    public boolean containsDisplay(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        return hasDisplayBlock(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** 玩家站立位置是否應顯示站點資訊（鑽石塊或金磚月台） */
    public boolean containsInfoLocation(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        if (hasDisplayBlock(x, y, z) || hasGoldBlock(x, y, z)) return true;
        return hasDisplayBlock(x, y - 1, z) || hasGoldBlock(x, y - 1, z);
    }

    public boolean containsRail(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        World w = loc.getWorld();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        if (hasGoldBlock(x, y - 1, z)) return true;
        if (hasGoldBlock(x, y, z)) return true;
        return false;
    }

    public Location getPrimaryRailLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null || goldBlocks.isEmpty()) return null;
        int[] p = parseKey(goldBlocks.get(0));
        if (p == null) return null;
        return new Location(w, p[0] + 0.5, p[1] + 1.0, p[2] + 0.5);
    }

    public void addGoldBlock(int x, int y, int z) {
        String k = key(x, y, z);
        if (!goldBlocks.contains(k)) goldBlocks.add(k);
    }

    public void addDisplayBlock(int x, int y, int z) {
        String k = key(x, y, z);
        if (!displayBlocks.contains(k)) displayBlocks.add(k);
    }

    public Set<Location> getGoldLocations(World w) {
        Set<Location> out = new HashSet<>();
        if (w == null) return out;
        for (String k : goldBlocks) {
            int[] p = parseKey(k);
            if (p != null) out.add(new Location(w, p[0], p[1], p[2]));
        }
        return out;
    }
}
