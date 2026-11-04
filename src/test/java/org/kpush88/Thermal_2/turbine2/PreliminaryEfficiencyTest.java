package org.kpush88.Thermal_2.turbine2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Компактная модель КПД: базовая точка, знаки поправок, осреднение отсека и ограничение
 * диапазоном.
 *
 * <p>Числа здесь — не эталон турбины, а проверка самой формулы. Согласие с опорной машиной
 * проверяет {@code turbine.PreliminaryEfficiencyReferenceTest}: там модель сравнивается с
 * {@code ReferenceTurbine}.
 */
class PreliminaryEfficiencyTest {

    private static final PreliminaryEfficiency.StageKind NON_REG =
            PreliminaryEfficiency.StageKind.NON_REGULATING;

    private final PreliminaryEfficiency model = new PreliminaryEfficiency();

    @Test
    @DisplayName("Опорная точка калибровки: H = 50, v = 0.1, p = 1, x = 1, G = 7.5 → η = 0.827")
    void basePointIsTheCalibrationConstant() {
        PreliminaryEfficiency.StageEstimate stage = model.stage(NON_REG,
                new PreliminaryEfficiency.StageState(7.5, 50.0, 1.0, 0.1, 1.0));

        assertEquals(0.827, stage.efficiency(), 1e-12);
        assertEquals(0.0, stage.totalCorrection(), 1e-12);
        assertEquals(0.827 * 50.0, stage.internalWork(), 1e-12);
        assertFalse(stage.clamped(), "опорная точка не должна ограничиваться");
    }

    @Test
    @DisplayName("Рост теплоперепада снижает КПД, рост расхода — повышает: знаки как в калибровке")
    void heatDropAndFlowCorrectionsHaveExpectedSigns() {
        PreliminaryEfficiency.StageEstimate hot = model.stage(NON_REG,
                new PreliminaryEfficiency.StageState(7.5, 100.0, 1.0, 0.1, 1.0));
        PreliminaryEfficiency.StageEstimate greedy = model.stage(NON_REG,
                new PreliminaryEfficiency.StageState(15.0, 50.0, 1.0, 0.1, 1.0));

        assertEquals(0.779, hot.efficiency(), 1e-12);
        assertEquals(0.926, greedy.efficiency(), 1e-12);
        assertTrue(hot.heatDropCorrection() < 0.0, "H/H₀ > 1 — поправка отрицательная");
        assertTrue(greedy.flowCorrection() > 0.0, "G/G₀ > 1 — поправка положительная");
    }

    @Test
    @DisplayName("Объём входит логарифмом, влажность — линейно, давление в калибровке не даёт вклада")
    void volumePressureAndWetnessCorrections() {
        PreliminaryEfficiency.StageEstimate largeVolume = model.stage(NON_REG,
                new PreliminaryEfficiency.StageState(7.5, 50.0, 1.0, 0.2, 1.0));
        PreliminaryEfficiency.StageEstimate highPressure = model.stage(NON_REG,
                new PreliminaryEfficiency.StageState(7.5, 50.0, 10.0, 0.1, 1.0));
        PreliminaryEfficiency.StageEstimate wet = model.stage(NON_REG,
                new PreliminaryEfficiency.StageState(7.5, 50.0, 1.0, 0.1, 0.9));

        assertEquals(0.827 + 0.029 * Math.log(2.0), largeVolume.efficiency(), 1e-12);
        assertEquals(0.827, highPressure.efficiency(), 1e-12);
        assertEquals(0.0, highPressure.pressureCorrection(), 0.0,
                "в калибровке давление коллинеарно объёму, отдельного вклада нет");
        assertEquals(0.827 - 0.635 * 0.1, wet.efficiency(), 1e-12);
        assertTrue(wet.efficiency() < largeVolume.efficiency(),
                "влажность снижает КПД сильнее, чем удвоение объёма его повышает");
    }

    @Test
    @DisplayName("StageLaw.efficiency и stage() дают одно и то же неограниченное значение")
    void lawMatchesStage() {
        PreliminaryEfficiency.StageLaw law = PreliminaryEfficiency.law(NON_REG);
        double raw = law.efficiency(60.0, 0.2, 2.0, 0.95, 9.0);
        PreliminaryEfficiency.StageEstimate estimate = model.stage(NON_REG,
                new PreliminaryEfficiency.StageState(9.0, 60.0, 2.0, 0.2, 0.95));

        assertEquals(raw, estimate.rawEfficiency(), 1e-12);
    }

