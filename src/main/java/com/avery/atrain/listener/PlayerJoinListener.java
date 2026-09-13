package com.avery.atrain.listener;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.update.ReleaseInfo;
import com.avery.atrain.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final AtrainPlugin plugin;

    public PlayerJoinListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("atrain.admin")) return;
        if (!plugin.getConfigManager().isAutoCheckUpdate()) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            String currentVer = plugin.getPluginMeta().getVersion();
            var updateService = plugin.getUpdateService();
            ReleaseInfo latest = updateService.getLatestRelease();
            if (latest == null) latest = updateService.getLatestBeta();

            if (latest != null && updateService.isNewerVersion(currentVer, latest.tagName())) {
                TextUtil.send(player, "<gold>[atrain] ⚠️ 檢測到新版本 <yellow>" + latest.tagName() + " <gold>可供更新！輸入 <gold>/train update <gold>進行查看與下載。");
            }
        }, 40L);
    }
}
