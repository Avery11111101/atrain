package com.avery.atrain.route;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.RoutePoint;
import com.avery.atrain.model.Stop;
import com.avery.atrain.model.TravelDirection;
import com.avery.atrain.util.RailUtil;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 分段錄製（支援去程／回程獨立軌跡） */
public final class RouteRecordingSession {

    private final AtrainPlugin plugin;
    private final Player player;
    private final String lineId;
    private final TravelDirection direction;

    private int segmentIndex;
    private RideableMinecart cart;
    private Location lastSampleLoc;
    private final List<RoutePoint> currentSegmentPoints = new ArrayList<>();
    private boolean awaitingCart;
    private boolean everHadCart;
    private boolean notifiedCartLost;
    private boolean ended;
    private boolean autoFinished;
    private boolean dwelling;
    private int dwellTicksRemaining;
    private float savedMaxSpeed;
    private List<RoutePoint> overwriteBackup;
    private boolean overwriteApplied;
    private boolean notifiedDwellWait;

    RouteRecordingSession(AtrainPlugin plugin, Player player, String lineId,
                          int segmentIndex, TravelDirection direction, boolean clearOnFirstSample) {
        this.plugin = plugin;
        this.player = player;
        this.lineId = lineId;
        this.segmentIndex = segmentIndex;
        this.direction = direction != null ? direction : TravelDirection.FORWARD;
        if (clearOnFirstSample) {
            Line line = plugin.getLineManager().getLine(lineId);
            if (line != null) {
                overwriteBackup = new ArrayList<>(line.getSegmentPoints(segmentIndex, this.direction));
            }
        }
    }

    String getLineId() { return lineId; }

    TravelDirection getDirection() { return direction; }

    int getSegmentIndex() { return segmentIndex; }

    boolean isAwaitingCart() { return awaitingCart; }

    boolean hasCart() {
        return cart != null && cart.isValid() && !cart.isDead();
    }

    boolean ownsCart(UUID cartId) {
        return cart != null && cart.getUniqueId().equals(cartId);
    }

    RideableMinecart getCart() { return cart; }

    public int getCurrentPointCount() {
        return currentSegmentPoints.size();
    }

    void beginPending() {
        awaitingCart = true;
        currentSegmentPoints.clear();
    }

    void detachCart() {
        cart = null;
        awaitingCart = true;
    }

