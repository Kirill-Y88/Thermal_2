package org.kpush88.Thermal_2.turbine2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Предварительный расчёт 2.1–2.4 на опорном варианте методички.
 *
 * <p>Вход: 8 МВт, 28 бар, 430 °C, 0.04 бар, 3000 об/мин, двухвенечная регулирующая ступень,
 * перепады отсеков 339.19 / 375.21 / 347.35 кДж/кг. Ожидаемые величины — из
 * {@code university/Курс турб.docx}, но там, где документ читает hs-диаграмму, проверяется
 * расчёт IF97: например, {@code h₀′ = 3303.75} кДж/кг вместо {@code 3302.52} на рисунке.
 *
 * <p>Допуски оставлены такими, чтобы тест ловил ошибку в формуле, но не падал от последнего
 * знака IF97. Для сверки с методичкой есть отдельный тест с более широкими допусками.
 */
class TurbineDesignTest {

    private static final DesignInput.SectionHeatDrops DROPS =
            new DesignInput.SectionHeatDrops(339.19, 375.21, 347.35);

    private static final DesignInput INPUT = DesignInput.ofBar(8.0, 28.0, 430.0, 0.04,
            3000.0, CourseProjectEfficiency.RegulatingKind.DOUBLE_ROW, DROPS);

    private final TurbineDesign design = TurbineDesign.builder(INPUT).build();
    private final PreliminaryResult result = design.preliminary();

    @Test
    @DisplayName("2.1: N_эк = 6400 кВт, H₀′ ≈ 1162.3 кДж/кг, G′ ≈ 6.798 кг/с")
    void computesEnergyEstimate() {
        PreliminaryResult.EnergyEstimate energy = result.energy();

        assertEquals(6400.0, energy.economicPowerKW(), 1e-9);
        assertEquals(2.8, energy.inletPressureAtValvesMPa(), 1e-12);
        assertEquals(2.716, energy.inletPressureAtNozzlesMPa(), 1e-12);
        assertEquals(0.004, energy.exhaustPressureMPa(), 1e-12);
        assertEquals(0.0044, energy.exhaustPressureWithLossMPa(), 1e-12);
        assertEquals(3303.75, energy.inletEnthalpyKJkg(), 0.02);
        assertEquals(7.072455, energy.inletEntropyKJkgK(), 1e-5);
        assertEquals(0.115831, energy.inletVolumeM3kg(), 2e-6);
        assertEquals(2141.45, energy.isentropicExhaustEnthalpyKJkg(), 0.02);
        assertEquals(1162.30, energy.availableHeatDropKJkg(), 0.02);
        assertEquals(6.7979, energy.estimatedFlowKgS(), 1e-3);
        assertEquals(energy.inletEnthalpyKJkg() - energy.isentropicExhaustEnthalpyKJkg(),
                energy.availableHeatDropKJkg(), 1e-9,
                "H₀′ — это разность энтальпий, а не сумма перепадов отсеков");
    }

    @Test
    @DisplayName("2.2: Ca = 591.61 м/с, U = 153.82 м/с, d = 0.979 м, e = 0.2011, l₁ = 0.01846 м")
    void computesRegulatingStage() {
        PreliminaryResult.RegulatingStage stage = result.regulatingStage();

        assertEquals(CourseProjectEfficiency.RegulatingKind.DOUBLE_ROW, stage.kind());
        assertEquals(591.61, stage.fictiveVelocityMS(), 0.01);
        assertEquals(153.82, stage.circumferentialVelocityMS(), 0.01);
        assertEquals(0.9792, stage.meanDiameterM(), 1e-4);
        assertEquals(164.5, stage.nozzleHeatDropKJkg(), 1e-9);
        assertEquals(573.59, stage.nozzleTheoreticalVelocityMS(), 0.01);
        assertEquals(1.5577, stage.nozzlePressureMPa(), 1e-3);
        assertEquals(0.178348, stage.nozzleVolumeM3kg(), 1e-5);
        assertEquals(0.0021791, stage.nozzleAreaM2(), 1e-6);
        assertEquals(0.201064, stage.admission(), 1e-5);
        assertEquals(0.018463, stage.nozzleHeightM(), 1e-5);
        assertEquals(11.0, stage.nozzleExitAngleDeg(), 1e-12);
    }

