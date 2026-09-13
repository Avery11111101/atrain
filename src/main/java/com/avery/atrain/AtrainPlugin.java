package com.avery.atrain;

import com.avery.atrain.cinematic.CinematicTransitManager;
import com.avery.atrain.command.LangCommand;
import com.avery.atrain.route.RouteRecordingManager;
import com.avery.atrain.command.TrainCommand;
import com.avery.atrain.config.ConfigManager;
import com.avery.atrain.config.DataStore;
import com.avery.atrain.gui.GuiListener;
import com.avery.atrain.gui.GuiManager;
import com.avery.atrain.hangrail.HangRailTask;
import com.avery.atrain.i18n.LanguageManager;
import com.avery.atrain.listener.ChatInputListener;
import com.avery.atrain.listener.EmptyCartListener;
import com.avery.atrain.listener.PlayerInteractListener;
import com.avery.atrain.listener.PlayerQuitListener;
import com.avery.atrain.listener.StationAutoStopListener;
import com.avery.atrain.listener.StationBlockListener;
import com.avery.atrain.listener.StationDisplayListener;
import com.avery.atrain.listener.VehicleListener;
import com.avery.atrain.manager.BindPlatformManager;
import com.avery.atrain.manager.CartSpawnManager;
import com.avery.atrain.manager.ChatInputManager;
import com.avery.atrain.manager.LineManager;
import com.avery.atrain.manager.RouteManager;
import com.avery.atrain.manager.SpeedBlockManager;
import com.avery.atrain.manager.StopManager;
import com.avery.atrain.map.BlueMapManager;
import com.avery.atrain.train.TrainController;
import com.avery.atrain.train.TrainTaskRegistry;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.minecart.RideableMinecart;

public final class AtrainPlugin extends JavaPlugin {

    private static AtrainPlugin instance;

    private ConfigManager configManager;
    private DataStore dataStore;
    private LanguageManager languageManager;
    private LineManager lineManager;
    private StopManager stopManager;
    private RouteManager routeManager;
    private CartSpawnManager cartSpawnManager;
    private SpeedBlockManager speedBlockManager;
    private ChatInputManager chatInputManager;
    private BindPlatformManager bindPlatformManager;
    private GuiManager guiManager;
    private StationAutoStopListener stationAutoStopListener;
    private PlayerInteractListener playerInteractListener;
    private VehicleListener vehicleListener;
    private TrainController trainController;
    private CinematicTransitManager cinematicTransitManager;
    private RouteRecordingManager routeRecordingManager;
    private HangRailTask hangRailTask;
    private BlueMapManager blueMapManager;
    private com.avery.atrain.service.RoutePlannerService routePlannerService;
    private com.avery.atrain.service.ActiveNavigationManager activeNavigationManager;
    private com.avery.atrain.service.ConfigMigrationService configMigrationService;
    private com.avery.atrain.update.UpdateService updateService;

    private NamespacedKey managedCartKey;

