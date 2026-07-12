package com.avery.atrain.manager;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.RoutePoint;
import com.avery.atrain.util.TextUtil;
import com.avery.atrain.util.TrackUtil;
import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import java.util.*;

public class RouteRecorder {
    private final AtrainPlugin plugin;
    private final Map<UUID, String> recording = new HashMap<>();
    private final Map<UUID, Location> lastSample = new HashMap<>();
    private final Map<UUID, Boolean> pendingClear = new HashMap<>();
    private final Map<UUID, Boolean> hasSamples = new HashMap<>();

    public RouteRecorder(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRecording(Player player) {
        return recording.containsKey(player.getUniqueId());
    }

    public String getRecordingLine(Player player) {
        return recording.get(player.getUniqueId());
    }

    public void startRecording(Player player, String lineId) {
        UUID id = player.getUniqueId();
        if (recording.containsKey(id)) {
            stopRecording(player);
        }
        recording.put(id, lineId);
        pendingClear.put(id, true);
        hasSamples.put(id, false);
        lastSample.remove(id);
    }

    public void stopRecording(Player player) {
        UUID id = player.getUniqueId();
        if (!recording.containsKey(id)) return;
        if (Boolean.TRUE.equals(hasSamples.get(id))) {
            plugin.getDataStore().save();
        }
        recording.remove(id);
        lastSample.remove(id);
        pendingClear.remove(id);
        hasSamples.remove(id);
    }

    public void stopAllRecording() {
        stopAllRecording(false);
    }

    public void stopAllRecording(boolean notifyPlayers) {
        if (notifyPlayers) {
            var lang = plugin.getLanguageManager();
            for (UUID id : new ArrayList<>(recording.keySet())) {
                Player player = plugin.getServer().getPlayer(id);
                if (player != null) {
                    TextUtil.send(player, lang.get(player, "route.recording_stop_reload"));
                }
            }
        }
        boolean anySamples = hasSamples.values().stream().anyMatch(Boolean::booleanValue);
        if (anySamples) {
            plugin.getDataStore().save();
        }
        recording.clear();
        lastSample.clear();
        pendingClear.clear();
        hasSamples.clear();
    }

    public int getRoutePointCount(String lineId) {
        Line line = plugin.getLineManager().getLine(lineId);
        return line == null ? 0 : line.getRoutePoints().size();
    }

    /** 任意礦車移動時採樣（不限 PDC 標記的列車礦車） */
    public void onAnyCartMove(Minecart cart, Location to) {
        if (!TrackUtil.isOnTrack(plugin, to)) return;
        for (var entry : new ArrayList<>(recording.entrySet())) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isInsideVehicle() || player.getVehicle() != cart) continue;

            UUID pid = entry.getKey();
            String lineId = entry.getValue();
            Line line = plugin.getLineManager().getLine(lineId);
            if (line == null) continue;

            if (Boolean.TRUE.equals(pendingClear.remove(pid))) {
                line.getRoutePoints().clear();
            }

            Location last = lastSample.get(pid);
            double sampleDist = plugin.getConfigManager().getSampleDistance();
            if (last != null && last.distance(to) < sampleDist) continue;

            Location sample = TrackUtil.sampleLocation(plugin, to);
            line.getRoutePoints().add(new RoutePoint(sample));
            lastSample.put(pid, sample.clone());
            hasSamples.put(pid, true);

            if (line.getRoutePoints().size() % 20 == 0) {
                plugin.getDataStore().save();
            }
        }
    }

    public int clearRoute(String lineId) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return 0;
        int count = line.getRoutePoints().size();
        line.getRoutePoints().clear();
        plugin.getDataStore().save();
        return count;
    }
}
