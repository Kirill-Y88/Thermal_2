package org.kpush88.Thermal_2.turbine3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointTest {

    private static final Point WET = new Point(
            0.004, 28.96, 2148.5, 7.0900, 34.8000, 0.8334);

    @Test
    @DisplayName("Point хранит все параметры точки h—s диаграммы")
    void storesHsDiagramParameters() {
        assertEquals(0.004, WET.pressureMPa(), 1e-12);
        assertEquals(28.96, WET.temperatureC(), 1e-12);
        assertEquals(2148.5, WET.enthalpyKJkg(), 1e-12);
        assertEquals(7.0900, WET.entropyKJkgK(), 1e-12);
        assertEquals(34.8000, WET.volumeM3kg(), 1e-12);
        assertEquals(0.8334, WET.vapourFraction(), 1e-12);
    }

    @Test
    @DisplayName("Влажный пар: y = 1 − x; сухой пар имеет x = 1")
    void classifiesWetAndDrySteam() {
        assertTrue(WET.isWet());
        assertFalse(WET.isSuperheated());
        assertEquals(0.1666, WET.wetness(), 1e-12);

        Point dry = new Point(14.0, 500.0, 3320.0, 6.5000, 0.0240, 1.0);
        assertFalse(dry.isWet());
        assertTrue(dry.isSuperheated());
        assertEquals(0.0, dry.wetness(), 1e-12);
    }

    @Test
    @DisplayName("Строка протокола содержит давление, энтальпию и степень сухости")
    void describesPoint() {
        String text = WET.describe();

        assertTrue(text.contains("p = 0.004 МПа"), text);
        assertTrue(text.contains("h = 2148.50 кДж/кг"), text);
        assertTrue(text.contains("x = 0.8334"), text);
    }

    @Test
    @DisplayName("Некорректные параметры точки отклоняются")
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new Point(
                0.0, 28.96, 2148.5, 7.09, 34.8, 0.8334));
        assertThrows(IllegalArgumentException.class, () -> new Point(
                0.004, Double.NaN, 2148.5, 7.09, 34.8, 0.8334));
        assertThrows(IllegalArgumentException.class, () -> new Point(
                0.004, 28.96, -1.0, 7.09, 34.8, 0.8334));
        assertThrows(IllegalArgumentException.class, () -> new Point(
                0.004, 28.96, 2148.5, Double.POSITIVE_INFINITY, 34.8, 0.8334));
        assertThrows(IllegalArgumentException.class, () -> new Point(
                0.004, 28.96, 2148.5, 7.09, 0.0, 0.8334));
        assertThrows(IllegalArgumentException.class, () -> new Point(
                0.004, 28.96, 2148.5, 7.09, 34.8, -0.01));
        assertThrows(IllegalArgumentException.class, () -> new Point(
                0.004, 28.96, 2148.5, 7.09, 34.8, 1.01));
    }
}
