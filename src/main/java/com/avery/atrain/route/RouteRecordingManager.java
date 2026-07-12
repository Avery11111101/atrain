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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 分段路線錄製：站點到站點，到站自動接下一段 */
public final class RouteRecordingManager {

    private final AtrainPlugin plugin;
    private final Map<UUID, RouteRecordingSession> byPlayer = new HashMap<>();
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
            line.clearSegment(segmentIndex, dir);
        }

        RouteRecordingSession session = new RouteRecordingSession(plugin, player, lineId, segmentIndex, dir);
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
                RouteRecordingSession.segmentLabel(plugin, line, segmentIndex, dir)));
        return true;
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

        Line line = plugin.getLineManager().getLine(session.getLineId());
        if (line == null) return false;

        String expectedFrom = line.getSegmentFromStopId(session.getSegmentIndex(), session.getDirection());
        if (expectedFrom == null || !expectedFrom.equals(stop.getId())) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_wrong_segment_start",
                    RouteRecordingSession.segmentLabel(plugin, line, session.getSegmentIndex(), session.getDirection())));
            return false;
        }

        if (session.hasCart()) {
            RideableMinecart existing = session.getCart();
            TextUtil.send(player, plugin.getLanguageManager().get(player, "route.recording_cart_exists"));
            if (plugin.getConfigManager().isCartSpawnAutoMount() && existing != null) {
                mountNextTick(player, existing);
            }
            return true;
        }

        World world = rail.getWorld();
        if (world == null) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "error.world_not_loaded"));
            return false;
        }

        Location spawnLoc = rail.getLocation().add(0.5, 0.0625, 0.5);
        double radius = plugin.getConfigManager().getCartSpawnRadius();
        for (var entity : world.getNearbyEntities(spawnLoc, radius, radius, radius)) {
            if (!(entity instanceof Minecart) || !entity.isValid() || entity.isDead()) continue;
            TextUtil.send(player, plugin.getLanguageManager().get(player, "cart.already_nearby"));
            if (entity instanceof RideableMinecart rideable
                    && plugin.getConfigManager().isCartSpawnAutoMount()) {
                plugin.getCartSpawnManager().cancelDespawn(rideable.getUniqueId());
                session.attachCart(rideable, stop);
                mountNextTick(player, rideable);
            }
            return false;
        }

        RideableMinecart cart = world.spawn(spawnLoc, RideableMinecart.class, entity -> entity.setGravity(true));
        session.attachCart(cart, stop);
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
            int pts = line != null ? line.getRoutePoints().size() : 0;
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
        stop();
    }

    private String formatLineName(String lineId) {
        if (lineId == null) return "?";
        Line line = plugin.getLineManager().getLine(lineId);
        return line != null ? line.getDisplayName() : lineId;
    }
}
