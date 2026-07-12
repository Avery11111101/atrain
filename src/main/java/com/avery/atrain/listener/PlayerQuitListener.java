package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final AtrainPlugin plugin;

    public PlayerQuitListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getChatInputManager().clear(event.getPlayer());
        plugin.getBindPlatformManager().clear(event.getPlayer());
        plugin.getCartSpawnManager().clearCooldown(event.getPlayer());
        if (event.getPlayer().getVehicle() instanceof org.bukkit.entity.minecart.RideableMinecart cart
                && plugin.getCinematicTransitManager() != null) {
            plugin.getCinematicTransitManager().cancelCart(cart.getUniqueId());
        }
        if (plugin.getRouteRecordingManager() != null
                && plugin.getRouteRecordingManager().isRecording(event.getPlayer())) {
            plugin.getRouteRecordingManager().stop(event.getPlayer());
        }
    }
}
