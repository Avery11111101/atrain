package com.avery.atrain.map;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.RoutePoint;
import com.avery.atrain.model.Stop;
import com.avery.atrain.model.TravelDirection;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import com.flowpowered.math.vector.Vector3d;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BlueMapManager {
    private final AtrainPlugin plugin;
    private Consumer<BlueMapAPI> onEnableListener;
    private Consumer<BlueMapAPI> onDisableListener;
    private boolean apiEnabled = false;

    public BlueMapManager(AtrainPlugin plugin) {
        this.plugin = plugin;
        setupListeners();
    }

    private void setupListeners() {
        onEnableListener = api -> {
            apiEnabled = true;
            updateMap(api);
        };
        onDisableListener = api -> {
            apiEnabled = false;
        };
        BlueMapAPI.onEnable(onEnableListener);
        BlueMapAPI.onDisable(onDisableListener);
    }

    public void updateMap() {
        if (apiEnabled) {
            BlueMapAPI.getInstance().ifPresent(this::updateMap);
        }
    }

    private void updateMap(BlueMapAPI api) {
        // 先建立每個 BlueMapMap 對應的 MarkerSet
        Map<String, MarkerSet> mapMarkerSets = new HashMap<>();
        for (BlueMapMap map : api.getMaps()) {
            MarkerSet markerSet = MarkerSet.builder()
                    .label("火車路線 (Atrain)")
                    .toggleable(true)
                    .defaultHidden(false)
                    .build();
            mapMarkerSets.put(map.getId(), markerSet);
            // 將新的 MarkerSet 放入地圖中 (會覆蓋舊的同名 MarkerSet)
            map.getMarkerSets().put("atrain", markerSet);
        }

        // 添加站點標記
        for (Stop stop : plugin.getDataStore().getStops().values()) {
            World w = Bukkit.getWorld(stop.getWorld());
            if (w == null) continue;

            BlueMapWorld bmWorld = api.getWorld(w).orElse(null);
            if (bmWorld == null) continue;

            Location loc = stop.getRailLocation(TravelDirection.FORWARD);
            if (loc == null && !stop.getGoldLocations(w).isEmpty()) {
                loc = stop.getGoldLocations(w).iterator().next();
            }
            if (loc == null) continue;

            POIMarker stationMarker = POIMarker.builder()
                    .label(stop.getDisplayName())
                    .position(new Vector3d(loc.getX(), loc.getY(), loc.getZ()))
                    .maxDistance(5000)
                    .build();

            // 放入該世界的所有地圖
            for (BlueMapMap map : bmWorld.getMaps()) {
                MarkerSet ms = mapMarkerSets.get(map.getId());
                if (ms != null) {
                    ms.put("station_" + stop.getId(), stationMarker);
                }
            }
        }

        // 添加路線標記
        for (com.avery.atrain.model.Line lineInfo : plugin.getLineManager().getAllLines()) {
            List<RoutePoint> pts = lineInfo.getRoutePoints();
            if (pts == null || pts.isEmpty()) continue;

            List<Vector3d> vectorLine = new ArrayList<>();
            String currentWorldName = null;

            for (RoutePoint pt : pts) {
                if (currentWorldName == null) currentWorldName = pt.getWorld();
                if (!pt.getWorld().equals(currentWorldName)) {
                    if (vectorLine.size() >= 2) {
                        drawLineForWorld(api, mapMarkerSets, lineInfo, currentWorldName, vectorLine);
                    }
                    vectorLine.clear();
                    currentWorldName = pt.getWorld();
                }
                vectorLine.add(new Vector3d(pt.getX(), pt.getY() + 0.5, pt.getZ()));
            }
            if (vectorLine.size() >= 2) {
                drawLineForWorld(api, mapMarkerSets, lineInfo, currentWorldName, vectorLine);
            }
        }
    }

    private void drawLineForWorld(BlueMapAPI api, Map<String, MarkerSet> mapMarkerSets, com.avery.atrain.model.Line lineInfo, String worldName, List<Vector3d> points) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;
        
        BlueMapWorld bmWorld = api.getWorld(w).orElse(null);
        if (bmWorld == null) return;

        Line bmLine = new Line(points);
        Color color = parseColor(lineInfo.getColor());

        LineMarker lineMarker = LineMarker.builder()
                .label(lineInfo.getDisplayName())
                .line(bmLine)
                .lineColor(color)
                .lineWidth(4)
                .depthTestEnabled(false)
                .maxDistance(10000)
                .build();

        String markerId = "line_" + lineInfo.getId() + "_" + System.nanoTime();
        
        for (BlueMapMap map : bmWorld.getMaps()) {
            MarkerSet ms = mapMarkerSets.get(map.getId());
            if (ms != null) {
                ms.put(markerId, lineMarker);
            }
        }
    }

    private Color parseColor(String colorCode) {
        if (colorCode == null || colorCode.isEmpty()) return new Color(100, 255, 100, 255);
        char code = colorCode.charAt(colorCode.length() - 1);
        switch (code) {
            case '0': return new Color(0, 0, 0, 255);
            case '1': return new Color(0, 0, 170, 255);
            case '2': return new Color(0, 170, 0, 255);
            case '3': return new Color(0, 170, 170, 255);
            case '4': return new Color(170, 0, 0, 255);
            case '5': return new Color(170, 0, 170, 255);
            case '6': return new Color(255, 170, 0, 255);
            case '7': return new Color(170, 170, 170, 255);
            case '8': return new Color(85, 85, 85, 255);
            case '9': return new Color(85, 85, 255, 255);
            case 'a': return new Color(85, 255, 85, 255);
            case 'b': return new Color(85, 255, 255, 255);
            case 'c': return new Color(255, 85, 85, 255);
            case 'd': return new Color(255, 85, 255, 255);
            case 'e': return new Color(255, 255, 85, 255);
            case 'f': return new Color(255, 255, 255, 255);
            default: return new Color(100, 255, 100, 255);
        }
    }

    public void disable() {
        if (onEnableListener != null) BlueMapAPI.unregisterListener(onEnableListener);
        if (onDisableListener != null) BlueMapAPI.unregisterListener(onDisableListener);
    }
}
