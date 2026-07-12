package com.avery.atrain.manager;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.StationUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

public class StopManager {
    private final AtrainPlugin plugin;

    public StopManager(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public Collection<Stop> getAllStops() {
        return plugin.getDataStore().getStops().values();
    }

    public Stop getStop(String id) {
        return id == null ? null : plugin.getDataStore().getStops().get(id);
    }

    public Stop getStopAtRail(Location loc) {
        if (loc == null) return null;
        for (Stop stop : getAllStops()) {
            if (stop.containsRail(loc)) return stop;
        }
        return null;
    }

    public Stop getStopByDisplay(Location loc) {
        if (loc == null) return null;
        Location feet = loc.clone();
        for (Stop stop : getAllStops()) {
            if (stop.containsInfoLocation(feet)) return stop;
        }
        Block below = feet.getBlock();
        Block stand = feet.clone().subtract(0, 1, 0).getBlock();
        for (Block candidate : new Block[]{below, stand}) {
            if (!StationUtil.isDisplayBlock(candidate.getType())) continue;
            Stop adjacent = findStopAdjacentTo(candidate);
            if (adjacent != null) {
                rescanDisplayBlocks(adjacent);
                return adjacent;
            }
        }
        return null;
    }

    public Stop findStopOwningGold(Block goldBlock) {
        if (goldBlock == null) return null;
        String key = Stop.key(goldBlock.getX(), goldBlock.getY(), goldBlock.getZ());
        for (Stop stop : getAllStops()) {
            if (stop.getGoldBlocks().contains(key)) return stop;
        }
        return null;
    }

    /** 方塊是否鄰近某站點的金磚月台（含鑽石顯示塊） */
    public Stop findStopAdjacentTo(Block block) {
        if (block == null || block.getWorld() == null) return null;
        int x = block.getX(), y = block.getY(), z = block.getZ();
        for (Stop stop : getAllStops()) {
            if (!stop.getWorld().equals(block.getWorld().getName())) continue;
            for (String gk : stop.getGoldBlocks()) {
                int[] g = Stop.parseKey(gk);
                if (g == null) continue;
                if (Math.abs(g[0] - x) <= 1 && Math.abs(g[1] - y) <= 1 && Math.abs(g[2] - z) <= 1) {
                    return stop;
                }
            }
        }
        return null;
    }

    /** 從點擊的金磚/軌道掃描並建立或更新站點 */
    public RegisterResult registerFromBlock(Block clicked, String defaultName) {
        Block gold = StationUtil.resolveGoldBlock(clicked);
        if (gold == null) return null;

        Set<String> goldKeys = StationUtil.scanConnectedGoldPlatform(gold);
        if (goldKeys.isEmpty()) return null;

        List<Stop> touched = findAllStopsOwningAnyGold(goldKeys);
        if (!touched.isEmpty()) {
            return new RegisterResult(mergeStopsInto(touched.get(0), touched, goldKeys), false);
        }

        String world = gold.getWorld().getName();
        Set<String> displayKeys = StationUtil.scanAdjacentDisplayBlocks(goldKeys, world);

        String id = "stop_" + UUID.randomUUID().toString().substring(0, 8);
        String name = (defaultName != null && !defaultName.isBlank()) ? defaultName : id;
        Stop stop = new Stop(id, name, world);
        stop.setGoldBlocks(new ArrayList<>(goldKeys));
        stop.setDisplayBlocks(new ArrayList<>(displayKeys));
        stop.setDwellTimeTicks(plugin.getConfigManager().getDefaultDwellTime());

        plugin.getDataStore().getStops().put(id, stop);
        plugin.getDataStore().save();
        return new RegisterResult(stop, true);
    }

    public record RegisterResult(Stop stop, boolean created) {}

    private List<Stop> findAllStopsOwningAnyGold(Set<String> goldKeys) {
        List<Stop> found = new ArrayList<>();
        for (Stop stop : getAllStops()) {
            for (String key : goldKeys) {
                if (stop.getGoldBlocks().contains(key)) {
                    found.add(stop);
                    break;
                }
            }
        }
        return found;
    }

    private Stop mergeStopsInto(Stop primary, List<Stop> allTouched, Set<String> scannedGold) {
        Set<String> merged = new LinkedHashSet<>(scannedGold);
        for (Stop stop : allTouched) {
            merged.addAll(stop.getGoldBlocks());
        }
        primary.setGoldBlocks(new ArrayList<>(merged));
        Set<String> displays = StationUtil.scanAdjacentDisplayBlocks(merged, primary.getWorld());
        primary.setDisplayBlocks(new ArrayList<>(displays));

        for (int i = 1; i < allTouched.size(); i++) {
            mergeTextFields(primary, allTouched.get(i));
        }

        for (int i = 1; i < allTouched.size(); i++) {
            Stop other = allTouched.get(i);
            String otherId = other.getId();
            plugin.getDataStore().getStops().remove(otherId);
            for (Line line : plugin.getLineManager().getAllLines()) {
                List<String> ids = line.getStopIds();
                for (int j = 0; j < ids.size(); j++) {
                    if (otherId.equals(ids.get(j))) {
                        ids.set(j, primary.getId());
                    }
                }
                for (int j = ids.size() - 1; j > 0; j--) {
                    if (ids.get(j).equals(ids.get(j - 1))) {
                        ids.remove(j);
                    }
                }
            }
        }
        plugin.getDataStore().save();
        return primary;
    }

    private void mergeTextFields(Stop primary, Stop other) {
        if (isUnset(primary.getInfoPrev()) && !isUnset(other.getInfoPrev())) {
            primary.setInfoPrev(other.getInfoPrev());
        }
        if (isUnset(primary.getInfoNext()) && !isUnset(other.getInfoNext())) {
            primary.setInfoNext(other.getInfoNext());
        }
        if (isUnset(primary.getKeyStation()) && !isUnset(other.getKeyStation())) {
            primary.setKeyStation(other.getKeyStation());
        }
        if (isUnset(primary.getKeyDirection()) && !isUnset(other.getKeyDirection())) {
            primary.setKeyDirection(other.getKeyDirection());
        }
        if (primary.getDisplayName().equals(primary.getId()) && !other.getDisplayName().equals(other.getId())) {
            primary.setDisplayName(other.getDisplayName());
        }
        if (!other.getAdminInfo().isBlank()) {
            if (primary.getAdminInfo().isBlank()) {
                primary.setAdminInfo(other.getAdminInfo());
            } else if (!primary.getAdminInfo().contains(other.getAdminInfo())) {
                primary.setAdminInfo(primary.getAdminInfo() + " | " + other.getAdminInfo());
            }
        }
        primary.setDwellTimeTicks(Math.max(primary.getDwellTimeTicks(), other.getDwellTimeTicks()));
        for (String lineId : other.getLineIds()) {
            if (!primary.getLineIds().contains(lineId)) {
                primary.getLineIds().add(lineId);
            }
        }
        if (primary.getDisplayLineId() == null && other.getDisplayLineId() != null) {
            primary.setDisplayLineId(other.getDisplayLineId());
        }
        if (primary.getReturnLineId() == null && other.getReturnLineId() != null) {
            primary.setReturnLineId(other.getReturnLineId());
        }
        for (String k : other.getReturnGoldBlocks()) {
            if (!primary.getReturnGoldBlocks().contains(k)) primary.getReturnGoldBlocks().add(k);
        }
    }

    private boolean isUnset(String value) {
        return value == null || value.isBlank() || "-".equals(value);
    }

    public Stop refreshFromBlock(Block clicked) {
        RegisterResult result = registerFromBlock(clicked, null);
        return result != null ? result.stop() : null;
    }

    public boolean createStop(Stop stop) {
        if (stop == null || getStop(stop.getId()) != null) return false;
        plugin.getDataStore().getStops().put(stop.getId(), stop);
        plugin.getDataStore().save();
        return true;
    }

    public boolean deleteStop(String id) {
        if (plugin.getDataStore().getStops().remove(id) == null) return false;
        for (Line line : plugin.getLineManager().getAllLines()) {
            line.getStopIds().remove(id);
        }
        plugin.getDataStore().save();
        return true;
    }

    public void rescanDisplayBlocks(Stop stop) {
        if (stop == null) return;
        Set<String> displays = StationUtil.scanAdjacentDisplayBlocks(
                new LinkedHashSet<>(stop.getGoldBlocks()), stop.getWorld());
        stop.setDisplayBlocks(new ArrayList<>(displays));
        plugin.getDataStore().save();
    }

    public String getPrevStopName(Line line, String stopId) {
        if (line == null) return "-";
        int idx = line.getStopIds().indexOf(stopId);
        if (idx < 0) return "-";
        if (idx == 0) {
            if (line.isCircular() && !line.getStopIds().isEmpty()) {
                return nameOf(line.getStopIds().get(line.getStopIds().size() - 1));
            }
            return "-";
        }
        return nameOf(line.getStopIds().get(idx - 1));
    }

    public String getNextStopName(Line line, String stopId) {
        if (line == null) return "-";
        int idx = line.getStopIds().indexOf(stopId);
        if (idx < 0) return "-";
        if (idx + 1 < line.getStopIds().size()) return nameOf(line.getStopIds().get(idx + 1));
        if (line.isCircular() && !line.getStopIds().isEmpty()) return nameOf(line.getStopIds().get(0));
        return "終點";
    }

    private String nameOf(String stopId) {
        Stop s = getStop(stopId);
        return s != null ? s.getDisplayName() : stopId;
    }

    /** 解析用於顯示上下站的路線（優先 displayLineId，否則取第一條所屬路線） */
    public Line resolveDisplayLine(Stop stop) {
        return resolveDisplayLine(stop, null);
    }

    public Line resolveDisplayLine(Stop stop, Location at) {
        if (stop == null) return null;
        if (at != null && stop.isOnReturnPlatform(at) && stop.getReturnLineId() != null) {
            Line returnLine = plugin.getLineManager().getLine(stop.getReturnLineId());
            if (returnLine != null && returnLine.getStopIds().contains(stop.getId())) return returnLine;
        }
        String displayId = stop.getDisplayLineId();
        if (displayId != null) {
            Line line = plugin.getLineManager().getLine(displayId);
            if (line != null && line.getStopIds().contains(stop.getId())) return line;
        }
        for (String lineId : stop.getLineIds()) {
            Line line = plugin.getLineManager().getLine(lineId);
            if (line != null && line.getStopIds().contains(stop.getId())) return line;
        }
        return null;
    }

    public String resolveDisplayPrev(Stop stop) {
        return resolveDisplayPrev(stop, null);
    }

    public String resolveDisplayPrev(Stop stop, Location at) {
        Line line = resolveDisplayLine(stop, at);
        if (line != null) return getPrevStopName(line, stop.getId());
        return stop.getInfoPrev();
    }

    public String resolveDisplayNext(Stop stop) {
        return resolveDisplayNext(stop, null);
    }

    public String resolveDisplayNext(Stop stop, Location at) {
        Line line = resolveDisplayLine(stop, at);
        if (line != null) return getNextStopName(line, stop.getId());
        return stop.getInfoNext();
    }

    /** 將另一組金磚月台綁定為此站點的回程月台 */
    public boolean bindReturnPlatform(String primaryId, Block clicked) {
        Stop primary = getStop(primaryId);
        Block gold = StationUtil.resolveGoldBlock(clicked);
        if (primary == null || gold == null) return false;
        if (!gold.getWorld().getName().equals(primary.getWorld())) return false;

        Set<String> scanned = StationUtil.scanConnectedGoldPlatform(gold);
        if (scanned.isEmpty()) return false;

        Stop other = findStopOwningGold(gold);
        if (other != null && other.getId().equals(primaryId)) return false;

        if (other != null) {
            mergeStopsInto(primary, List.of(other), scanned);
            for (String k : other.getGoldBlocks()) {
                if (!primary.getReturnGoldBlocks().contains(k)) {
                    primary.getReturnGoldBlocks().add(k);
                }
            }
        } else {
            for (String k : scanned) {
                if (!primary.getGoldBlocks().contains(k)) primary.getGoldBlocks().add(k);
                if (!primary.getReturnGoldBlocks().contains(k)) primary.getReturnGoldBlocks().add(k);
            }
            Set<String> displays = StationUtil.scanAdjacentDisplayBlocks(
                    new LinkedHashSet<>(primary.getGoldBlocks()), primary.getWorld());
            primary.setDisplayBlocks(new ArrayList<>(displays));
        }
        plugin.getDataStore().save();
        return true;
    }
}
