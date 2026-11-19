package org.kpush88.Thermal_2.turbine4;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Формулы разделов 2.1–2.4 на числах {@code university/Курс турб.docx}.
 *
 * <p>Ожидаемые значения — из числового примера курсового проекта. Где в документе есть
 * расхождение подстановки и формулы, проверяется результат по формуле (см. также
 * {@code turbine2.CourseProjectEfficiencyTest}).
 */
class CourseProjectFormulasTest {

    @Test
    @DisplayName("2.1: N_эк = 6400 кВт, P₀′ = 2.716 МПа, P_к′ = 0.0044 МПа")
    void energyAndPressuresMatchMethodology() {
        assertEquals(6400.0, CourseProjectFormulas.economicPower(8000.0, 0.8), 0.1);
        assertEquals(2.716, CourseProjectFormulas.valvePressure(2.8, 0.97), 1e-9);
        assertEquals(0.0044,
                CourseProjectFormulas.exhaustPressureWithLoss(0.004, 100.0, 0.1), 1e-9);
        assertEquals(1161.6,
                CourseProjectFormulas.availableHeatDrop(3303.75, 2142.15), 0.05);
        assertEquals(6.8,
                CourseProjectFormulas.estimatedFlow(6400.0, 1161.6, 0.81), 0.01);
    }

    @Test
    @DisplayName("2.2: C_a = 591.61 м/с, d_рс = 0.979 м, l₁ = 0.0185 м")
    void regulatingStageMatchesMethodology() {
        double fictive = CourseProjectFormulas.fictiveVelocity(175.0);
        double circumferential = CourseProjectFormulas.circumferentialVelocity(fictive, 0.26);
        double diameter = CourseProjectFormulas.regulatingMeanDiameter(circumferential, 3000.0);
        double nozzleDrop = CourseProjectFormulas.nozzleHeatDrop(175.0, 0.06);
        double nozzleVelocity = CourseProjectFormulas.nozzleTheoreticalVelocity(nozzleDrop);
        double area = CourseProjectFormulas.nozzleArea(6.8, 0.1783, 0.97, nozzleVelocity);
        double admissionLength = CourseProjectFormulas.admissionLength(area, diameter, 11.0);
        double admission = CourseProjectFormulas.admission(admissionLength, 0.33);
        double height = CourseProjectFormulas.nozzleHeight(admissionLength, admission);

        assertEquals(591.61, fictive, 0.01);
        assertEquals(153.82, circumferential, 0.01);
        assertEquals(0.979, diameter, 0.001);
        assertEquals(164.5, nozzleDrop, 0.01);
        assertEquals(573.59, nozzleVelocity, 0.01);
        assertEquals(0.00218, area, 1e-5);
        assertEquals(0.00371, admissionLength, 1e-5);
        assertEquals(0.2011, admission, 1e-4);
        assertEquals(0.01846, height, 1e-5);
    }

    @Test
    @DisplayName("2.3: КПД отсеков, влажностная поправка и внутренняя работа")
    void efficiencyAndWorkMatchMethodology() {
        assertEquals(0.6575, CourseProjectFormulas.regulatingEfficiency(
                StageKind.TWO_ROW_CONTROL_STAGE, 6.8, 2.716, 0.1157), 1e-4);
        assertEquals(0.7075, CourseProjectFormulas.regulatingEfficiency(
                StageKind.SINGLE_ROW_CONTROL_STAGE, 6.8, 2.716, 0.1157), 1e-4);
        assertEquals(115.07, CourseProjectFormulas.regulatingInternalWork(175.0, 0.6575), 0.2);

        assertEquals(0.326, CourseProjectFormulas.meanVolume(0.1927, 0.5519), 1e-3);
        assertEquals(1.359, CourseProjectFormulas.meanVolume(0.613, 3.016), 2e-3);
        assertEquals(9.29, CourseProjectFormulas.meanVolume(3.078, 28.05), 2e-3);

        assertEquals(0.7085, CourseProjectFormulas.highPressureEfficiency(
                6.8, 339.19, 0.326), 1e-3);
        assertEquals(0.99646, CourseProjectFormulas.wetnessFactor(
                0.0, 0.03261, 101.91, 375.21), 1e-5);
        assertEquals(0.8776, CourseProjectFormulas.chsdEfficiency(
                6.8, 375.21, 1.359, 0.99646), 1e-3);
        assertEquals(0.87458, CourseProjectFormulas.lowPressureEfficiency(347.35), 1e-4);
        assertEquals(0.04606, CourseProjectFormulas.exitVelocityLoss(16.0, 347.35), 1e-5);
        assertEquals(0.7828, CourseProjectFormulas.chndEfficiency(
                347.35, 0.94776, 0.04606), 1e-3);
        assertEquals(271.92, CourseProjectFormulas.sectionInternalWork(347.35, 0.7828), 0.2);
    }

    @Test
    @DisplayName("2.3: h_i = 956.67 кДж/кг, G = 6.967 кг/с, η_oi = 0.824")
    void turbineSummaryMatchesMethodology() {
        double internalWork = CourseProjectFormulas.totalInternalWork(
                115.07, 240.4, 329.28, 271.92);
        double flow = CourseProjectFormulas.refinedFlow(6400.0, internalWork, 0.99, 0.97);

        assertEquals(956.67, internalWork, 0.5);
        assertEquals(6.967, flow, 0.05);
        assertEquals(0.824, CourseProjectFormulas.turbineEfficiency(
                internalWork, 1161.6), 1e-3);
    }

    @Test
    @DisplayName("2.4: C₂z = 178.89 м/с, N_пр ≈ 43.35 МВт, один выхлоп")
    void powerLimitMatchesMethodology() {
        double exitVelocity = CourseProjectFormulas.exitVelocity(16.0);
        double limitPower = CourseProjectFormulas.limitPower(
                1.0, 2.2, 0.824, 420.0, exitVelocity, 1161.6, 8000.0, 3000.0, 29.046);

        assertEquals(178.89, exitVelocity, 0.01);
        assertEquals(43.35, limitPower, 1.0);
        assertEquals(1, CourseProjectFormulas.exhaustCount(8.0, limitPower));
        assertEquals(3, CourseProjectFormulas.exhaustCount(100.0, limitPower));
    }

    @Test
    @DisplayName("Некорректные данные дают IllegalArgumentException")
    void invalidInputsThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectFormulas.economicPower(0.0, 0.8));
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectFormulas.availableHeatDrop(100.0, 100.0));
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectFormulas.admission(10.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectFormulas.regulatingEfficiency(
                        StageKind.NON_CONTROL_STAGE, 6.8, 2.716, 0.1157));
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectFormulas.limitPower(1.0, 2.2, 0.824, 420.0,
                        178.89, 1161.6, 8000.0, 0.0, 29.046));
        assertTrue(CourseProjectFormulas.exhaustCount(8.0, 100.0) >= 1);
    }
}
