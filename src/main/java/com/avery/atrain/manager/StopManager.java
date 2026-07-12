package com.avery.atrain.manager;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.BlockCoords;
import com.avery.atrain.util.StationUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.*;

public class StopManager {
    private final AtrainPlugin plugin;
    /** 世界名 -> 金磚座標 -> 站點（O(1) 查詢，避免每次移動線性掃描） */
    private final Map<String, Map<Long, Stop>> goldBlockIndex = new HashMap<>();

    public StopManager(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void rebuildSpatialIndex() {
        goldBlockIndex.clear();
        for (Stop stop : getAllStops()) {
            indexStopGoldBlocks(stop);
        }
    }

    private void indexStopGoldBlocks(Stop stop) {
        String world = stop.getWorld();
        Map<Long, Stop> worldIndex = goldBlockIndex.computeIfAbsent(world, k -> new HashMap<>());
        for (String key : stop.getGoldBlocks()) {
            long packed = BlockCoords.fromDataString(key);
            if (BlockCoords.isValid(packed)) worldIndex.put(packed, stop);
        }
        for (String key : stop.getReturnGoldBlocks()) {
            long packed = BlockCoords.fromDataString(key);
            if (BlockCoords.isValid(packed)) worldIndex.put(packed, stop);
        }
    }

    private Stop lookupGoldBlock(String world, int x, int y, int z) {
        Map<Long, Stop> worldIndex = goldBlockIndex.get(world);
        if (worldIndex == null) return null;
        return worldIndex.get(BlockCoords.pack(x, y, z));
    }

    private Stop lookupGoldNear(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        String world = loc.getWorld().getName();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        Stop stop = lookupGoldBlock(world, x, y, z);
        if (stop != null) return stop;
        return lookupGoldBlock(world, x, y - 1, z);
    }

    public Collection<Stop> getAllStops() {
        return plugin.getDataStore().getStops().values();
    }

    public Stop getStop(String id) {
        return id == null ? null : plugin.getDataStore().getStops().get(id);
    }

    public Stop getStopAtRail(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        Stop quick = lookupGoldNear(loc);
        if (quick != null) return quick;

        String world = loc.getWorld().getName();
        Block rail = com.avery.atrain.util.RailUtil.findRailBlock(loc);
        if (rail == null) return null;
        Block below = rail.getRelative(BlockFace.DOWN);
        for (int d = 0; d < 4; d++) {
            Stop stop = lookupGoldBlock(world, below.getX(), below.getY(), below.getZ());
            if (stop != null) return stop;
            below = below.getRelative(BlockFace.DOWN);
        }
        return null;
    }

    public Stop getStopByDisplay(Location loc) {
        return lookupGoldNear(loc);
    }

    public Stop findStopOwningGold(Block goldBlock) {
        if (goldBlock == null || goldBlock.getWorld() == null) return null;
        return lookupGoldBlock(goldBlock.getWorld().getName(), goldBlock.getX(), goldBlock.getY(), goldBlock.getZ());
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
        stop.removeGoldKey(key);
        if (!stop.hasAnyGoldBlock()) {
            deleteStop(stop.getId());
        } else {
            plugin.getDataStore().save();
            rebuildSpatialIndex();
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
        rebuildSpatialIndex();
        return new RegisterResult(stop, true);
    }

    public record RegisterResult(Stop stop, boolean created) {}

    private List<Stop> findAllStopsOwningAnyGold(Set<String> goldKeys) {
        List<Stop> found = new ArrayList<>();
        for (Stop stop : getAllStops()) {
            for (String key : goldKeys) {
                if (stop.ownsGoldKey(key)) {
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
        rebuildSpatialIndex();
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
            primary.addReturnGoldKey(k);
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
        rebuildSpatialIndex();
        return true;
    }

    public boolean deleteStop(String id) {
        if (plugin.getDataStore().getStops().remove(id) == null) return false;
        for (Line line : plugin.getLineManager().getAllLines()) {
            line.getStopIds().remove(id);
        }
        plugin.getDataStore().save();
        rebuildSpatialIndex();
        return true;
    }

    /** 將玩家傳送至站點月台（優先去程鐵軌，否則取金磚上方） */
    public boolean teleportPlayerToStop(Player player, Stop stop) {
        if (player == null || stop == null) return false;
        org.bukkit.Location loc = stop.getPrimaryRailLocation();
        if (loc == null) {
            loc = resolveTeleportFallback(stop);
        }
        if (loc == null) return false;
        loc = loc.clone();
        org.bukkit.Location from = player.getLocation();
        loc.setYaw(from.getYaw());
        loc.setPitch(from.getPitch());
        return player.teleport(loc);
    }

    private org.bukkit.Location resolveTeleportFallback(Stop stop) {
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(stop.getWorld());
        if (w == null) return null;
        for (String k : stop.getGoldBlocks()) {
            org.bukkit.Location loc = locationAboveGold(w, k);
            if (loc != null) return loc;
        }
        for (String k : stop.getReturnGoldBlocks()) {
            org.bukkit.Location loc = locationAboveGold(w, k);
            if (loc != null) return loc;
        }
        return null;
    }

    private org.bukkit.Location locationAboveGold(org.bukkit.World w, String goldKey) {
        int[] p = Stop.parseKey(goldKey);
        if (p == null) return null;
        return new org.bukkit.Location(w, p[0] + 0.5, p[1] + 1.0, p[2] + 0.5);
    }

    /** 鑽石塊已改為調速方塊，此方法保留相容性（清空舊顯示塊資料） */
    public void rescanDisplayBlocks(Stop stop) {
        if (stop == null) return;
        stop.setDisplayBlocks(List.of());
        plugin.getDataStore().save();
    }

    public String getPrevStopName(Line line, String stopId) {
        if (line == null) return null;
        int idx = line.getStopIds().indexOf(stopId);
        if (idx < 0) return null;
        if (idx == 0) {
            if (line.isCircular() && line.getStopIds().size() > 1) {
                return nameOf(line.getStopIds().get(line.getStopIds().size() - 1));
            }
            return null;
        }
        return nameOf(line.getStopIds().get(idx - 1));
    }

    public String getNextStopName(Line line, String stopId) {
        if (line == null) return null;
        int idx = line.getStopIds().indexOf(stopId);
        if (idx < 0) return null;
        if (idx + 1 < line.getStopIds().size()) return nameOf(line.getStopIds().get(idx + 1));
        if (line.isCircular() && line.getStopIds().size() > 1) return nameOf(line.getStopIds().get(0));
        return null;
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
        if (line == null) {
            String manual = stop.getInfoPrev();
            return isUnset(manual) ? null : manual;
        }
        return isReturnReversed(stop, at)
                ? getNextStopName(line, stop.getId())
                : getPrevStopName(line, stop.getId());
    }

    public String resolveDisplayNext(Stop stop) {
        return resolveDisplayNext(stop, null);
    }

    public String resolveDisplayNext(Stop stop, Location at) {
        Line line = resolveDisplayLine(stop, at);
        if (line == null) {
            String manual = stop.getInfoNext();
            return isUnset(manual) ? null : manual;
        }
        return isReturnReversed(stop, at)
                ? getPrevStopName(line, stop.getId())
                : getNextStopName(line, stop.getId());
    }

    public boolean hasDisplayPrev(Stop stop, Location at) {
        String value = resolveDisplayPrev(stop, at);
        return value != null && !value.isBlank();
    }

    public boolean hasDisplayNext(Stop stop, Location at) {
        String value = resolveDisplayNext(stop, at);
        return value != null && !value.isBlank();
    }

    public boolean hasDisplayPrev(Stop stop) {
        return hasDisplayPrev(stop, null);
    }

    public boolean hasDisplayNext(Stop stop) {
        return hasDisplayNext(stop, null);
    }

    /**
     * 判斷是否需要在回程月台反向顯示上下站。
     * 同線反向（含 returnLineId 指向去程同一條路線）需對調；僅獨立回程路線（不同 lineId）時沿用該線站序。
     */
    private boolean isReturnReversed(Stop stop, Location at) {
        if (at == null || !stop.isOnReturnPlatform(at)) return false;
        String returnLineId = stop.getReturnLineId();
        if (returnLineId == null) return true;
        Line returnLine = plugin.getLineManager().getLine(returnLineId);
        if (returnLine == null || !returnLine.getStopIds().contains(stop.getId())) return true;
        Line forwardLine = resolveForwardDisplayLine(stop);
        return forwardLine == null || forwardLine.getId().equals(returnLine.getId());
    }

    /** 去程顯示路線（不考慮玩家站在哪側月台） */
    private Line resolveForwardDisplayLine(Stop stop) {
        if (stop == null) return null;
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

    /** 將另一組金磚月台綁定為此站點的回程月台 */
    public boolean bindReturnPlatform(String primaryId, Block clicked) {
        Stop primary = getStop(primaryId);
        Block gold = StationUtil.resolveGoldBlock(clicked);
        if (primary == null || gold == null) return false;
        if (!gold.getWorld().getName().equals(primary.getWorld())) return false;

        Set<String> scanned = StationUtil.scanConnectedGoldPlatform(gold);
        if (scanned.isEmpty()) return false;

        for (String k : scanned) {
            int[] p = Stop.parseKey(k);
            if (p != null
                    && primary.hasGoldBlock(p[0], p[1], p[2])
                    && !primary.hasReturnGoldBlock(p[0], p[1], p[2])) {
                return false;
            }
        }

        Stop other = findStopOwningGold(gold);
        if (other != null && other.getId().equals(primaryId)) return false;

        if (other != null) {
            absorbStopAsReturnPlatform(primary, other, scanned);
        } else {
            attachReturnGoldBlocks(primary, scanned);
        }
        plugin.getDataStore().save();
        rebuildSpatialIndex();
        return true;
    }

    private void attachReturnGoldBlocks(Stop stop, Set<String> goldKeys) {
        for (String k : goldKeys) {
            int[] p = Stop.parseKey(k);
            if (p != null) stop.addGoldBlock(p[0], p[1], p[2]);
            stop.addReturnGoldKey(k);
        }
    }

    /** 將另一站點的金磚月台併入本站回程月台（站名與路線資料保留，不當成去程月台合併） */
    private void absorbStopAsReturnPlatform(Stop primary, Stop other, Set<String> returnGold) {
        mergeTextFields(primary, other);

        Set<String> keys = new LinkedHashSet<>(returnGold);
        keys.addAll(other.getGoldBlocks());
        keys.addAll(other.getReturnGoldBlocks());
        attachReturnGoldBlocks(primary, keys);

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
}
