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
    private boolean ended;
    private boolean autoFinished;

    RouteRecordingSession(AtrainPlugin plugin, Player player, String lineId,
                          int segmentIndex, TravelDirection direction) {
        this.plugin = plugin;
        this.player = player;
        this.lineId = lineId;
        this.segmentIndex = segmentIndex;
        this.direction = direction != null ? direction : TravelDirection.FORWARD;
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

    void attachCart(RideableMinecart cart, Stop spawnStop) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null || spawnStop == null) return;
        String expectedFrom = line.getSegmentFromStopId(segmentIndex, direction);
        if (expectedFrom == null || !expectedFrom.equals(spawnStop.getId())) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_wrong_segment_start",
                    segmentLabel(plugin, line, segmentIndex, direction)));
            return;
        }

        this.cart = cart;
        awaitingCart = false;
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
    }

    boolean tick(long serverTick) {
        if (ended || !player.isOnline()) return false;
        if (!hasCart()) return true;
        if (!cart.getPassengers().contains(player)) return true;
        if (!RailUtil.isOnRail(cart.getLocation())) return true;

        sampleIfMoved(cart.getLocation());
        checkSegmentDestination(cart.getLocation());
        return !autoFinished;
    }

    private void sampleIfMoved(Location loc) {
        double minDist = plugin.getConfigManager().getSampleDistance();
        if (lastSampleLoc != null && lastSampleLoc.getWorld().equals(loc.getWorld())
                && lastSampleLoc.distanceSquared(loc) < minDist * minDist) {
            return;
        }
        sampleNow(loc);
    }

    private void sampleNow(Location loc) {
        Location snapped = RailUtil.snapToRailCenter(loc);
        currentSegmentPoints.add(new RoutePoint(snapped));
        lastSampleLoc = snapped.clone();
    }

    private void checkSegmentDestination(Location loc) {
        Stop stop = plugin.getStopManager().getStopAtRail(loc);
        if (stop == null) return;

        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;

        String destId = line.getSegmentToStopId(segmentIndex, direction);
        if (destId == null || !destId.equals(stop.getId())) return;

        completeSegmentAt(stop, line);
    }

    private void completeSegmentAt(Stop destStop, Line line) {
        persistCurrentSegment();
        TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_segment_done",
                segmentLabel(plugin, line, segmentIndex, direction)));

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
                segmentLabel(plugin, line, segmentIndex, direction)));
    }

    private void persistCurrentSegment() {
        if (currentSegmentPoints.isEmpty()) return;
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;
        line.setSegmentPoints(segmentIndex, direction, new ArrayList<>(currentSegmentPoints));
        plugin.getDataStore().save();
    }

    boolean isAutoFinished() { return autoFinished; }

    static Map<String, String> segmentLabel(AtrainPlugin plugin, Line line,
                                            int segmentIndex, TravelDirection direction) {
        String from = "?";
        String to = "?";
        String dirLabel = direction == TravelDirection.REVERSE ? "回程" : "去程";
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