    @Test
    @DisplayName("Отсек сжимается в среднюю ступень: H/z, √(v₁v₂), √(p₁p₂), (x₁+x₂)/2")
    void sectionUsesTheMeanStage() {
        PreliminaryEfficiency.SectionState state = new PreliminaryEfficiency.SectionState(
                7.5, 100.0, 2, 1.0, 0.1, 0.5, 0.4, 1.0, 0.8);
        PreliminaryEfficiency.SectionEstimate section = model.section(NON_REG, state);

        assertEquals(50.0, section.meanStage().heatDrop(), 1e-12);
        assertEquals(0.2, section.meanStage().volume(), 1e-12);
        assertEquals(Math.sqrt(0.5), section.meanStage().pressure(), 1e-12);
        assertEquals(0.9, section.meanStage().quality(), 1e-12);
        assertEquals(section.meanStage().efficiency(), section.efficiency(), 1e-12);

        double expected = 0.827 + 0.029 * Math.log(2.0) - 0.635 * 0.1;
        assertEquals(expected, section.efficiency(), 1e-12);
        assertEquals(state.flow() * state.heatDrop() * section.efficiency(),
                section.internalPower(), 1e-9);
    }

    @Test
    @DisplayName("КПД турбины — средневзвешенный по G·H, а не среднее арифметическое")
    void turbineWeightsSectionsByFlowAndDrop() {
        PreliminaryEfficiency.SectionEstimate first = model.section(NON_REG,
                new PreliminaryEfficiency.SectionState(10.0, 100.0, 2,
                        1.0, 0.1, 0.5, 0.4, 1.0, 0.8));
        PreliminaryEfficiency.SectionEstimate second = model.section(NON_REG,
                new PreliminaryEfficiency.SectionState(8.0, 120.0, 3,
                        0.5, 0.4, 0.1, 1.2, 0.8, 1.0));
        PreliminaryEfficiency.TurbineEstimate turbine = model.turbine(List.of(first, second));

        double expected = (first.internalPower() + second.internalPower())
                / (first.availablePower() + second.availablePower());
        assertEquals(expected, turbine.efficiency(), 1e-12);
        assertEquals(10.0, turbine.flow(), 1e-12, "расход турбины — расход первого отсека");
        assertEquals(first.availablePower() + second.availablePower(),
                turbine.availablePower(), 1e-9);
        assertEquals(first.internalPower() + second.internalPower(),
                turbine.internalPower(), 1e-9);
        assertEquals(2, turbine.sections().size());
    }

    @Test
    @DisplayName("Регулирующие ступени: η_ол 0.78…0.82 одновенечная и 0.75…0.78 двухвенечная")
    void regulatingStagesStayInTheirRanges() {
        PreliminaryEfficiency.StageEstimate single = model.stage(
                PreliminaryEfficiency.StageKind.REGULATING_SINGLE_ROW,
                new PreliminaryEfficiency.StageState(10.0, 100.0, 1.0, 0.04, 1.0));
        PreliminaryEfficiency.StageEstimate doubleRow = model.stage(
                PreliminaryEfficiency.StageKind.REGULATING_DOUBLE_ROW,
                new PreliminaryEfficiency.StageState(10.0, 100.0, 1.0, 0.04, 1.0));
        PreliminaryEfficiency.StageEstimate heavyDrop = model.stage(
                PreliminaryEfficiency.StageKind.REGULATING_DOUBLE_ROW,
                new PreliminaryEfficiency.StageState(10.0, 175.0, 1.0, 0.04, 1.0));

        assertEquals(0.80, single.efficiency(), 1e-12);
        assertEquals(0.765, doubleRow.efficiency(), 1e-12);
        assertTrue(single.efficiency() >= 0.78 && single.efficiency() <= 0.82);
        assertTrue(doubleRow.efficiency() >= 0.75 && doubleRow.efficiency() <= 0.78);
        assertTrue(heavyDrop.efficiency() >= 0.75 && heavyDrop.efficiency() <= 0.78,
                "h₀ = 175 кДж/кг остаётся в полосе двухвенечной ступени");
        assertTrue(single.efficiency() > doubleRow.efficiency(),
                "одновенечная ступень эффективнее двухвенечной");
    }

