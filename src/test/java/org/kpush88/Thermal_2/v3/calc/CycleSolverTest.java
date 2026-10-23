package org.kpush88.Thermal_2.v3.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kpush88.Thermal_2.v3.Scheme;
import org.kpush88.Thermal_2.v3.Turbine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Расчёт цикла: подбор расхода свежего пара под заданную электрическую мощность.
 *
 * <p>Стенд — регенеративный цикл {@link RegenerativeScheme} с заданием 1040.6 кВт на клеммах
 * генератора: столько даёт реперный расчёт v1 (1056.46 кВт внутренней мощности при
 * η_м·η_г = 0.985). Проверяется не только ответ, но и то, ради чего расчёт разбит на итерации:
 * <ul>
 *   <li>первое приближение промахивается на 15 %, а поправка коэффициента регенерации снимает
 *       промах за один шаг — и вторая итерация это подтверждает;</li>
 *   <li>результат совпадает с репертным расчётом v1 по мощности и КПД;</li>
 *   <li>энергетический баланс цикла замыкается, а журнал доходит до этапа {@code RESULT} без
 *       незаданных величин;</li>
 *   <li>повторный расчёт по той же схеме даёт тот же ответ: итерация начинается с исходных данных,
 *       а не с того, что посчитала предыдущая.</li>
 * </ul>
 */
class CycleSolverTest {

    @Test
    @DisplayName("Цикл сходится за две итерации: заданная мощность достигается поправкой k_рег")
    void convergesToGivenPower() {
        RegenerativeScheme bench = RegenerativeScheme.reference();

        CycleResult result = CycleSolver.of(bench.scheme).calculate();

        assertEquals(2, result.iterations(), () -> result.describe());
        assertEquals(CycleInputs.reference().electricPower(), result.calculatedElectricPower(), 1e-6,
                "расчётная мощность совпала с заданием");
        assertTrue(Math.abs(result.deviation()) <= CycleSolver.DEFAULT_TOLERANCE,
                () -> "σ = " + result.deviation());

        List<CycleResult.Iteration> history = result.history();
        assertTrue(Math.abs(history.get(0).deviation()) > 0.1,
                "первое приближение без поправки промахивается заметно: " + history.get(0).describe());
        assertTrue(Math.abs(history.get(1).deviation()) <= CycleSolver.DEFAULT_TOLERANCE,
                history.get(1).describe());
        assertEquals(1.0, history.get(0).regenerationCoefficient(), 1e-9,
                "расчёт начинается с коэффициента по умолчанию");
        assertEquals(result.regenerationCoefficient(), history.get(1).regenerationCoefficient(),
                1e-12, "найденный коэффициент — тот, с которым получен ответ");
    }

    @Test
    @DisplayName("Показатели цикла совпадают с реперным расчётом v1")
    void matchesReferenceCalculation() {
        RegenerativeScheme bench = RegenerativeScheme.reference();

        CycleResult result = CycleSolver.of(bench.scheme).calculate();

        assertEquals(1.0021, result.liveSteamFlow(), 1e-3, "D₀ ≈ 1 кг/с в реперном цикле");
        assertEquals(1.1774, result.regenerationCoefficient(), 1e-3, "надбавка на регенерацию");
        assertEquals(1056.45, result.internalPower(), 0.1, "N_i в v1 — 1056.46 кВт");
        assertEquals(33.21, result.pumpPower(), 0.1,
                "N_н в v1 — 32.19 кВт без конденсатного насоса");
        assertEquals(2293.77, result.boilerDuty(), 0.2, "Q_котла в v1 — 2292.83 кВт");
        assertEquals(1270.53, result.condenserDuty(), 0.2, "Q_к в v1 — 1268.56 кВт");
        assertEquals(0.4606, result.grossEfficiency(), 1e-4, "η_брутто в v1 — 46.08 %");
        assertEquals(0.4461, result.netEfficiency(), 1e-4, "η_нетто в v1 — 44.67 %");
        assertEquals(7935.4, result.heatRate(), 1.0,
                "удельный расход теплоты 3600·Q_котла/N_э, кДж/(кВт·ч)");
    }

    @Test
    @DisplayName("Энергетический баланс цикла замыкается: Q_котла + N_н = N_i + Q_к")
    void cycleEnergyBalanceCloses() {
        RegenerativeScheme bench = RegenerativeScheme.reference();

        CycleResult result = CycleSolver.of(bench.scheme).calculate();

        assertEquals(0.0, result.balanceResidual(), 1e-6,
                () -> "невязка баланса: " + result.describe());
        assertEquals(1023.23, result.internalPower() - result.pumpPower(), 0.1,
                "нетто-мощность: N_i − N_н");
    }

