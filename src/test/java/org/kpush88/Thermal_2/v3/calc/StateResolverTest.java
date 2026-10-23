package org.kpush88.Thermal_2.v3.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kpush88.Thermal_2.v3.Boiler;
import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.MixingHeater;
import org.kpush88.Thermal_2.v3.Pipe;
import org.kpush88.Thermal_2.v3.PipeTee;
import org.kpush88.Thermal_2.v3.Pump;
import org.kpush88.Thermal_2.v3.Scheme;
import org.kpush88.Thermal_2.v3.SpaceType;
import org.kpush88.Thermal_2.v3.Turbine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Разрешение состояний: конвейер этапов, модели элементов и отчёт о незаданном.
 *
 * <p>Стенды — реальные участки схемы, а не заглушки: «котёл → паропровод → турбина» для
 * переноса по трассе и баланса, смешивающий подогреватель для баланса энергии, тройник для
 * слияния потоков, насос для энтальпии без работы. На них проверяется главное свойство
 * резолвера — он доводит до конца всё, что следует из уже известных величин, и <b>не
 * выдумывает</b> остальное: незаданное остаётся в {@link StateResolver.Report#missing()}.
 *
 * <p>Отдельно проверяются правила, ради которых резолвер устроен именно так: перенос работает
 * против порядка элементов (многопроходность), журнал можно доводить повторно (снапшоты не
 * пересчитываются), противоречивые ГУ отвергаются с указанием обоих происхождений, а модель
 * можно добавить снаружи.
 */
class StateResolverTest {

    private static final double PRESSURE = 25.0;
    private static final double ENTHALPY = 3300.0;
    private static final double FEEDWATER_ENTHALPY = 900.0;
    private static final double FLOW = 100.0;

    /** Участок «котёл → паропровод → турбина» с открытыми концами схемы. */
    private record Bench(Scheme scheme, Pipe pipe, Turbine turbine, Connection feedwater,
                         Connection liveSteam, Connection atTurbine, Connection exhaust) {
    }

    private static Bench buildBench() {
        Boiler boiler = new Boiler("Котёл");
        Pipe pipe = new Pipe("Паропровод свежего пара", SpaceType.STEAM);
        Turbine turbine = new Turbine("Турбина", 1);
        Scheme scheme = new Scheme().add(boiler).add(pipe).add(turbine);
        Connection feedwater = scheme.openIn(boiler.waterIn(), "питательная вода");
        Connection liveSteam = scheme.link(boiler.steamOut(), pipe.in(), "свежий пар");
        Connection atTurbine = scheme.link(pipe.out(), turbine.steamIn(), "свежий пар");
        Connection exhaust = scheme.openOut(turbine.exhaustOut(), "выхлоп");
        return new Bench(scheme, pipe, turbine, feedwater, liveSteam, atTurbine, exhaust);
    }

    /** Тот же участок с входными данными: расход питательной воды и состояние пара. */
    private static Bench inputBench() {
        Bench bench = buildBench();
        bench.feedwater().withState(PRESSURE, FEEDWATER_ENTHALPY).withFlow(FLOW);
        bench.liveSteam().withState(PRESSURE, ENTHALPY);
        return bench;
    }

    /** Две трубы подряд: состояние задано у турбины, то есть против порядка элементов. */
    private record LongLine(Scheme scheme, Connection firstPipe, Connection secondPipe,
                            Connection atTurbine, Connection exhaust) {
    }

    private static LongLine buildLongLine() {
        Boiler boiler = new Boiler("Котёл");
        Pipe first = new Pipe("Паропровод 1", SpaceType.STEAM);
        Pipe second = new Pipe("Паропровод 2", SpaceType.STEAM);
        Turbine turbine = new Turbine("Турбина", 0);
        Scheme scheme = new Scheme().add(boiler).add(first).add(second).add(turbine);
        Connection feedwater = scheme.openIn(boiler.waterIn(), "питательная вода");
        Connection firstPipe = scheme.link(boiler.steamOut(), first.in(), "свежий пар");
        Connection secondPipe = scheme.link(first.out(), second.in(), "свежий пар");
        Connection atTurbine = scheme.link(second.out(), turbine.steamIn(), "свежий пар");
        Connection exhaust = scheme.openOut(turbine.exhaustOut(), "выхлоп");
        feedwater.withFlow(FLOW);
        atTurbine.withState(PRESSURE, ENTHALPY);
        return new LongLine(scheme, firstPipe, secondPipe, atTurbine, exhaust);
    }

    private static StateResolver.StageResult stage(StateResolver.Report report, Stage stage) {
        return report.stages().stream()
                .filter(result -> result.stage() == stage)
                .findFirst()
                .orElseThrow(() -> new AssertionError("этап " + stage + " не выполнялся: "
                        + report.describe()));
    }


    @Test
    @DisplayName("Разрешение доводит журнал до SOLVED и переносит состояние по участку трассы")
    void resolvesPipelineOnSteamLine() {
        Bench bench = inputBench();
        CalculationJournal journal = CalculationJournal.of(bench.scheme());

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(List.of(Stage.INPUT, Stage.NORMALIZED, Stage.RESOLVED, Stage.SOLVED),
                journal.states().stream().map(SchemeState::stage).toList());
        assertEquals(Stage.SOLVED, report.reached());

        assertEquals(PRESSURE, bench.atTurbine().getPressure(), 1e-9, "давление за трубой");
        assertEquals(ENTHALPY, bench.atTurbine().getEnthalpy(), 1e-9, "энтальпия за трубой");
        assertEquals(FLOW, bench.atTurbine().getFlow(), 1e-9, "расход за трубой");
        assertEquals(FLOW, bench.exhaust().getFlow(), 1e-9, "расход через турбину");

        assertEquals(new Origin(Stage.NORMALIZED, bench.pipe().name()),
                journal.origin(bench.atTurbine(), Quantity.PRESSURE));
        assertEquals(new Origin(Stage.SOLVED, bench.turbine().name()),
                journal.origin(bench.exhaust(), Quantity.FLOW));

        assertEquals(2, stage(report, Stage.NORMALIZED).writes(), "труба переносит p и h");
        assertEquals(0, stage(report, Stage.RESOLVED).writes(), "насоса на стенде нет");
        assertEquals(3, stage(report, Stage.SOLVED).writes(),
                "котёл, труба и турбина добавляют по расходу");
    }

    @Test
    @DisplayName("Незаданное остаётся в отчёте: давление и энтальпия выхлопа не выводятся из ГУ")
    void reportsWhatIsStillMissing() {
        Bench bench = inputBench();
        CalculationJournal journal = CalculationJournal.of(bench.scheme());

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertFalse(report.isComplete());
        assertEquals(2, report.missing().size(), report::describe);
        assertTrue(report.missing().stream().allMatch(item -> item.connection() == bench.exhaust()),
                report::describe);
        assertEquals(List.of(Quantity.PRESSURE, Quantity.ENTHALPY),
                report.missing().stream().map(SchemeState.Missing::quantity).toList());
        assertTrue(report.describe().contains("NORMALIZED (перенос по участкам трассы)"),
                report::describe);
        assertTrue(report.describe().contains("не задано: 2 величин"), report::describe);
        assertTrue(report.describe().contains("выхлоп"), report::describe);
    }

    @Test
    @DisplayName("Перенос идёт против порядка элементов: проходы повторяются до неподвижной точки")
    void repeatsPassesUntilNothingChanges() {
        LongLine line = buildLongLine();
        CalculationJournal journal = CalculationJournal.of(line.scheme());

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(PRESSURE, line.firstPipe().getPressure(), 1e-9,
                "давление дошло от турбины до котла через две трубы");
        assertEquals(ENTHALPY, line.firstPipe().getEnthalpy(), 1e-9);
        assertEquals(PRESSURE, line.secondPipe().getPressure(), 1e-9);
        assertEquals(4, stage(report, Stage.NORMALIZED).writes(), "две трубы по две величины");
        assertTrue(stage(report, Stage.NORMALIZED).passes() > 1,
                "первый проход не мог увидеть состояние за второй трубой: " + report.describe());
        assertEquals(FLOW, line.exhaust().getFlow(), 1e-9);
    }

    @Test
    @DisplayName("Смешивающий подогреватель: энтальпия выхода — из баланса энергии входов")
    void mixesEnthalpyInMixingHeater() {
        MixingHeater heater = new MixingHeater("Смешивающий подогреватель");
        Scheme scheme = new Scheme().add(heater);
        Connection steam = scheme.openIn(heater.steamIn(), "греющий пар");
        Connection water = scheme.openIn(heater.waterIn(), "основной конденсат");
        Connection mixture = scheme.openOut(heater.waterOut(), "питательная вода");
        steam.withState(0.6, 3000.0).withFlow(10.0);
        water.withState(0.6, 600.0).withFlow(90.0);

        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(100.0, mixture.getFlow(), 1e-9, "расход смеси");
        assertEquals(840.0, mixture.getEnthalpy(), 1e-9,
                "(10·3000 + 90·600) / 100");
        assertEquals(new Origin(Stage.SOLVED, heater.name()),
                journal.origin(mixture, Quantity.ENTHALPY));
        assertEquals(List.of(Quantity.PRESSURE),
                report.missing().stream().map(SchemeState.Missing::quantity).toList(),
                "давление смеси гидравлика не задаёт, а модель его не выдумывает");
    }

    @Test
    @DisplayName("Тройник: давление переносится при согласованных входах, энтальпия — по смешению")
    void mergesTeeStreams() {
        PipeTee tee = new PipeTee("Сборный коллектор", SpaceType.WATER);
        Scheme scheme = new Scheme().add(tee);
        Connection first = scheme.openIn(tee.in(), "поток 1");
        Connection second = scheme.openIn(tee.in(), "поток 2");
        Connection merged = scheme.openOut(tee.out(), "смесь");
        first.withState(5.0, 3000.0).withFlow(10.0);
        second.withState(5.0, 500.0).withFlow(90.0);

        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(5.0, merged.getPressure(), 1e-9);
        assertEquals(750.0, merged.getEnthalpy(), 1e-9, "(10·3000 + 90·500) / 100");
        assertEquals(100.0, merged.getFlow(), 1e-9);
        assertEquals(new Origin(Stage.NORMALIZED, tee.name()),
                journal.origin(merged, Quantity.PRESSURE));
        assertEquals(new Origin(Stage.SOLVED, tee.name()),
                journal.origin(merged, Quantity.ENTHALPY));
        assertTrue(report.isComplete(), report::describe);
    }

    @Test
    @DisplayName("Тройник с разными давлениями на входах: давление выхода не выдумывается")
    void doesNotInventMergePressure() {
        PipeTee tee = new PipeTee("Сборный коллектор", SpaceType.WATER);
        Scheme scheme = new Scheme().add(tee);
        Connection first = scheme.openIn(tee.in(), "поток 1");
        Connection second = scheme.openIn(tee.in(), "поток 2");
        Connection merged = scheme.openOut(tee.out(), "смесь");
        first.withFlow(10.0);
        second.withFlow(90.0);
        first.withState(5.0, 3000.0);
        second.withState(4.0, 500.0);

        StateResolver.Report report = StateResolver.of(CalculationJournal.of(scheme)).resolve();

        assertEquals(750.0, merged.getEnthalpy(), 1e-9, "энтальпия от давлений не зависит");
        assertEquals(List.of(Quantity.PRESSURE), report.missing().stream()
                .map(SchemeState.Missing::quantity).toList());
    }

    @Test
    @DisplayName("Насос: энтальпия переносится на напор, давление остаётся неизвестным")
    void keepsEnthalpyThroughPump() {
        Pump pump = new Pump("Питательный насос");
        Scheme scheme = new Scheme().add(pump);
        Connection suction = scheme.openIn(pump.waterIn(), "всасывание");
        Connection pressure = scheme.openOut(pump.waterOut(), "напор");
        suction.withState(0.9, 500.0).withFlow(FLOW);

        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(500.0, pressure.getEnthalpy(), 1e-9);
        assertEquals(FLOW, pressure.getFlow(), 1e-9);
        assertEquals(new Origin(Stage.RESOLVED, pump.name()),
                journal.origin(pressure, Quantity.ENTHALPY));
        assertEquals(List.of(Quantity.PRESSURE), report.missing().stream()
                .map(SchemeState.Missing::quantity).toList());
    }


    @Test
    @DisplayName("Противоречивые ГУ на концах трубы отвергаются с обоими происхождениями")
    void rejectsContradictingLineInputs() {
        Bench bench = buildBench();
        bench.feedwater().withFlow(FLOW);
        bench.liveSteam().withState(PRESSURE, ENTHALPY);
        bench.atTurbine().withState(PRESSURE - 0.5, ENTHALPY);

        CalculationJournal journal = CalculationJournal.of(bench.scheme());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> StateResolver.of(journal).resolve());

        assertTrue(exception.getMessage().contains("Паропровод свежего пара"),
                exception::getMessage);
        assertTrue(exception.getMessage().contains("25.0"), exception::getMessage);
        assertTrue(exception.getMessage().contains("24.5"), exception::getMessage);
        assertTrue(exception.getMessage().contains("исходные данные схемы"),
                exception::getMessage);
    }

    @Test
    @DisplayName("Невязка массового баланса — ошибка, а не молчаливая запись")
    void rejectsMassImbalance() {
        Bench bench = buildBench();
        bench.feedwater().withState(PRESSURE, FEEDWATER_ENTHALPY).withFlow(FLOW);
        bench.liveSteam().withState(PRESSURE, ENTHALPY).withFlow(FLOW - 10);

        CalculationJournal journal = CalculationJournal.of(bench.scheme());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> StateResolver.of(journal).resolve());

        assertTrue(exception.getMessage().contains("Котёл"), exception::getMessage);
        assertTrue(exception.getMessage().contains("массовый баланс не сходится"),
                exception::getMessage);
    }

    @Test
    @DisplayName("Журнал можно доводить частями: зафиксированные этапы не пересчитываются")
    void resumesFromCapturedStage() {
        Bench bench = inputBench();
        CalculationJournal journal = CalculationJournal.of(bench.scheme());
        StateResolver resolver = StateResolver.of(journal);

        StateResolver.Report normalized = resolver.resolveTo(Stage.NORMALIZED);

        assertEquals(Stage.NORMALIZED, normalized.reached());
        assertEquals(Stage.NORMALIZED, journal.last().stage());
        assertThrows(IllegalArgumentException.class, () -> journal.state(Stage.RESOLVED));
        assertNull(bench.exhaust().getFlow(), "балансы ещё не решались");

        StateResolver.Report solved = resolver.resolve();

        assertEquals(Stage.SOLVED, solved.reached());
        assertEquals(List.of(Stage.RESOLVED, Stage.SOLVED),
                solved.stages().stream().map(StateResolver.StageResult::stage).toList());
        assertEquals(1, stage(solved, Stage.RESOLVED).passes());
        assertEquals(0, stage(solved, Stage.RESOLVED).writes());
        assertEquals(FLOW, bench.exhaust().getFlow(), 1e-9);
        assertEquals(PRESSURE, bench.atTurbine().getPressure(), 1e-9);
    }

    @Test
    @DisplayName("Повторное разрешение ничего не делает и не портит снапшоты")
    void secondResolveIsNoOp() {
        Bench bench = inputBench();
        CalculationJournal journal = CalculationJournal.of(bench.scheme());
        StateResolver resolver = StateResolver.of(journal);
        resolver.resolve();
        SchemeState solved = journal.state(Stage.SOLVED);

        StateResolver.Report again = resolver.resolve();

        assertTrue(again.stages().isEmpty(), again::describe);
        assertEquals(Stage.SOLVED, again.reached());
        assertTrue(again.describe().contains("этап уже зафиксирован"), again::describe);
        assertEquals(0, again.writes());
        assertSame(solved, journal.last());
        assertEquals(PRESSURE, bench.atTurbine().getPressure(), 1e-9);
    }

    @Test
    @DisplayName("Неподключённые порты не ломают разрешение: схема просто неполна")
    void toleratesIncompleteScheme() {
        Scheme scheme = new Scheme().add(new Pipe("Пустая труба", SpaceType.STEAM));
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(Stage.SOLVED, report.reached());
        assertEquals(0, report.writes());
        assertTrue(report.isComplete(), "величин в схеме нет — и незаданных тоже");
    }

    @Test
    @DisplayName("Модель можно добавить снаружи: этап RESOLVED принимает и чужие правила")
    void acceptsExternalModel() {
        Bench bench = inputBench();
        CalculationJournal journal = CalculationJournal.of(bench.scheme());
        Connection exhaust = bench.exhaust();
        ElementModel exhaustPressure = new ElementModel() {
            @Override
            public String title() {
                return "давление выхлопа по ГУ";
            }

            @Override
            public Stage stage() {
                return Stage.RESOLVED;
            }

            @Override
            public boolean supports(Element element) {
                return element == bench.turbine();
            }

            @Override
            public int resolve(Element element, StateAccess access) {
                return access.write(exhaust, Quantity.PRESSURE, 0.004) ? 1 : 0;
            }
        };

        StateResolver.Report report = StateResolver.of(journal).with(exhaustPressure).resolve();

        assertEquals(0.004, exhaust.getPressure(), 1e-9);
        assertEquals(new Origin(Stage.RESOLVED, bench.turbine().name()),
                journal.origin(exhaust, Quantity.PRESSURE));
        assertEquals(1, stage(report, Stage.RESOLVED).writes());
        assertEquals(List.of(Quantity.ENTHALPY), report.missing().stream()
                .map(SchemeState.Missing::quantity).toList());
    }
}