    @Test
    @DisplayName("2.3: η_рс = 0.6575, η_ЧВД = 0.7086, η_ЧСД = 0.8775, η_ЧНД = 0.7831")
    void computesSectionsAndInternalWork() {
        PreliminaryResult.Expansion expansion = result.expansion();

        assertEquals(0.657536, expansion.regulating().efficiency(), 1e-5);
        assertEquals(1.499711, expansion.regulatingStageOutletPressureMPa(), 1e-4);
        assertEquals(0.192811, expansion.regulatingStageOutletVolumeM3kg(), 1e-5);

        assertEquals(0.382683, expansion.chvdOutletPressureMPa(), 1e-5);
        assertEquals(0.551453, expansion.chvdOutletVolumeM3kg(), 1e-5);
        assertEquals(0.708555, expansion.chvd().efficiency(), 1e-5);

        assertEquals(0.052341, expansion.chsdOutletPressureMPa(), 1e-5);
        assertEquals(3.004577, expansion.chsdOutletVolumeM3kg(), 1e-5);
        assertEquals(0.877526, expansion.chsd().efficiency(), 1e-5);

        assertEquals(0.783126, expansion.chnd().efficiency(), 1e-5);
        assertEquals(28.0568, expansion.exhaustVolumeM3kg(), 1e-3,
                "изоэнтропный объём V₂z используется в среднем объёме ЧНД");
        assertEquals(29.046, expansion.exhaustActualVolumeM3kg(), 2e-3,
                "действительный объём V₂z используется в предельной мощности 2.4");

        assertEquals(957.337, expansion.internalWorkKJkg(), 0.01);
        assertEquals(6.9616, expansion.refinedFlowKgS(), 1e-3);
        assertEquals(0.823658, expansion.turbineEfficiency(), 1e-5);
    }

    @Test
    @DisplayName("2.4: C₂z = 178.89 м/с, V₂z = 29.046 м³/кг, N_пр ≈ 43.35 МВт, один выхлоп")
    void computesPowerLimit() {
        PreliminaryResult.PowerLimit limit = result.powerLimit();

        assertEquals(178.885, limit.exitVelocityMS(), 1e-3);
        assertEquals(29.046, limit.lastStageVolumeM3kg(), 2e-3);
        assertEquals(43.35, limit.limitPowerMW(), 0.05);
        assertEquals(1, limit.exhaustCount());
        assertTrue(result.notes().isEmpty(),
                () -> "опорный вариант не должен давать предупреждений: " + result.notes());
    }

    @Test
    @DisplayName("Сверка с методичкой: G′ ≈ 6.8, η_рс ≈ 0.658, η_oi ≈ 0.824, N_пр ≈ 43.3 МВт")
    void reproducesMethodologyReferenceValues() {
        assertEquals(6.8, result.energy().estimatedFlowKgS(), 0.01);
        assertEquals(0.658, result.expansion().regulating().efficiency(), 2e-3);
        assertEquals(0.824, result.expansion().turbineEfficiency(), 2e-3);
        assertEquals(43.3, result.powerLimit().limitPowerMW(), 0.1);
        assertEquals(0.824, CourseProjectEfficiency.turbineEfficiency(
                result.expansion().internalWorkKJkg(),
                result.energy().availableHeatDropKJkg()), 2e-3);
    }

    @Test
    @DisplayName("Контрольный перепад ЧНД из входа не подменяет расчёт: расхождение — в примечаниях")
    void reportsControlChndMismatch() {
        DesignInput input = new DesignInput(8.0, 2.8, 430.0, 0.004, 3000.0,
                CourseProjectEfficiency.RegulatingKind.DOUBLE_ROW,
                new DesignInput.SectionHeatDrops(339.19, 375.21, 300.0));
        PreliminaryResult mismatched = TurbineDesign.builder(input).build().preliminary();

        assertFalse(mismatched.notes().isEmpty());
        assertTrue(mismatched.notes().get(0).contains("перепад ЧНД"));
        assertEquals(348.19, mismatched.expansion().chnd().heatDrop(), 0.05,
                "в расчёте остаётся перепад по P_к′, а не контрольное число");
    }

