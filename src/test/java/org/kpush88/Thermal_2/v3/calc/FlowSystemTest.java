package org.kpush88.Thermal_2.v3.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Scheme;
import org.kpush88.Thermal_2.v3.SurfaceHeater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Система балансов: расходы регенеративного цикла из балансов массы и энергии.
 *
 * <p>Числа — доли расхода свежего пара (D₀ = 1 кг/с), и они совпадают с реперным расчётом
 * {@code v1.cycle.RegenerativeCycle} с точностью до процента: в v1 давление отбора на деаэратор
 * было равно рабочему давлению деаэратора (0.6 МПа), а здесь пар дросселируется с 0.7 до 0.6 МПа,
 * поэтому энтальпия отбора чуть выше и отбор на деаэратор чуть меньше.
 *
 * <p>Проверяются не только значения, но и три свойства, ради которых система устроена именно так:
 * <ul>
 *   <li>балансы определяют расходы только вместе с граничным условием — без него замкнутый цикл
 *       определён с точностью до масштаба, и система об этом сообщает рангом, а не «удобным»
 *       решением;</li>
 *   <li>баланс энергии аппарата — настоящее уравнение: он и даёт расход отбора;</li>
 *   <li>энтальпия, которая не разрешена, не превращается в нулевой коэффициент, а называется в
 *       сообщении.</li>
 * </ul>
 */
class FlowSystemTest {

    /** Допуск расходов: доли от D₀, посчитанные через IF97 и QR-разложение. */
    private static final double FLOW = 1e-6;

    private static CalculationJournal resolved(RegenerativeScheme bench) {
        CalculationJournal journal = CalculationJournal.of(bench.scheme);
        StateResolver.of(journal).resolveTo(Stage.RESOLVED);
        return journal;
    }

    @Test
    @DisplayName("Балансы цикла: расходы отборов, выхлопа и конденсата при D₀ = 1 кг/с")
    void solvesReferenceCycle() {
        RegenerativeScheme bench = RegenerativeScheme.reference();
        CalculationJournal journal = resolved(bench);

        FlowSystem.FlowSolution solution = FlowSystem.of(journal)
                .withFlow(bench.liveSteam, 1.0)
                .solve();

        assertEquals(27, solution.unknowns(), "расходы всех сечений неизвестны до решения");
        assertEquals(28, solution.equations(), "24 баланса массы и 3 баланса энергии плюс граница");
        assertEquals(27, solution.rank(), "ранг равен числу неизвестных: решение единственно");
        assertTrue(solution.residual() < 1e-9, () -> solution.describe());

        assertEquals(1.0, solution.flow(bench.liveSteam), FLOW, "расход свежего пара");
        assertEquals(0.176736, solution.flow(bench.hpExtraction), FLOW, "отбор ПВД");
        assertEquals(0.063209, solution.flow(bench.deaeratorExtraction), FLOW, "отбор деаэратора");
        assertEquals(0.115224, solution.flow(bench.lpExtraction), FLOW, "отбор ПНД");
        assertEquals(0.644830, solution.flow(bench.exhaust), FLOW, "выхлоп в конденсатор");
        assertEquals(0.760055, solution.flow(bench.condensate), FLOW, "основной конденсат");
        assertEquals(1.0, solution.flow(bench.feedwater), FLOW, "питательная вода");
        assertEquals(new Origin(Stage.SOLVED, FlowSystem.OWNER),
                journal.origin(bench.hpExtraction, Quantity.FLOW),
                "расход подписан решением системы, а не моделью элемента");
    }

    @Test
    @DisplayName("Расходы замыкаются: сумма отборов и выхлопа — свежий пар, конденсат — выхлоп и дренаж")
    void balancesClose() {
        RegenerativeScheme bench = RegenerativeScheme.reference();
        CalculationJournal journal = resolved(bench);

        FlowSystem.FlowSolution solution = FlowSystem.of(journal)
                .withFlow(bench.liveSteam, 1.0)
                .solve();

        double extractions = solution.flow(bench.hpExtraction)
                + solution.flow(bench.deaeratorExtraction)
                + solution.flow(bench.lpExtraction);
        assertEquals(1.0, extractions + solution.flow(bench.exhaust), 1e-9,
                "баланс массы турбины");
        assertEquals(0.760055, solution.flow(bench.exhaust) + solution.flow(bench.lpExtraction), FLOW,
                "конденсатор: выхлоп и дренаж ПНД дают конденсат");
    }

    @Test
    @DisplayName("Без граничного расхода замкнутый цикл не определён: сообщается ранг, а не решение")
    void closedCycleWithoutBoundaryIsUnderdetermined() {
        RegenerativeScheme bench = RegenerativeScheme.reference();
        CalculationJournal journal = resolved(bench);

        FlowSystem system = FlowSystem.of(journal);

        IllegalStateException error = assertThrows(IllegalStateException.class, system::solve);

        assertTrue(error.getMessage().contains("не определяют 1 из 27"), error.getMessage());
        assertTrue(error.getMessage().contains("ранг 26"), error.getMessage());
        assertTrue(error.getMessage().contains("G «"), "неопределяемый расход назван: "
                + error.getMessage());
    }

    @Test
    @DisplayName("Нерешённая энтальпия не превращается в нулевой коэффициент: она называется")
    void missingEnthalpyBlocksEnergyBalance() {
        SurfaceHeater heater = new SurfaceHeater("ПНД без недогрева");
        Scheme scheme = new Scheme().add(heater);
        Connection steam = scheme.openIn(heater.steamIn(), "греющий пар");
        scheme.openOut(heater.drainOut(), "дренаж");
        Connection water = scheme.openIn(heater.waterIn(), "основной конденсат");
        Connection heated = scheme.openOut(heater.waterOut(), "основной конденсат");
        steam.withState(0.12, 2393.24);
        water.withState(1.0, 121.40);
        CalculationJournal journal = CalculationJournal.of(scheme);
        StateResolver.of(journal).resolveTo(Stage.RESOLVED);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> FlowSystem.of(journal));

        assertTrue(error.getMessage().contains("не разрешена"), error.getMessage());
        assertTrue(error.getMessage().contains(heated.getName()), error.getMessage());
        assertTrue(error.getMessage().contains(heater.name()), error.getMessage());
    }

    @Test
    @DisplayName("Балансы решаются после состояний: до этапа SOLVED система не собирается")
    void requiresResolvedStates() {
        RegenerativeScheme bench = RegenerativeScheme.reference();
        CalculationJournal journal = CalculationJournal.of(bench.scheme);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> FlowSystem.of(journal));

        assertTrue(error.getMessage().contains("сначала разрешите состояния"), error.getMessage());
    }

    @Test
    @DisplayName("Заданный расход не переписывается граничным условием")
    void boundaryDoesNotOverwriteGivenFlow() {
        RegenerativeScheme bench = RegenerativeScheme.reference();
        bench.liveSteam.withFlow(2.0);
        CalculationJournal journal = resolved(bench);

        FlowSystem system = FlowSystem.of(journal);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> system.withFlow(bench.liveSteam, 1.0));

        assertTrue(error.getMessage().contains("уже задан 2.0"), error.getMessage());
        assertEquals(26, system.unknowns().size(), "заданный расход в неизвестные не попадает");
    }
}
