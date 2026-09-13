package com.avery.atrain;

import com.avery.atrain.util.RailPathSampler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SmoothTransitTest {

    private static void checkTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static void checkEquals(double expected, double actual, double eps) {
        if (Math.abs(expected - actual) > eps) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    public static void main(String[] args) {
        SmoothTransitTest test = new SmoothTransitTest();
        test.testBoundaries();
        test.testMonotonicity();
        test.testNonZeroInitialVelocity();
        test.testMiddleCruisingSpeed();
        System.out.println("ALL SMOOTH TRANSIT TESTS PASSED SUCCESSFULLY!");
    }

    @Test
    @DisplayName("測試平滑過渡邊界值 (t=0 與 t=1)")
    void testBoundaries() {
        checkEquals(0.0, RailPathSampler.smoothTransit(0.0), 1e-6);
        checkEquals(1.0, RailPathSampler.smoothTransit(1.0), 1e-6);
        checkEquals(0.0, RailPathSampler.smoothTransit(-0.5), 1e-6);
        checkEquals(1.0, RailPathSampler.smoothTransit(1.5), 1e-6);
    }

    @Test
    @DisplayName("測試嚴格單調遞增（無回退抖動）")
    void testMonotonicity() {
        double last = -1.0;
        int steps = 1000;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double val = RailPathSampler.smoothTransit(t);
            checkTrue(val >= last, "值應隨 t 增加而嚴格遞增: t=" + t + ", val=" + val + ", last=" + last);
            last = val;
        }
    }

    @Test
    @DisplayName("測試起步具備平滑非零初速，優於純二次方停滯")
    void testNonZeroInitialVelocity() {
        // 在 t=0.01 時，純 easeInOut 僅 0.0002，smoothTransit 應有顯著更高的平滑初速
        double pureEase = RailPathSampler.easeInOut(0.01);
        double smooth = RailPathSampler.smoothTransit(0.01);
        checkTrue(smooth > pureEase, "smoothTransit 應提供顯著優於純二次方之平滑起步速度");
        checkTrue(smooth >= 0.0005, "起步速度基底應保證每 tick 移動不為 0");
    }

    @Test
    @DisplayName("測試中段高速巡航區間具備強烈速度感 (>= 1.2x 平均速度)")
    void testMiddleCruisingSpeed() {
        // 在 t=0.50 時，測試瞬時斜率（微積分數值差分）
        double dt = 0.001;
        double speedAtMid = (RailPathSampler.smoothTransit(0.50 + dt) - RailPathSampler.smoothTransit(0.50 - dt)) / (2 * dt);
        checkTrue(speedAtMid >= 1.20, "中段巡航速度應達到平均速度 1.2 倍以上，確保速度感: speed=" + speedAtMid);
    }
}