    @Test
    @DisplayName("80 МВт при N_пр ≈ 43 МВт требуют двух выхлопов и дают примечание")
    void requiresMoreExhaustsForLargePower() {
        DesignInput input = new DesignInput(80.0, 2.8, 430.0, 0.004, 3000.0,
                CourseProjectEfficiency.RegulatingKind.DOUBLE_ROW, DROPS);
        PreliminaryResult large = TurbineDesign.builder(input).build().preliminary();

        assertEquals(2, large.powerLimit().exhaustCount());
        assertTrue(large.notes().stream().anyMatch(note -> note.contains("выхлопов")),
                () -> "при двух выхлопах должно быть примечание: " + large.notes());
    }

    @Test
    @DisplayName("Теплоперепад регулирующей ступени больше перепада турбины — исключение")
    void rejectsRegulatingHeatDropAboveTurbineDrop() {
        DesignAssumptions assumptions = assumptionsWithRegulatingDrop(2000.0);
        TurbineDesign bad = TurbineDesign.builder(INPUT).assumptions(assumptions).build();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                bad::preliminary);
        assertTrue(failure.getMessage().contains("меньше располагаемого"),
                () -> "ошибка должна объяснять несовместимость: " + failure.getMessage());
    }

    @Test
    @DisplayName("Некорректные исходные данные и допущения не проходят молча")
    void rejectsInvalidInputsAndAssumptions() {
        assertThrows(IllegalArgumentException.class, () -> TurbineDesign.builder(null));
        assertThrows(IllegalArgumentException.class, () -> DesignInput.ofBar(0.0, 28.0,
                430.0, 0.04, 3000.0, CourseProjectEfficiency.RegulatingKind.DOUBLE_ROW,
                DROPS));
        assertThrows(IllegalArgumentException.class, () -> DesignInput.ofBar(8.0, 28.0,
                430.0, 300.0, 3000.0, CourseProjectEfficiency.RegulatingKind.DOUBLE_ROW,
                DROPS));
        assertThrows(IllegalArgumentException.class, () -> new DesignInput(8.0, 2.8, 430.0,
                0.004, 3000.0, null, DROPS));
        assertThrows(IllegalArgumentException.class,
                () -> new DesignInput.SectionHeatDrops(0.0, 375.21, 347.35));
        assertThrows(IllegalArgumentException.class,
                () -> new DesignAssumptions(0.8, 0.81, 0.99, 0.97, 0.97, 100.0, 0.1,
                        175.0, 0.26, 1.0, 11.0, 0.97, 0.33, 1.0, 2.2, 420.0, 8000.0, 16.0));
        assertThrows(IllegalArgumentException.class,
                () -> new DesignAssumptions(0.8, 0.81, 0.99, 0.97, 0.97, 100.0, 0.1,
                        175.0, 0.26, 0.06, 90.0, 0.97, 0.33, 1.0, 2.2, 420.0, 8000.0, 16.0));
    }

    @Test
    @DisplayName("Допущения reference() — это опорный вариант 8 МВт")
    void referenceAssumptionsMatchMethodology() {
        DesignAssumptions a = DesignAssumptions.reference();

        assertEquals(0.8, a.economicPowerFactor(), 1e-12);
        assertEquals(0.81, a.electricalEfficiency(), 1e-12);
        assertEquals(0.97, a.valvePressureFactor(), 1e-12);
        assertEquals(175.0, a.regulatingHeatDropKJkg(), 1e-12);
        assertEquals(0.26, a.regulatingSpeedRatio(), 1e-12);
        assertEquals(420.0, a.allowableBladeStressMPa(), 1e-12);
        assertEquals(DesignAssumptions.reference(), design.assumptions(),
                "builder по умолчанию берёт reference()");
    }

    @Test
    @DisplayName("Строка отчёта содержит все четыре этапа")
    void describeContainsAllStages() {
        String text = result.describe();

        assertTrue(text.contains("2.1:"));
        assertTrue(text.contains("2.2:"));
        assertTrue(text.contains("2.3:"));
        assertTrue(text.contains("2.4:"));
        assertTrue(text.contains("η_oi"));
    }

    private static DesignAssumptions assumptionsWithRegulatingDrop(double drop) {
        return new DesignAssumptions(0.8, 0.81, 0.99, 0.97, 0.97, 100.0, 0.1, drop,
                0.26, 0.06, 11.0, 0.97, 0.33, 1.0, 2.2, 420.0, 8000.0, 16.0);
    }

}

