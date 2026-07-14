package com.avery.atrain.route;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.model.TravelDirection;
import com.avery.atrain.util.RailUtil;
import com.avery.atrain.util.StationUtil;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 分段路線錄製：站點到站點，到站自動接下一段 */
public final class RouteRecordingManager {

    private final AtrainPlugin plugin;
    private final Map<UUID, RouteRecordingSession> byPlayer = new HashMap<>();
    private final Set<UUID> pendingRecordingCarts = new HashSet<>();
    private BukkitTask tickTask;

    public RouteRecordingManager(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (byPlayer.isEmpty()) return;
            tick(plugin.getServer().getCurrentTick());
        }, 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public boolean isRecording(Player player) {
        return player != null && byPlayer.containsKey(player.getUniqueId());
    }

    public boolean isAwaitingCart(Player player) {
        RouteRecordingSession s = getSession(player);
        return s != null && s.isAwaitingCart();
    }

    public boolean isRecordingCart(UUID cartId) {
        if (cartId == null) return false;
        if (pendingRecordingCarts.contains(cartId)) return true;
        for (RouteRecordingSession s : byPlayer.values()) {
            if (s.ownsCart(cartId)) return true;
        }
        return false;
    }

    public RouteRecordingSession getSession(Player player) {
        return player == null ? null : byPlayer.get(player.getUniqueId());
    }

    public String getRecordingLineId(Player player) {
        RouteRecordingSession s = getSession(player);
        return s != null ? s.getLineId() : null;
    }

    public TravelDirection getRecordingDirection(Player player) {
        RouteRecordingSession s = getSession(player);
        return s != null ? s.getDirection() : TravelDirection.FORWARD;
    }

    public boolean isRecordingLine(Player player, String lineId, TravelDirection direction) {
        RouteRecordingSession s = getSession(player);
        return s != null && lineId.equals(s.getLineId()) && s.getDirection() == direction;
    }

    public int getRecordingSegmentIndex(Player player) {
        RouteRecordingSession s = getSession(player);
        return s != null ? s.getSegmentIndex() : -1;
    }

    /**
     * 從指定路段開始錄製。
     * @param segmentIndex 路段索引（0 = 第 1 站→第 2 站）
     */
    public boolean start(Player player, String lineId, int segmentIndex, boolean clearSegment) {
        return start(player, lineId, segmentIndex, TravelDirection.FORWARD, clearSegment);
    }

