package org.kpush88.Thermal_2.turbine2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Формулы методички на числах самого документа.
 *
 * <p>Ожидаемые значения взяты из {@code university/Курс турб.docx}, раздел 2.3. Где в документе
 * есть опечатка в подстановке, проверяется результат по формуле: {@code G′ = 6.8} (а не 6.08) и
 * {@code k_вл = 0.99646} (а не 0.98894). Поэтому, например, ЧСД даёт 0.8776, как и напечатано в
 * документе, хотя подстановка содержит другое число.
 */
class CourseProjectEfficiencyTest {

    private static final CourseProjectEfficiency.Section CHVD =
            CourseProjectEfficiency.Section.CHVD;
    private static final CourseProjectEfficiency.Section CHSD =
            CourseProjectEfficiency.Section.CHSD;
    private static final CourseProjectEfficiency.Section CHND =
            CourseProjectEfficiency.Section.CHND;

    @Test
    @DisplayName("G′ = N_эк/(H₀′·η_оэ) = 6400/(1161.6·0.81) ≈ 6.8 кг/с")
    void flowMatchesMethodology() {
        assertEquals(6.8, CourseProjectEfficiency.flow(6400.0, 1161.6, 0.81), 0.01);
        assertEquals(6.8, CourseProjectEfficiency.flow(6400.0, 1161.6,
                CourseProjectEfficiency.DEFAULT_ELECTRICAL_EFFICIENCY), 0.01);
    }

    @Test
    @DisplayName("V_ср = √(V_вх·V_вых): 0.326, 1.359 и 9.29 м³/кг")
    void meanVolumeMatchesMethodology() {
        assertEquals(0.326, CourseProjectEfficiency.meanVolume(0.1927, 0.5519), 1e-3);
        assertEquals(1.359, CourseProjectEfficiency.meanVolume(0.613, 3.016), 2e-3);
        assertEquals(9.29, CourseProjectEfficiency.meanVolume(3.078, 28.05), 2e-3);
    }

    @Test
    @DisplayName("k_вл: ЧСД 0.99646, ЧНД 0.94776 — по формуле, а не по подстановке 0.98894")
    void wetnessFactorMatchesMethodology() {
        assertEquals(0.99646,
                CourseProjectEfficiency.wetnessFactor(0.0, 0.03261, 101.91, 375.21), 1e-5);
        assertEquals(0.94776,
                CourseProjectEfficiency.wetnessFactor(0.1179, 0.0127, 347.35, 347.35), 1e-5);
    }

    @Test
    @DisplayName("ξ_вс = 16/347.35 = 0.04606")
    void exitVelocityLossMatchesMethodology() {
        assertEquals(0.04606, CourseProjectEfficiency.exitVelocityLoss(16.0, 347.35), 1e-5);
        assertEquals(CourseProjectEfficiency.DEFAULT_EXIT_HEAT_DROP, 16.0, 0.0);
    }

    @Test
    @DisplayName("ЧВД и ЧСД: 0.7085 и 0.8776; полезные перепады 240.4 и 329.3 кДж/кг")
    void highPressureSectionsMatchMethodology() {
        CourseProjectEfficiency.SectionEstimate chvd = CourseProjectEfficiency.section(
                CHVD, 6.8, 339.19, 0.326);
        CourseProjectEfficiency.SectionEstimate chsd = CourseProjectEfficiency.section(
                CHSD, 6.8, 375.21, 1.359, 0.99646, 0.0);

        assertEquals(0.7085, chvd.efficiency(), 1e-3);
        assertEquals(240.4, chvd.internalWork(), 0.2);
        assertEquals(0.8776, chsd.efficiency(), 1e-3);
        assertEquals(329.28, chsd.internalWork(), 0.2);
    }

    @Test
    @DisplayName("ЧНД: 0.7828 с k_вл = 0.94776 и ξ_вс = 0.04606; h_i = 271.92 кДж/кг")
    void lowPressureSectionMatchesMethodology() {
        double wetness = CourseProjectEfficiency.wetnessFactor(0.1179, 0.0127, 347.35, 347.35);
        double exitLoss = CourseProjectEfficiency.exitVelocityLoss(16.0, 347.35);
        CourseProjectEfficiency.SectionEstimate chnd = CourseProjectEfficiency.section(
                CHND, 6.8, 347.35, 9.29, wetness, exitLoss);

        assertEquals(0.7828, chnd.efficiency(), 1e-3);
        assertEquals(271.92, chnd.internalWork(), 0.2);
    }

    @Test
    @DisplayName("Двухвенечная регулирующая ступень: 0.6575, h_i = 115.07 кДж/кг")
    void regulatingDoubleRowMatchesMethodology() {
        CourseProjectEfficiency.RegulatingEstimate regulating =
                CourseProjectEfficiency.regulatingDoubleRow(6.8, 2.716, 0.1157, 175.0);

        assertEquals(0.6575, regulating.efficiency(), 1e-4);
        assertEquals(115.07, regulating.internalWork(), 0.2);
        assertEquals(CourseProjectEfficiency.RegulatingKind.DOUBLE_ROW, regulating.kind());
    }

