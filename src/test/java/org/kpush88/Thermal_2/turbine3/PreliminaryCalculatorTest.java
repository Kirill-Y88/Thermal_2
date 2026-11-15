package org.kpush88.Thermal_2.turbine3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Предварительный расчёт 2.1–2.4 на данных курсового проекта: турбина 8 МВт, 28 бар,
 * 430 °C, 0.04 бар, 3000 об/мин, двухвенечная регулирующая ступень.
 */
class PreliminaryCalculatorTest {

    private static final DesignInput.SectionHeatDrops DROPS =
            new DesignInput.SectionHeatDrops(339.19, 375.21, 347.35);

    private static final DesignInput INPUT = DesignInput.ofBar(8.0, 28.0, 430.0, 0.04,
            3000.0, RegulatingKind.TWO_ROW, DROPS);

    @Test
    @DisplayName("2.1–2.4: предварительный расчёт по данным курсового проекта")
    void calculatesCourseProjectPreliminary() {
        PreliminaryResult result = PreliminaryCalculator.calculate(
                INPUT, DesignAssumptions.reference(), new HsDiagram());

        System.out.println(result.describe());

        assertEquals(6400.0, result.energy().economicPowerKW(), 1.0);
        assertEquals(6.8, result.energy().estimatedFlowKgS(), 0.1);
        assertEquals(0.979, result.regulatingStage().meanDiameterM(), 0.01);
        assertEquals(6.967, result.expansion().refinedFlowKgS(), 0.05);
        assertEquals(0.824, result.expansion().turbineEfficiency(), 0.01);
        assertEquals(43.3, result.powerLimit().limitPowerMW(), 1.0);
        assertEquals(1, result.powerLimit().exhaustCount());
    }
}
