package org.kpush88.Thermal_2.turbine2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Предварительное разбиение на ступени по разделу 2.5 на опорном варианте методички.
 *
 * <p>Проверяются таблицы 2.1–2.9: для ЧВД и ЧСД по 6 ступеней, для ЧНД — 4. Диаметры и высоты
 * лопаток сверяются с методичкой, а теплоперепады — с её итоговыми таблицами 2.3, 2.6 и 2.9.
 *
 * <p><b>Про средний перепад ЧСД.</b> В методичке написано {@code h₀_ср = 61.5} кДж/кг, но сумма
 * её же шести значений {@code 58.41 + 59.95 + 61.53 + 63.10 + 64.67 + 66.24} даёт {@code 62.32}.
 * Здесь проверяется сумма, а не опечатка: расчёт не должен подгоняться под неверное среднее.
 */
class PreliminaryStagesTest {

    private static final DesignInput.SectionHeatDrops DROPS =
            new DesignInput.SectionHeatDrops(339.19, 375.21, 347.35);

    private static final DesignInput INPUT = DesignInput.ofBar(8.0, 28.0, 430.0, 0.04,
            3000.0, CourseProjectEfficiency.RegulatingKind.DOUBLE_ROW, DROPS);

    private final TurbineDesign design = TurbineDesign.builder(INPUT).build();
    private final PreliminaryResult preliminary = design.preliminary();
    private final PreliminaryStages stages = design.preliminaryStages(preliminary);

    @Test
    @DisplayName("2.5: ЧВД — 6 ступеней, d_ср = 0.879…0.909 м, l₂z = 0.0458 м")
    void laysOutChvd() {
        PreliminaryStages.SectionLayout chvd = stages.chvd();

        assertEquals(6, chvd.stageCount());
        assertEquals(0.879, chvd.firstMeanDiameterM(), 1e-9);
        assertEquals(0.909, chvd.lastMeanDiameterM(), 1e-9);
        assertEquals(0.01575, chvd.firstBladeHeightM(), 1e-9);
        assertEquals(0.04575, chvd.lastBladeHeightM(), 1e-5);
        assertEquals(0.86325, chvd.lastRootDiameterM(), 1e-4);

        assertEquals(347.31, chvd.totalHeatDropKJkg(), 0.02);
        assertEquals(59.99, chvd.meanHeatDropKJkg(), 0.02);
        assertEquals(8.12, chvd.recoveryHeatKJkg(), 0.02);
        assertEquals(0.02393, chvd.recoveryCoefficient(), 1e-4);
        assertEquals(0.7086, chvd.efficiency(), 1e-4,
                "КПД отсека должен остаться из раздела 2.3, а не пересчитываться через Σh₀");
    }

    @Test
    @DisplayName("2.5.1: теплоперепады ЧВД совпадают с таблицей 2.3")
    void distributesChvdHeatDrops() {
        PreliminaryStages.SectionLayout chvd = stages.chvd();

        assertEquals(62.36, chvd.stages().get(0).heatDropKJkg(), 0.02);
        assertEquals(55.19, chvd.stages().get(5).heatDropKJkg(), 0.02);
        assertEquals(347.31, chvd.stages().stream()
                .mapToDouble(PreliminaryStages.StageLayout::heatDropKJkg).sum(), 0.02);
        assertTrue(chvd.stages().get(0).regulating(),
                "первая нерегулируемая ступень ЧВД идёт с K₀ = 1 и помечается как особая");
        assertTrue(chvd.stages().stream().skip(1)
                .noneMatch(PreliminaryStages.StageLayout::regulating));
    }

    @Test
    @DisplayName("2.5.2: ЧСД — 6 ступеней, d_срz = 0.998 м, l₂z = 0.1348 м")
    void laysOutChsd() {
        PreliminaryStages.SectionLayout chsd = stages.chsd();

        assertEquals(6, chsd.stageCount());
        assertEquals(0.915, chsd.firstMeanDiameterM(), 1e-9);
        assertEquals(0.998, chsd.lastMeanDiameterM(), 1e-9);
        assertEquals(0.0518, chsd.firstBladeHeightM(), 1e-9);
        assertEquals(0.1348, chsd.lastBladeHeightM(), 1e-4);
        assertEquals(0.86325, chsd.lastRootDiameterM(), 1e-4);

        assertEquals(381.83, chsd.totalHeatDropKJkg(), 0.02);
        assertEquals(62.31, chsd.meanHeatDropKJkg(), 0.02);
        assertEquals(6.62, chsd.recoveryHeatKJkg(), 0.02);
        assertEquals(0.01764, chsd.recoveryCoefficient(), 1e-4);
        assertEquals(0.8775, chsd.efficiency(), 1e-4);
    }