    @Test
    @DisplayName("Одновенечная ступень — экстраполяция с базой 0.85: 0.7075")
    void regulatingSingleRowIsHigher() {
        CourseProjectEfficiency.RegulatingEstimate single =
                CourseProjectEfficiency.regulatingSingleRow(6.8, 2.716, 0.1157, 175.0);
        CourseProjectEfficiency.RegulatingEstimate doubleRow =
                CourseProjectEfficiency.regulatingDoubleRow(6.8, 2.716, 0.1157, 175.0);

        assertEquals(0.7075, single.efficiency(), 1e-4);
        assertTrue(single.efficiency() > doubleRow.efficiency(),
                "одновенечная ступень эффективнее двухвенечной");
    }

    @Test
    @DisplayName("Турбина: h_i = 956.6 кДж/кг, H₀′ = 1161.6 кДж/кг, η = 0.824")
    void turbineMatchesMethodology() {
        CourseProjectEfficiency.RegulatingEstimate regulating =
                CourseProjectEfficiency.regulatingDoubleRow(6.8, 2.716, 0.1157, 175.0);
        CourseProjectEfficiency.SectionEstimate chvd = CourseProjectEfficiency.section(
                CHVD, 6.8, 339.19, 0.326);
        CourseProjectEfficiency.SectionEstimate chsd = CourseProjectEfficiency.section(
                CHSD, 6.8, 375.21, 1.359, 0.99646, 0.0);
        double wetness = CourseProjectEfficiency.wetnessFactor(0.1179, 0.0127, 347.35, 347.35);
        double exitLoss = CourseProjectEfficiency.exitVelocityLoss(16.0, 347.35);
        CourseProjectEfficiency.SectionEstimate chnd = CourseProjectEfficiency.section(
                CHND, 6.8, 347.35, 9.29, wetness, exitLoss);

        CourseProjectEfficiency.TurbineEstimate turbine = CourseProjectEfficiency.turbine(
                6.8, 1161.6, regulating, List.of(chvd, chsd, chnd));

        assertEquals(0.824, turbine.efficiency(), 1e-3);
        assertEquals(956.67, turbine.internalWork(), 0.5);
        assertEquals(6505.0, turbine.internalPower(), 5.0);
        assertEquals(3, turbine.sections().size());
        assertEquals(0.824, CourseProjectEfficiency.turbineEfficiency(956.67, 1161.6), 1e-3);
    }

    @Test
    @DisplayName("Формула ЧВД/ЧСД вне области: 0.5/(G′·V_ср) ≥ 0.925 — исключение, а не отрицательный КПД")
    void highPressureFormulaOutsideRangeThrows() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> CourseProjectEfficiency.section(CHVD, 9.4, 390.0, 0.03));
        assertTrue(failure.getMessage().contains("вне области применимости"),
                () -> "ошибка должна объяснять причину: " + failure.getMessage());
    }

    @Test
    @DisplayName("ЧВД не принимает k_вл и ξ_вс, ЧСД — ξ_вс: иначе формула была бы другой")
    void sectionRejectsArgumentsThatAreNotInItsFormula() {
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectEfficiency.section(CHVD, 6.8, 339.19, 0.326, 0.99, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectEfficiency.section(CHVD, 6.8, 339.19, 0.326, 1.0, 0.01));
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectEfficiency.section(CHSD, 6.8, 375.21, 1.359, 0.99646, 0.01));
    }

    @Test
    @DisplayName("Некорректные данные и разные расходы не проходят молча")
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectEfficiency.flow(6400.0, 1161.6, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectEfficiency.wetnessFactor(-0.1, 0.03, 100.0, 375.0));
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectEfficiency.wetnessFactor(0.0, 0.03, 400.0, 375.0));
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectEfficiency.section(CHND, 6.8, 347.35, 9.29, 0.9478, 0.9));

        CourseProjectEfficiency.RegulatingEstimate regulating =
                CourseProjectEfficiency.regulatingDoubleRow(6.8, 2.716, 0.1157, 175.0);
        CourseProjectEfficiency.SectionEstimate otherFlow = CourseProjectEfficiency.section(
                CHVD, 7.0, 339.19, 0.326);
        assertThrows(IllegalArgumentException.class,
                () -> CourseProjectEfficiency.turbine(6.8, 1161.6, regulating,
                        List.of(otherFlow)));
    }

    @Test
    @DisplayName("Строки отчёта содержат отсек, η и перепады")
    void describeIsReadable() {
        CourseProjectEfficiency.SectionEstimate chvd = CourseProjectEfficiency.section(
                CHVD, 6.8, 339.19, 0.326);
        assertTrue(chvd.describe().contains("ЧВД"));
        assertTrue(chvd.describe().contains("η_oi"));
        assertTrue(chvd.describe().contains("h₀"));
    }
}