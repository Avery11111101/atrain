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
        for (Stop stop : getAllStops()) {
            if (stop.containsInfoLocation(loc)) return stop;
        }
        return null;
    }

    public Stop findStopOwningGold(Block goldBlock) {
        if (goldBlock == null) return null;
        String key = Stop.key(goldBlock.getX(), goldBlock.getY(), goldBlock.getZ());
        for (Stop stop : getAllStops()) {
            if (stop.getGoldBlocks().contains(key) || stop.getReturnGoldBlocks().contains(key)) {
                return stop;
            }
        }
        return null;
    }

    /** 該金磚是否為已註冊站點的一部分（受保護不可被一般玩家破壞） */
    public boolean isProtectedGold(Block block) {
        return findStopOwningGold(block) != null;
    }

    /** 管理員破壞金磚後，從站點資料移除；若站點無金磚則刪除站點 */
    public void unregisterGoldBlock(Block block) {
        Stop stop = findStopOwningGold(block);
        if (stop == null) return;
        String key = Stop.key(block.getX(), block.getY(), block.getZ());
        stop.getGoldBlocks().remove(key);
        stop.getReturnGoldBlocks().remove(key);
        if (stop.getGoldBlocks().isEmpty()) {
            deleteStop(stop.getId());
        } else {
            plugin.getDataStore().save();
        }
    }

    /** 方塊是否鄰近某站點的金磚月台 */
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

        String id = "stop_" + UUID.randomUUID().toString().substring(0, 8);
        String name = (defaultName != null && !defaultName.isBlank()) ? defaultName : id;
        Stop stop = new Stop(id, name, world);
        stop.setGoldBlocks(new ArrayList<>(goldKeys));
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

    /** 鑽石塊已改為調速方塊，此方法保留相容性（清空舊顯示塊資料） */
    public void rescanDisplayBlocks(Stop stop) {
        if (stop == null) return;
        stop.setDisplayBlocks(List.of());
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
        return plugin.getLanguageManager().getRaw(
                plugin.getLanguageManager().getDefaultLanguage(), "station.terminus");
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
        if (line == null) return stop.getInfoPrev();
        return isReturnReversed(stop, at)
                ? getNextStopName(line, stop.getId())
                : getPrevStopName(line, stop.getId());
    }

    public String resolveDisplayNext(Stop stop) {
        return resolveDisplayNext(stop, null);
    }

    public String resolveDisplayNext(Stop stop, Location at) {
        Line line = resolveDisplayLine(stop, at);
        if (line == null) return stop.getInfoNext();
        return isReturnReversed(stop, at)
                ? getPrevStopName(line, stop.getId())
                : getNextStopName(line, stop.getId());
    }

    /**
     * 判斷是否需要在回程月台反向顯示上下站。
     * 條件：站在回程月台，且未設定有效的 returnLineId（沒設、對應路線為 null 或不含本站）。
     * 此時 resolveDisplayLine 會 fallback 到去程顯示路線，需將上下站對調。
     */
    private boolean isReturnReversed(Stop stop, Location at) {
        if (at == null || !stop.isOnReturnPlatform(at)) return false;
        String returnLineId = stop.getReturnLineId();
        if (returnLineId == null) return true;
        Line returnLine = plugin.getLineManager().getLine(returnLineId);
        return returnLine == null || !returnLine.getStopIds().contains(stop.getId());
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
                if (!primary.getReturnGoldBlocks().contains(k)) primary.getReturnGoldBlocks().add(k);
            }
        }
        plugin.getDataStore().save();
        return true;
    }
}
