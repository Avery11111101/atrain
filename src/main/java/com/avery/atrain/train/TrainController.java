package com.avery.atrain.train;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.RailUtil;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 列車控制核心（每 tick 執行）：
 * <ul>
 *   <li>自動偵測相鄰礦車 → 整條相連鏈連結成一列（train_max_cars 為建議節數）</li>
 *   <li>巡航時全速 {@code cart_speed}；接近轉彎 / 上下坡 / 非直線時降到 {@code curve_speed}</li>
 *   <li>{@code auto_stop} 開啟時整列一起進站停靠，dwell 時間到整列一起自動往前發車</li>
 * </ul>
 * {@code train_control} 關閉時完全不介入，回歸原版物理。
 */
public class TrainController {

    private final AtrainPlugin plugin;
    private BukkitTask task;
    private long tick;

    /** 停靠中：cartId -> 可發車的 tick */
    private final Map<UUID, Long> dwellUntil = new HashMap<>();
    /** 發車後須先離站才會再次觸發停靠：cartId -> stopId */
    private final Map<UUID, String> mustLeave = new HashMap<>();
    /** 進站前記錄的行進朝向，供 dwell 結束後整列發車：cartId -> heading */
    private final Map<UUID, Vector> departHeading = new HashMap<>();

    public TrainController(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void clearAll() {
        restoreFrozenCarts();
        dwellUntil.clear();
        mustLeave.clear();
        departHeading.clear();
    }

    /** 解除因停靠/阻擋被凍結（maxSpeed=0）的礦車，避免停用或關閉控速後永久卡死 */
    private void restoreFrozenCarts() {
        for (World world : Bukkit.getWorlds()) {
            for (RideableMinecart cart : world.getEntitiesByClass(RideableMinecart.class)) {
                if (!cart.isValid() || cart.isDead()) continue;
                if (cart.getMaxSpeed() < 0.01) cart.setMaxSpeed(0.4);
            }
        }
    }

    private void tick() {
        if (!plugin.getConfigManager().isTrainControlEnabled()) return;
        tick++;

        List<RideableMinecart> onRail = new ArrayList<>();
        Set<UUID> alive = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (RideableMinecart cart : world.getEntitiesByClass(RideableMinecart.class)) {
                if (!cart.isValid() || cart.isDead()) continue;
                alive.add(cart.getUniqueId());
                // 路線導引系統管理中的礦車不由本控制器接管，避免搶控
                if (com.avery.atrain.train.TrainTaskRegistry.get(cart) != null) continue;
                if (RailUtil.isOnRail(cart.getLocation())) onRail.add(cart);
            }
        }

        dwellUntil.keySet().retainAll(alive);
        mustLeave.keySet().retainAll(alive);
        departHeading.keySet().retainAll(alive);

        if (onRail.isEmpty()) return;

        boolean autoStop = plugin.getConfigManager().isAutoStopEnabled();
        double cruise = plugin.getConfigManager().getCartSpeed();
        double curve = plugin.getConfigManager().getCurveSpeed();

        for (List<RideableMinecart> group : groupCarts(onRail)) {
            processGroup(group, autoStop, cruise, curve);
        }
    }

    /**
     * 依中心距離把相鄰礦車連結成一列（整條相連鏈視為同一列，不硬性切斷，
     * 避免物理相連卻分裂成互鬥的兩組）。{@code train_max_cars} 作為建議上限，
     * 超過仍會整列連動，不會拆列。
     */
    private List<List<RideableMinecart>> groupCarts(List<RideableMinecart> carts) {
        double dist = plugin.getConfigManager().getTrainCoupleDistance();
        double distSq = dist * dist;

        List<List<RideableMinecart>> groups = new ArrayList<>();
        Set<UUID> used = new HashSet<>();

        for (RideableMinecart seed : carts) {
            if (used.contains(seed.getUniqueId())) continue;
            List<RideableMinecart> group = new ArrayList<>();
            ArrayDeque<RideableMinecart> queue = new ArrayDeque<>();
            queue.add(seed);
            used.add(seed.getUniqueId());

            while (!queue.isEmpty()) {
                RideableMinecart cur = queue.poll();
                group.add(cur);
                for (RideableMinecart other : carts) {
                    if (used.contains(other.getUniqueId())) continue;
                    if (!other.getWorld().equals(cur.getWorld())) continue;
                    if (cur.getLocation().distanceSquared(other.getLocation()) <= distSq
                            && coupled(cur, other)) {
                        used.add(other.getUniqueId());
                        queue.add(other);
                    }
                }
            }
            groups.add(group);
        }
        return groups;
    }

