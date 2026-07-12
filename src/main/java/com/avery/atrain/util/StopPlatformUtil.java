package com.avery.atrain.util;

import com.avery.atrain.model.PlatformSide;
import com.avery.atrain.model.Stop;
import org.bukkit.Location;

public final class StopPlatformUtil {

    public enum ConflictLevel {
        OK,
        SAME_RAIL_WARN,
        SAME_DIRECTION_BLOCK
    }

    private StopPlatformUtil() {}

    public static boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    public static ConflictLevel checkConflict(Stop stop) {
        if (!stop.hasForwardPoint() || !stop.hasReturnPoint()) return ConflictLevel.OK;
        Location f = stop.getForwardPoint();
        Location r = stop.getReturnPoint();
        if (!isSameBlock(f, r)) return ConflictLevel.OK;
        float diff = Math.abs(normalizeYaw(f.getYaw() - r.getYaw()));
        if (diff < 90f) return ConflictLevel.SAME_DIRECTION_BLOCK;
        return ConflictLevel.SAME_RAIL_WARN;
    }

    public static PlatformSide detectPlatform(Stop stop, Location clickedBlock) {
        if (stop == null || clickedBlock == null) return null;
        Location probe = clickedBlock.clone().add(0.5, 0.5, 0.5);
        if (stop.hasForwardPoint() && isSameBlock(probe, stop.getForwardPoint())) {
            return PlatformSide.FORWARD;
        }
        if (stop.hasReturnPoint() && isSameBlock(probe, stop.getReturnPoint())) {
            return PlatformSide.RETURN;
        }
        // 若未精確點到月台，依距離判斷較近的軌道
        if (stop.hasForwardPoint() && stop.hasReturnPoint()) {
            Location f = stop.getForwardPoint();
            Location r = stop.getReturnPoint();
            double df = probe.distanceSquared(f);
            double dr = probe.distanceSquared(r);
            return df <= dr ? PlatformSide.FORWARD : PlatformSide.RETURN;
        }
        return null;
    }

    private static float normalizeYaw(float yaw) {
        float n = yaw % 360f;
        if (n < 0) n += 360f;
        if (n > 180f) n = 360f - n;
        return n;
    }
}