    public final Set<RideableMinecart> activeManagedCarts = new HashSet<>();
    private final List<File> pendingOldJars = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    public void onEnable() {
        instance = this;
        cleanupResidualFiles();
        managedCartKey = new NamespacedKey(this, "managed_cart");

        configMigrationService = new com.avery.atrain.service.ConfigMigrationService(this);
        configMigrationService.checkAndMigrate();

        configManager = new ConfigManager(this);
        dataStore = new DataStore(this);
        languageManager = new LanguageManager(this);
        lineManager = new LineManager(this);
        stopManager = new StopManager(this);
        routeManager = new RouteManager(this);
        cartSpawnManager = new CartSpawnManager(this);
        speedBlockManager = new SpeedBlockManager(this);
        chatInputManager = new ChatInputManager();
        bindPlatformManager = new BindPlatformManager();
        guiManager = new GuiManager(this);
        stationAutoStopListener = new StationAutoStopListener(this);
        routePlannerService = new com.avery.atrain.service.RoutePlannerService(this);
        activeNavigationManager = new com.avery.atrain.service.ActiveNavigationManager(this);
        updateService = new com.avery.atrain.update.UpdateService(this);

        configManager.load();
        languageManager.load();
        dataStore.load(); // 會在這裡面順便把舊的 line route data 轉移到 RouteManager 並 save
        routeManager.load();
        speedBlockManager.load();
        stopManager.rebuildSpatialIndex();

        var pm = getServer().getPluginManager();
        vehicleListener = new VehicleListener(this);
        playerInteractListener = new PlayerInteractListener(this);
        pm.registerEvents(vehicleListener, this);
        pm.registerEvents(playerInteractListener, this);
        pm.registerEvents(stationAutoStopListener, this);
        pm.registerEvents(new ChatInputListener(this), this);
        pm.registerEvents(new PlayerQuitListener(this), this);
        pm.registerEvents(new StationDisplayListener(this), this);
        pm.registerEvents(new EmptyCartListener(this), this);
        pm.registerEvents(new StationBlockListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new com.avery.atrain.listener.PlayerJoinListener(this), this);

        if (configManager.isAutoCheckUpdate()) {
            updateService.fetchReleasesAsync(false).thenAccept(catalog -> {
                if (catalog == null) return;
                String current = getPluginMeta().getVersion();
                boolean hasOfficial = catalog.latestOfficial() != null && com.avery.atrain.update.UpdateService.isNewerVersion(current, catalog.latestOfficial().tagName());
                boolean hasBeta = catalog.latestBeta() != null && com.avery.atrain.update.UpdateService.isNewerVersion(current, catalog.latestBeta().tagName());

                if (hasOfficial) {
                    getLogger().info("🌟 發現新正式穩定版: " + catalog.latestOfficial().tagName() + " (" + catalog.latestOfficial().name() + ")");
                }
                if (hasBeta) {
                    getLogger().info("🧪 發現新搶先測試版: " + catalog.latestBeta().tagName() + " (" + catalog.latestBeta().name() + ")");
                }
            });
        }



        var trainCmd = new TrainCommand(this);
        getCommand("train").setExecutor(trainCmd);
        getCommand("train").setTabCompleter(trainCmd);
        var langCmd = new LangCommand(this);
        getCommand("lang").setExecutor(langCmd);
        getCommand("lang").setTabCompleter(langCmd);

        trainController = new TrainController(this);
        if (configManager.isTrainControlEnabled()) {
            trainController.start();
        }

        cinematicTransitManager = new CinematicTransitManager(this);
        if (configManager.isCinematicTransitEnabled()) {
            cinematicTransitManager.start();
        }

        routeRecordingManager = new RouteRecordingManager(this);
        routeRecordingManager.start();

        hangRailTask = new HangRailTask(this, vehicleListener.getHangRailHandler());
        hangRailTask.start();

        getLogger().info("atrain v" + getPluginMeta().getVersion()
                + " 已啟用 — 站點導引:" + configManager.isCinematicTransitEnabled()
                + " 列車控速:" + configManager.isTrainControlEnabled());

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (RideableMinecart cart : world.getEntitiesByClass(RideableMinecart.class)) {
                if (isManagedCart(cart)) activeManagedCarts.add(cart);
            }
        }
        