    @Test
    @DisplayName("Смешивание по числу ступеней: (0.80·2 + 0.85·8)/10 = 0.84")
    void mixesByStageCount() {
        assertEquals(0.84, PreliminaryEfficiency.mix(0.80, 2, 0.85, 8), 1e-12);
        assertEquals(0.85, PreliminaryEfficiency.mix(0.80, 0, 0.85, 5), 1e-12);
        assertEquals(0.80, PreliminaryEfficiency.mix(0.80, 2, 0.85, 0), 1e-12);
        assertThrows(IllegalArgumentException.class,
                () -> PreliminaryEfficiency.mix(0.80, 0, 0.85, 0));
    }

    @Test
    @DisplayName("Перегрузки по hs-диаграмме совпадают с явно заданным состоянием")
    void hsOverloadsAgreeWithExplicitState() {
        HsDiagram hs = new HsDiagram();
        double hIn = hs.enthalpyPT(14.0, 550.0);
        double hOut = hs.expand(14.0, hIn, 3.5, 0.85);
        double flow = 9.4;

        PreliminaryEfficiency.StageEstimate viaState = model.stage(NON_REG,
                new PreliminaryEfficiency.StageState(flow, hs.isentropicDrop(14.0, hIn, 3.5),
                        14.0, hs.volumePH(14.0, hIn), hs.vapourFractionPH(3.5, hOut)));
        PreliminaryEfficiency.StageEstimate viaHs = model.stage(NON_REG,
                flow, 14.0, hIn, 3.5, hOut);
        assertEquals(viaState.efficiency(), viaHs.efficiency(), 1e-12);

        PreliminaryEfficiency.SectionEstimate sectionViaState = model.section(NON_REG,
                new PreliminaryEfficiency.SectionState(flow,
                        hs.isentropicDrop(14.0, hIn, 3.5), 10, 14.0,
                        hs.volumePH(14.0, hIn), 3.5, hs.volumePH(3.5, hOut),
                        hs.vapourFractionPH(14.0, hIn), hs.vapourFractionPH(3.5, hOut)));
        PreliminaryEfficiency.SectionEstimate sectionViaHs = model.section(NON_REG,
                flow, 14.0, hIn, 3.5, hOut, 10);
        assertEquals(sectionViaState.efficiency(), sectionViaHs.efficiency(), 1e-12);
    }

    @Test
    @DisplayName("Экстремальные данные дают 0, а не отрицательный КПД; виден признак ограничения")
    void clampsExtremeResult() {
        PreliminaryEfficiency.StageEstimate stage = model.stage(NON_REG,
                new PreliminaryEfficiency.StageState(0.001, 10000.0, 0.001, 0.001, 0.01));
        assertEquals(0.0, stage.efficiency(), 0.0);
        assertTrue(stage.rawEfficiency() < 0.0);
        assertTrue(stage.clamped());

        PreliminaryEfficiency.SectionEstimate section = model.section(NON_REG,
                new PreliminaryEfficiency.SectionState(0.001, 10000.0, 1,
                        1.0, 0.001, 0.001, 0.001, 0.01, 0.01));
        assertEquals(0.0, section.efficiency(), 0.0);
        PreliminaryEfficiency.TurbineEstimate turbine = model.turbine(List.of(section));
        assertEquals(0.0, turbine.efficiency(), 0.0);
    }

    @Test
    @DisplayName("Некорректные входные данные называют виновника, а не считаются молча")
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new PreliminaryEfficiency.StageState(0.0, 50.0, 1.0, 0.1, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new PreliminaryEfficiency.StageState(7.5, 50.0, 1.0, 0.1, 1.1));
        assertThrows(IllegalArgumentException.class,
                () -> new PreliminaryEfficiency.SectionState(7.5, 100.0, 0,
                        1.0, 0.1, 0.5, 0.4, 1.0, 0.8));
        assertThrows(IllegalArgumentException.class,
                () -> new PreliminaryEfficiency.SectionState(7.5, 100.0, 2,
                        0.5, 0.1, 1.0, 0.4, 1.0, 0.8));
        assertThrows(IllegalArgumentException.class,
                () -> model.stage(NON_REG, 7.5, 1.0, 3000.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> model.section(NON_REG, 7.5, 1.0, 3000.0, 1.0, 2));
        assertThrows(IllegalArgumentException.class, () -> model.turbine(List.of()));
        assertThrows(IllegalArgumentException.class, () -> PreliminaryEfficiency.law(null));
    }
}