    @Test
    @DisplayName("2.5.3: ЧНД — 4 ступени, d_срz = 1.4 м, l₂z = 0.35 м")
    void laysOutChnd() {
        PreliminaryStages.SectionLayout chnd = stages.chnd();

        assertEquals(4, chnd.stageCount());
        assertEquals(1.008, chnd.firstMeanDiameterM(), 1e-9);
        assertEquals(1.4, chnd.lastMeanDiameterM(), 1e-9);
        assertEquals(0.144, chnd.firstBladeHeightM(), 1e-9);
        assertEquals(0.35, chnd.lastBladeHeightM(), 1e-9);
        assertEquals(1.05, chnd.lastRootDiameterM(), 1e-9);

        assertEquals(352.68, chnd.totalHeatDropKJkg(), 0.02);
        assertEquals(90.44, chnd.meanHeatDropKJkg(), 0.02);
        assertEquals(4.49, chnd.recoveryHeatKJkg(), 0.02);
        assertEquals(0.01289, chnd.recoveryCoefficient(), 1e-4);
        assertEquals(0.7831, chnd.efficiency(), 1e-4);
    }

    @Test
    @DisplayName("Закон изменения: диаметры линейны, U/Ca и реактивность растут к последней ступени")
    void keepsSmoothSectionLaw() {
        PreliminaryStages.SectionLayout chvd = stages.chvd();

        assertEquals(0.39, chvd.stages().get(0).speedRatio(), 1e-12);
        assertEquals(0.42, chvd.stages().get(5).speedRatio(), 1e-12);
        assertEquals(0.05, chvd.stages().get(0).reaction(), 1e-12);
        assertEquals(0.1344, chvd.stages().get(5).reaction(), 1e-4);
        assertEquals(138.1, chvd.stages().get(0).circumferentialSpeedMS(), 0.1);
        assertEquals(142.8, chvd.stages().get(5).circumferentialSpeedMS(), 0.1);

        for (int index = 1; index < chvd.stageCount(); index++) {
            double previousDiameter = chvd.stages().get(index - 1).meanDiameterM();
            double diameter = chvd.stages().get(index).meanDiameterM();
            assertTrue(diameter > previousDiameter,
                    "средний диаметр ЧВД должен расти: " + previousDiameter + " → " + diameter);
            assertEquals(0.006, diameter - previousDiameter, 1e-9,
                    "шаг диаметра в ЧВД постоянен: " + diameter);
        }
        assertEquals(0.909, chvd.stages().get(5).meanDiameterM(), 1e-9);
    }

    @Test
    @DisplayName("Давления по ступеням падают от входа отсека к выходу")
    void interpolatesStagePressures() {
        for (PreliminaryStages.SectionLayout section : stages.sections()) {
            assertTrue(section.stages().get(0).outletPressureMPa()
                            < section.inletPressureMPa(),
                    "давление за первой ступенью " + section.name() + " должно быть ниже входа");
            assertEquals(section.outletPressureMPa(),
                    section.stages().get(section.stageCount() - 1).outletPressureMPa(), 1e-12);
            for (int index = 1; index < section.stageCount(); index++) {
                assertTrue(section.stages().get(index).outletPressureMPa()
                                < section.stages().get(index - 1).outletPressureMPa(),
                        "давление в " + section.name() + " должно падать по ступеням");
            }
        }
    }

    @Test
    @DisplayName("Суммы по отсекам сходятся с общим результатом 2.5")
    void totalsMatchSections() {
        assertEquals(16, stages.totalStages());
        assertEquals(347.31 + 381.83 + 352.68, stages.totalHeatDropKJkg(), 0.05);
        assertEquals(preliminary.expansion().regulating().internalWork()
                        + stages.internalWorkKJkg(),
                preliminary.expansion().internalWorkKJkg(), 0.01,
                "2.5 считает только нерегулируемые ступени; регулирующая ступень входит в h_i из 2.3");
        assertEquals(preliminary.expansion().refinedFlowKgS() * stages.internalWorkKJkg(),
                stages.totalPowerKW(), 1e-6);
        assertEquals(19.23, stages.recoveryHeatKJkg(), 0.02);
    }

    @Test
    @DisplayName("2.5 доступен и по готовому результату 2.1–2.4, и отдельным вызовом")
    void supportsBothEntryPoints() {
        PreliminaryStages direct = design.preliminaryStages();
        PreliminaryStages fromResult = design.preliminaryStages(preliminary);

        assertEquals(fromResult.chvd(), direct.chvd());
        assertEquals(fromResult.chsd(), direct.chsd());
        assertEquals(fromResult.chnd(), direct.chnd());
        assertEquals(StageAssumptions.reference(), design.stageAssumptions());
        assertNotNull(stages.describe());
        assertTrue(stages.describe().contains("2.5:"),
                () -> "строка отчёта должна содержать раздел 2.5: " + stages.describe());
    }

    @Test
    @DisplayName("Нулевой результат 2.1–2.4 не подменяется молча")
    void rejectsNullPreliminary() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> design.preliminaryStages(null));
        assertTrue(failure.getMessage().contains("2.1–2.4"),
                () -> "ошибка должна объяснять, чего не хватает: " + failure.getMessage());
    }
}

