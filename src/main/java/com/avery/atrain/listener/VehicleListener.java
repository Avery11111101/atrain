package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.hangrail.HangRailHandler;
import com.avery.atrain.train.TrainMovementTask;
import com.avery.atrain.train.TrainTaskRegistry;
import com.avery.atrain.util.RailUtil;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VehicleListener implements Listener {

    private static final int DERAIL_GRACE_TICKS = 4;

    private final AtrainPlugin plugin;
    private final HangRailHandler hangRailHandler;
    private final Map<UUID, Integer> offTrackTicks = new ConcurrentHashMap<>();
    private final Set<UUID> derailing = ConcurrentHashMap.newKeySet();

    public VehicleListener(AtrainPlugin plugin) {
        this.plugin = plugin;
        this.hangRailHandler = new HangRailHandler(plugin);
    }

    private boolean isTrainCart(Minecart cart) {
        return cart.getPersistentDataContainer().has(plugin.getMinecartKey(), PersistentDataType.BYTE);
    }

    private void cancelTask(Minecart cart) {
        TrainMovementTask task = TrainTaskRegistry.get(cart);
        if (task != null) task.cancel();
        else TrainTaskRegistry.unregister(cart);
        offTrackTicks.remove(cart.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart)) return;
        if (!(event.getExited() instanceof Player)) return;
        if (!isTrainCart(cart)) return;

        cancelTask(cart);
        cart.setGravity(true);

        int delay = plugin.getConfigManager().getDespawnDelay();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (cart.isValid() && !cart.isDead()) {
                cart.eject();
                cart.remove();
            }
        }, delay);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart)) return;
        if (!isTrainCart(cart)) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        boolean onHang = hangRailHandler.handleMove(cart, from, to);
        if (onHang) return;

        boolean onRail = RailUtil.isOnRail(to);
        boolean onTrack = onRail || onHang;

        if (plugin.getConfigManager().isVanillaMovement() && onRail) {
            offTrackTicks.remove(cart.getUniqueId());
            return;
        }

        if (cart.getMaxSpeed() < 0.01) {
            if (!onHang) {
                cart.setVelocity(new Vector(0, 0, 0));
            }
            return;
        }

        if (!onTrack) {
            int ticks = offTrackTicks.merge(cart.getUniqueId(), 1, Integer::sum);
            if (ticks >= DERAIL_GRACE_TICKS) {
                handleDerail(cart);
            }
            return;
        }

        offTrackTicks.remove(cart.getUniqueId());
    }

    private void handleDerail(Minecart cart) {
        if (!derailing.add(cart.getUniqueId())) return;

        Player passenger = cart.getPassengers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .findFirst().orElse(null);

        cancelTask(cart);
        cart.setGravity(true);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (passenger != null) {
                TextUtil.send(passenger, plugin.getLanguageManager().get(passenger, "ride.derailed"));
            }
            if (cart.isValid() && !cart.isDead()) {
                cart.eject();
                cart.remove();
            }
            derailing.remove(cart.getUniqueId());
        });
    }

    public HangRailHandler getHangRailHandler() {
        return hangRailHandler;
    }
}
