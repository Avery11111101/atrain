package com.avery.atrain.train;

import org.bukkit.util.Vector;

/**
 * 列車群組的持久移動狀態（參考 TrainCarts 的 speedLimit + motionSpeed 分離）。
 * speedLimit 由進入的軌道方塊（調速鑽石塊）更新；motionSpeed 每 tick 平滑逼近目標。
 */
public final class TrainGroupState {

    /** 本列目前速度上限（方塊/tick） */
    private double speedLimit;
    /** 實際施加的速度（平滑過渡中） */
    private double motionSpeed;
    /** 車頭上次所在的軌道方塊鍵，用於偵測進入新格 */
    private String lastRailKey = "";
    /** 直線段鎖定的行進方向，轉彎時避免分叉來回翻轉 */
    private Vector stableHeading;

    public TrainGroupState(double initialLimit) {
        this.speedLimit = initialLimit;
        this.motionSpeed = initialLimit;
    }

    public double getSpeedLimit() { return speedLimit; }
    public void setSpeedLimit(double speedLimit) { this.speedLimit = Math.max(0.05, speedLimit); }

    public double getMotionSpeed() { return motionSpeed; }
    public void setMotionSpeed(double motionSpeed) { this.motionSpeed = Math.max(0, motionSpeed); }

    public String getLastRailKey() { return lastRailKey; }
    public void setLastRailKey(String lastRailKey) { this.lastRailKey = lastRailKey != null ? lastRailKey : ""; }

    public Vector getStableHeading() { return stableHeading; }

    public void updateStableHeading(Vector heading) {
        if (heading != null && heading.lengthSquared() > 0.01) {
            this.stableHeading = heading.clone().setY(0).normalize();
        }
    }

    /** 轉彎時優先使用鎖定方向，避免速度趨近 0 時分叉抖動 */
    public Vector resolveHeading(Vector liveHeading) {
        if (liveHeading != null && liveHeading.lengthSquared() > 0.01) {
            return liveHeading.clone();
        }
        return stableHeading != null ? stableHeading.clone() : new Vector();
    }

    /** 朝目標速度平滑加減速（TrainCarts Launcher 簡化版） */
    public void approachTarget(double target, double accelPerTick) {
        if (motionSpeed < target) {
            motionSpeed = Math.min(target, motionSpeed + accelPerTick);
        } else if (motionSpeed > target) {
            motionSpeed = Math.max(target, motionSpeed - accelPerTick);
        }
    }
}
