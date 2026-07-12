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
    }
}
