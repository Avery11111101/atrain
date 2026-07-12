package com.avery.atrain.config;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.hangrail.HangRailType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private final AtrainPlugin plugin;
    private FileConfiguration config;
    private List<HangRailType> hangRailTypes = new ArrayList<>();

    public ConfigManager(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadHangRailTypes();
    }

    private void loadHangRailTypes() {
        hangRailTypes = new ArrayList<>();
        List<?> raw = config.getList("hang_rail.types");
        if (raw == null || raw.isEmpty()) {
            hangRailTypes.add(HangRailType.ironBarsDefault());
            return;
        }
        for (Object item : raw) {
            if (item instanceof ConfigurationSection sec) {
                parseHangType(sec);
            } else if (item instanceof java.util.Map<?, ?> map) {
                String block = String.valueOf(map.get("block"));
                int offset = map.containsKey("offset") ? ((Number) map.get("offset")).intValue() : -2;
                Material mat = parseMaterial(block);
                if (mat != null) hangRailTypes.add(new HangRailType(mat, offset));
            }
        }
        if (hangRailTypes.isEmpty()) {
            hangRailTypes.add(HangRailType.ironBarsDefault());
        }
    }

    private void parseHangType(ConfigurationSection sec) {
        Material mat = parseMaterial(sec.getString("block", "IRON_BARS"));
        int offset = sec.getInt("offset", -2);
        if (mat != null) hangRailTypes.add(new HangRailType(mat, offset));
    }

    private Material parseMaterial(String name) {
        if (name == null) return null;
        String normalized = name.toUpperCase()
                .replace("IRON_FENCE", "IRON_BARS");
        if (normalized.contains(":")) {
            normalized = normalized.split(":")[0];
        }
        Material mat = Material.matchMaterial(normalized);
        return mat;
    }

    public double getCartSpeed() { return config.getDouble("settings.cart_speed", 0.35); }
    public int getSpawnDelay() { return config.getInt("settings.cart_spawn_delay", 40); }
    public int getDepartureDelay() { return config.getInt("settings.cart_departure_delay", 80); }
    public int getDefaultDwellTime() { return config.getInt("settings.default_dwell_time", 80); }
    public int getDespawnDelay() { return config.getInt("settings.cart_despawn_delay", 20); }

    public boolean isPathGuidance() { return config.getBoolean("movement.path_guidance", false); }
    public boolean isVanillaMovement() { return !isPathGuidance(); }
    public double getGuidanceStrength() { return config.getDouble("movement.guidance_strength", 0.65); }
    public int getGuidanceInterval() { return config.getInt("movement.guidance_interval_ticks", 2); }
    public boolean isStallRecovery() { return config.getBoolean("movement.stall_recovery", false); }
    public int getStallRecoveryTicks() { return config.getInt("movement.stall_recovery_ticks", 10); }
    public double getMinCruiseSpeed() { return config.getDouble("movement.min_cruise_speed", 0.06); }
    public boolean isObstructionCheck() { return config.getBoolean("movement.obstruction_check", true); }
    public double getObstructionDistance() { return config.getDouble("movement.obstruction_distance", 1.2); }

    public boolean isTitleEnabled() { return config.getBoolean("display.title_enabled", true); }
    public boolean isActionbarEnabled() { return config.getBoolean("display.actionbar_enabled", true); }
    public boolean isScoreboardEnabled() { return config.getBoolean("display.scoreboard_enabled", true); }

    public double getSampleDistance() { return config.getDouble("route_recording.sample_distance", 2.0); }
    public String getDefaultLanguage() { return config.getString("default_language", "zh_TW"); }

    public boolean isHangRailEnabled() { return config.getBoolean("hang_rail.enabled", true); }
    public List<HangRailType> getHangRailTypes() { return hangRailTypes; }

    public void setHangRailEnabled(boolean enabled) {
        config.set("hang_rail.enabled", enabled);
        plugin.saveConfig();
    }
}