    @Test
    @DisplayName("Журнал доходит до этапа RESULT: незаданных величин не осталось")
    void journalReachesResult() {
        RegenerativeScheme bench = RegenerativeScheme.reference();

        CycleResult result = CycleSolver.of(bench.scheme).calculate();

        CalculationJournal journal = result.journal();
        assertEquals(Stage.RESULT, journal.last().stage(), journal::describe);
        assertTrue(journal.missing().isEmpty(), journal::describe);
        assertEquals(List.of(Stage.INPUT, Stage.NORMALIZED, Stage.RESOLVED, Stage.SOLVED,
                        Stage.RESULT),
                journal.states().stream().map(SchemeState::stage).toList(),
                "этапы идут по порядку, и RESULT — последний");
        assertEquals(new Origin(Stage.SOLVED, FlowSystem.OWNER),
                journal.origin(bench.hpExtraction, Quantity.FLOW));
    }

    @Test
    @DisplayName("Верное начальное приближение даёт одну итерацию: поправка не нужна")
    void startsFromGivenCoefficient() {
        RegenerativeScheme bench = RegenerativeScheme.reference();

        CycleResult result = CycleSolver.of(bench.scheme)
                .withRegenerationCoefficient(1.1774)
                .calculate();

        assertEquals(1, result.iterations(), result::describe);
        assertEquals(1.1774, result.history().get(0).regenerationCoefficient(), 1e-9);
        assertTrue(Math.abs(result.deviation()) <= CycleSolver.DEFAULT_TOLERANCE,
                () -> "σ = " + result.deviation());
    }

    @Test
    @DisplayName("Повторный расчёт по той же схеме даёт тот же ответ: итерация начинается с исходных данных")
    void repeatedCalculationIsIdentical() {
        RegenerativeScheme bench = RegenerativeScheme.reference();

        CycleResult first = CycleSolver.of(bench.scheme).calculate();
        CycleResult second = CycleSolver.of(bench.scheme).calculate();

        assertEquals(first.liveSteamFlow(), second.liveSteamFlow(), 1e-12,
                "посчитанные расходы не стали входными данными второй итерации");
        assertEquals(first.internalPower(), second.internalPower(), 1e-9);
        assertEquals(first.grossEfficiency(), second.grossEfficiency(), 1e-12);
        assertEquals(2, second.iterations(), second::describe);
        assertNull(bench.liveSteam.getFlow(), "схема осталась исходными данными: расход не записан");
    }

    @Test
    @DisplayName("Без заданной мощности турбоагрегата расчёт цикла не начинается")
    void requiresElectricPower() {
        Turbine turbine = new Turbine("Турбина", 0).withInternalEfficiency(0.85);
        Scheme scheme = new Scheme().add(turbine);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> CycleSolver.of(scheme).calculate());

        assertTrue(error.getMessage().contains("не задана электрическая мощность"),
                error.getMessage());
        assertTrue(error.getMessage().contains("withElectricPower"), error.getMessage());
    }

    @Test
    @DisplayName("Расчёт цикла ведётся по одной турбине: две — это другая задача")
    void requiresSingleTurbine() {
        Scheme scheme = new Scheme().add(new Turbine("Первая", 0)).add(new Turbine("Вторая", 0));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> CycleSolver.of(scheme).calculate());

        assertTrue(error.getMessage().contains("в схеме их 2"), error.getMessage());
    }

    @Test
    @DisplayName("Несошедшаяся итерация не выдаёт себя за результат: ошибка с историей")
    void reportsNonConvergence() {
        RegenerativeScheme bench = RegenerativeScheme.reference();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> CycleSolver.of(bench.scheme).withMaxIterations(1).calculate());

        assertTrue(error.getMessage().contains("не сошлась"), error.getMessage());
        assertTrue(error.getMessage().contains("итерация 1"), error.getMessage());
    }

    @Test
    @DisplayName("Настройки расчёта проверяются при задании")
    void settingsAreValidated() {
        RegenerativeScheme bench = RegenerativeScheme.reference();
        CycleSolver solver = CycleSolver.of(bench.scheme);

        assertThrows(IllegalArgumentException.class,
                () -> solver.withRegenerationCoefficient(0.0));
        assertThrows(IllegalArgumentException.class, () -> solver.withTolerance(0.0));
        assertThrows(IllegalArgumentException.class, () -> solver.withTolerance(1.0));
        assertThrows(IllegalArgumentException.class, () -> solver.withMaxIterations(0));
    }
}
