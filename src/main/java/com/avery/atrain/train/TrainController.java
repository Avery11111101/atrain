package com.avery.atrain.train;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.SpeedBlock;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.RailUtil;
import com.avery.atrain.util.StationUtil;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
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
    /** 上車發車去重：已排程待發的 cartId */
    private final Set<UUID> launching = new HashSet<>();
    /** 列車群組持久速度狀態（TrainCarts 風格 speedLimit + motionSpeed） */
    private final Map<String, TrainGroupState> groupStates = new HashMap<>();

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
        launching.clear();
        groupStates.clear();
    }

    /**
     * 玩家坐上礦車 → 自動發車（往玩家面向的軌道方向）。
     * 下一 tick 才施加速度，確保乘客已完成上車；同一台同 tick 去重。
     * 路線導引管理中的礦車不介入。
     */
    public void onBoard(RideableMinecart cart, Player player) {
        if (!plugin.getConfigManager().isTrainControlEnabled()) return;
        if (TrainTaskRegistry.get(cart) != null) return; // 路線導引接管中，不搶控
        if (!launching.add(cart.getUniqueId())) return;   // 同 tick 多人上車去重
        Bukkit.getScheduler().runTask(plugin, () -> {
            launching.remove(cart.getUniqueId());
            launch(cart, player);
        });
    }

    private void launch(RideableMinecart cart, Player player) {
        if (!cart.isValid() || cart.isDead()) return;
        if (!cart.getPassengers().contains(player)) return; // 已下車就不發
        if (!RailUtil.isOnRail(cart.getLocation())) return;
        if (dwellUntil.containsKey(cart.getUniqueId())) return; // 停靠倒數中不插隊
        // 已在移動中不覆寫速度：避免行駛中上車造成急轉 / 倒車
        Vector cv = cart.getVelocity();
        if (cv.getX() * cv.getX() + cv.getZ() * cv.getZ() > 0.0064) return;

        Vector facing = player.getLocation().getDirection();
        facing.setY(0);
        Vector dir = facing.lengthSquared() > 0.0001
                ? RailUtil.pickBestRailDirection(cart.getLocation(), facing)
                : null;
        if (dir == null || dir.lengthSquared() < 0.0001) {
            List<Vector> dirs = RailUtil.getRailDirections(cart.getLocation());
            if (dirs.isEmpty()) return;
            dir = dirs.get(0);
        }

        // 從站點發車：對整列（coupling 群組）標記 mustLeave，避免站內發車立刻又被
        // auto_stop 停回（含車頭超出月台金磚的長編組）。
        List<RideableMinecart> group = groupContaining(cart);
        Stop groupStop = null;
        for (RideableMinecart c : group) {
            Stop s = plugin.getStopManager().getStopAtRail(c.getLocation());
            if (s != null) { groupStop = s; break; }
        }
        if (groupStop != null) {
            for (RideableMinecart c : group) mustLeave.put(c.getUniqueId(), groupStop.getId());
        }

        double cruise = plugin.getConfigManager().getCartSpeed();
        double curve = plugin.getConfigManager().getCurveSpeed();
        TrainGroupState state = getOrCreateState(group, cruise);
        Vector heading = dir.clone();
        boolean slow = isSlowSection(group, heading, cart);
        double speed = computeEffectiveTarget(group, state, slow, cruise, curve, heading);
        state.setSpeedLimit(speed);
        state.setMotionSpeed(speed);

        cart.setMaxSpeed(Math.max(speed, 0.1));
        Vector moveDir = RailUtil.pickForwardDirection(cart.getLocation(), dir);
        if (moveDir == null) moveDir = RailUtil.getRailMovementVector(cart.getLocation(), dir);
        if (moveDir == null) moveDir = dir.clone().normalize();
        state.updateStableHeading(moveDir);
        cart.setVelocity(moveDir.multiply(speed));
    }

    /** 找出包含指定礦車的連結群組（重用 groupCarts，供上車發車時整列標記） */
    private List<RideableMinecart> groupContaining(RideableMinecart seed) {
        List<RideableMinecart> onRail = new ArrayList<>();
        for (RideableMinecart c : seed.getWorld().getEntitiesByClass(RideableMinecart.class)) {
            if (!c.isValid() || c.isDead()) continue;
            if (!plugin.isManagedCart(c)) continue;
            if (TrainTaskRegistry.get(c) != null) continue;
            if (RailUtil.isOnRail(c.getLocation())) onRail.add(c);
        }
        for (List<RideableMinecart> g : groupCarts(onRail)) {
            for (RideableMinecart c : g) {
                if (c.getUniqueId().equals(seed.getUniqueId())) return g;
            }
        }
        List<RideableMinecart> single = new ArrayList<>();
        single.add(seed);
        return single;
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
                if (!plugin.isManagedCart(cart)) continue;
                alive.add(cart.getUniqueId());
                // 路線導引系統管理中的礦車不由本控制器接管，避免搶控
                if (com.avery.atrain.train.TrainTaskRegistry.get(cart) != null) continue;
                if (plugin.getCinematicTransitManager() != null
                        && plugin.getCinematicTransitManager().isManaged(cart)) continue;
                if (plugin.getRouteRecordingManager() != null
                        && plugin.getRouteRecordingManager().isRecordingCart(cart.getUniqueId())) continue;
                if (RailUtil.isOnRail(cart.getLocation())) onRail.add(cart);
            }
        }

        dwellUntil.keySet().retainAll(alive);
        mustLeave.keySet().retainAll(alive);
        departHeading.keySet().retainAll(alive);
        groupStates.keySet().removeIf(k -> !groupStillAlive(k, alive));

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
            if (!blocked) {
                int dwell = stop.getDwellTimeTicks();
                if (dwell > 0) {
                    Vector heading = currentHeading(group);
                    long until = tick + dwell;
                    for (RideableMinecart c : group) {
                        dwellUntil.put(c.getUniqueId(), until);
                        departHeading.put(c.getUniqueId(), heading.clone());
                    }
                    freeze(group);
                    notifyPassengers(group, "train.dwelling", String.valueOf(
                            (int) Math.ceil(stop.getDwellTimeTicks() / 20.0)));
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
        RideableMinecart front = frontCart(group, heading);
        boolean slow = isSlowSection(group, heading, front);
        TrainGroupState state = getOrCreateState(group, cruise);
        double speed = computeEffectiveTarget(group, state, slow, cruise, curve, heading);
        state.setSpeedLimit(speed);
        state.setMotionSpeed(speed);

        for (RideableMinecart c : group) {
            Vector dir = RailUtil.pickForwardDirection(c.getLocation(), heading);
            if (dir == null) {
                dir = RailUtil.getRailMovementVector(c.getLocation(), heading);
            }
            if (dir == null || dir.lengthSquared() < 0.0001) {
                Vector flat = heading.clone();
                flat.setY(0);
                dir = flat.lengthSquared() > 0.0001 ? flat.normalize() : null;
            }
            c.setMaxSpeed(Math.max(speed, 0.1));
            if (dir != null) {
                c.setVelocity(dir.clone().multiply(speed));
            }
            departHeading.remove(c.getUniqueId());
        }
    }

    private boolean isSlowSection(List<RideableMinecart> group, Vector heading, RideableMinecart front) {
        if (front != null && RailUtil.isCurveOrSlopeRail(front.getLocation())) return true;
        return front != null
                && RailUtil.curveOrSlopeAhead(front.getLocation(), heading, plugin.getConfigManager().getCurveLookahead());
    }

    private String groupKey(List<RideableMinecart> group) {
        return group.stream()
                .map(c -> c.getUniqueId().toString())
                .sorted()
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
    }

    private TrainGroupState getOrCreateState(List<RideableMinecart> group, double cruise) {
        return groupStates.computeIfAbsent(groupKey(group), k -> new TrainGroupState(cruise));
    }

    private boolean groupStillAlive(String key, Set<UUID> alive) {
        if (key.isEmpty()) return false;
        for (String part : key.split("\\|")) {
            try {
                if (alive.contains(UUID.fromString(part))) return true;
            } catch (IllegalArgumentException ignored) {}
        }
        return false;
    }

    /**
     * 車頭進入新軌道格時更新 speedLimit（TrainCarts onBlockChange 簡化版）。
     */
    private void updateSpeedLimitOnBlockChange(RideableMinecart front, TrainGroupState state, double cruise, Vector heading) {
        Block rail = RailUtil.findRailBlock(front.getLocation());
        String key = RailUtil.blockKey(rail);
        if (key == null || key.equals(state.getLastRailKey())) return;
        state.setLastRailKey(key);

        SpeedBlock sb = plugin.getSpeedBlockManager().getUnderRail(front.getLocation());
        if (sb != null) {
            state.setSpeedLimit(sb.getSpeed());
            return;
        }
        state.setSpeedLimit(cruise);
    }

    /**
     * 計算本 tick 目標速度：調速方塊 > 彎道 > 充能軌 > 巡航；含 ramp 提前過渡。
     */
    private double computeEffectiveTarget(List<RideableMinecart> group, TrainGroupState state,
                                          boolean slow, double cruise, double curve, Vector heading) {
        RideableMinecart front = frontCart(group, heading);
        if (front != null) {
            updateSpeedLimitOnBlockChange(front, state, cruise, heading);
        }

        double limit = state.getSpeedLimit();
        SpeedBlock onBlock = null;
        for (RideableMinecart c : group) {
            SpeedBlock sb = plugin.getSpeedBlockManager().getUnderRail(c.getLocation());
            if (sb != null) {
                onBlock = sb;
                limit = sb.getSpeed();
                break;
            }
        }

        if (onBlock == null && front != null) {
            SpeedBlockAhead ahead = findSpeedBlockAhead(front.getLocation(), heading);
            if (ahead != null && ahead.sb.getRamp() > 0 && ahead.distance <= ahead.sb.getRamp()) {
                double base = state.getSpeedLimit();
                double t = 1.0 - (ahead.distance / (double) ahead.sb.getRamp());
                limit = base + (ahead.sb.getSpeed() - base) * Math.min(1.0, t);
            }
        }

        if (onBlock != null) return limit;

        if (slow) {
            double ratio = plugin.getConfigManager().getCurveSpeedRatio();
            double scaled = Math.max(curve, limit * ratio);
            return Math.min(limit, scaled);
        }
        if (plugin.getConfigManager().isPoweredBoost() && onPoweredRail(group)) {
            return Math.max(limit, plugin.getConfigManager().getBoostSpeed());
        }
        return limit;
    }

    private void applyMotion(List<RideableMinecart> group, double motionSpeed, Vector heading, TrainGroupState state) {
        Vector basis = state.resolveHeading(heading);
        if (basis.lengthSquared() > 0.01) {
            state.updateStableHeading(basis);
        }

        for (RideableMinecart c : group) {
            double cap = Math.max(motionSpeed, 0.1);
            c.setMaxSpeed(cap);

            if (RailUtil.isCurveRail(c.getLocation())) {
                // 彎道格：不每 tick 硬蓋 velocity，避免與原版轉彎物理打架造成抽動
                double horiz = Math.hypot(c.getVelocity().getX(), c.getVelocity().getZ());
                double minPush = Math.max(0.25, cap * 0.5);
                if (horiz < minPush) {
                    Vector dir = RailUtil.pickForwardDirection(c.getLocation(), basis);
                    if (dir != null && dir.lengthSquared() > 0.0001) {
                        c.setVelocity(dir.clone().normalize().multiply(minPush));
                    }
                }
                continue;
            }

            Vector dir = RailUtil.getRailMovementVector(c.getLocation(), basis);
            if (dir == null) {
                dir = RailUtil.pickForwardDirection(c.getLocation(), basis);
            }
            if (dir != null && dir.lengthSquared() > 0.0001) {
                c.setVelocity(dir.clone().multiply(cap));
            }
        }
    }

    private record SpeedBlockAhead(SpeedBlock sb, int distance) {}

    /** 沿真實軌道路徑往前找最近的軌下調速方塊 */
    private SpeedBlockAhead findSpeedBlockAhead(org.bukkit.Location loc, Vector direction) {
        Block start = RailUtil.findRailBlock(loc);
        if (start == null || direction == null || direction.lengthSquared() < 0.001) return null;
        List<Block> path = RailUtil.walkRailPath(start, direction, 20);
        for (int i = 0; i < path.size(); i++) {
            Block rail = path.get(i);
            Block diamond = StationUtil.findSpeedBlockBelow(rail);
            if (diamond != null) {
                SpeedBlock sb = plugin.getSpeedBlockManager().resolveAt(diamond);
                if (sb != null) return new SpeedBlockAhead(sb, i + 1);
            }
        }
        return null;
    }

    /** 列車是否有任一節正踩在「已通電的充能鐵軌」上 */
    private boolean onPoweredRail(List<RideableMinecart> group) {
        for (RideableMinecart c : group) {
            if (RailUtil.isOnActivePoweredRail(c.getLocation())) return true;
        }
        return false;
    }

    private boolean isMoving(List<RideableMinecart> group) {
        for (RideableMinecart c : group) {
            Vector v = c.getVelocity();
            if (v.getX() * v.getX() + v.getZ() * v.getZ() > 0.0004) return true;
        }
        return false;
    }

    private void cruise(List<RideableMinecart> group, double cruise, double curve) {
        TrainGroupState state = getOrCreateState(group, cruise);
        Vector heading = state.resolveHeading(currentHeading(group));
        RideableMinecart front = frontCart(group, heading);
        if (heading.lengthSquared() > 0.01) {
            state.updateStableHeading(heading);
        }
        boolean slow = isSlowSection(group, heading, front);

        double target = computeEffectiveTarget(group, state, slow, cruise, curve, heading);
        double accel = plugin.getConfigManager().getSpeedBlockAcceleration();

        if (!isMoving(group)) {
            state.approachTarget(target, accel);
            if (state.getMotionSpeed() < 0.02 && target > 0.05) {
                state.setMotionSpeed(target);
            }
            applyMotion(group, state.getMotionSpeed(), heading, state);
            return;
        }

        int obsLook = Math.max(1, (int) Math.ceil(plugin.getConfigManager().getObstructionDistance()));
        boolean obstructed = plugin.getConfigManager().isObstructionCheck()
                && front != null
                && RailUtil.isRailPathBlocked(front.getLocation(), heading, obsLook);
        if (obstructed) {
            freeze(group);
            state.setMotionSpeed(0);
            return;
        }

        state.approachTarget(target, accel);
        applyMotion(group, state.getMotionSpeed(), heading, state);
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
