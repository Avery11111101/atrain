package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.hangrail.HangRailHandler;
import com.avery.atrain.util.RailUtil;
import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.avery.atrain.train.TrainMovementTask;
import com.avery.atrain.train.TrainTaskRegistry;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

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
        TrainMovementTask task = TrainTaskRegistry.get(cart);
        if (task != null) task.cancel();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart)) return;
        if (!plugin.getConfigManager().isHangRailEnabled()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        boolean onHang = hangRailHandler.handleMove(cart, from, to);
        if (!onHang && RailUtil.isOnRail(to)) {
            // 一般鐵軌由原版物理處理
        }
    }

    public HangRailHandler getHangRailHandler() {
        return hangRailHandler;
    }
}
