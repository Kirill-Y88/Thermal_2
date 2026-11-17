package org.kpush88.Thermal_2.turbine3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("2.3: процесс расширения построен графом FlowPoint")
    void buildsExpansionProcessGraph() {
        PreliminaryResult result = PreliminaryCalculator.calculate(
                INPUT, DesignAssumptions.reference(), new HsDiagram());
        ExpansionProcess process = result.expansion().process();

        assertDoesNotThrow(process::validate);

        List<FlowPoint> path = process.actualPath();
        assertEquals(5, path.size());
        assertSame(result.energy().inlet(), process.inlet());
        assertSame(process.inlet(), path.get(0));
        assertSame(process.exhaust(), path.get(path.size() - 1));
        assertTrue(path.stream()
                .allMatch(point -> point.getPointType() == FlowPointType.UNRESOLVED));
        assertTrue(process.isentropicPoints().stream()
                .allMatch(point -> point.getPointType() == FlowPointType.ISENTROPIC));

        for (FlowPoint point : path) {
            assertNotNull(point.getMassFlow());
            assertEquals(6.798, point.getMassFlow().doubleValue(), 0.01);
        }
        assertEquals(3303.75, process.inlet().getPoint().enthalpyKJkg(), 0.1);
        assertEquals(175.0, isentropicDrop(process.inlet()), 0.05);
        assertEquals(339.19, isentropicDrop(process.regulatingStageOutlet()), 0.05);
        assertEquals(375.21, isentropicDrop(process.chvdOutlet()), 0.05);
        assertTrue(isentropicDrop(process.chsdOutlet()) > 0.0);
        assertTrue(process.exhaust().getPoint().isWet());
        assertEquals(0.913, process.exhaust().getPoint().vapourFraction(), 0.01);

        for (int i = 1; i < path.size(); i++) {
            assertTrue(path.get(i).getPoint().pressureMPa()
                    < path.get(i - 1).getPoint().pressureMPa());
            assertTrue(path.get(i).getPoint().enthalpyKJkg()
                    < path.get(i - 1).getPoint().enthalpyKJkg());
        }
        assertSame(process.regulatingStageOutlet(),
                process.sectionInlet(CourseProjectEfficiency.Section.CHVD));
        assertSame(process.exhaust(),
                process.sectionOutlet(CourseProjectEfficiency.Section.CHND));
    }

    private static double isentropicDrop(FlowPoint point) {
        FlowPoint isentropic = point.getNextIsentropic();
        assertNotNull(isentropic);
        return point.getPoint().enthalpyKJkg() - isentropic.getPoint().enthalpyKJkg();
    }
}