    public boolean start(Player player, String lineId, int segmentIndex,
                         TravelDirection direction, boolean clearSegment) {
        if (player == null || lineId == null) return false;
        if (byPlayer.containsKey(player.getUniqueId())) {
            RouteRecordingSession active = getSession(player);
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_other_line",
                    Map.of("line", formatLineName(active != null ? active.getLineId() : null))));
            return false;
        }

        TravelDirection dir = direction != null ? direction : TravelDirection.FORWARD;
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return false;
        if (line.getStopIds().size() < 2) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.need_two_stops"));
            return false;
        }
        if (segmentIndex < 0 || segmentIndex >= line.getSegmentCount()) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.segment_invalid"));
            return false;
        }

        if (clearSegment) {
            // 延後至首次採樣時覆寫，取消錄製可還原舊軌跡
        }

        RouteRecordingSession session = new RouteRecordingSession(plugin, player, lineId, segmentIndex, dir, clearSegment);
        byPlayer.put(player.getUniqueId(), session);
        session.beginPending();

        String dirKey = dir == TravelDirection.REVERSE ? "route.direction_reverse" : "route.direction_forward";
        TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_start",
                Map.of("line", line.getFormattedName(),
                        "count", String.valueOf(line.getRecordedSegmentCount(dir)),
                        "dir", plugin.getLanguageManager().get(player, dirKey))));
        TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_spawn_hint",
                Map.of("sec", String.valueOf(plugin.getConfigManager().getRecordingDwellTicks() / 20))));
        TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_segment_start",
                RouteRecordingSession.segmentLabel(plugin, player, line, segmentIndex, dir)));
        return true;
    }

    public void autoRecordSegment(Player player, String lineId, int segmentIndex, TravelDirection direction) {
        if (player == null || lineId == null) return;
        player.closeInventory();
        if (byPlayer.containsKey(player.getUniqueId())) {
            RouteRecordingSession active = getSession(player);
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_other_line",
                    Map.of("line", formatLineName(active != null ? active.getLineId() : null))));
            return;
        }

        TravelDirection dir = direction != null ? direction : TravelDirection.FORWARD;
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;
        if (line.getStopIds().size() < 2) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.need_two_stops"));
            return;
        }
        if (segmentIndex < 0 || segmentIndex >= line.getSegmentCount()) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.segment_invalid"));
            return;
        }

        String fromStopId, toStopId;
        if (dir == TravelDirection.FORWARD) {
            fromStopId = line.getStopIds().get(segmentIndex);
            toStopId = line.getStopIds().get(segmentIndex + 1);
        } else {
            int maxIdx = line.getStopIds().size() - 1;
            fromStopId = line.getStopIds().get(maxIdx - segmentIndex);
            toStopId = line.getStopIds().get(maxIdx - segmentIndex - 1);
        }

        Stop fromStop = plugin.getStopManager().getStop(fromStopId);
        Stop toStop = plugin.getStopManager().getStop(toStopId);
        if (fromStop == null || toStop == null) return;

        Location fromRailLoc = fromStop.getRailLocation(dir);
        Location toRailLoc = toStop.getRailLocation(dir);
        if (fromRailLoc == null || toRailLoc == null) {
            TextUtil.send(player, "§c起點或終點站沒有設定鐵軌，無法自動取徑。");
            return;
        }

        Block fromRail = StationUtil.resolveRailBlock(fromRailLoc.getBlock());
        Block toRail = StationUtil.resolveRailBlock(toRailLoc.getBlock());

        if (fromRail == null || toRail == null) {
            TextUtil.send(player, "§c起點或終點站找不到軌道，無法自動取徑。");
            return;
        }

        player.sendMessage("§a開始自動計算路線...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            var path = com.avery.atrain.util.RailPathSampler.betweenStops(fromStop, toStop, null, dir);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (path != null && path.size() >= 2) {
                    java.util.List<com.avery.atrain.model.RoutePoint> points = new java.util.ArrayList<>();
                    for (Location loc : path) {
                        points.add(new com.avery.atrain.model.RoutePoint(loc));
                    }
                    line.setSegmentPoints(segmentIndex, dir, points);
                    plugin.getDataStore().save();
                    TextUtil.send(player, "§a自動取徑成功！儲存了 " + points.size() + " 個軌跡點。");
                } else {
                    TextUtil.send(player, "§c自動取徑失敗，可能兩站之間沒有相連的鐵軌。");
                }
            });
        });
    }

    public boolean spawnRecordingCart(Player player, Block clicked) {
        RouteRecordingSession session = getSession(player);
        if (session == null) return false;

        Block rail = StationUtil.resolveRailBlock(clicked);
        if (rail == null) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "cart.no_rail"));
            return false;
        }

        Stop stop = plugin.getStopManager().getStopAtRail(rail.getLocation());
        if (stop == null) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "cart.need_station"));
            return false;
        }

        TravelDirection dir = session.getDirection();
        Location railLoc = rail.getLocation();
        if (dir == TravelDirection.REVERSE) {
            if (!stop.isOnReturnPlatformOnly(railLoc)) {
                TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_wrong_platform_return"));
                return false;
            }
        } else if (!stop.getReturnGoldBlocks().isEmpty()
                && stop.isOnReturnPlatformOnly(railLoc)) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_wrong_platform_forward"));
            return false;
        }

        Line line = plugin.getLineManager().getLine(session.getLineId());
        if (line == null) return false;

        String expectedFrom = line.getSegmentFromStopId(session.getSegmentIndex(), session.getDirection());
        if (expectedFrom == null || !expectedFrom.equals(stop.getId())) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_wrong_segment_start",
                    RouteRecordingSession.segmentLabel(plugin, player, line, session.getSegmentIndex(), session.getDirection())));
            return false;
        }

        if (session.hasCart()) {
            RideableMinecart existing = session.getCart();
            double radius = plugin.getConfigManager().getCartSpawnRadius();
            if (existing != null && existing.isValid() && !existing.isDead()
                    && player.getLocation().distanceSquared(existing.getLocation()) <= radius * radius * 4) {
                TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_cart_exists"));
                if (plugin.getConfigManager().isCartSpawnAutoMount()) {
                    mountNextTick(player, existing);
                }
                return true;
            }
            session.detachCart();
        }

        World world = rail.getWorld();
        if (world == null) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "error.world_not_loaded"));
            return false;
        }

        Location spawnLoc = rail.getLocation().add(0.5, 0.0625, 0.5);
        double radius = plugin.getConfigManager().getCartSpawnRadius();
        for (var entity : world.getNearbyEntities(spawnLoc, radius, radius, radius)) {
            if (!(entity instanceof RideableMinecart rideable) || !entity.isValid() || entity.isDead()) continue;
            plugin.getCartSpawnManager().cancelDespawn(rideable.getUniqueId());
            session.attachCart(rideable, stop);
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_cart_exists"));
            if (plugin.getConfigManager().isCartSpawnAutoMount()) {
                mountNextTick(player, rideable);
            }
            return true;
        }

        RideableMinecart cart = world.spawn(spawnLoc, RideableMinecart.class, entity -> {
            entity.setGravity(true);
            entity.setMaxSpeed((float) plugin.getConfigManager().getCartSpeed());
            plugin.markAsManagedCart(entity);
            pendingRecordingCarts.add(entity.getUniqueId());
        });
        try {
            session.attachCart(cart, stop);
        } finally {
            pendingRecordingCarts.remove(cart.getUniqueId());
        }
        TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_cart_spawned",
                Map.of("stop", TextUtil.escapePlain(stop.getDisplayName()))));

        if (!RailUtil.isPoweredRail(spawnLoc)) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "cart.need_power"));
        }
        if (plugin.getConfigManager().isCartSpawnAutoMount()) {
            mountNextTick(player, cart);
        }
        return true;
    }

    private void mountNextTick(Player player, RideableMinecart cart) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!cart.isValid() || cart.isDead()) return;
            if (player.isInsideVehicle()) return;
            cart.addPassenger(player);
        });
    }

    /** 停止錄製：儲存目前路段並結束 */
    public void stop(Player player) {
        if (player == null) return;
        RouteRecordingSession session = byPlayer.get(player.getUniqueId());
        if (session == null) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.not_recording"));
            return;
        }
        session.saveCurrentSegmentAndFinish();
        finishSession(player, session, true);
    }

    /** 取消錄製：不儲存目前路段 */
    public void cancel(Player player) {
        if (player == null) return;
        RouteRecordingSession session = byPlayer.remove(player.getUniqueId());
        if (session == null) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.not_recording"));
            return;
        }
        session.discardCurrentSegment();
        session.end();
        TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_cancelled"));
    }

    public void stop(Player player, boolean save) {
        if (save) stop(player);
        else cancel(player);
    }

    private void finishSession(Player player, RouteRecordingSession session, boolean showSummary) {
        byPlayer.remove(player.getUniqueId());
        session.end();
        plugin.getDataStore().save();
        if (showSummary) {
            Line line = plugin.getLineManager().getLine(session.getLineId());
            int segsFwd = line != null ? line.getRecordedSegmentCount(TravelDirection.FORWARD) : 0;
            int segsRev = line != null ? line.getRecordedSegmentCount(TravelDirection.REVERSE) : 0;
            int pts = line != null ? countAllSegmentPoints(line) : 0;
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_stop",
                    Map.of("count", String.valueOf(pts),
                            "segments", String.valueOf(segsFwd + segsRev),
                            "forward", String.valueOf(segsFwd),
                            "reverse", String.valueOf(segsRev))));
        }
    }

    public void tick(long serverTick) {
        byPlayer.entrySet().removeIf(e -> {
            RouteRecordingSession session = e.getValue();
            if (!session.tick(serverTick)) {
                Player p = plugin.getServer().getPlayer(e.getKey());
                if (p != null && session.isAutoFinished()) {
                    finishSession(p, session, true);
                } else {
                    session.end();
                }
                return true;
            }
            return false;
        });
    }

    public void clearAll() {
        for (RouteRecordingSession s : byPlayer.values()) {
            s.end();
        }
        byPlayer.clear();
        pendingRecordingCarts.clear();
        stop();
    }

    /** 管理員 reload 前儲存並結束所有錄製 */
    public void stopAllForReload() {
        for (var entry : new ArrayList<>(byPlayer.entrySet())) {
            RouteRecordingSession session = entry.getValue();
            session.saveCurrentSegmentAndFinish();
            session.end();
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                TextUtil.send(p, plugin.getLanguageManager().get(p, "route.recording_stop_reload"));
            }
        }
        byPlayer.clear();
        pendingRecordingCarts.clear();
    }

    private String formatLineName(String lineId) {
        if (lineId == null) return "?";
        Line line = plugin.getLineManager().getLine(lineId);
        return line != null ? line.getDisplayName() : lineId;
    }

    private static int countAllSegmentPoints(Line line) {
        int total = 0;
        for (int i = 0; i < line.getSegmentCount(); i++) {
            total += line.getSegmentPoints(i, TravelDirection.FORWARD).size();
            total += line.getSegmentPoints(i, TravelDirection.REVERSE).size();
        }
        return total;
    }
}
