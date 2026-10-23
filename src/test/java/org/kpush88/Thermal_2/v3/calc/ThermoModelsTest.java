package org.kpush88.Thermal_2.v3.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kpush88.Thermal_2.v3.Boiler;
import org.kpush88.Thermal_2.v3.Condenser;
import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Pipe;
import org.kpush88.Thermal_2.v3.Pump;
import org.kpush88.Thermal_2.v3.Scheme;
import org.kpush88.Thermal_2.v3.SpaceType;
import org.kpush88.Thermal_2.v3.SurfaceHeater;
import org.kpush88.Thermal_2.v3.Turbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Термодинамические модели аппаратов: параметры конструкции превращаются в состояние.
 *
 * <p>Стенды — минимальные участки схемы вокруг одного аппарата: котёл с выходом за границу,
 * турбина с отбором и выхлопом, подогреватель с дренажом и водой, конденсатор, насос. Так
 * видно, что каждая модель делает <b>только своё</b>: котёл задаёт свежий пар, турбина считает
 * отборы по изоэнтропе, подогреватель — дренаж и недогрев, конденсатор — конденсат, насос —
 * напор и работу. Числа взяты из IF97 (24 МПа / 540 °C, отбор 3.5 МПа, вакуум 0.004 МПа) и
 * совпадают с реперными значениями {@code theory/06} и {@code theory/09}.
 *
 * <p>Отдельно проверяются три правила, ради которых модели устроены именно так:
 * <ul>
 *   <li>параметры свежего пара — это определение состояния, поэтому расхождение с ГУ на
 *       выходе котла отвергается, а не замалчивается;</li>
 *   <li>остальные модели только дополняют состояние: уже известное значение сильнее
 *       выведенного, и модель его не перезаписывает;</li>
 *   <li>чего не хватает для расчёта, остаётся в {@code missing()}: модель не подставляет
 *       правдоподобные числа вместо отсутствующих параметров.</li>
 * </ul>
 * В конце — сквозная проверка цикла: котёл → турбина → конденсатор → насос → котёл. После
 * разрешения известны все давления и энтальпии, и незаданными остаются только расходы, то есть
 * ровно то, что должна решить СЛАУ.
 */
class ThermoModelsTest {

    private static final double LIVE_PRESSURE = 24.0;
    private static final double LIVE_TEMPERATURE = 540.0;
    private static final double LIVE_ENTHALPY = 3318.775433689934;
    private static final double EFFICIENCY = 0.85;
    private static final double EXTRACTION_PRESSURE = 3.5;
    private static final double CONDENSER_PRESSURE = 0.004;

    /**
     * h отбора 3.5 МПа: h_вх − η·(h_вх − h_ks). Совпадает с h_с из theory/09; допуск проверок
     * 0.01 кДж/кг — точность обратных функций IF97 (энтропия берётся из пары p, h).
     */
    private static final double EXTRACTION_ENTHALPY = 2901.2580;

    /** h выхлопа при 0.004 МПа. */
    private static final double EXHAUST_ENTHALPY = 2077.5500;

    /** Допуск для величин, посчитанных через обратные функции IF97. */
    private static final double THERMO = 0.01;

    private static StateResolver.StageResult stage(StateResolver.Report report, Stage stage) {
        return report.stages().stream()
                .filter(result -> result.stage() == stage)
                .findFirst()
                .orElseThrow(() -> new AssertionError("этап " + stage + " не выполнялся: "
                        + report.describe()));
    }

    // ---------- котёл ----------

    @Test
    @DisplayName("Котёл: давление и температура свежего пара становятся состоянием на выходе")
    void boilerDefinesFreshSteam() {
        Boiler boiler = new Boiler("Котёл").withFreshSteam(LIVE_PRESSURE, LIVE_TEMPERATURE);
        Scheme scheme = new Scheme().add(boiler);
        scheme.openIn(boiler.waterIn(), "питательная вода");
        Connection liveSteam = scheme.openOut(boiler.steamOut(), "свежий пар");
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(LIVE_PRESSURE, liveSteam.getPressure(), 1e-9);
        assertEquals(LIVE_ENTHALPY, liveSteam.getEnthalpy(), 1e-6, "h(24 МПа, 540 °C) по IF97");
        assertEquals(new Origin(Stage.RESOLVED, boiler.name()),
                journal.origin(liveSteam, Quantity.ENTHALPY));
        assertEquals(2, stage(report, Stage.RESOLVED).writes(), "давление и энтальпия");
        assertFalse(report.isComplete(), "питательная вода и расходы ещё не заданы");
    }

