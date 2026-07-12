package com.avery.atrain.train;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.RoutePoint;
import com.avery.atrain.hangrail.HangRailInfo;
import com.avery.atrain.hangrail.HangRailUtil;
import com.avery.atrain.util.RailUtil;
import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * 路徑導引控制器 — 修正 Metro 飄線問題。
 * 沿錄製的 route_points 主動糾偏，在岔路選擇正確分支。
 */
public class PathGuideController {

    private final AtrainPlugin plugin;

    public PathGuideController(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public int findNearestRouteIndex(List<RoutePoint> points, Location loc) {
        return findForwardRouteIndex(points, loc, 0);
    }

    public int findForwardRouteIndex(List<RoutePoint> points, Location loc, int fromIndex) {
        if (points.isEmpty() || loc.getWorld() == null) return 0;
        int start = Math.max(0, fromIndex);
        int best = start;
        double bestDist = Double.MAX_VALUE;
        for (int i = start; i < points.size(); i++) {
            RoutePoint pt = points.get(i);
            if (!pt.getWorld().equals(loc.getWorld().getName())) continue;
            double d = pt.distanceSquared(loc);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    public RoutePoint findNextWaypoint(List<RoutePoint> points, Location loc, int fromIndex) {
        if (points.isEmpty()) return null;
        int start = Math.max(0, fromIndex);
        double minAhead = 1.5;
        for (int i = start; i < points.size(); i++) {
            RoutePoint pt = points.get(i);
            if (!pt.getWorld().equals(loc.getWorld().getName())) continue;
            Vector diff = pt.toVector().subtract(loc.toVector());
            if (diff.lengthSquared() >= minAhead * minAhead) return pt;
        }
        return points.get(points.size() - 1);
    }

    public void applyGuidance(Minecart cart, List<RoutePoint> routePoints, int routeIndex) {
        if (!plugin.getConfigManager().isPathGuidance() || routePoints.isEmpty()) return;

        Location loc = cart.getLocation();
        if (!isOnTrack(loc)) return;

        RoutePoint target = findNextWaypoint(routePoints, loc, routeIndex);
        if (target == null) return;

        Vector toTarget = target.toVector().subtract(loc.toVector());
        toTarget.setY(0);
        if (toTarget.lengthSquared() < 0.01) return;

        Vector steerDir = resolveSteerDirection(loc, toTarget, cart);
        if (steerDir == null) return;

        if (plugin.getConfigManager().isObstructionCheck()
                && RailUtil.hasObstructionAhead(loc, steerDir, plugin.getConfigManager().getObstructionDistance())) {
            return;
        }

        double strength = plugin.getConfigManager().getGuidanceStrength();
        double maxSpeed = cart.getMaxSpeed();
        Vector current = cart.getVelocity();
        double speed = Math.max(current.length(), plugin.getConfigManager().getMinCruiseSpeed());
        speed = Math.min(speed, maxSpeed);

        Vector targetVel = steerDir.clone().multiply(speed);
        Vector blended = current.clone().multiply(1 - strength).add(targetVel.multiply(strength));
        blended.setY(current.getY());
        cart.setVelocity(blended);
    }

    public void recoverStall(Minecart cart, List<RoutePoint> routePoints, int routeIndex) {
        if (!plugin.getConfigManager().isStallRecovery()
                || plugin.getConfigManager().isVanillaMovement()) return;

        Location loc = cart.getLocation();
        if (!isOnTrack(loc)) return;

        double minSpeed = plugin.getConfigManager().getMinCruiseSpeed();
        Vector vel = cart.getVelocity();
        if (vel.clone().setY(0).length() >= minSpeed) return;

        Vector steerDir = null;
        if (!routePoints.isEmpty()) {
            RoutePoint target = findNextWaypoint(routePoints, loc, routeIndex);
            if (target != null) {
                Vector toTarget = target.toVector().subtract(loc.toVector());
                toTarget.setY(0);
                steerDir = resolveSteerDirection(loc, toTarget, cart);
            }
        }
        if (steerDir == null) {
            steerDir = resolveSteerDirection(loc, null, cart);
        }
        if (steerDir == null) return;

        if (plugin.getConfigManager().isObstructionCheck()
                && RailUtil.hasObstructionAhead(loc, steerDir, plugin.getConfigManager().getObstructionDistance())) {
            return;
        }

        double targetSpeed = Math.min(cart.getMaxSpeed(), plugin.getConfigManager().getCartSpeed());
        Vector newVel = steerDir.clone().multiply(targetSpeed);
        newVel.setY(vel.getY());
        cart.setVelocity(newVel);
    }

    private boolean isOnTrack(Location loc) {
        if (RailUtil.isOnRail(loc)) return true;
        if (!plugin.getConfigManager().isHangRailEnabled()) return false;
        return HangRailUtil.findHangRail(loc, plugin.getConfigManager().getHangRailTypes()) != null;
    }

    private Vector resolveSteerDirection(Location loc, org.bukkit.util.Vector toTarget, Minecart cart) {
        if (plugin.getConfigManager().isHangRailEnabled()) {
            HangRailInfo info = HangRailUtil.findHangRail(loc, plugin.getConfigManager().getHangRailTypes());
            if (info != null) {
                if (toTarget != null) {
                    return HangRailUtil.pickBestDirection(loc, info, toTarget);
                }
                return HangRailUtil.pickContinuation(loc, info, cart.getVelocity());
            }
        }
        if (toTarget != null) {
            return RailUtil.pickBestRailDirection(loc, toTarget);
        }
        return RailUtil.pickContinuationDirection(loc, cart.getVelocity());
    }
}