        blueMapManager = new BlueMapManager(this);
    }

    @Override
    public void onDisable() {
        if (trainController != null) {
            trainController.stop();
            trainController.clearAll();
        }
        if (cinematicTransitManager != null) {
            cinematicTransitManager.stop();
            cinematicTransitManager.clearAll();
        }
        if (routeRecordingManager != null) {
            routeRecordingManager.clearAll();
        }
        if (hangRailTask != null) hangRailTask.stop();
        if (vehicleListener != null) vehicleListener.getHangRailHandler().restoreAll();
        stationAutoStopListener.clearAll();
        TrainTaskRegistry.cancelAllTasks();
        dataStore.save();
        if (speedBlockManager != null) speedBlockManager.save();
        if (blueMapManager != null) blueMapManager.disable();

        // 嘗試清理已標記待刪除的舊版本 Jar（適用於 PlugMan 卸載重載情境）
        for (File oldJar : pendingOldJars) {
            try {
                if (oldJar != null && oldJar.exists() && oldJar.delete()) {
                    getLogger().info("已成功清理舊版本檔案: " + oldJar.getName());
                }
            } catch (Throwable ignored) {}
        }

        getLogger().info("atrain 已停用");
    }

    private void cleanupResidualFiles() {
        try {
            File pluginsFolder = getDataFolder().getParentFile();
            if (pluginsFolder != null && pluginsFolder.exists()) {
                File[] leftovers = pluginsFolder.listFiles((dir, name) ->
                        (name.startsWith(".atrain_") && (name.endsWith(".part") || name.endsWith(".downloading")))
                                || (name.startsWith("atrain-") && name.endsWith(".old"))
                                || name.endsWith(".jar.old")
                );
                if (leftovers != null) {
                    for (File f : leftovers) {
                        try {
                            if (f.delete()) {
                                getLogger().info("已清理暫存或過期備份檔: " + f.getName());
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public void registerPendingOldJar(File jarFile) {
        if (jarFile != null && !pendingOldJars.contains(jarFile)) {
            pendingOldJars.add(jarFile);
        }
    }

    public void reloadAll() {
        if (trainController != null) trainController.clearAll();
        if (cinematicTransitManager != null) cinematicTransitManager.clearAll();
        if (routeRecordingManager != null) {
            routeRecordingManager.stopAllForReload();
        }
        if (routeRecordingManager != null) routeRecordingManager.start();
        if (vehicleListener != null) vehicleListener.getHangRailHandler().restoreAll();
        stationAutoStopListener.clearAll();
        TrainTaskRegistry.cancelAllTasks();
        chatInputManager.clearAll();
        bindPlatformManager.clearAll();
        dataStore.save();
        configManager.load();
        languageManager.load();
        dataStore.load();
        speedBlockManager.load();
        stopManager.rebuildSpatialIndex();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("atrain.admin")) {
                TextUtil.send(player, languageManager.get(player, "plugin.reload"));
            }
        }
        if (blueMapManager != null) blueMapManager.updateMap();
        Bukkit.getConsoleSender().sendMessage(TextUtil.colorize(languageManager.getRaw(languageManager.getDefaultLanguage(), "plugin.reload")));
    }

    public static AtrainPlugin getInstance() { return instance; }

    public ConfigManager getConfigManager() { return configManager; }
    public DataStore getDataStore() { return dataStore; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public LineManager getLineManager() { return lineManager; }
    public StopManager getStopManager() { return stopManager; }
    public RouteManager getRouteManager() { return routeManager; }
    public CartSpawnManager getCartSpawnManager() { return cartSpawnManager; }
    public SpeedBlockManager getSpeedBlockManager() { return speedBlockManager; }
    public ChatInputManager getChatInputManager() { return chatInputManager; }
    public BindPlatformManager getBindPlatformManager() { return bindPlatformManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public StationAutoStopListener getStationAutoStopListener() { return stationAutoStopListener; }
    public TrainController getTrainController() { return trainController; }
    public CinematicTransitManager getCinematicTransitManager() { return cinematicTransitManager; }
    public RouteRecordingManager getRouteRecordingManager() { return routeRecordingManager; }
    public BlueMapManager getBlueMapManager() { return blueMapManager; }
    public com.avery.atrain.service.RoutePlannerService getRoutePlannerService() { return routePlannerService; }
    public com.avery.atrain.service.ActiveNavigationManager getActiveNavigationManager() { return activeNavigationManager; }
    public com.avery.atrain.service.ConfigMigrationService getConfigMigrationService() { return configMigrationService; }
    public com.avery.atrain.update.UpdateService getUpdateService() { return updateService; }

    public java.io.File getPluginFile() { return getFile(); }

    public NamespacedKey getManagedCartKey() { return managedCartKey; }



    public void markAsManagedCart(Minecart cart) {
        if (cart == null) return;
        cart.getPersistentDataContainer().set(managedCartKey, PersistentDataType.BYTE, (byte) 1);
        if (cart instanceof RideableMinecart rc) {
            activeManagedCarts.add(rc);
        }
    }

    public boolean isManagedCart(Minecart cart) {
        if (cart == null) return false;
        return cart.getPersistentDataContainer().has(managedCartKey, PersistentDataType.BYTE);
    }
}
