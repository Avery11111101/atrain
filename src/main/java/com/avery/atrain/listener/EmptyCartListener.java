package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.train.TrainTaskRegistry;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 玩家下車後移除礦車；上車時觸發導引／列車控速 */
public class EmptyCartListener implements Listener {

    private final AtrainPlugin plugin;
    private final Map<UUID, PermissionAttachment> grimExemptions = new HashMap<>();

    public EmptyCartListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof RideableMinecart rideable)) return;
        if (!plugin.isManagedCart(rideable)) return;
        plugin.getCartSpawnManager().cancelDespawn(rideable.getUniqueId());

        if (!(event.getEntered() instanceof Player player)) return;

        // 避免重複賦予
        if (grimExemptions.containsKey(player.getUniqueId())) return;

        // 給予 Grim Anticheat 豁免權限 (避免在礦車上被誤判)
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission("grim.disabled", true);
        grimExemptions.put(player.getUniqueId(), attachment);
        player.recalculatePermissions();
        updateGrimPermissions(player);

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
        if (!plugin.isManagedCart(cart)) return;
        if (!(event.getExited() instanceof Player player)) return;

        // 移除 Grim Anticheat 豁免權限
        PermissionAttachment attachment = grimExemptions.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
                player.recalculatePermissions();
                updateGrimPermissions(player);
            } catch (IllegalArgumentException ignored) {}
        }

        if (plugin.getRouteRecordingManager() != null
                && plugin.getRouteRecordingManager().isRecordingCart(cart.getUniqueId())) {
            return;
        }

        if (plugin.getCinematicTransitManager() != null
                && plugin.getCinematicTransitManager().isManaged(cart)) {
            plugin.getCinematicTransitManager().cancelCart(cart.getUniqueId());
        }
        TrainTaskRegistry.cancel(cart);

        plugin.getCartSpawnManager().despawnWhenEmpty(cart);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PermissionAttachment attachment = grimExemptions.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
                // 玩家退出遊戲時不一定需要 recalculate，但安全起見
                player.recalculatePermissions();
                updateGrimPermissions(player);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void updateGrimPermissions(Player player) {
        if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("GrimAC")) return;
        try {
            ac.grim.grimac.api.GrimUser grimUser = ac.grim.grimac.api.GrimAPIProvider.get().getGrimUser(player.getUniqueId());
            if (grimUser != null) {
                grimUser.updatePermissions();
            }
        } catch (Throwable t) {
            // Ignore
        }
    }
}
