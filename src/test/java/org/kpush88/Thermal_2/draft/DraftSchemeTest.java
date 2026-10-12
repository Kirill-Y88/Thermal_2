package org.kpush88.Thermal_2.draft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка каркаса: сборка СЛАУ из топологии и пересчёт расходов при добавлении
 * подогревателя.
 */
class DraftSchemeTest {

    private static final double EPS = 1e-9;

    @Test
    void threeHeaterScheme_matchesHandCalculation() {
        Solution s = DraftDemo.buildScheme(3).solve();

        assertEquals(1.0, s.value(VarId.of("Gw")), EPS);

        // Ручной расчёт каскада (снизу вверх, Gw = 1):
        double gx2 = (270.0 - 200.0) / (3000.0 - 1000.0);
        double gx1 = (40.0 - gx2 * (1000.0 - 600.0)) / (2700.0 - 600.0);
        double gd1 = gx1 + gx2;
        double gx0 = (20.0 - gd1 * (600.0 - 400.0)) / (2500.0 - 400.0);

        assertEquals(gx2, s.value(VarId.of("Gx2")), EPS);
        assertEquals(gx1, s.value(VarId.of("Gx1")), EPS);
        assertEquals(gx0, s.value(VarId.of("Gx0")), EPS);
    }

    @Test
    void addingFourthHeater_changesLowerExtractionFlows() {
        Solution three = DraftDemo.buildScheme(3).solve();
        Solution four = DraftDemo.buildScheme(4).solve();

        for (int i = 0; i < 3; i++) {
            VarId var = VarId.of("Gx" + i);
            double before = three.value(var);
            double after = four.value(var);
            assertTrue(after < before,
                    var + ": при добавлении подогревателя расход отбора должен уменьшиться, "
                            + "было " + before + ", стало " + after);
        }
    }

    @Test
    void schemeIsAutoAssembled_withoutHardcodingMatrix() {
        // 4 подогревателя × 2 уравнения + 1 граничное условие = 9 уравнений,
        // столько же неизвестных (4·Gx + 4·Gd + Gw).
        SchemeGraph graph = DraftDemo.buildScheme(4);
        assertEquals(4 * 2 + 1, graph.buildRegistry().size());
    }
}
