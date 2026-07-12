package com.avery.atrain.config;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.RoutePoint;
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
            List<RoutePoint> points = new ArrayList<>();
            for (String s : ls.getStringList("route_points")) {
                RoutePoint rp = RoutePoint.fromString(s);
                if (rp != null) points.add(rp);
            }
            line.setRoutePoints(points);
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
            stop.setLaunchYaw((float) ss.getDouble("launch_yaw", 0));
            stop.setLineIds(new ArrayList<>(ss.getStringList("lines")));
            ConfigurationSection c1 = ss.getConfigurationSection("corner1");
            ConfigurationSection c2 = ss.getConfigurationSection("corner2");
            ConfigurationSection fp = ss.getConfigurationSection("forward_point");
            ConfigurationSection rp = ss.getConfigurationSection("return_point");
            ConfigurationSection sp = ss.getConfigurationSection("stop_point");
            if (c1 != null) {
                stop.setCorner1(new org.bukkit.Location(
                    org.bukkit.Bukkit.getWorld(stop.getWorld()),
                    c1.getDouble("x"), c1.getDouble("y"), c1.getDouble("z")));
            }
            if (c2 != null) {
                stop.setCorner2(new org.bukkit.Location(
                    org.bukkit.Bukkit.getWorld(stop.getWorld()),
                    c2.getDouble("x"), c2.getDouble("y"), c2.getDouble("z")));
            }
            if (fp != null) {
                stop.setBoardPoint(com.avery.atrain.model.PlatformSide.FORWARD, new org.bukkit.Location(
                        org.bukkit.Bukkit.getWorld(stop.getWorld()),
                        fp.getDouble("x"), fp.getDouble("y"), fp.getDouble("z"),
                        (float) fp.getDouble("yaw", 0), 0));
            } else if (sp != null) {
                stop.migrateLegacyStopPoint(
                        sp.getDouble("x"), sp.getDouble("y"), sp.getDouble("z"),
                        (float) sp.getDouble("yaw", stop.getLaunchYaw()));
            }
            if (rp != null) {
                stop.setBoardPoint(com.avery.atrain.model.PlatformSide.RETURN, new org.bukkit.Location(
                        org.bukkit.Bukkit.getWorld(stop.getWorld()),
                        rp.getDouble("x"), rp.getDouble("y"), rp.getDouble("z"),
                        (float) rp.getDouble("yaw", 0), 0));
            }
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
            List<String> pts = new ArrayList<>();
            for (RoutePoint rp : line.getRoutePoints()) pts.add(rp.toDataString());
            yaml.set(path + ".route_points", pts);
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
            yaml.set(path + ".launch_yaw", stop.getLaunchYaw());
            yaml.set(path + ".lines", stop.getLineIds());
            yaml.set(path + ".corner1.x", stop.getC1x());
            yaml.set(path + ".corner1.y", stop.getC1y());
            yaml.set(path + ".corner1.z", stop.getC1z());
            yaml.set(path + ".corner2.x", stop.getC2x());
            yaml.set(path + ".corner2.y", stop.getC2y());
            yaml.set(path + ".corner2.z", stop.getC2z());
            if (stop.hasForwardPoint()) {
                yaml.set(path + ".forward_point.x", stop.getStopX());
                yaml.set(path + ".forward_point.y", stop.getStopY());
                yaml.set(path + ".forward_point.z", stop.getStopZ());
                yaml.set(path + ".forward_point.yaw", stop.getLaunchYaw());
            }
            if (stop.hasReturnPoint()) {
                var rp = stop.getReturnPoint();
                yaml.set(path + ".return_point.x", rp.getX());
                yaml.set(path + ".return_point.y", rp.getY());
                yaml.set(path + ".return_point.z", rp.getZ());
                yaml.set(path + ".return_point.yaw", rp.getYaw());
            }
        }
        try { yaml.save(file); } catch (IOException e) {
            plugin.getLogger().severe("無法儲存 stops.yml: " + e.getMessage());
        }
    }

    public Map<String, Line> getLines() { return lines; }
    public Map<String, Stop> getStops() { return stops; }
}
