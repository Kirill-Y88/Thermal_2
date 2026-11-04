package org.kpush88.Thermal_2.turbine2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Полный конструкторский расчёт турбины по методичке курсового проекта: 2.1–2.5, 3.1–3.4
 * и 4.1–4.3.
 *
 * <p><b>Как задать свой вариант.</b> Измените {@link #DROPS} и {@link #INPUT}. Давления в
 * {@link DesignInput#ofBar} задаются в барах, как в методичке; внутри они переводятся в МПа.
 * Допущения при необходимости заменяются через
 * {@code TurbineDesign.builder(INPUT).assumptions(...).stageAssumptions(...).build()}.
 *
 * <p><b>Запуск.</b>
 * <pre>{@code
 * mvn test -Dtest=CourseProjectDesignTest
 * }</pre>
 * В консоль выводится протокол с результатами всех разделов. Проверки в конце — это лишь
 * контроль целостности результата; при изменении варианта их не нужно подгонять под числа.
 */
class CourseProjectDesignTest {

    private static final DesignInput.SectionHeatDrops DROPS =
            new DesignInput.SectionHeatDrops(339.19, 375.21, 347.35);

    private static final DesignInput INPUT = DesignInput.ofBar(8.0, 28.0, 430.0, 0.04,
            3000.0, CourseProjectEfficiency.RegulatingKind.DOUBLE_ROW, DROPS);

    @Test
    @DisplayName("2.1–4.3: полный конструкторский расчёт и протокол")
    void calculatesFullCourseProject() {
        TurbineDesign design = TurbineDesign.builder(INPUT).build();

        PreliminaryResult preliminary = design.preliminary();
        PreliminaryStages stages = design.preliminaryStages(preliminary);
        RegulatingStageDetail regulating = design.regulatingStageDetail(preliminary);
        StageDetail first = design.firstStageDetail(preliminary, stages);
        List<StageDetail> last = design.lastStageDetails(preliminary, stages);
        TwistResult twist = design.lastStageTwist(preliminary, stages);
        StrengthResults strength = design.strength(preliminary, stages);

        printReport(preliminary, stages, regulating, first, last, twist, strength);

        assertEquals(3, last.size(), "3.3 должна вернуть три последние ступени");
        assertEquals(stages.totalStages(), last.get(last.size() - 1).globalNumber(),
                "последняя ступень должна иметь номер, равный числу нерегулируемых ступеней");
        assertEquals(stages.totalStages(), strength.stageCount(),
                "прочность 4.1 должна считаться по всем нерегулируемым ступеням");
        assertTrue(regulating.internalEfficiency() > 0.0
                        && regulating.internalEfficiency() < 1.0,
                "КПД регулирующей ступени должен лежать в (0, 1)");
        assertTrue(first.internalEfficiency() > 0.0 && first.internalEfficiency() < 1.0,
                "КПД первой нерегулируемой ступени должен лежать в (0, 1)");
        assertEquals(twist.totalFlowKgS(), twist.designFlowKgS(), 1e-9,
                "сумма расходов участков 3.4 должна совпадать с расчётным расходом");
        assertTrue(strength.blade().safetyFactor() >= 1.65,
                "запас рабочей лопатки должен быть не меньше 1.65");
        assertTrue(strength.diaphragm().safetyFactor() >= 2.0,
                "запас диафрагмы должен быть не меньше 2");
    }

    private static void printReport(PreliminaryResult preliminary, PreliminaryStages stages,
                                    RegulatingStageDetail regulating, StageDetail first,
                                    List<StageDetail> last, TwistResult twist,
                                    StrengthResults strength) {
        StringBuilder report = new StringBuilder();
        report.append(String.format(Locale.ROOT,
                        "Исходные данные: N = %.2f МВт, P₀ = %.2f МПа, t₀ = %.0f °C, "
                                + "P_к = %.3f МПа, n = %.0f об/мин",
                        INPUT.nominalPowerMW(), INPUT.inletPressureMPa(),
                        INPUT.inletTemperatureC(), INPUT.exhaustPressureMPa(),
                        INPUT.rotationRpm()))
                .append(System.lineSeparator());
        report.append(preliminary.describe()).append(System.lineSeparator());
        report.append(stages.describe()).append(System.lineSeparator());
        report.append(regulating.describe()).append(System.lineSeparator());
        report.append(first.describe()).append(System.lineSeparator());
        for (StageDetail stage : last) {
            report.append(stage.describe()).append(System.lineSeparator());
        }
        report.append(twist.describe()).append(System.lineSeparator());
        report.append(strength.describe());
        System.out.println(report);
    }
}
