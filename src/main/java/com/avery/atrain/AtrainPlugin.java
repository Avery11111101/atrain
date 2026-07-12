package com.avery.atrain;

import com.avery.atrain.command.LangCommand;
import com.avery.atrain.command.TrainCommand;
import com.avery.atrain.config.ConfigManager;
import com.avery.atrain.config.DataStore;
import com.avery.atrain.gui.GuiListener;
import com.avery.atrain.gui.GuiManager;
import com.avery.atrain.i18n.LanguageManager;
import com.avery.atrain.listener.PlayerInteractListener;
import com.avery.atrain.listener.PlayerQuitListener;
import com.avery.atrain.listener.VehicleListener;
import com.avery.atrain.manager.LineManager;
import com.avery.atrain.manager.RouteRecorder;
import com.avery.atrain.manager.SelectionManager;
import com.avery.atrain.manager.StopManager;
import com.avery.atrain.train.TrainTaskRegistry;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AtrainPlugin extends JavaPlugin {

    private static AtrainPlugin instance;
    private NamespacedKey minecartKey;
    private NamespacedKey lineKey;
    private NamespacedKey stopKey;

    private ConfigManager configManager;
    private DataStore dataStore;
    private LanguageManager languageManager;
    private LineManager lineManager;
    private StopManager stopManager;
    private SelectionManager selectionManager;
    private RouteRecorder routeRecorder;
    private GuiManager guiManager;
    private TrainTaskRegistry trainTaskRegistry;
    private PlayerInteractListener playerInteractListener;
    private VehicleListener vehicleListener;

    @Override
    public void onEnable() {
        instance = this;
        minecartKey = new NamespacedKey(this, "train_minecart");
        lineKey = new NamespacedKey(this, "line_id");
        stopKey = new NamespacedKey(this, "stop_id");

        configManager = new ConfigManager(this);
        dataStore = new DataStore(this);
        languageManager = new LanguageManager(this);
        lineManager = new LineManager(this);
        stopManager = new StopManager(this);
        selectionManager = new SelectionManager();
        routeRecorder = new RouteRecorder(this);
        trainTaskRegistry = new TrainTaskRegistry();
        guiManager = new GuiManager(this);

        configManager.load();
        languageManager.load();
        dataStore.load();

        var pm = getServer().getPluginManager();
        vehicleListener = new VehicleListener(this);
        playerInteractListener = new PlayerInteractListener(this);
        pm.registerEvents(vehicleListener, this);
        pm.registerEvents(playerInteractListener, this);
        pm.registerEvents(new PlayerQuitListener(this), this);
        pm.registerEvents(new GuiListener(this, playerInteractListener), this);

        var trainCmd = new TrainCommand(this);
        getCommand("train").setExecutor(trainCmd);
        getCommand("train").setTabCompleter(trainCmd);
        var langCmd = new LangCommand(this);
        getCommand("lang").setExecutor(langCmd);
        getCommand("lang").setTabCompleter(langCmd);

        getLogger().info("atrain 已啟用 (Paper 1.21+)");
    }

    @Override
    public void onDisable() {
        trainTaskRegistry.cancelAll();
        dataStore.save();
        getLogger().info("atrain 已停用");
    }

    public void reloadAll() {
        trainTaskRegistry.shutdownAll();
        playerInteractListener.clearAllPendingSpawn();
        routeRecorder.stopAllRecording(true);
        dataStore.save();
        configManager.load();
        languageManager.load();
        dataStore.load();
        for (Player player : Bukkit.getOnlinePlayers()) {
            TextUtil.send(player, languageManager.get(player, "plugin.reload"));
        }
    }

    public static AtrainPlugin getInstance() { return instance; }

    public NamespacedKey getMinecartKey() { return minecartKey; }
    public NamespacedKey getLineKey() { return lineKey; }
    public NamespacedKey getStopKey() { return stopKey; }
    public ConfigManager getConfigManager() { return configManager; }
    public DataStore getDataStore() { return dataStore; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public LineManager getLineManager() { return lineManager; }
    public StopManager getStopManager() { return stopManager; }
    public SelectionManager getSelectionManager() { return selectionManager; }
    public RouteRecorder getRouteRecorder() { return routeRecorder; }
    public GuiManager getGuiManager() { return guiManager; }
    public TrainTaskRegistry getTrainTaskRegistry() { return trainTaskRegistry; }
    public PlayerInteractListener getPlayerInteractListener() { return playerInteractListener; }
}
