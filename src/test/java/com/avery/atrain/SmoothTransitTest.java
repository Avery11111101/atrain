package com.avery.atrain;

import com.avery.atrain.util.RailPathSampler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SmoothTransitTest {

    public static void main(String[] args) {
        SmoothTransitTest test = new SmoothTransitTest();
        test.testBoundaries();
        test.testMonotonicity();
        test.testNonZeroInitialVelocity();
        System.out.println("ALL SMOOTH TRANSIT TESTS PASSED SUCCESSFULLY!");
    }

    @Test
    @DisplayName("測試平滑過渡邊界值 (t=0 與 t=1)")
    void testBoundaries() {
        assertEquals(0.0, RailPathSampler.smoothTransit(0.0), 1e-6);
        assertEquals(1.0, RailPathSampler.smoothTransit(1.0), 1e-6);
        assertEquals(0.0, RailPathSampler.smoothTransit(-0.5), 1e-6);
        assertEquals(1.0, RailPathSampler.smoothTransit(1.5), 1e-6);
    }

    @Test
    @DisplayName("測試嚴格單調遞增（無回退抖動）")
    void testMonotonicity() {
        double last = -1.0;
        int steps = 1000;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double val = RailPathSampler.smoothTransit(t);
            assertTrue(val >= last, "值應隨 t 增加而嚴格遞增: t=" + t + ", val=" + val + ", last=" + last);
            last = val;
        }
    }

    @Test
    @DisplayName("測試起步與靠站具備平滑可見初速，而非停滯")
    void testNonZeroInitialVelocity() {
        // 在 t=0.01 時（第 6 tick / 30 秒旅程），純 easeInOut 僅 0.0002，smoothTransit 應有至少 0.0015
        double pureEase = RailPathSampler.easeInOut(0.01);
        double smooth = RailPathSampler.smoothTransit(0.01);
        assertTrue(smooth > pureEase, "smoothTransit 應提供顯著優於純二次方之平滑起步速度");
        assertTrue(smooth >= 0.0015, "起步速度基底應保證每 tick 移動不為 0");
    }
}
