package com.avery.atrain.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class Stop {
    private String id;
    private String displayName;
    private String world;
    private double c1x, c1y, c1z, c2x, c2y, c2z;

    private double forwardX, forwardY, forwardZ;
    private float forwardYaw;
    private boolean hasForwardPoint;

    private double returnX, returnY, returnZ;
    private float returnYaw;
    private boolean hasReturnPoint;

    private List<String> lineIds = new ArrayList<>();

    public Stop() {}

    public Stop(String id, String displayName, Location corner1, Location corner2) {
        this.id = id;
        this.displayName = displayName;
        setCorner1(corner1);
        setCorner2(corner2);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }
    public List<String> getLineIds() { return lineIds; }
    public void setLineIds(List<String> lineIds) { this.lineIds = lineIds; }

    public double getC1x() { return c1x; }
    public double getC1y() { return c1y; }
    public double getC1z() { return c1z; }
    public double getC2x() { return c2x; }
    public double getC2y() { return c2y; }
    public double getC2z() { return c2z; }

    public boolean hasForwardPoint() { return hasForwardPoint; }
    public boolean hasReturnPoint() { return hasReturnPoint; }

    /** 舊版相容：等同去程月台 */
    public float getLaunchYaw() {
        return hasForwardPoint ? forwardYaw : 0;
    }

    /** 舊版相容：寫入去程月台 */
    public void setLaunchYaw(float launchYaw) {
        if (hasForwardPoint) forwardYaw = launchYaw;
    }

    public double getStopX() { return hasForwardPoint ? forwardX : 0; }
    public double getStopY() { return hasForwardPoint ? forwardY : 0; }
    public double getStopZ() { return hasForwardPoint ? forwardZ : 0; }

    public void setCorner1(Location loc) {
        this.world = loc.getWorld().getName();
        this.c1x = loc.getX(); this.c1y = loc.getY(); this.c1z = loc.getZ();
    }

    public void setCorner2(Location loc) {
        this.c2x = loc.getX(); this.c2y = loc.getY(); this.c2z = loc.getZ();
    }

    public Location getForwardPoint() {
        return pointAt(forwardX, forwardY, forwardZ, forwardYaw, hasForwardPoint);
    }

    public Location getReturnPoint() {
        return pointAt(returnX, returnY, returnZ, returnYaw, hasReturnPoint);
    }

    public Location getBoardPoint(PlatformSide side) {
        return side == PlatformSide.RETURN ? getReturnPoint() : getForwardPoint();
    }

    public Location getBoardPoint(TravelDirection direction) {
        return direction == TravelDirection.REVERSE ? getReturnPoint() : getForwardPoint();
    }

    /** 抵達時使用的月台：順向抵達去程側，逆向抵達回程側 */
    public Location getArrivalPoint(TravelDirection direction) {
        return getBoardPoint(direction);
    }

    /** 舊版相容 */
    public Location getStopPoint() {
        return getForwardPoint();
    }

    public void setBoardPoint(PlatformSide side, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        this.world = loc.getWorld().getName();
        if (side == PlatformSide.RETURN) {
            returnX = loc.getX();
            returnY = loc.getY();
            returnZ = loc.getZ();
            returnYaw = loc.getYaw();
            hasReturnPoint = true;
        } else {
            forwardX = loc.getX();
            forwardY = loc.getY();
            forwardZ = loc.getZ();
            forwardYaw = loc.getYaw();
            hasForwardPoint = true;
        }
    }

    /** 舊版相容：寫入去程月台 */
    public void setStopPoint(Location loc) {
        setBoardPoint(PlatformSide.FORWARD, loc);
    }

    public Vector getLaunchDirection(PlatformSide side) {
        Location p = getBoardPoint(side);
        if (p == null) return null;
        return yawToVector(p.getYaw());
    }

    public Vector getLaunchDirection(TravelDirection direction) {
        return getLaunchDirection(direction == TravelDirection.REVERSE
                ? PlatformSide.RETURN : PlatformSide.FORWARD);
    }

    /** 舊版相容 */
    public Vector getLaunchDirection() {
        return getLaunchDirection(PlatformSide.FORWARD);
    }

    public BoundingBox getBoundingBox() {
        double minX = Math.min(c1x, c2x), maxX = Math.max(c1x, c2x);
        double minY = Math.min(c1y, c2y), maxY = Math.max(c1y, c2y);
        double minZ = Math.min(c1z, c2z), maxZ = Math.max(c1z, c2z);
        return new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        return getBoundingBox().contains(loc.getX(), loc.getY(), loc.getZ());
    }

    /** 從舊版單一停靠點遷移 */
    public void migrateLegacyStopPoint(double x, double y, double z, float yaw) {
        if (!hasForwardPoint) {
            forwardX = x;
            forwardY = y;
            forwardZ = z;
            forwardYaw = yaw;
            hasForwardPoint = true;
        }
    }

    public void clearBoardPoint(PlatformSide side) {
        if (side == PlatformSide.RETURN) {
            hasReturnPoint = false;
        } else {
            hasForwardPoint = false;
        }
    }

    private Location pointAt(double x, double y, double z, float yaw, boolean set) {
        if (!set) return null;
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, 0);
    }

    private static Vector yawToVector(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.sin(rad), 0, Math.cos(rad));
    }
}
