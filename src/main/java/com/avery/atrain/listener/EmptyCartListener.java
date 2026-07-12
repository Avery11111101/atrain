package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.train.TrainTaskRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

/** 站點空礦車延遲刪除 */
public class EmptyCartListener implements Listener {

    private final AtrainPlugin plugin;

    public EmptyCartListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart)) return;
        plugin.getCartSpawnManager().cancelDespawn(cart.getUniqueId());
    }

    @EventHandler
    public void onExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof RideableMinecart cart)) return;
        if (TrainTaskRegistry.get(cart) != null) return;
        // 列車控速啟用時，站內礦車視為列車車廂，不自動刪除以免拆散列車
        if (plugin.getConfigManager().isTrainControlEnabled()) return;
        if (!plugin.getCartSpawnManager().isManagedStationCart(cart)) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!cart.isValid() || cart.isDead()) return;
            if (!cart.getPassengers().isEmpty()) return;
            plugin.getCartSpawnManager().scheduleEmptyDespawn(cart);
        });
    }
}
