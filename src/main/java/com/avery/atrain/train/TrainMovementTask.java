package com.avery.atrain.train;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Stop;
import com.avery.atrain.model.TravelDirection;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;

public class TrainMovementTask implements Listener {

    private final TrainSession session;
    private final PathGuideController pathGuide;
    private BukkitTask guidanceTask;
    private BukkitTask stallTask;
    private BukkitTask speedTask;
    private BukkitTask departureTask;

    public TrainMovementTask(AtrainPlugin plugin, Minecart minecart, Player passenger,
                             String lineId, String fromStopId) {
        this(plugin, minecart, passenger, lineId, fromStopId, TravelDirection.FORWARD);
    }

    public TrainMovementTask(AtrainPlugin plugin, Minecart minecart, Player passenger,
                             String lineId, String fromStopId, TravelDirection direction) {
        this.session = new TrainSession(plugin, minecart, passenger, lineId, fromStopId, direction,
                TrainSession.State.STOPPED_AT_STATION);
        this.pathGuide = new PathGuideController(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        TrainTaskRegistry.register(minecart, this);
        scheduleDeparture();
    }

    public TrainSession getSession() { return session; }

    private void scheduleDeparture() {
        scheduleDeparture(session.getPlugin().getConfigManager().getDepartureDelay());
    }

    private void scheduleDeparture(int delayTicks) {
        if (departureTask != null) departureTask.cancel();
        departureTask = Bukkit.getScheduler().runTaskLater(session.getPlugin(), () -> {
            departureTask = null;
            handleDeparture();
        }, delayTicks);
    }

    private void handleDeparture() {
        if (!session.isPassengerRiding()) { cancel(); return; }
        Minecart cart = session.getMinecart();
        if (cart == null) { cancel(); return; }

        var line = session.getLine();
        if (line == null) {
            notifyPlayer("ride.line_missing");
            cancel();
            return;
        }

        String next = line.getNextStopId(session.getCurrentStopId(), session.getDirection());
        if (next == null) {
            notifyPlayer("ride.no_next_stop");
            return;
        }
        session.setTargetStopId(next);

        cart.setMaxSpeed(line.getMaxSpeed());
        session.initDepartureVelocity();
        session.setState(TrainSession.State.MOVING_BETWEEN_STATIONS);
        session.setBrakingTarget(null);
        session.setRouteIndex(0);
        startGuidanceTasks();
        notifyDeparture();
    }

    private void startGuidanceTasks() {
        stopGuidanceTasks();
        var cfg = session.getPlugin().getConfigManager();

        if (cfg.isVanillaMovement()) {
            speedTask = Bukkit.getScheduler().runTaskTimer(session.getPlugin(), () -> {
                if (!session.isPassengerRiding()
                        || session.getState() != TrainSession.State.MOVING_BETWEEN_STATIONS) return;
                var line = session.getLine();
                Minecart cart = session.getMinecart();
                if (line == null || cart == null) return;
                cart.setMaxSpeed(line.getMaxSpeed());
            }, 5L, 10L);
            return;
        }

        if (!cfg.isPathGuidance()) return;

        int guideInterval = cfg.getGuidanceInterval();
        int stallInterval = cfg.getStallRecoveryTicks();

        guidanceTask = Bukkit.getScheduler().runTaskTimer(session.getPlugin(), () -> {
            if (!session.isPassengerRiding() || session.getState() != TrainSession.State.MOVING_BETWEEN_STATIONS) return;
            var line = session.getLine();
            if (line == null || session.getCurrentStopId() == null || session.getTargetStopId() == null) return;
            var points = session.getPlugin().getRouteManager().getRoute(
                    session.getCurrentStopId(), session.getTargetStopId());
            if (points == null || points.isEmpty()) return;
            int idx = pathGuide.findForwardRouteIndex(
                    points, session.getMinecart().getLocation(), session.getRouteIndex());
            session.setRouteIndex(idx);
            pathGuide.applyGuidance(session.getMinecart(), points, idx);
        }, guideInterval, guideInterval);

        if (cfg.isStallRecovery()) {
            stallTask = Bukkit.getScheduler().runTaskTimer(session.getPlugin(), () -> {
                if (!session.isPassengerRiding() || session.getState() != TrainSession.State.MOVING_BETWEEN_STATIONS) return;
                var line = session.getLine();
                if (line == null || session.getCurrentStopId() == null || session.getTargetStopId() == null) return;
                var points = session.getPlugin().getRouteManager().getRoute(
                        session.getCurrentStopId(), session.getTargetStopId());
                if (points == null || points.isEmpty()) return;
                pathGuide.recoverStall(session.getMinecart(), points, session.getRouteIndex());
            }, stallInterval, stallInterval);
        }
    }

    private void stopGuidanceTasks() {
        if (guidanceTask != null) { guidanceTask.cancel(); guidanceTask = null; }
        if (stallTask != null) { stallTask.cancel(); stallTask = null; }
        if (speedTask != null) { speedTask.cancel(); speedTask = null; }
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (event.getVehicle() != session.getMinecart()) return;
        Location from = event.getFrom(), to = event.getTo();
        session.addDistance(from.distance(to));

        var stopMgr = session.getPlugin().getStopManager();
        Stop entered = stopMgr.getStopAtRail(to);
        Stop target = session.getTargetStop();

        if (session.getState() == TrainSession.State.MOVING_BETWEEN_STATIONS) {
            if (entered != null && session.getTargetStopId() != null
                    && entered.getId().equals(session.getTargetStopId())) {
                session.setState(TrainSession.State.MOVING_IN_STATION);
                session.setBrakingTarget(entered);
                notifyApproaching(entered);
            }
        }

        if (session.getState() == TrainSession.State.MOVING_IN_STATION) {
            Stop brakeStop = session.getBrakingTarget() != null ? session.getBrakingTarget() : target;
            if (brakeStop != null) {
                boolean arrived = session.applyBraking(brakeStop);
                if (arrived || (session.isStopped() && isNearStop(to, brakeStop))) {
                    arriveAtStation(brakeStop);
                }
            }
        }
    }

    private boolean isNearStop(Location loc, Stop stop) {
        return stop.containsRail(loc);
    }

    private void arriveAtStation(Stop stop) {
        stopGuidanceTasks();
        session.setState(TrainSession.State.STOPPED_AT_STATION);
        session.setCurrentStopId(stop.getId());
        session.setBrakingTarget(null);
        notifyArrival(stop);

        var line = session.getLine();
        String nextId = line != null ? line.getNextStopId(stop.getId(), session.getDirection()) : null;
        if (nextId == null) {
            notifyPlayer("ride.terminus");
            return;
        }
        session.setTargetStopId(nextId);
        scheduleDeparture(stop.getDwellTimeTicks());
    }

    private void notifyPlayer(String key) {
        Player p = session.getPassenger();
        if (p != null) {
            TextUtil.send(p, session.getPlugin().getLanguageManager().get(p, key));
        }
    }

    private void notifyDeparture() {
        Player p = session.getPassenger();
        var lang = session.getPlugin().getLanguageManager();
        Stop target = session.getTargetStop();
        var line = session.getLine();
        if (p != null && target != null && line != null) {
            TextUtil.send(p, lang.get(p, "ride.departing", Map.of(
                    "line", line.getFormattedName(),
                    "stop", target.getDisplayName())));
        }
    }

    private void notifyApproaching(Stop stop) {
        Player p = session.getPassenger();
        if (p != null) {
            TextUtil.send(p, session.getPlugin().getLanguageManager().get(p, "ride.approaching",
                    Map.of("stop", stop.getDisplayName())));
            sendTransferInfo(p, stop);
        }
    }

    private void notifyArrival(Stop stop) {
        Player p = session.getPassenger();
        if (p != null) {
            TextUtil.send(p, session.getPlugin().getLanguageManager().get(p, "ride.arrived",
                    Map.of("stop", stop.getDisplayName())));
            // sendTransferInfo is already sent in notifyApproaching, so we don't repeat it here,
            // or we can send it here. The user said "顯示給該玩家", doing it once is enough.
            // Let's just keep it in approaching to give them time to read before they get off.
        }
    }

    private void sendTransferInfo(Player p, Stop stop) {
        if (stop.getKeyStations().isEmpty()) return;
        for (String kid : stop.getKeyStations()) {
            Stop ks = session.getPlugin().getStopManager().getStop(kid);
            if (ks != null) {
                com.avery.atrain.model.Line ksLine = session.getPlugin().getStopManager().resolveDisplayLine(ks, null);
                String lineName = ksLine != null ? ksLine.getDisplayName() : "未知";
                String ksNext = session.getPlugin().getStopManager().resolveDisplayNext(ks);
                String ksPrev = session.getPlugin().getStopManager().resolveDisplayPrev(ks);
                boolean hasNext = ksNext != null && !ksNext.isBlank() && !"-".equals(ksNext);
                boolean hasPrev = ksPrev != null && !ksPrev.isBlank() && !"-".equals(ksPrev);

                TextUtil.send(p, "§6本站可轉乘 §e" + lineName + " §6線");
                StringBuilder sb = new StringBuilder("§7 ➔ §b" + ks.getDisplayName() + " §f");
                if (hasNext && hasPrev) {
                    sb.append("§a(去) => ").append(ksNext).append(" §f/ §c(回) => ").append(ksPrev);
                } else if (hasNext) {
                    sb.append("§a(去) => ").append(ksNext);
                } else if (hasPrev) {
                    sb.append("§c(回) => ").append(ksPrev);
                } else {
                    sb.append("§8(終點站)");
                }
                TextUtil.send(p, sb.toString());
            }
        }
    }

    public void cancel() {
        if (departureTask != null) {
            departureTask.cancel();
            departureTask = null;
        }
        stopGuidanceTasks();
        HandlerList.unregisterAll(this);
        TrainTaskRegistry.unregister(session.getMinecart());
    }

    /** reload 或強制結束時：下車並移除列車礦車 */
    public void forceEnd() {
        Player passenger = session.getPassenger();
        Minecart cart = session.getMinecart();
        cancel();
        if (passenger != null && cart != null && passenger.getVehicle() == cart) {
            cart.removePassenger(passenger);
        }
        if (cart != null && !cart.isDead()) {
            cart.remove();
        }
    }
}
