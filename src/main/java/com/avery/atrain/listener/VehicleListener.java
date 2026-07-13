package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.hangrail.HangRailHandler;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.avery.atrain.train.TrainMovementTask;
import com.avery.atrain.train.TrainTaskRegistry;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

public class VehicleListener implements Listener {

    private final AtrainPlugin plugin;
    private final HangRailHandler hangRailHandler;

    public VehicleListener(AtrainPlugin plugin) {
        this.plugin = plugin;
        this.hangRailHandler = new HangRailHandler(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart)) return;
        if (!plugin.isManagedCart(cart)) return;
        TrainMovementTask task = TrainTaskRegistry.get(cart);
        if (task != null) task.cancel();
        if (plugin.getCinematicTransitManager() != null) {
            plugin.getCinematicTransitManager().cancelCart(cart.getUniqueId());
        }
    }

    // 懸浮軌道改由 HangRailTask 每 tick 主動吊住（見 HangRailTask），
    // 不再依賴 VehicleMoveEvent（事件僅在移動時觸發，靜止/掉落中的礦車會抓不到）。

    public HangRailHandler getHangRailHandler() {
        return hangRailHandler;
    }
}
