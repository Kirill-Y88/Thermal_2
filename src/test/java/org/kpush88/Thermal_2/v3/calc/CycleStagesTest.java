package org.kpush88.Thermal_2.v3.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kpush88.Thermal_2.v3.Connection;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Стартовые параметры и данные каждого этапа: параметры схемы задаются в тесте, расчёт
 * запускается, а результат читается по этапам.
 *
 * <p><b>Что такое «данные этапа».</b> Снапшот журнала ({@link SchemeState}): давление, энтальпия
 * и расход в каждом сечении вместе с происхождением. Снапшоты берутся из журнала результата
 * ({@link CycleResult#journal()}), поэтому видно, что схема знала на входе, что перенесли участки
 * трассы, что посчитали аппараты, что решили балансы и что осталось показателям цикла. Чтение по
 * этапам собрано в {@link StageData}: {@link StageData#of(CalculationJournal)} — этапы по порядку,
 * {@link StageData#summary} — сводка, {@link StageData#table} — таблица «сечение × этап».
 *
 * <p><b>Отчёт печатается в консоль.</b> {@code mvn -Dtest=CycleStagesTest test} показывает
 * стартовые параметры, сводку по этапам и таблицу величин. Проверки относятся не к печати, а к
 * значениям: у каждого этапа своя — то, что он обязан был добавить.
 *
 * <p>Схему собирает {@link RegenerativeScheme#of(CycleInputs)}, поэтому «другие стартовые
 * параметры» — это другой набор данных для той же схемы, а не другая схема.
 */
class CycleStagesTest {

    /** Сечения, по которым печатается таблица: вся трасса от котла до котла. */
    private static List<Connection> traced(RegenerativeScheme bench) {
        return List.of(bench.liveSteam, bench.atTurbine, bench.hpExtraction,
                bench.deaeratorExtraction, bench.lpExtraction, bench.exhaust, bench.condensate,
                bench.feedwater);
    }

    /** Печатает то, ради чего тест написан: параметры, этапы и величины по этапам. */
    private static void print(CycleInputs inputs, RegenerativeScheme bench, CycleResult result) {
        List<StageData> stages = StageData.of(result.journal());
        System.out.println(inputs.describe());
        System.out.println(StageData.summary(stages));
        System.out.println(StageData.table(stages, traced(bench)));
        System.out.println(result.describe());
    }

    @Test
    @DisplayName("Расчёт отдаёт данные каждого этапа: вход → перенос → состояния → расходы → показатели")
    void reportsDataFromEveryStage() {
        CycleInputs inputs = CycleInputs.reference();
        RegenerativeScheme bench = RegenerativeScheme.of(inputs);
        CycleResult result = CycleSolver.of(bench.scheme).calculate();

        List<StageData> stages = StageData.of(result.journal());
        print(inputs, bench, result);

        assertEquals(List.of(Stage.INPUT, Stage.NORMALIZED, Stage.RESOLVED, Stage.SOLVED,
                        Stage.RESULT), stages.stream().map(StageData::stage).toList(),
                "журнал фиксирует этапы по порядку расчёта");
        assertTrue(result.journal().describe().contains("RESULT (показатели рассчитаны)"),
                result.journal().describe());

        // 1. Вход: известны только ГУ — давления отборов и вакуум. Энтальпий и расходов нет.
        StageData input = StageData.of(result.journal(), Stage.INPUT);
        assertEquals(4, input.state().filled(),
                "на входе — только давления отборов и вакуум: " + input.state().describe());
        assertEquals(inputs.highPressureExtraction(),
                input.value(bench.hpExtraction, Quantity.PRESSURE), 1e-9,
                "давление отбора задано исходными данными");
        assertEquals(inputs.condenserPressure(), input.value(bench.exhaust, Quantity.PRESSURE),
                1e-12, "вакуум задан исходными данными");
        assertEquals(CalculationJournal.SCHEME_INPUT_OWNER,
                input.origin(bench.hpExtraction, Quantity.PRESSURE).owner(),
                "у входных данных свой владелец, а не модель");
        assertNull(input.value(bench.atTurbine, Quantity.ENTHALPY),
                "энтальпию свежего пара режим не задаёт — её посчитает котёл");
        assertTrue(input.missing().contains(
                        new SchemeState.Missing(bench.exhaust, Quantity.FLOW)),
                "расход выхлопа на входе неизвестен: " + input.missing().size() + " незаданных");

        // 2. Перенос по участкам трассы: известное давление доходит до аппарата, остальное не трогается.
        StageData normalized = StageData.of(result.journal(), Stage.NORMALIZED);
        assertTrue(normalized.added() > 0, normalized.describe());
        assertTrue(normalized.changes().stream()
                        .allMatch(change -> change.quantity() == Quantity.PRESSURE),
                "участки трассы переносят давление: " + normalized.state().describe());
        assertNull(normalized.value(bench.atTurbine, Quantity.PRESSURE),
                "свежий пар ещё не посчитан: котёл работает на этапе RESOLVED");

        // 3. Состояния на выходах аппаратов: энтальпии есть, расходов ещё нет.
        StageData resolved = StageData.of(result.journal(), Stage.RESOLVED);
        assertEquals(inputs.livePressure(), resolved.value(bench.atTurbine, Quantity.PRESSURE),
                1e-9, "котёл задаёт давление свежего пара");
        double liveEnthalpy = resolved.value(bench.atTurbine, Quantity.ENTHALPY);
        assertTrue(liveEnthalpy > 3000 && liveEnthalpy < 3500, "h₀ = " + liveEnthalpy);
        assertEquals(Stage.RESOLVED,
                resolved.origin(bench.atTurbine, Quantity.ENTHALPY).stage(),
                "энтальпию посчитал аппарат, а не вход");
        assertEquals(121.4, resolved.value(bench.condensate, Quantity.ENTHALPY), 1.0,
                "конденсат — на линии насыщения при давлении в конденсаторе");
        assertNull(resolved.value(bench.liveSteam, Quantity.FLOW),
                "расходы решаются на следующем этапе: " + resolved.describe());

        // 4. Балансы: появились расходы, и только они.
        StageData solved = StageData.of(result.journal(), Stage.SOLVED);
        assertTrue(solved.changes().stream().allMatch(change -> change.quantity() == Quantity.FLOW),
                "балансы добавляют только расходы: " + solved.state().describe());
        assertEquals(result.liveSteamFlow(), solved.value(bench.liveSteam, Quantity.FLOW), 1e-12,
                "расход на границе схемы — тот, что нашла внешняя итерация");
        assertEquals(1.0021, solved.value(bench.liveSteam, Quantity.FLOW), 1e-3,
                () -> "D₀ в реперном цикле: " + solved.value(bench.liveSteam, Quantity.FLOW));
        assertEquals(0.1771, solved.value(bench.hpExtraction, Quantity.FLOW), 2e-3,
                () -> "отбор на ПВД: " + solved.value(bench.hpExtraction, Quantity.FLOW));
        assertEquals(0.0633, solved.value(bench.deaeratorExtraction, Quantity.FLOW), 2e-3,
                () -> "отбор на деаэратор: "
                        + solved.value(bench.deaeratorExtraction, Quantity.FLOW));
        assertEquals(0.1155, solved.value(bench.lpExtraction, Quantity.FLOW), 2e-3,
                () -> "отбор на ПНД: " + solved.value(bench.lpExtraction, Quantity.FLOW));
        assertEquals(0.6462, solved.value(bench.exhaust, Quantity.FLOW), 2e-3,
                () -> "выхлоп в конденсатор: " + solved.value(bench.exhaust, Quantity.FLOW));
        assertEquals(0.7617, solved.value(bench.condensate, Quantity.FLOW), 2e-3,
                () -> "конденсат: " + solved.value(bench.condensate, Quantity.FLOW));
        assertEquals(solved.value(bench.liveSteam, Quantity.FLOW),
                solved.value(bench.feedwater, Quantity.FLOW), 1e-9,
                "цикл замкнут: вся питательная вода возвращается в котёл");
        assertTrue(solved.missing().isEmpty(), "после балансов незаданных величин нет");

        // 5. Показатели: нового состояния сечения не появляется — числа живут в CycleResult.
        StageData outcome = StageData.of(result.journal(), Stage.RESULT);
        assertTrue(outcome.changes().isEmpty(),
                "мощности и КПД — свёртка состояний, а не состояние сечения");
        assertEquals(solved.state().filled(), outcome.state().filled(),
                "снапшот RESULT совпадает с SOLVED по значениям");
        assertTrue(outcome.missing().isEmpty(), outcome.describe());
        assertFalse(Double.isNaN(result.internalPower()), result.describe());
        assertEquals(result.internalPower(), result.electricPower()
                / inputs.electricEfficiency(), 1e-6, "N_i = N_э / η_эм");
        assertNull(bench.liveSteam.getFlow(),
                "схема осталась исходными данными: посчитанное живёт в журнале результата");
    }

    @Test
    @DisplayName("Стартовые параметры задаются в тесте: другой режим — другой расчёт по той же схеме")
    void startingParametersDefineTheCalculation() {
        CycleResult reference = CycleSolver.of(RegenerativeScheme.of(CycleInputs.reference()).scheme)
                .calculate();

        CycleInputs inputs = CycleInputs.builder()
                .liveSteam(12.75, 510.0)
                .condenserPressure(0.005)
                .build();
        RegenerativeScheme bench = RegenerativeScheme.of(inputs);
        CycleResult result = CycleSolver.of(bench.scheme).calculate();

        print(inputs, bench, result);

        StageData input = StageData.of(result.journal(), Stage.INPUT);
        assertEquals(0.005, input.value(bench.exhaust, Quantity.PRESSURE), 1e-12,
                "на входе — заданный вакуум, а не реперный");
        StageData resolved = StageData.of(result.journal(), Stage.RESOLVED);
        assertEquals(12.75, resolved.value(bench.atTurbine, Quantity.PRESSURE), 1e-9,
                "в расчёт пошло заданное давление свежего пара");
        StageData solved = StageData.of(result.journal(), Stage.SOLVED);
        assertEquals(result.liveSteamFlow(), solved.value(bench.liveSteam, Quantity.FLOW), 1e-12,
                "расход найден для этого режима");

        assertTrue(result.grossEfficiency() < reference.grossEfficiency(),
                "ниже параметры пара и хуже вакуум — ниже КПД: " + result.grossEfficiency()
                        + " против " + reference.grossEfficiency());
        assertTrue(result.liveSteamFlow() > reference.liveSteamFlow(),
                "меньше теплоперепад — больше пара на ту же мощность: " + result.liveSteamFlow()
                        + " против " + reference.liveSteamFlow());
        assertEquals(result.electricPower(), result.calculatedElectricPower(), 1e-6,
                "задание по мощности выполнено и в другом режиме");
    }

    @Test
    @DisplayName("Давление питательной воды: по умолчанию p₀, задаётся отдельно — напор ПН и энтальпия растут")
    void feedwaterPressureIsSetByStartParameters() {
        CycleInputs referenceInputs = CycleInputs.reference();
        RegenerativeScheme referenceBench = RegenerativeScheme.of(referenceInputs);
        CycleResult reference = CycleSolver.of(referenceBench.scheme).calculate();
        StageData referenceSolved = StageData.of(reference.journal(), Stage.SOLVED);

        assertEquals(referenceInputs.livePressure(), referenceInputs.feedwaterPressure(), 1e-12,
                "по умолчанию питательная вода идёт с давлением свежего пара, как в прототипе v1");
        assertEquals(24.0, referenceSolved.value(referenceBench.feedwater, Quantity.PRESSURE),
                1e-9, "реперное давление питательной воды — 24 МПа, как у свежего пара");
        double referenceEnthalpy = referenceSolved.value(referenceBench.feedwater,
                Quantity.ENTHALPY);

        CycleInputs inputs = CycleInputs.builder().feedwaterPressure(31.2).build();
        RegenerativeScheme bench = RegenerativeScheme.of(inputs);
        CycleResult result = CycleSolver.of(bench.scheme).calculate();

        print(inputs, bench, result);

        StageData solved = StageData.of(result.journal(), Stage.SOLVED);
        assertEquals(31.2, solved.value(bench.feedwater, Quantity.PRESSURE), 1e-9,
                "заданное давление питательной воды доходит до котла");
        assertEquals(inputs.feedwaterPressure() - inputs.deaeratorPressure(),
                bench.feedPump.getPressureRise(), 1e-9,
                "напор питательного насоса = p_пв − p_деаэратора");
        assertTrue(solved.value(bench.feedwater, Quantity.ENTHALPY) > referenceEnthalpy,
                "сжатие сильнее — энтальпия питательной воды выше: "
                        + solved.value(bench.feedwater, Quantity.ENTHALPY)
                        + " против " + referenceEnthalpy);
        assertTrue(result.pumpPower() > reference.pumpPower(),
                "выше напор — больше мощность насосов: " + result.pumpPower()
                        + " против " + reference.pumpPower());
        assertTrue(result.netEfficiency() < reference.netEfficiency(),
                "работа насосов вычитается из КПД нетто: " + result.netEfficiency()
                        + " против " + reference.netEfficiency());
        assertEquals(result.electricPower(), result.calculatedElectricPower(), 1e-6,
                "задание по мощности выполнено");
    }

    @Test
    @DisplayName("Несогласованные стартовые параметры отвергаются с указанием, что именно не так")
    void rejectsInconsistentParameters() {
        IllegalArgumentException deaerator = assertThrows(IllegalArgumentException.class,
                () -> CycleInputs.builder().deaeratorPressure(0.9).build());
        assertTrue(deaerator.getMessage().contains("деаэратора"), deaerator.getMessage());
        assertTrue(deaerator.getMessage().contains("0.9"), deaerator.getMessage());

        IllegalArgumentException vacuum = assertThrows(IllegalArgumentException.class,
                () -> CycleInputs.builder().condenserPressure(0.2).build());
        assertTrue(vacuum.getMessage().contains("конденсаторе"), vacuum.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> CycleInputs.builder().liveSteam(0.0, 540.0));
        assertThrows(IllegalArgumentException.class,
                () -> CycleInputs.builder().internalEfficiency(1.2));
        assertThrows(IllegalArgumentException.class,
                () -> CycleInputs.builder().subcooling(-1.0));
        assertThrows(IllegalArgumentException.class,
                () -> CycleInputs.builder().electricPower(0.0));
    }
}
