package com.avery.atrain.cinematic;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.model.TravelDirection;
import com.avery.atrain.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 管理所有進行中的站點導引列車 */
public final class CinematicTransitManager {

    private final AtrainPlugin plugin;
    private final Map<UUID, CinematicTransitTask> tasks = new ConcurrentHashMap<>();
    private BukkitTask ticker;

    public CinematicTransitManager(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    public void clearAll() {
        for (CinematicTransitTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }

    public boolean isManaged(UUID cartId) {
        return tasks.containsKey(cartId);
    }

    public boolean isManaged(RideableMinecart cart) {
        return cart != null && tasks.containsKey(cart.getUniqueId());
    }

    /** 玩家上車：若在已註冊站點且有所屬路線，啟動導引模式；否則回傳 false */
    public boolean onBoard(RideableMinecart cart, Player player) {
        if (cart == null || player == null) return false;
        if (tasks.containsKey(cart.getUniqueId())) return true;
        if (plugin.getRouteRecordingManager() != null
                && plugin.getRouteRecordingManager().isRecordingCart(cart.getUniqueId())) {
            return false;
        }

        Stop stop = plugin.getStopManager().getStopAtRail(cart.getLocation());
        if (stop == null) {
            return false;
        }

        Line line = plugin.getStopManager().resolveDisplayLine(stop, cart.getLocation());
        if (line == null && !stop.getLineIds().isEmpty()) {
            line = plugin.getLineManager().getLine(stop.getLineIds().get(0));
        }
        if (line == null) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "cinematic.no_line"));
            return false;
        }

        TravelDirection direction = resolveTravelDirection(stop, cart.getLocation(), line);
        if (line.getNextStopId(stop.getId(), direction) == null) {
            TextUtil.send(player, plugin.getLanguageManager().get(player, "ride.terminus"));
            plugin.getServer().getScheduler().runTask(plugin, () -> cart.removePassenger(player));
            return true;
        }

        CinematicTransitTask task = new CinematicTransitTask(plugin, cart, player, stop, line, direction);
        tasks.put(cart.getUniqueId(), task);
        task.beginDwell();
        return true;
    }

    private void tickAll() {
        tasks.entrySet().removeIf(e -> {
            CinematicTransitTask task = e.getValue();
            if (!task.isPassengerRiding()) {
                task.cancel();
                return true;
            }
            task.tick();
            return false;
        });
    }

    public void cancelCart(UUID cartId) {
        CinematicTransitTask task = tasks.remove(cartId);
        if (task != null) task.cancel();
    }

    /** 回程月台：同線反向；獨立回程路線則用該線去程軌跡 */
    private TravelDirection resolveTravelDirection(Stop stop, org.bukkit.Location at, Line line) {
        if (stop == null || at == null || line == null) return TravelDirection.FORWARD;
        boolean onReturn = stop.isOnReturnPlatform(at);
        boolean onForward = stop.isOnForwardPlatform(at);
        if (!onReturn || onForward) return TravelDirection.FORWARD;
        String returnLineId = stop.getReturnLineId();
        if (returnLineId != null) {
            Line returnLine = plugin.getLineManager().getLine(returnLineId);
            if (returnLine != null && returnLine.getId().equals(line.getId())) {
                return TravelDirection.REVERSE;
            }
            return TravelDirection.FORWARD;
        }
        return TravelDirection.REVERSE;
    }
}