    void attachCart(RideableMinecart cart, Stop spawnStop) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null || spawnStop == null) return;
        String expectedFrom = line.getSegmentFromStopId(segmentIndex, direction);
        if (expectedFrom == null || !expectedFrom.equals(spawnStop.getId())) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_wrong_segment_start",
                    segmentLabel(plugin, player, line, segmentIndex, direction)));
            return;
        }

        this.cart = cart;
        awaitingCart = false;
        everHadCart = true;
        notifiedCartLost = false;
        lastSampleLoc = cart.getLocation().clone();
        sampleNow(cart.getLocation());
    }

    void end() {
        ended = true;
    }

    void saveCurrentSegmentAndFinish() {
        persistCurrentSegment();
        autoFinished = false;
    }

    void discardCurrentSegment() {
        currentSegmentPoints.clear();
        restoreOverwriteBackup();
    }

    private void restoreOverwriteBackup() {
        if (overwriteBackup == null) return;
        if (overwriteApplied) {
            Line line = plugin.getLineManager().getLine(lineId);
            if (line != null) {
                if (overwriteBackup.isEmpty()) {
                    line.clearSegment(segmentIndex, direction);
                } else {
                    line.setSegmentPoints(segmentIndex, direction, new ArrayList<>(overwriteBackup));
                }
                plugin.getDataStore().save();
            }
        }
        overwriteBackup = null;
        overwriteApplied = false;
    }

    private void maybeApplyOverwrite() {
        if (overwriteBackup == null || overwriteApplied) return;
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;
        line.clearSegment(segmentIndex, direction);
        overwriteApplied = true;
    }

    boolean tick(long serverTick) {
        if (ended || !player.isOnline()) return false;
        if (!hasCart()) {
            if (everHadCart && !notifiedCartLost) {
                notifiedCartLost = true;
                awaitingCart = true;
                currentSegmentPoints.clear();
                lastSampleLoc = null;
                TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_cart_lost"));
            }
            return true;
        }

        if (!cart.getPassengers().contains(player)) {
            if (lastSampleLoc != null) {
                if (!player.getWorld().equals(lastSampleLoc.getWorld()) || player.getLocation().distanceSquared(lastSampleLoc) > 25) {
                    TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_cancelled"));
                    discardCurrentSegment();
                    ended = true;
                    return false;
                }
            }
            // If dwelling and out of cart but nearby, wait for them to board
            if (dwelling) {
                if (!notifiedDwellWait) {
                    notifiedDwellWait = true;
                    TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_dwell_board"));
                }
                return !autoFinished;
            }
            // If not dwelling and out of cart but nearby, wait for them to board
            return true;
        }

        if (dwelling) {
            holdCartStill();
            notifiedDwellWait = false;
            dwellTicksRemaining--;
            if (dwellTicksRemaining <= 0) {
                finishDwellAndAdvance();
            }
            return !autoFinished;
        }

        if (!RailUtil.isOnRail(cart.getLocation())) return true;

        sampleIfMoved(cart.getLocation());
        checkSegmentDestination(cart.getLocation());
        return !autoFinished;
    }

    private void holdCartStill() {
        if (cart == null || !cart.isValid()) return;
        cart.setVelocity(new Vector(0, 0, 0));
        cart.setMaxSpeed(0);
    }

    private void sampleIfMoved(Location loc) {
        double minDist = plugin.getConfigManager().getSampleDistance();
        if (lastSampleLoc != null && lastSampleLoc.getWorld().equals(loc.getWorld())
                && lastSampleLoc.distanceSquared(loc) < minDist * minDist) {
            return;
        }
        sampleNow(loc);
    }

    private Vector approachVector;

    private void sampleNow(Location loc) {
        maybeApplyOverwrite();
        Location snapped = RailUtil.snapToRailCenter(loc);
        if (lastSampleLoc != null) {
            Vector diff = snapped.toVector().subtract(lastSampleLoc.toVector());
            if (diff.lengthSquared() > 0.01) {
                approachVector = diff.normalize();
            }
        }
        currentSegmentPoints.add(new RoutePoint(snapped));
        lastSampleLoc = snapped.clone();
    }

    private void checkSegmentDestination(Location loc) {
        if (dwelling) return;
        Stop stop = plugin.getStopManager().getStopAtRail(loc);
        if (stop == null) return;

        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;

        String destId = line.getSegmentToStopId(segmentIndex, direction);
        if (destId == null || !destId.equals(stop.getId())) return;

        beginDwellAtDestination(stop, line);
    }

    private void beginDwellAtDestination(Stop destStop, Line line) {
        if (dwelling) return;

        persistCurrentSegment();
        TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_segment_done",
                segmentLabel(plugin, player, line, segmentIndex, direction)));

        int dwell = plugin.getConfigManager().getRecordingDwellTicks();
        if (dwell <= 0) {
            advanceToNextSegment(line);
            return;
        }

        dwelling = true;
        dwellTicksRemaining = dwell;
        savedMaxSpeed = cart != null ? (float) cart.getMaxSpeed() : 0f;
        if (savedMaxSpeed < 0.01f) {
            savedMaxSpeed = (float) plugin.getConfigManager().getCartSpeed();
        }
        holdCartStill();
        TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_dwelling",
                Map.of("sec", String.valueOf((dwell + 19) / 20))));
    }

    private void finishDwellAndAdvance() {
        dwelling = false;
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            autoFinished = true;
            return;
        }
        advanceToNextSegment(line);
        if (!autoFinished && cart != null && cart.isValid()) {
            cart.setMaxSpeed(savedMaxSpeed);
            Vector push = pickDepartDirection(cart.getLocation());
            if (push != null) {
                cart.setVelocity(push.multiply(Math.max(savedMaxSpeed, 0.12)));
            }
        }
    }

    private Vector pickDepartDirection(Location loc) {
        var dirs = RailUtil.getRailDirections(loc);
        if (dirs.isEmpty()) return null;
        if (approachVector != null) {
            Vector best = dirs.get(0);
            double maxDot = -Double.MAX_VALUE;
            for (Vector d : dirs) {
                if (d.dot(approachVector) > maxDot) {
                    maxDot = d.dot(approachVector);
                    best = d;
                }
            }
            return best.clone();
        }
        return dirs.get(0).clone();
    }

    private void advanceToNextSegment(Line line) {
        int next = segmentIndex + 1;
        if (next >= line.getSegmentCount()) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_finished_line"));
            autoFinished = true;
            return;
        }

        segmentIndex = next;
        currentSegmentPoints.clear();
        if (cart != null && cart.isValid()) {
            lastSampleLoc = cart.getLocation().clone();
            sampleNow(cart.getLocation());
        }
        TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_segment_next",
                segmentLabel(plugin, player, line, segmentIndex, direction)));
    }

    private void persistCurrentSegment() {
        if (currentSegmentPoints.isEmpty()) return;
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;
        line.setSegmentPoints(segmentIndex, direction, new ArrayList<>(currentSegmentPoints));
        overwriteBackup = null;
        overwriteApplied = false;
        plugin.getDataStore().save();
    }

    boolean isAutoFinished() { return autoFinished; }

    static Map<String, String> segmentLabel(AtrainPlugin plugin, Player player, Line line,
                                            int segmentIndex, TravelDirection direction) {
        String from = "?";
        String to = "?";
        String dirKey = direction == TravelDirection.REVERSE
                ? "route.direction_reverse" : "route.direction_forward";
        String dirLabel = plugin.getLanguageManager().get(player, dirKey);
        if (line != null) {
            String fromId = line.getSegmentFromStopId(segmentIndex, direction);
            String toId = line.getSegmentToStopId(segmentIndex, direction);
            if (fromId != null) {
                Stop fs = plugin.getStopManager().getStop(fromId);
                from = fs != null ? fs.getDisplayName() : fromId;
            }
            if (toId != null) {
                Stop ts = plugin.getStopManager().getStop(toId);
                to = ts != null ? ts.getDisplayName() : toId;
            }
        }
        return Map.of(
                "from", from,
                "to", to,
                "dir", dirLabel,
                "index", String.valueOf(segmentIndex + 1),
                "total", String.valueOf(line != null ? line.getSegmentCount() : 0));
    }
}