    @Test
    @DisplayName("Котёл не переписывает ГУ: другое значение на выходе — противоречие данных")
    void boilerRejectsContradictingBoundaryValue() {
        Boiler boiler = new Boiler("Котёл").withFreshSteam(LIVE_PRESSURE, LIVE_TEMPERATURE);
        Scheme scheme = new Scheme().add(boiler);
        scheme.openIn(boiler.waterIn(), "питательная вода");
        Connection liveSteam = scheme.openOut(boiler.steamOut(), "свежий пар");
        liveSteam.withState(LIVE_PRESSURE, 3300.0);
        CalculationJournal journal = CalculationJournal.of(scheme);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> StateResolver.of(journal).resolve());

        assertTrue(error.getMessage().contains("3300.0"), error.getMessage());
        assertTrue(error.getMessage().contains("3318.77"), error.getMessage());
        assertTrue(error.getMessage().contains(boiler.name()), error.getMessage());
        assertTrue(error.getMessage().contains(CalculationJournal.SCHEME_INPUT_OWNER),
                error.getMessage());
    }

    @Test
    @DisplayName("Котёл без параметров ничего не выдумывает: выход остаётся незаданным")
    void boilerWithoutParametersLeavesStateUnknown() {
        Boiler boiler = new Boiler("Котёл");
        Scheme scheme = new Scheme().add(boiler);
        scheme.openIn(boiler.waterIn(), "питательная вода");
        Connection liveSteam = scheme.openOut(boiler.steamOut(), "свежий пар");
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertNull(liveSteam.getPressure(), "параметры не заданы — давления нет");
        assertNull(liveSteam.getEnthalpy(), "и энтальпии тоже");
        assertEquals(0, stage(report, Stage.RESOLVED).writes());
    }

    // ---------- турбина ----------

    @Test
    @DisplayName("Турбина: отбор и выхлоп расширяются по изоэнтропе с поправкой на ηoi")
    void turbineExpandsExtractionAndExhaust() {
        Turbine turbine = new Turbine("Турбина", 1).withInternalEfficiency(EFFICIENCY);
        Scheme scheme = new Scheme().add(turbine);
        Connection live = scheme.openIn(turbine.steamIn(), "свежий пар");
        Connection extraction = scheme.openOut(turbine.extractionOut(1), "отбор");
        Connection exhaust = scheme.openOut(turbine.exhaustOut(), "выхлоп");
        live.withState(LIVE_PRESSURE, LIVE_ENTHALPY);
        extraction.setPressure(EXTRACTION_PRESSURE);
        exhaust.setPressure(CONDENSER_PRESSURE);
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(EXTRACTION_ENTHALPY, extraction.getEnthalpy(), THERMO, "энтальпия отбора");
        assertEquals(EXHAUST_ENTHALPY, exhaust.getEnthalpy(), THERMO, "энтальпия выхлопа");
        assertEquals(new Origin(Stage.RESOLVED, turbine.name()),
                journal.origin(exhaust, Quantity.ENTHALPY));
        assertEquals(new Origin(Stage.INPUT, CalculationJournal.SCHEME_INPUT_OWNER),
                journal.origin(exhaust, Quantity.PRESSURE), "давление — исходные данные");
        assertEquals(2, stage(report, Stage.RESOLVED).writes(), "две энтальпии");
        assertTrue(report.describe().contains("не задано: 3 величин"),
                "незаданными остаются только расходы: " + report.describe());
    }

    @Test
    @DisplayName("Турбина без ηoi не считает энтальпии: незаданное остаётся в отчёте")
    void turbineWithoutEfficiencyLeavesEnthalpyUnknown() {
        Turbine turbine = new Turbine("Турбина", 0);
        Scheme scheme = new Scheme().add(turbine);
        Connection live = scheme.openIn(turbine.steamIn(), "свежий пар");
        Connection exhaust = scheme.openOut(turbine.exhaustOut(), "выхлоп");
        live.withState(LIVE_PRESSURE, LIVE_ENTHALPY);
        exhaust.setPressure(CONDENSER_PRESSURE);
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertNull(exhaust.getEnthalpy());
        assertEquals(0, stage(report, Stage.RESOLVED).writes());
        assertTrue(report.missing().stream().anyMatch(item -> item.connection() == exhaust
                && item.quantity() == Quantity.ENTHALPY), report::describe);
    }

    @Test
    @DisplayName("Турбина не расширяет пар до более высокого давления")
    void turbineRejectsRisingPressure() {
        Turbine turbine = new Turbine("Турбина", 0).withInternalEfficiency(EFFICIENCY);
        Scheme scheme = new Scheme().add(turbine);
        Connection live = scheme.openIn(turbine.steamIn(), "свежий пар");
        Connection exhaust = scheme.openOut(turbine.exhaustOut(), "выхлоп");
        live.withState(LIVE_PRESSURE, LIVE_ENTHALPY);
        exhaust.setPressure(LIVE_PRESSURE + 1.0);
        CalculationJournal journal = CalculationJournal.of(scheme);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> StateResolver.of(journal).resolve());

        assertTrue(error.getMessage().contains("не может расширяться"), error.getMessage());
        assertTrue(error.getMessage().contains("exhaustOut"), error.getMessage());
    }

    @Test
    @DisplayName("Заданная энтальпия сильнее выведенной: модель дополняет, а не переписывает")
    void turbineKeepsExplicitEnthalpy() {
        Turbine turbine = new Turbine("Турбина", 0).withInternalEfficiency(EFFICIENCY);
        Scheme scheme = new Scheme().add(turbine);
        Connection live = scheme.openIn(turbine.steamIn(), "свежий пар");
        Connection exhaust = scheme.openOut(turbine.exhaustOut(), "выхлоп");
        live.withState(LIVE_PRESSURE, LIVE_ENTHALPY);
        exhaust.withState(CONDENSER_PRESSURE, 2000.0);
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(2000.0, exhaust.getEnthalpy(), 1e-9, "ГУ не тронуто");
        assertEquals(0, stage(report, Stage.RESOLVED).writes());
    }

    // ---------- поверхностный подогреватель ----------

    @Test
    @DisplayName("Подогреватель: дренаж — насыщенная жидкость, вода — недогрев до насыщения")
    void surfaceHeaterCondensesAndCoolsWater() {
        SurfaceHeater heater = new SurfaceHeater("ПВД-1").withSubcooling(5.0);
        Scheme scheme = new Scheme().add(heater);
        Connection steam = scheme.openIn(heater.steamIn(), "греющий пар");
        Connection drain = scheme.openOut(heater.drainOut(), "дренаж");
        Connection water = scheme.openIn(heater.waterIn(), "питательная вода");
        Connection heated = scheme.openOut(heater.waterOut(), "питательная вода");
        steam.withState(EXTRACTION_PRESSURE, EXTRACTION_ENTHALPY);
        water.withState(LIVE_PRESSURE, 900.0);
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(EXTRACTION_PRESSURE, drain.getPressure(), 1e-9, "давление дренажа");
        assertEquals(1049.7753, drain.getEnthalpy(), 1e-3, "h′(3.5 МПа)");
        assertEquals(LIVE_PRESSURE, heated.getPressure(), 1e-9,
                "вода проходит аппарат без потерь напора");
        assertEquals(1029.9191, heated.getEnthalpy(), 1e-3, "h(24 МПа, t_нас(3.5 МПа) − 5 K)");
        assertEquals(new Origin(Stage.RESOLVED, heater.name()),
                journal.origin(heated, Quantity.ENTHALPY));
        assertEquals(4, stage(report, Stage.RESOLVED).writes(),
                "давление дренажа и воды, энтальпии дренажа и воды");
        assertTrue(report.missing().stream().allMatch(item -> item.quantity() == Quantity.FLOW),
                "незаданными остаются только расходы: " + report.describe());
    }

    @Test
    @DisplayName("Подогреватель без недогрева: дренаж считается, энтальпия воды — нет")
    void surfaceHeaterWithoutSubcoolingLeavesWaterEnthalpyUnknown() {
        SurfaceHeater heater = new SurfaceHeater("ПНД-2");
        Scheme scheme = new Scheme().add(heater);
        Connection steam = scheme.openIn(heater.steamIn(), "греющий пар");
        Connection drain = scheme.openOut(heater.drainOut(), "дренаж");
        Connection water = scheme.openIn(heater.waterIn(), "основной конденсат");
        Connection heated = scheme.openOut(heater.waterOut(), "основной конденсат");
        steam.withState(EXTRACTION_PRESSURE, EXTRACTION_ENTHALPY);
        water.withState(LIVE_PRESSURE, 500.0);
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(1049.7753, drain.getEnthalpy(), 1e-3, "дренаж от недогрева не зависит");
        assertNull(heated.getEnthalpy(), "недогрев не задан — нагрев неизвестен");
        assertTrue(report.missing().stream().anyMatch(item -> item.connection() == heated
                && item.quantity() == Quantity.ENTHALPY), report::describe);
    }

    @Test
    @DisplayName("Недогрев не может быть больше температуры насыщения греющего пара")
    void surfaceHeaterRejectsSubcoolingAboveSaturation() {
        SurfaceHeater heater = new SurfaceHeater("ПВД-1").withSubcooling(300.0);
        Scheme scheme = new Scheme().add(heater);
        Connection steam = scheme.openIn(heater.steamIn(), "греющий пар");
        scheme.openOut(heater.drainOut(), "дренаж");
        Connection water = scheme.openIn(heater.waterIn(), "питательная вода");
        scheme.openOut(heater.waterOut(), "питательная вода");
        steam.withState(EXTRACTION_PRESSURE, EXTRACTION_ENTHALPY);
        water.withState(LIVE_PRESSURE, 900.0);
        CalculationJournal journal = CalculationJournal.of(scheme);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> StateResolver.of(journal).resolve());

        assertTrue(error.getMessage().contains("не меньше температуры насыщения"),
                error.getMessage());
        assertTrue(error.getMessage().contains(heater.name()), error.getMessage());
    }

    // ---------- конденсатор ----------

    @Test
    @DisplayName("Конденсатор: конденсат — насыщенная жидкость при давлении выхлопа")
    void condenserCondensesExhaust() {
        Condenser condenser = new Condenser("Конденсатор");
        Scheme scheme = new Scheme().add(condenser);
        Connection exhaust = scheme.openIn(condenser.exhaustIn(), "выхлоп");
        Connection condensate = scheme.openOut(condenser.condensateOut(), "конденсат");
        exhaust.withState(CONDENSER_PRESSURE, EXHAUST_ENTHALPY);
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(CONDENSER_PRESSURE, condensate.getPressure(), 1e-9);
        assertEquals(121.4036, condensate.getEnthalpy(), 1e-3, "h′(0.004 МПа)");
        assertEquals(new Origin(Stage.RESOLVED, condenser.name()),
                journal.origin(condensate, Quantity.ENTHALPY));
        assertEquals(2, stage(report, Stage.RESOLVED).writes());
    }

    @Test
    @DisplayName("Конденсатор с переохлаждением: конденсат холоднее насыщения")
    void condenserCoolsCondensate() {
        Condenser condenser = new Condenser("Конденсатор").withSubcooling(3.0);
        Scheme scheme = new Scheme().add(condenser);
        Connection exhaust = scheme.openIn(condenser.exhaustIn(), "выхлоп");
        Connection condensate = scheme.openOut(condenser.condensateOut(), "конденсат");
        exhaust.withState(CONDENSER_PRESSURE, EXHAUST_ENTHALPY);
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.of(journal).resolve();

        assertEquals(108.8601, condensate.getEnthalpy(), 1e-3, "h(0.004 МПа, t_нас − 3 K)");
    }

    @Test
    @DisplayName("Переохлаждение не может быть больше температуры насыщения")
    void condenserRejectsSubcoolingAboveSaturation() {
        Condenser condenser = new Condenser("Конденсатор").withSubcooling(30.0);
        Scheme scheme = new Scheme().add(condenser);
        Connection exhaust = scheme.openIn(condenser.exhaustIn(), "выхлоп");
        scheme.openOut(condenser.condensateOut(), "конденсат");
        exhaust.withState(CONDENSER_PRESSURE, EXHAUST_ENTHALPY);
        CalculationJournal journal = CalculationJournal.of(scheme);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> StateResolver.of(journal).resolve());

        assertTrue(error.getMessage().contains("не меньше температуры насыщения"),
                error.getMessage());
    }

    // ---------- насос ----------

    @Test
    @DisplayName("Насос: напор повышает давление, работа повышает энтальпию")
    void pumpRaisesPressureAndAccountsWork() {
        Pump pump = new Pump("ПН").withPressureRise(6.0).withEfficiency(0.8);
        Scheme scheme = new Scheme().add(pump);
        Connection suction = scheme.openIn(pump.waterIn(), "всасывание");
        Connection discharge = scheme.openOut(pump.waterOut(), "напор");
        suction.withState(CONDENSER_PRESSURE, 121.40356367708806);
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(6.004, discharge.getPressure(), 1e-9, "p_вс + Δp");
        assertEquals(128.9343, discharge.getEnthalpy(), 1e-4,
                "h_вс + v′(0.004)·6·1000/0.8");
        assertEquals(new Origin(Stage.RESOLVED, pump.name()),
                journal.origin(discharge, Quantity.ENTHALPY));
        assertEquals(2, stage(report, Stage.RESOLVED).writes());
    }

    @Test
    @DisplayName("Насос без КПД: работа считается по идеальному процессу")
    void pumpWithoutEfficiencyUsesIdealWork() {
        Pump pump = new Pump("ПН").withPressureRise(6.0);
        Scheme scheme = new Scheme().add(pump);
        Connection suction = scheme.openIn(pump.waterIn(), "всасывание");
        Connection discharge = scheme.openOut(pump.waterOut(), "напор");
        suction.withState(CONDENSER_PRESSURE, 121.40356367708806);
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.of(journal).resolve();

        assertEquals(127.4282, discharge.getEnthalpy(), 1e-4, "h_вс + v′·Δp при η = 1");
    }

    @Test
    @DisplayName("Насос без напора: энтальпия переносится, давление остаётся тракту")
    void pumpWithoutHeadKeepsEnthalpy() {
        Pump pump = new Pump("ПН");
        Scheme scheme = new Scheme().add(pump);
        Connection suction = scheme.openIn(pump.waterIn(), "всасывание");
        Connection discharge = scheme.openOut(pump.waterOut(), "напор");
        suction.withState(CONDENSER_PRESSURE, 121.40356367708806);
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(121.4036, discharge.getEnthalpy(), 1e-3, "работа неизвестна — h не меняется");
        assertNull(discharge.getPressure());
        assertTrue(report.missing().stream().anyMatch(item -> item.connection() == discharge
                && item.quantity() == Quantity.PRESSURE), report::describe);
    }

    @Test
    @DisplayName("Напор и заданные давления должны согласовываться")
    void pumpVerifiesGivenHead() {
        Pump pump = new Pump("ПН").withPressureRise(6.0);
        Scheme scheme = new Scheme().add(pump);
        Connection suction = scheme.openIn(pump.waterIn(), "всасывание");
        Connection discharge = scheme.openOut(pump.waterOut(), "напор");
        suction.withState(1.0, 121.40356367708806);
        discharge.setPressure(8.0);
        CalculationJournal journal = CalculationJournal.of(scheme);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> StateResolver.of(journal).resolve());

        assertTrue(error.getMessage().contains("напор задан 6.0"), error.getMessage());
        assertTrue(error.getMessage().contains("различаются на 7.0"), error.getMessage());
    }

    // ---------- сквозная проверка ----------

    @Test
    @DisplayName("Цикл котёл → турбина → конденсатор → насос: известны все p и h, нет только расходов")
    void resolvesHeatSideOfSimpleCycle() {
        Boiler boiler = new Boiler("Котёл").withFreshSteam(LIVE_PRESSURE, LIVE_TEMPERATURE);
        Pipe live = new Pipe("Паропровод свежего пара", SpaceType.STEAM);
        Turbine turbine = new Turbine("Турбина", 1).withInternalEfficiency(EFFICIENCY);
        Pipe exhaustPipe = new Pipe("Выхлопной паропровод", SpaceType.STEAM);
        Condenser condenser = new Condenser("Конденсатор");
        Pipe condensatePipe = new Pipe("Линия основного конденсата", SpaceType.WATER);
        Pump pump = new Pump("Питательный насос").withPressureRise(23.996).withEfficiency(0.8);
        Pipe feedwaterPipe = new Pipe("Питательная линия", SpaceType.WATER);
        Scheme scheme = new Scheme().add(boiler).add(live).add(turbine).add(exhaustPipe)
                .add(condenser).add(condensatePipe).add(pump).add(feedwaterPipe);

        Connection liveSteam = scheme.link(boiler.steamOut(), live.in(), "свежий пар");
        Connection atTurbine = scheme.link(live.out(), turbine.steamIn(), "свежий пар");
        Connection extraction = scheme.openOut(turbine.extractionOut(1), "отбор");
        Connection exhaust = scheme.link(turbine.exhaustOut(), exhaustPipe.in(), "выхлоп");
        Connection atCondenser = scheme.link(exhaustPipe.out(), condenser.exhaustIn(), "выхлоп");
        Connection condensate = scheme.link(condenser.condensateOut(), condensatePipe.in(),
                "конденсат");
        Connection atPump = scheme.link(condensatePipe.out(), pump.waterIn(), "конденсат");
        Connection discharge = scheme.link(pump.waterOut(), feedwaterPipe.in(), "питательная вода");
        Connection feedwater = scheme.link(feedwaterPipe.out(), boiler.waterIn(),
                "питательная вода");
        extraction.setPressure(EXTRACTION_PRESSURE);
        exhaust.setPressure(CONDENSER_PRESSURE);
        CalculationJournal journal = CalculationJournal.of(scheme);

        StateResolver.Report report = StateResolver.of(journal).resolve();

        assertEquals(Stage.SOLVED, report.reached());
        assertEquals(LIVE_PRESSURE, liveSteam.getPressure(), 1e-9);
        assertEquals(LIVE_ENTHALPY, atTurbine.getEnthalpy(), 1e-6, "свежий пар дошёл до турбины");
        assertEquals(EXTRACTION_ENTHALPY, extraction.getEnthalpy(), THERMO);
        assertEquals(CONDENSER_PRESSURE, atCondenser.getPressure(), 1e-9);
        assertEquals(EXHAUST_ENTHALPY, atCondenser.getEnthalpy(), THERMO);
        assertEquals(121.4036, condensate.getEnthalpy(), 1e-3, "конденсат на линии насыщения");
        assertEquals(LIVE_PRESSURE, discharge.getPressure(), 1e-9, "насос поднял до котла");
        assertEquals(151.5215, feedwater.getEnthalpy(), 1e-3, "энтальпия за насосом");

        assertEquals(new Origin(Stage.RESOLVED, live.name()),
                journal.origin(atTurbine, Quantity.ENTHALPY),
                "перенос по трассе повторяется на RESOLVED: иначе состояние котла не дошло бы");
        assertEquals(new Origin(Stage.RESOLVED, turbine.name()),
                journal.origin(extraction, Quantity.ENTHALPY));

        assertEquals(9, report.missing().size(), report::describe);
        assertTrue(report.missing().stream().allMatch(item -> item.quantity() == Quantity.FLOW),
                () -> "незаданными остаются только расходы: " + report.describe());
        assertTrue(report.missing().stream().anyMatch(item -> item.connection() == atPump),
                report::describe);
    }
}
