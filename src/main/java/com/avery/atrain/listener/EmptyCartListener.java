package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.train.TrainTaskRegistry;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

/** 玩家下車後移除礦車；上車時觸發導引／列車控速 */
public class EmptyCartListener implements Listener {

    private final AtrainPlugin plugin;

    public EmptyCartListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof RideableMinecart rideable)) return;
        plugin.getCartSpawnManager().cancelDespawn(rideable.getUniqueId());

        if (!(event.getEntered() instanceof Player player)) return;

        if (plugin.getRouteRecordingManager() != null
                && plugin.getRouteRecordingManager().isRecordingCart(rideable.getUniqueId())) {
            return;
        }
        if (plugin.getConfigManager().isCinematicTransitEnabled()
                && plugin.getCinematicTransitManager() != null
                && plugin.getCinematicTransitManager().onBoard(rideable, player)) {
            return;
        }
        if (plugin.getConfigManager().isTrainControlEnabled()
                && plugin.getTrainController() != null) {
            plugin.getTrainController().onBoard(rideable, player);
        }
    }

    @EventHandler
    public void onExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof RideableMinecart cart)) return;
        if (!(event.getExited() instanceof Player)) return;

        if (plugin.getCinematicTransitManager() != null
                && plugin.getCinematicTransitManager().isManaged(cart)) {
            plugin.getCinematicTransitManager().cancelCart(cart.getUniqueId());
        }
        TrainTaskRegistry.cancel(cart);

        plugin.getCartSpawnManager().despawnWhenEmpty(cart);
    }
}
