package com.avery.atrain.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

public class RoutePoint {
    private String world;
    private double x, y, z;

    public RoutePoint() {}

    public RoutePoint(Location loc) {
        this.world = loc.getWorld().getName();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
    }

    public RoutePoint(String world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z);
    }

    public Vector toVector() {
        return new Vector(x, y, z);
    }

    public double distanceSquared(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return Double.MAX_VALUE;
        double dx = loc.getX() - x, dy = loc.getY() - y, dz = loc.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static RoutePoint fromString(String s) {
        try {
            String[] p = s.split(",");
            if (p.length < 4) return null;
            return new RoutePoint(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        } catch (Exception e) {
            return null;
        }
    }

    public String toDataString() {
        return world + "," + x + "," + y + "," + z;
    }
}
