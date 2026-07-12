package com.avery.atrain.hangrail;

import com.avery.atrain.AtrainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 懸浮軌道物理處理：維持礦車懸掛於鐵欄杆等方塊下方/上方行駛。
 */
public class HangRailHandler {

    private final AtrainPlugin plugin;
    /** 目前被吊住（已關重力）的礦車，用於離開懸浮軌時還原重力 */
    private final Set<UUID> held = new HashSet<>();

    public HangRailHandler(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().isHangRailEnabled();
    }

    public boolean isOnHangRail(Location loc) {
        if (!isEnabled()) return false;
        return HangRailUtil.findHangRail(loc, plugin.getConfigManager().getHangRailTypes()) != null;
    }

    /**
     * 每 tick 主動吊住礦車（不依賴 VehicleMoveEvent，靜止時也不會掉下去）。
     * @return 是否正被吊在懸浮軌道上
     */
    public boolean hold(Minecart cart) {
        if (!isEnabled()) return false;
        UUID id = cart.getUniqueId();
        Location loc = cart.getLocation();
        var types = plugin.getConfigManager().getHangRailTypes();
        HangRailInfo info = HangRailUtil.findHangRail(loc, types);
        if (info == null) {
            if (held.remove(id)) cart.setGravity(true); // 離開懸浮段 → 還原重力
            return false;
        }

        held.add(id);
        cart.setGravity(false); // 每 tick 確保關重力，避免靜止或掉落中被重力拉走

        Vector velocity = cart.getVelocity();
        double horiz = Math.hypot(velocity.getX(), velocity.getZ());
        Vector dir = HangRailUtil.pickContinuation(loc, info, velocity);

        if (dir != null && horiz > 0.02) {
            // 巡航保底：移動中維持 cart_speed，避免 off-rail 摩擦讓懸浮車逐漸停住
            double cruise = plugin.getConfigManager().getCartSpeed();
            cart.setMaxSpeed(Math.max(cruise, 0.4));
            double speed = Math.min(Math.max(horiz, cruise), cart.getMaxSpeed());
            Vector newVel = dir.clone().multiply(speed);
            BlockFace slope = HangRailUtil.findSlope(info.getRailBlock(), info.getType());
            if (slope != null) {
                double yVel = info.getType().isBelowRail() ? -0.08 : 0.08;
                if (slope == BlockFace.NORTH || slope == BlockFace.WEST) yVel = -yVel;
                newVel.setY(yVel);
            } else {
                newVel.setY(0);
            }
            cart.setVelocity(newVel);
        } else {
            cart.setVelocity(new Vector(0, 0, 0)); // 靜止：僅吊住，不漂移
        }

        // 高度吸附：確保維持吊掛高度（gravity 若被忽略時的保險）。
        // 有乘客時避免每 tick teleport 造成抖動，改用較大門檻。
        Location anchor = (dir != null && horiz > 0.02)
                ? HangRailUtil.snapLocation(loc, info, dir)
                : HangRailUtil.getCartAnchor(info);
        double threshold = cart.getPassengers().isEmpty() ? 0.02 : 0.16;
        if (loc.distanceSquared(anchor) > threshold) {
            cart.teleport(anchor);
        }
        return true;
    }

    /** 還原所有被吊住礦車的重力（停用/reload/關閉懸浮軌時呼叫） */
    public void restoreAll() {
        for (UUID id : held) {
            if (Bukkit.getEntity(id) instanceof Minecart m && m.isValid()) {
                m.setGravity(true);
            }
        }
        held.clear();
    }

    /** @return 是否正在懸浮軌道上 */
    public boolean handleMove(Minecart cart, Location from, Location to) {
        if (!isEnabled()) return false;

        var types = plugin.getConfigManager().getHangRailTypes();
        HangRailInfo info = HangRailUtil.findHangRail(to, types);
        if (info == null) {
            HangRailInfo fromInfo = HangRailUtil.findHangRail(from, types);
            if (fromInfo != null) {
                cart.setGravity(true);
            }
            return false;
        }

        cart.setGravity(false);

        Vector velocity = cart.getVelocity();
        Vector dir = HangRailUtil.pickContinuation(to, info, velocity);
        if (dir == null) return true;

        Location anchor = HangRailUtil.snapLocation(to, info, dir);

        double speed = Math.max(velocity.length(), plugin.getConfigManager().getMinCruiseSpeed());
        speed = Math.min(speed, cart.getMaxSpeed());
        if (speed < 0.01 && cart.getMaxSpeed() > 0) {
            speed = Math.min(cart.getMaxSpeed(), plugin.getConfigManager().getCartSpeed() * 0.5);
        }

        Vector newVel = dir.clone().multiply(speed);
        BlockFace slope = HangRailUtil.findSlope(info.getRailBlock(), info.getType());
        if (slope != null) {
            double yVel = info.getType().isBelowRail() ? -0.08 : 0.08;
            if (slope == BlockFace.NORTH || slope == BlockFace.WEST) {
                yVel = -yVel;
            }
            newVel.setY(yVel);
        } else {
            newVel.setY(0);
        }

        cart.setVelocity(newVel);

        if (to.distanceSquared(anchor) > 0.04) {
            cart.teleport(anchor);
        }
        return true;
    }
}
