package com.avery.atrain.hangrail;

import com.avery.atrain.AtrainPlugin;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

/**
 * 懸浮軌道物理處理：維持礦車懸掛於鐵欄杆等方塊下方/上方行駛。
 */
public class HangRailHandler {

    private final AtrainPlugin plugin;

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
