package com.avery.atrain.config;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DataStore {
    private final AtrainPlugin plugin;
    private final Map<String, Line> lines = new LinkedHashMap<>();
    private final Map<String, Stop> stops = new LinkedHashMap<>();

    public DataStore(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        lines.clear();
        stops.clear();
        loadLines();
        loadStops();
    }

    private void loadLines() {
        File file = new File(plugin.getDataFolder(), "lines.yml");
        if (!file.exists()) plugin.saveResource("lines.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yaml.getConfigurationSection("lines");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection ls = sec.getConfigurationSection(id);
            if (ls == null) continue;
            Line line = new Line(id, ls.getString("display_name", id));
            line.setColor(ls.getString("color", "§a"));
            line.setMaxSpeed(ls.getDouble("max_speed", 0.35));
            line.setCircular(ls.getBoolean("circular", false));
            line.setStopIds(new ArrayList<>(ls.getStringList("stops")));
            lines.put(id, line);
        }
    }

    private void loadStops() {
        File file = new File(plugin.getDataFolder(), "stops.yml");
        if (!file.exists()) {
            YamlConfiguration empty = new YamlConfiguration();
            empty.createSection("stops");
            try { empty.save(file); } catch (IOException ignored) {}
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yaml.getConfigurationSection("stops");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection ss = sec.getConfigurationSection(id);
            if (ss == null) continue;
            Stop stop = new Stop();
            stop.setId(id);
            stop.setDisplayName(ss.getString("display_name", id));
            stop.setWorld(ss.getString("world", "world"));
            stop.setLineIds(new ArrayList<>(ss.getStringList("lines")));
            stop.setDisplayLineId(ss.getString("display_line", null));
            stop.setReturnLineId(ss.getString("return_line", null));
            stop.setReturnGoldBlocks(new ArrayList<>(ss.getStringList("return_gold")));
            stop.setDwellTimeTicks(ss.getInt("dwell_time", plugin.getConfigManager().getDefaultDwellTime()));
            stop.setInfoPrev(ss.getString("info_prev", "-"));
            stop.setInfoNext(ss.getString("info_next", "-"));
            stop.setKeyStation(ss.getString("key_station", "-"));
            stop.setKeyDirection(ss.getString("key_direction", "-"));
            stop.setAdminInfo(ss.getString("admin_info", ""));
            stop.setGoldBlocks(ss.getStringList("gold_blocks"));
            stop.setDisplayBlocks(ss.getStringList("display_blocks"));
            stops.put(id, stop);
        }
    }

    public void save() {
        saveLines();
        saveStops();
    }

    private void saveLines() {
        File file = new File(plugin.getDataFolder(), "lines.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        for (Line line : lines.values()) {
            String path = "lines." + line.getId();
            yaml.set(path + ".display_name", line.getDisplayName());
            yaml.set(path + ".color", line.getColor());
            yaml.set(path + ".max_speed", line.getMaxSpeed());
            yaml.set(path + ".circular", line.isCircular());
            yaml.set(path + ".stops", line.getStopIds());
        }
        try { yaml.save(file); } catch (IOException e) {
            plugin.getLogger().severe("無法儲存 lines.yml: " + e.getMessage());
        }
    }

    private void saveStops() {
        File file = new File(plugin.getDataFolder(), "stops.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        for (Stop stop : stops.values()) {
            String path = "stops." + stop.getId();
            yaml.set(path + ".display_name", stop.getDisplayName());
            yaml.set(path + ".world", stop.getWorld());
            yaml.set(path + ".lines", stop.getLineIds());
            yaml.set(path + ".display_line", stop.getDisplayLineId());
            yaml.set(path + ".return_line", stop.getReturnLineId());
            yaml.set(path + ".return_gold", stop.getReturnGoldBlocks());
            yaml.set(path + ".dwell_time", stop.getDwellTimeTicks());
            yaml.set(path + ".info_prev", stop.getInfoPrev());
            yaml.set(path + ".info_next", stop.getInfoNext());
            yaml.set(path + ".key_station", stop.getKeyStation());
            yaml.set(path + ".key_direction", stop.getKeyDirection());
            yaml.set(path + ".admin_info", stop.getAdminInfo());
            yaml.set(path + ".gold_blocks", stop.getGoldBlocks());
            yaml.set(path + ".display_blocks", stop.getDisplayBlocks());
        }
        try { yaml.save(file); } catch (IOException e) {
            plugin.getLogger().severe("無法儲存 stops.yml: " + e.getMessage());
        }
    }

    public Map<String, Line> getLines() { return lines; }
    public Map<String, Stop> getStops() { return stops; }
}