    /**
     * 兩節礦車是否真的「沿軌前後相鄰」可連結。
     * 用兩車連線方向是否與軌道切線平行來判定，藉此排除並排平行軌的側向誤併。
     */
    private boolean coupled(RideableMinecart a, RideableMinecart b) {
        Vector between = b.getLocation().toVector().subtract(a.getLocation().toVector());
        between.setY(0);
        if (between.lengthSquared() < 0.01) return true; // 幾乎重疊視為相連
        between.normalize();
        for (Vector rd : RailUtil.getRailDirections(a.getLocation())) {
            Vector r = rd.clone();
            r.setY(0);
            if (r.lengthSquared() < 0.0001) continue;
            r.normalize();
            if (Math.abs(r.dot(between)) > 0.6) return true; // 落在軌道前後方向上
        }
        return false;
    }

    private void processGroup(List<RideableMinecart> group, boolean autoStop, double cruise, double curve) {
        // 逐車清除 mustLeave：離開該站的車廂即解除封鎖（不論 auto_stop 狀態）
        for (RideableMinecart c : group) {
            String ms = mustLeave.get(c.getUniqueId());
            if (ms == null) continue;
            Stop s = plugin.getStopManager().getStop(ms);
            if (s == null || !s.containsRail(c.getLocation())) {
                mustLeave.remove(c.getUniqueId());
            }
        }

        long releaseAt = Long.MIN_VALUE;
        boolean dwelling = false;
        for (RideableMinecart c : group) {
            Long until = dwellUntil.get(c.getUniqueId());
            if (until != null) {
                dwelling = true;
                releaseAt = Math.max(releaseAt, until);
            }
        }

        if (dwelling) {
            if (tick >= releaseAt) {
                Vector heading = groupDepartHeading(group);
                Stop stop = firstStopRail(group);
                for (RideableMinecart c : group) {
                    dwellUntil.remove(c.getUniqueId());
                    if (stop != null) mustLeave.put(c.getUniqueId(), stop.getId());
                }
                notifyPassengers(group, "train.departing", null);
                depart(group, heading, cruise, curve);
            } else {
                freeze(group);
                if (tick % 10 == 0) {
                    int secs = (int) Math.ceil(Math.max(releaseAt - tick, 0) / 20.0);
                    notifyPassengers(group, "train.dwelling", String.valueOf(secs));
                }
            }
            return;
        }

        Stop stop = stopAtHead(group);
        if (autoStop && stop != null) {
            boolean blocked = false;
            for (RideableMinecart c : group) {
                if (stop.getId().equals(mustLeave.get(c.getUniqueId()))) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked && isMoving(group)) {
                int dwell = stop.getDwellTimeTicks();
                if (dwell > 0) {
                    Vector heading = currentHeading(group);
                    long until = tick + dwell;
                    for (RideableMinecart c : group) {
                        dwellUntil.put(c.getUniqueId(), until);
                        departHeading.put(c.getUniqueId(), heading.clone());
                    }
                    freeze(group);
                    return;
                }
                for (RideableMinecart c : group) {
                    mustLeave.put(c.getUniqueId(), stop.getId());
                }
            }
        }

        cruise(group, cruise, curve);
    }

    private void notifyPassengers(List<RideableMinecart> group, String key, String secValue) {
        for (RideableMinecart c : group) {
            for (Entity e : c.getPassengers()) {
                if (!(e instanceof Player p)) continue;
                String msg = secValue != null
                        ? plugin.getLanguageManager().get(p, key, Map.of("sec", secValue))
                        : plugin.getLanguageManager().get(p, key);
                p.sendActionBar(TextUtil.component(msg));
            }
        }
    }

    private void freeze(List<RideableMinecart> group) {
        for (RideableMinecart c : group) {
            c.setVelocity(new Vector(0, 0, 0));
            c.setMaxSpeed(0);
        }
    }

    private void depart(List<RideableMinecart> group, Vector heading, double cruise, double curve) {
        // 整列統一發車速度：依車頭與是否進入彎道/坡道決定
        RideableMinecart front = frontCart(group, heading);
        boolean slow = false;
        for (RideableMinecart c : group) {
            if (RailUtil.isCurveOrSlopeRail(c.getLocation())) {
                slow = true;
                break;
            }
        }
        if (!slow && front != null && RailUtil.curveOrSlopeAhead(front.getLocation(), heading, plugin.getConfigManager().getCurveLookahead())) {
            slow = true;
        }
        double speed = slow ? curve : cruise;

        for (RideableMinecart c : group) {
            Vector dir = RailUtil.pickBestRailDirection(c.getLocation(), heading);
            if (dir == null || dir.lengthSquared() < 0.0001) {
                Vector flat = heading.clone();
                flat.setY(0);
                dir = flat.lengthSquared() > 0.0001 ? flat.normalize() : null;
            }
            c.setMaxSpeed(Math.max(speed, 0.1));
            if (dir != null) {
                // 爬升軌不強制 Y，交由原版軌道物理依行進方向決定上/下坡，避免下坡上飄
                c.setVelocity(dir.clone().multiply(speed));
            }
            departHeading.remove(c.getUniqueId());
        }
    }

