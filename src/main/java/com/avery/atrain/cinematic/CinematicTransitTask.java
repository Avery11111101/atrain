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
    private Location lastPos;

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
        int dwell = plugin.getCinematicTransitManager() != null
                ? plugin.getCinematicTransitManager().calculateDwellTicks(currentStop, lineId, direction)
                : (currentStop != null ? currentStop.getDwellTimeTicks() : 80);
        beginDwell(dwell);
    }

    public void beginDwell(int dwellTicks) {
        phase = Phase.DWELLING;
        freeze();
        int displaySec = Math.max(1, (int) Math.ceil(dwellTicks / 20.0));
        if (currentStop != null) {
            notifyPassenger("cinematic.dwelling", Map.of(
                    "stop", currentStop.getDisplayName(),
                    "sec", String.valueOf(displaySec)));
        }
        if (dwellTask != null) dwellTask.cancel();
        if (dwellTicks <= 0) {
            plugin.getServer().getScheduler().runTask(plugin, this::beginMove);
            return;
        }
        dwellTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            dwellTask = null;
            beginMove();
        }, dwellTicks);
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

        int defaultSec = plugin.getConfigManager().getDefaultSegmentSeconds();
        int seconds = currentStop.getTravelSecondsToNext(lineId, -1);
        if (seconds <= 0 && direction == TravelDirection.REVERSE && target != null) {
            // 回程時若當前站未設定，對稱繼承目標站點（去程起點）的行駛秒數
            seconds = target.getTravelSecondsToNext(lineId, -1);
        }
        if (seconds <= 0) {
            seconds = defaultSec;
        }
        moveTicks = Math.max(20, seconds * 20);
        moveElapsed = 0;
        phase = Phase.MOVING;

        gravityWasEnabled = cart.hasGravity();
        cart.setGravity(false);
        // 解除停靠時 maxSpeed=0 的凍結，賦予足夠的最高速度讓伺服器與客戶端正確產生 FOV 與疾馳速度感
        cart.setMaxSpeed(Math.max(1.0, line.getMaxSpeed() * 2.5));
        lastPos = cart.getLocation();

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
        double t = RailPathSampler.smoothTransit(Math.min(1.0, moveElapsed / (double) moveTicks));
        Location pos = RailPathSampler.sampleAt(path, t);
        if (pos != null) {
            if (lastPos != null && lastPos.getWorld().equals(pos.getWorld())) {
                Vector vel = pos.toVector().subtract(lastPos.toVector());
                cart.setVelocity(vel);
            }
            cart.teleport(pos);
            lastPos = pos;
        }

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
        if (passenger != null) {
            plugin.getActiveNavigationManager().onPlayerArriveAtStop(passenger, target);
        }

        Line line = plugin.getLineManager().getLine(lineId);
        String next = line != null ? line.getNextStopId(target.getId(), direction) : null;
        targetStopId = next;
        beginDwell();
    }

    private void freeze() {
        cart.setVelocity(new Vector(0, 0, 0));
        cart.setMaxSpeed(0);
        lastPos = null;
    }

    public void cancel() {
        if (dwellTask != null) {
            dwellTask.cancel();
            dwellTask = null;
        }
        lastPos = null;
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
