package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Stop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 礦車經過金磚站點時自動停車，與路線系統無關 */
public class StationAutoStopListener implements Listener {

    private final AtrainPlugin plugin;
    private final Map<UUID, DwellState> dwelling = new ConcurrentHashMap<>();
    /** 發車後須先離開該站才會再次觸發 */
    private final Map<UUID, String> mustLeaveStop = new ConcurrentHashMap<>();
    /** 本次進站是否已觸發（含生點在站內的情況） */
    private final Map<UUID, String> visitStop = new ConcurrentHashMap<>();

    public StationAutoStopListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreate(VehicleCreateEvent event) {
        if (!(event.getVehicle() instanceof RideableMinecart cart)) return;
        if (!plugin.getConfigManager().isAutoStopEnabled()) return;
        Stop on = plugin.getStopManager().getStopAtRail(cart.getLocation());
        if (on == null) return;
        UUID cartId = cart.getUniqueId();
        if (mustLeaveStop.containsKey(cartId) || visitStop.containsKey(cartId)) return;
        visitStop.put(cartId, on.getId());
        Bukkit.getScheduler().runTask(plugin, () -> beginDwell(cart, on));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof RideableMinecart)) return;
        UUID cartId = event.getVehicle().getUniqueId();
        cancelDwell(cartId, false);
        mustLeaveStop.remove(cartId);
        visitStop.remove(cartId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof RideableMinecart cart)) return;
        if (!plugin.getConfigManager().isAutoStopEnabled()) return;

        Location to = cart.getLocation();
        UUID cartId = cart.getUniqueId();

        DwellState state = dwelling.get(cartId);
        if (state != null && state.active) {
            if (state.stop != null && !state.stop.containsRail(to)) {
                releaseDwell(cartId, true);
            } else {
                cart.setVelocity(new Vector(0, 0, 0));
                cart.setMaxSpeed(0);
            }
            return;
        }

        Stop on = plugin.getStopManager().getStopAtRail(to);
        if (on == null) {
            visitStop.remove(cartId);
            mustLeaveStop.remove(cartId);
            return;
        }

        String leaveId = mustLeaveStop.get(cartId);
        if (leaveId != null && leaveId.equals(on.getId())) {
            return;
        }

        String visited = visitStop.get(cartId);
        if (on.getId().equals(visited)) {
            return;
        }

        visitStop.put(cartId, on.getId());
        beginDwell(cart, on);
    }

    private void beginDwell(Minecart cart, Stop stop) {
        UUID cartId = cart.getUniqueId();
        cancelDwell(cartId, false);

        float savedSpeed = (float) cart.getMaxSpeed();
        if (savedSpeed < 0.01f) {
            savedSpeed = (float) plugin.getConfigManager().getCartSpeed();
        }

        int dwell = stop.getDwellTimeTicks();
        if (dwell <= 0) {
            mustLeaveStop.put(cartId, stop.getId());
            return;
        }

        Vector approach = cart.getVelocity().clone();
        if (approach.lengthSquared() < 0.0001) {
            var dirs = com.avery.atrain.util.RailUtil.getRailDirections(cart.getLocation());
            if (!dirs.isEmpty()) approach = dirs.get(0).clone();
        } else {
            approach.normalize();
        }

        DwellState state = new DwellState(stop.getId(), savedSpeed, stop, approach);
        dwelling.put(cartId, state);

        cart.setVelocity(new Vector(0, 0, 0));
        cart.setMaxSpeed(0);
        state.active = true;

        state.releaseTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> releaseDwell(cartId, true), dwell);
    }

    private void releaseDwell(UUID cartId, boolean restoreSpeed) {
        DwellState state = dwelling.remove(cartId);
        if (state == null) return;
        if (state.releaseTask != null) {
            state.releaseTask.cancel();
            state.releaseTask = null;
        }
        state.active = false;

        if (!restoreSpeed) return;

        Minecart cart = findCart(cartId);
        if (cart != null && cart.isValid() && !cart.isDead()) {
            cart.setMaxSpeed(state.savedMaxSpeed);
            if (state.departDir != null && state.departDir.lengthSquared() > 0.0001) {
                double push = Math.max(state.savedMaxSpeed, 0.12);
                cart.setVelocity(state.departDir.clone().multiply(push));
            }
            mustLeaveStop.put(cartId, state.stopId);
        }
    }

    private void cancelDwell(UUID cartId, boolean restoreSpeed) {
        releaseDwell(cartId, restoreSpeed);
    }

    private Minecart findCart(UUID cartId) {
        var entity = Bukkit.getEntity(cartId);
        return entity instanceof Minecart m ? m : null;
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(dwelling.keySet())) {
            releaseDwell(id, true);
        }
        dwelling.clear();
        mustLeaveStop.clear();
        visitStop.clear();
    }

    private static final class DwellState {
        final String stopId;
        final float savedMaxSpeed;
        final Stop stop;
        boolean active;
        BukkitTask releaseTask;
        final Vector departDir;

        DwellState(String stopId, float savedMaxSpeed, Stop stop, Vector departDir) {
            this.stopId = stopId;
            this.savedMaxSpeed = savedMaxSpeed;
            this.stop = stop;
            this.departDir = departDir != null ? departDir.clone() : new Vector();
        }
    }
}
