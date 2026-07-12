package com.avery.atrain.train;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.model.TravelDirection;
import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class TrainSession {
    public enum State {
        STOPPED_AT_STATION,
        MOVING_IN_STATION,
        MOVING_BETWEEN_STATIONS
    }

    private final AtrainPlugin plugin;
    private Minecart minecart;
    private final Player passenger;
    private final String lineId;
    private final TravelDirection direction;
    private String currentStopId;
    private String targetStopId;
    private State state;
    private int routeIndex;
    private double distanceTraveled;
    private Stop brakingTarget;

    public TrainSession(AtrainPlugin plugin, Minecart minecart, Player passenger,
                        String lineId, String fromStopId, TravelDirection direction, State state) {
        this.plugin = plugin;
        this.minecart = minecart;
        this.passenger = passenger;
        this.lineId = lineId;
        this.direction = direction != null ? direction : TravelDirection.FORWARD;
        this.currentStopId = fromStopId;
        Line line = plugin.getLineManager().getLine(lineId);
        this.targetStopId = line != null ? line.getNextStopId(fromStopId, this.direction) : null;
        this.state = state;
        this.routeIndex = 0;
    }

    public AtrainPlugin getPlugin() { return plugin; }
    public Minecart getMinecart() { return minecart; }
    public void setMinecart(Minecart minecart) { this.minecart = minecart; }
    public Player getPassenger() { return passenger; }
    public String getLineId() { return lineId; }
    public TravelDirection getDirection() { return direction; }

    public Line getLine() {
        return lineId != null ? plugin.getLineManager().getLine(lineId) : null;
    }

    public String getCurrentStopId() { return currentStopId; }
    public void setCurrentStopId(String currentStopId) { this.currentStopId = currentStopId; }
    public String getTargetStopId() { return targetStopId; }
    public void setTargetStopId(String targetStopId) { this.targetStopId = targetStopId; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public int getRouteIndex() { return routeIndex; }
    public void setRouteIndex(int routeIndex) { this.routeIndex = routeIndex; }
    public double getDistanceTraveled() { return distanceTraveled; }
    public void addDistance(double d) { this.distanceTraveled += d; }
    public Stop getBrakingTarget() { return brakingTarget; }
    public void setBrakingTarget(Stop brakingTarget) { this.brakingTarget = brakingTarget; }

    public boolean isPassengerRiding() {
        return passenger != null && passenger.isOnline()
                && minecart != null && !minecart.isDead()
                && minecart.getPassengers().contains(passenger);
    }

    public Stop getCurrentStop() {
        return currentStopId != null ? plugin.getStopManager().getStop(currentStopId) : null;
    }

    public Stop getTargetStop() {
        return targetStopId != null ? plugin.getStopManager().getStop(targetStopId) : null;
    }

    public void initDepartureVelocity() {
        if (minecart == null) return;
        Line line = getLine();
        double speed = line != null ? line.getMaxSpeed() : plugin.getConfigManager().getCartSpeed();
        minecart.setMaxSpeed(speed);
    }

    public boolean applyBraking(Stop stop) {
        if (minecart == null || stop == null) return false;
        Location cartLoc = minecart.getLocation();
        Location stopLoc = stop.getPrimaryRailLocation();
        if (stopLoc == null) return false;

        double dist = cartLoc.distance(stopLoc);
        if (dist < 1.2) {
            minecart.setVelocity(new Vector(0, 0, 0));
            minecart.setMaxSpeed(0);
            return true;
        }
        double ratio = Math.min(1.0, dist / 12.0);
        double base = getLine() != null ? getLine().getMaxSpeed() : plugin.getConfigManager().getCartSpeed();
        double targetSpeed = 0.08 + (base - 0.08) * Math.pow(ratio, 0.7);
        minecart.setMaxSpeed(Math.min(minecart.getMaxSpeed(), targetSpeed));
        return minecart.getVelocity().length() < 0.05 && dist < 2.0;
    }

    public boolean isStopped() {
        if (minecart == null) return false;
        return minecart.getMaxSpeed() < 0.01
                || minecart.getVelocity().length() < 0.05;
    }
}
