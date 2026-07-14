package com.avery.atrain.cinematic;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.model.TravelDirection;
import com.avery.atrain.util.RailPathSampler;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 站點間定時導引：不依賴鐵軌物理，沿取樣路徑平滑移動並精準停靠下一站金磚。
 */
public final class CinematicTransitTask {

    public enum Phase { DWELLING, MOVING }

    private final AtrainPlugin plugin;
    private final RideableMinecart cart;
    private final Player passenger;
    private final String lineId;
    private final TravelDirection direction;
    private Stop currentStop;
    private String targetStopId;
    private Phase phase = Phase.DWELLING;
    private List<Location> path = List.of();
    private int moveTicks;
    private int moveElapsed;
    private BukkitTask dwellTask;
    private boolean gravityWasEnabled = true;

    public CinematicTransitTask(AtrainPlugin plugin, RideableMinecart cart, Player passenger,
                                Stop atStop, Line line, TravelDirection direction) {
        this.plugin = plugin;
        this.cart = cart;
        this.passenger = passenger;
        this.lineId = line.getId();
        this.direction = direction != null ? direction : TravelDirection.FORWARD;
        this.currentStop = atStop;
        this.targetStopId = line.getNextStopId(atStop.getId(), this.direction);
    }

    public UUID getCartId() { return cart.getUniqueId(); }
    public Phase getPhase() { return phase; }
    public boolean isPassengerRiding() {
        return passenger != null && passenger.isOnline()
                && cart.isValid() && !cart.isDead()
                && cart.getPassengers().contains(passenger);
    }

    /** 進站後開始停留倒數 */
    public void beginDwell() {
        phase = Phase.DWELLING;
        freeze();
        if (currentStop != null) {
            notifyPassenger("cinematic.dwelling", Map.of(
                    "stop", currentStop.getDisplayName(),
                    "sec", String.valueOf(Math.max(1, currentStop.getDwellTimeTicks() / 20))));
        }
        int dwell = currentStop != null ? currentStop.getDwellTimeTicks() : 80;
        if (dwellTask != null) dwellTask.cancel();
        if (dwell <= 0) {
            plugin.getServer().getScheduler().runTask(plugin, this::beginMove);
            return;
        }
        dwellTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            dwellTask = null;
            beginMove();
        }, dwell);
    }

    private void beginMove() {
        if (!isPassengerRiding()) {
            cancel();
            return;
        }
        if (targetStopId == null) {
            notifyPassenger("ride.terminus", Map.of());
            cancel();
            return;
        }
        Stop target = plugin.getStopManager().getStop(targetStopId);
        Line line = plugin.getLineManager().getLine(lineId);
        if (target == null || line == null || currentStop == null) {
            cancel();
            return;
        }

        path = RailPathSampler.betweenStops(currentStop, target, plugin.getRouteManager(), direction);
        if (path.size() < 2) {
            path = List.of(
                    currentStop.getRailLocation(direction),
                    target.getRailLocation(direction));
        }

        int seconds = currentStop.getTravelSecondsToNext(lineId,
                plugin.getConfigManager().getDefaultSegmentSeconds());
        moveTicks = Math.max(20, seconds * 20);
        moveElapsed = 0;
        phase = Phase.MOVING;

        gravityWasEnabled = cart.hasGravity();
        cart.setGravity(false);

        notifyPassenger("cinematic.departing", Map.of(
                "from", currentStop.getDisplayName(),
                "to", target.getDisplayName(),
                "sec", String.valueOf(seconds)));
    }

    /** 每 tick 推進（由 Manager 呼叫） */
    public void tick() {
        if (!isPassengerRiding()) {
            cancel();
            return;
        }
        if (phase != Phase.MOVING) return;

        moveElapsed++;
        double t = RailPathSampler.easeInOut(Math.min(1.0, moveElapsed / (double) moveTicks));
        Location pos = RailPathSampler.sampleAt(path, t);
        if (pos != null) {
            cart.teleport(pos);
        }
        cart.setVelocity(new Vector(0, 0, 0));

        if (moveElapsed >= moveTicks) {
            arrive();
        }
    }

    private void arrive() {
        Stop target = plugin.getStopManager().getStop(targetStopId);
        if (target == null) {
            cancel();
            return;
        }
        Location end = target.getRailLocation(direction);
        if (end != null) {
            cart.teleport(end);
        }
        freeze();
        currentStop = target;
        notifyPassenger("cinematic.arrived", Map.of("stop", target.getDisplayName()));

        Line line = plugin.getLineManager().getLine(lineId);
        String next = line != null ? line.getNextStopId(target.getId(), direction) : null;
        targetStopId = next;
        beginDwell();
    }

    private void freeze() {
        cart.setVelocity(new Vector(0, 0, 0));
        cart.setMaxSpeed(0);
    }

    public void cancel() {
        if (dwellTask != null) {
            dwellTask.cancel();
            dwellTask = null;
        }
        if (cart.isValid() && !cart.isDead()) {
            cart.setGravity(gravityWasEnabled);
            cart.setMaxSpeed(0.4);
        }
    }

    private void notifyPassenger(String key, Map<String, String> ph) {
        if (passenger == null) return;
        TextUtil.send(passenger, plugin.getLanguageManager().get(passenger, key, ph));
    }
}
