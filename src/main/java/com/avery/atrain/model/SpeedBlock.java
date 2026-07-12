package com.avery.atrain.model;

/**
 * 礦車調速方塊：埋在鐵軌正下方的鑽石塊。
 * 礦車經過其上方軌道時，會在 {@code ramp} 格內平滑加速/減速到 {@code speed}。
 */
public class SpeedBlock {

    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private double speed;
    private int ramp;

    public SpeedBlock(String world, int x, int y, int z, double speed, int ramp) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.speed = speed;
        this.ramp = ramp;
    }

    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
    public int getRamp() { return ramp; }
    public void setRamp(int ramp) { this.ramp = ramp; }

    public String key() { return key(world, x, y, z); }

    /** 全域唯一鍵：world@x,y,z（同時作為聊天輸入的 contextId） */
    public static String key(String world, int x, int y, int z) {
        return world + "@" + x + "," + y + "," + z;
    }
}
