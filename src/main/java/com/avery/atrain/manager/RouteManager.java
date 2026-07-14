package com.avery.atrain.manager;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.RoutePoint;
import com.avery.atrain.model.Stop;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteManager {

    private final AtrainPlugin plugin;
    private final File routesFolder;
    
    // Key: fromStopId_toStopId
    private final Map<String, List<RoutePoint>> routes = new HashMap<>();

    public RouteManager(AtrainPlugin plugin) {
        this.plugin = plugin;
        this.routesFolder = new File(plugin.getDataFolder(), "routes");
        if (!routesFolder.exists()) {
            routesFolder.mkdirs();
        }
    }

    public void load() {
        routes.clear();
        if (!routesFolder.exists()) return;
        
        File[] files = routesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String fromId = yaml.getString("from_id");
            String toId = yaml.getString("to_id");
            if (fromId == null || toId == null) continue;

            List<String> pointsStr = yaml.getStringList("points");
            List<RoutePoint> points = new ArrayList<>();
            for (String s : pointsStr) {
                RoutePoint rp = RoutePoint.fromString(s);
                if (rp != null) {
                    points.add(rp);
                }
            }
            
            String key = buildKey(fromId, toId);
            routes.put(key, points);
        }
    }

    public void saveRoute(Stop from, Stop to, List<RoutePoint> points) {
        String key = buildKey(from.getId(), to.getId());
        if (points == null || points.isEmpty()) {
            routes.remove(key);
            deleteFile(from, to);
            return;
        }
        
        routes.put(key, new ArrayList<>(points));
        
        File file = getFile(from, to);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("from_id", from.getId());
        yaml.set("to_id", to.getId());
        yaml.set("from_name", from.getDisplayName());
        yaml.set("to_name", to.getDisplayName());
        
        List<String> pointsStr = new ArrayList<>();
        for (RoutePoint rp : points) {
            pointsStr.add(rp.toDataString());
        }
        yaml.set("points", pointsStr);
        
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("無法儲存軌跡檔案: " + file.getName());
        }
    }
    
    public void deleteRoute(Stop from, Stop to) {
        String key = buildKey(from.getId(), to.getId());
        routes.remove(key);
        deleteFile(from, to);
    }

    public List<RoutePoint> getRoute(String fromId, String toId) {
        return routes.get(buildKey(fromId, toId));
    }
    
    public List<Location> getRouteLocations(String fromId, String toId) {
        List<RoutePoint> points = getRoute(fromId, toId);
        if (points == null) return null;
        List<Location> locs = new ArrayList<>();
        for (RoutePoint rp : points) {
            locs.add(rp.toLocation());
        }
        return locs;
    }

    public int getRecordedSegmentCount(com.avery.atrain.model.Line line, com.avery.atrain.model.TravelDirection direction) {
        if (line == null) return 0;
        int n = 0;
        for (int i = 0; i < line.getSegmentCount(); i++) {
            if (hasSegment(line, i, direction)) n++;
        }
        return n;
    }

    public boolean hasSegment(com.avery.atrain.model.Line line, int segmentIndex, com.avery.atrain.model.TravelDirection direction) {
        if (line == null) return false;
        String fromId = line.getSegmentFromStopId(segmentIndex, direction);
        String toId = line.getSegmentToStopId(segmentIndex, direction);
        if (fromId == null || toId == null) return false;
        return routes.containsKey(buildKey(fromId, toId));
    }

    private String buildKey(String fromId, String toId) {
        return fromId + "_" + toId;
    }

    private File getFile(Stop from, Stop to) {
        String fromName = sanitizeFilename(from.getDisplayName());
        String toName = sanitizeFilename(to.getDisplayName());
        return new File(routesFolder, fromName + "_" + toName + ".yml");
    }
    
    private void deleteFile(Stop from, Stop to) {
        File file = getFile(from, to);
        if (file.exists()) {
            file.delete();
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
