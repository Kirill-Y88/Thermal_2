package org.kpush88.Thermal_2.turbine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kpush88.Thermal_2.turbine2.CourseProjectEfficiency;
import org.kpush88.Thermal_2.turbine2.HsDiagram;
import org.kpush88.Thermal_2.turbine2.PreliminaryEfficiency;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Согласие компактной модели с опорной машиной 10 МВт.
 *
 * <p>Тест лежит в пакете {@code turbine}, потому что {@link ReferenceTurbine} — фикстура этого
 * пакета и не экспортируется. Сравниваются не «абсолютные» числа, а два независимых способа
 * получить КПД: пошаговый марш ({@code StageSolver}) и эмпирическая формула. Допуски отражают
 * назначение модели: отсеки — 3 п.п., турбина — 1 п.п., ступени — 3 п.п.
 */
class PreliminaryEfficiencyReferenceTest {

    private static final PreliminaryEfficiency.StageKind NON_REG =
            PreliminaryEfficiency.StageKind.NON_REGULATING;
    private static final PreliminaryEfficiency MODEL = new PreliminaryEfficiency();

    @Test
    @DisplayName("Пять отсеков опорной машины: |Δη| ≤ 0.03")
    void sectionsMatchReference() {
        TurbineResult result = ReferenceTurbine.march();
        List<PreliminaryEfficiency.SectionEstimate> estimates = estimates();

        assertEquals(result.sections().size(), estimates.size());
        for (int index = 0; index < result.sections().size(); index++) {
            TurbineResult.SectionResult section = result.sections().get(index);
            PreliminaryEfficiency.SectionEstimate estimate = estimates.get(index);
            assertEquals(section.efficiency(), estimate.efficiency(), 0.03,
                    () -> section.name() + ": η эталона " + section.efficiency()
                            + ", оценка " + estimate.efficiency());
        }
    }

    @Test
    @DisplayName("КПД турбины: |Δη| ≤ 0.01")
    void turbineMatchesReference() {
        TurbineResult result = ReferenceTurbine.march();
        PreliminaryEfficiency.TurbineEstimate turbine = MODEL.turbine(estimates());

        assertEquals(result.efficiency(), turbine.efficiency(), 0.01,
                () -> "η эталона " + result.efficiency() + ", оценка "
                        + turbine.efficiency());
    }

    @Test
    @DisplayName("Все 27 ступеней: |Δη| ≤ 0.03, включая первую ЦВД и последнюю ЦНД")
    void everyStageStaysWithinThreePoints() {
        TurbineResult result = ReferenceTurbine.march();

        for (TurbineResult.StagePoint point : result.stages()) {
            StageResult stage = point.result();
            double flow = result.section(point.section()).flow();
            PreliminaryEfficiency.StageEstimate estimate = MODEL.stage(NON_REG, flow,
                    stage.inletPressure(), stage.inletEnthalpy(),
                    stage.outletPressure(), stage.outletEnthalpy());
            assertEquals(stage.efficiency(), estimate.efficiency(), 0.03,
                    () -> point.describe() + ": η эталона " + stage.efficiency()
                            + ", оценка " + estimate.efficiency());
        }
    }

    @Test
    @DisplayName("Первая ступень ЦВД и последняя ЦНД — границы калибровки")
    void boundaryStagesMatchReference() {
        TurbineResult result = ReferenceTurbine.march();
        List<TurbineResult.StagePoint> points = result.stages();

        TurbineResult.StagePoint first = points.get(0);
        double firstFlow = result.sections().get(0).flow();
        PreliminaryEfficiency.StageEstimate firstEstimate = MODEL.stage(NON_REG, firstFlow,
                first.result().inletPressure(), first.result().inletEnthalpy(),
                first.result().outletPressure(), first.result().outletEnthalpy());
        assertEquals(first.result().efficiency(), firstEstimate.efficiency(), 0.03,
                () -> "первая ступень ЦВД: " + first.describe());

        TurbineResult.StagePoint last = points.get(points.size() - 1);
        double lastFlow = result.section("ЦНД").flow();
        PreliminaryEfficiency.StageEstimate lastEstimate = MODEL.stage(NON_REG, lastFlow,
                last.result().inletPressure(), last.result().inletEnthalpy(),
                last.result().outletPressure(), last.result().outletEnthalpy());
        assertEquals(last.result().efficiency(), lastEstimate.efficiency(), 0.03,
                () -> "последняя ступень ЦНД: " + last.describe());
    }

    @Test
    @DisplayName("Формулы методички неприменимы к ЦВД 14 МПа: 0.5/(G′·V_ср) > 0.925, а не отрицательный КПД")
    void courseProjectFormulaRejectsReferenceHpCylinder() {
        TurbineResult result = ReferenceTurbine.march();
        TurbineResult.SectionResult hp = result.section("ЦВД");
        HsDiagram hs = new HsDiagram();
        double meanVolume = Math.sqrt(
                hs.volumePH(hp.inletPressure(), hp.inletEnthalpy())
                        * hs.volumePH(hp.outletPressure(), hp.outletEnthalpy()));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> CourseProjectEfficiency.section(CourseProjectEfficiency.Section.CHVD,
                        hp.flow(), hp.availableDrop(), meanVolume));

        assertTrue(failure.getMessage().contains("0,5/(G′·V_ср)"),
                () -> "ошибка должна называть слагаемое: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("вне области применимости"),
                () -> "ошибка должна объяснять причину: " + failure.getMessage());
    }

    private static List<PreliminaryEfficiency.SectionEstimate> estimates() {
        TurbineResult result = ReferenceTurbine.march();
        List<PreliminaryEfficiency.SectionEstimate> estimates = new ArrayList<>();
        for (int index = 0; index < result.sections().size(); index++) {
            TurbineResult.SectionResult section = result.sections().get(index);
            int stageCount = ReferenceTurbine.geometry().section(index).stages().size();
            estimates.add(MODEL.section(NON_REG, section.flow(), section.inletPressure(),
                    section.inletEnthalpy(), section.outletPressure(),
                    section.outletEnthalpy(), stageCount));
        }
        return estimates;
    }
}