    private boolean isMoving(List<RideableMinecart> group) {
        for (RideableMinecart c : group) {
            Vector v = c.getVelocity();
            if (v.getX() * v.getX() + v.getZ() * v.getZ() > 0.0004) return true;
        }
        return false;
    }

    private void cruise(List<RideableMinecart> group, double cruise, double curve) {
        if (!isMoving(group)) {
            // 靜止列車不主動起步，只確保 maxSpeed 可被推動
            for (RideableMinecart c : group) c.setMaxSpeed(Math.max(cruise, 0.4));
            return;
        }
        Vector heading = currentHeading(group);

        boolean slow = false;
        for (RideableMinecart c : group) {
            if (RailUtil.isCurveOrSlopeRail(c.getLocation())) {
                slow = true;
                break;
            }
        }
        RideableMinecart front = frontCart(group, heading);
        if (!slow && front != null && RailUtil.curveOrSlopeAhead(front.getLocation(), heading, plugin.getConfigManager().getCurveLookahead())) {
            slow = true;
        }

        int obsLook = Math.max(1, (int) Math.ceil(plugin.getConfigManager().getObstructionDistance()));
        boolean obstructed = plugin.getConfigManager().isObstructionCheck()
                && front != null
                && RailUtil.isRailPathBlocked(front.getLocation(), heading, obsLook);
        if (obstructed) {
            freeze(group);
            return;
        }

        double target = slow ? curve : cruise;
        for (RideableMinecart c : group) {
            c.setMaxSpeed(Math.max(target, 0.1));
            // 爬升軌交給原版物理處理 Y 分量，僅限制速度，避免抹平上坡動量
            if (RailUtil.isAscendingRail(c.getLocation())) continue;

            Vector v = c.getVelocity().clone();
            v.setY(0);
            Vector basis = v.lengthSquared() > 0.0001 ? v : heading;
            Vector dir = RailUtil.pickContinuationDirection(c.getLocation(), basis);
            if (dir != null && dir.lengthSquared() > 0.0001) {
                c.setVelocity(dir.clone().multiply(target));
            }
        }
    }

    private Stop firstStopRail(List<RideableMinecart> group) {
        for (RideableMinecart c : group) {
            Stop s = plugin.getStopManager().getStopAtRail(c.getLocation());
            if (s != null) return s;
        }
        return null;
    }

    /** 以行進方向的車頭優先判定所在站點，避免長編組用錯車廂觸發停靠 */
    private Stop stopAtHead(List<RideableMinecart> group) {
        RideableMinecart front = frontCart(group, currentHeading(group));
        if (front != null) {
            Stop s = plugin.getStopManager().getStopAtRail(front.getLocation());
            if (s != null) return s;
        }
        return firstStopRail(group);
    }

    private Vector currentHeading(List<RideableMinecart> group) {
        Vector sum = new Vector();
        for (RideableMinecart c : group) {
            Vector v = c.getVelocity().clone();
            v.setY(0);
            if (v.lengthSquared() > 0.0001) sum.add(v);
        }
        if (sum.lengthSquared() > 0.0001) return sum.normalize();
        for (RideableMinecart c : group) {
            Vector h = departHeading.get(c.getUniqueId());
            if (h != null && h.lengthSquared() > 0.0001) return h.clone().normalize();
        }
        for (RideableMinecart c : group) {
            List<Vector> dirs = RailUtil.getRailDirections(c.getLocation());
            if (!dirs.isEmpty()) return dirs.get(0).clone();
        }
        return new Vector();
    }

    private Vector groupDepartHeading(List<RideableMinecart> group) {
        for (RideableMinecart c : group) {
            Vector h = departHeading.get(c.getUniqueId());
            if (h != null && h.lengthSquared() > 0.0001) return h.clone();
        }
        return currentHeading(group);
    }

    private RideableMinecart frontCart(List<RideableMinecart> group, Vector heading) {
        RideableMinecart front = null;
        double best = Double.NEGATIVE_INFINITY;
        for (RideableMinecart c : group) {
            double proj = c.getLocation().toVector().dot(heading);
            if (proj > best) {
                best = proj;
                front = c;
            }
        }
        return front;
    }
}
