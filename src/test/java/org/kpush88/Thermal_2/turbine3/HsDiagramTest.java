package org.kpush88.Thermal_2.turbine3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HsDiagramTest {

    private final HsDiagram hs = new HsDiagram();

    @Test
    @DisplayName("энтальпия свежего пара близка к значению методички")
    void enthalpyMatchesReference() {
        assertEquals(3302.52, hs.enthalpyPT(2.716, 430.0), 2.0);
    }

    @Test
    @DisplayName("располагаемый теплоперепад совпадает с методичкой")
    void isentropicDropMatchesReference() {
        double h0 = hs.enthalpyPT(2.716, 430.0);
        assertEquals(1161.6, hs.isentropicDrop(2.716, h0, 0.0044), 1.0);
    }

    @Test
    @DisplayName("обратная задача по перепаду даёт давление выхлопа")
    void pressureForIsentropicDropInvertsDrop() {
        double h0 = hs.enthalpyPT(2.716, 430.0);
        double pressure = hs.pressureForIsentropicDrop(2.716, h0, 1161.6);
        assertEquals(0.0044, pressure, 1.0e-3);
    }

    @Test
    @DisplayName("влажный пар распознаётся и имеет степень сухости в двухфазной области")
    void wetPointClassifiedCorrectly() {
        double x = hs.vapourFractionPH(0.004, 2148.5);
        assertTrue(x > 0.8 && x < 0.9, "x = " + x);
        assertTrue(hs.isWet(0.004, 2148.5));
        assertTrue(hs.pointPH(0.004, 2148.5).isWet());
    }
}
