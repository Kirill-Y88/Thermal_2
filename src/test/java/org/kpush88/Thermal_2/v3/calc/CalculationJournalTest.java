package org.kpush88.Thermal_2.v3.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kpush88.Thermal_2.v3.Boiler;
import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Pipe;
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
 * Журнал расчёта: этапы, снапшоты и происхождение величин — без системы уравнений.
 *
 * <p>Стенд — реальный участок трассы «котёл → паропровод → турбина»: две связи, шесть величин
 * (по три на сечение). На нём проверяется главное свойство журнала — состояние можно
 * зафиксировать на этапе и больше не потерять. Снимок {@code INPUT} хранит входные данные,
 * снимок {@code NORMALIZED} — то, что перенесла труба; разница между ними и есть вклад этапа.
 *
 * <p>Отдельно проверяются правила, ради которых журнал заменил флаги «задано/рассчитано»:
 * ГУ нельзя перезаписать, повторная запись того же числа — не конфликт, а другого —
 * двойное вычисление; писать можно только в текущий этап; после {@code RESULT} журнал закрыт.
 *
 * <p>Расходы на этих этапах остаются неизвестными: их определит система балансов, которую
 * добавит следующий шаг. Поэтому {@link SchemeState#missing()} на {@code INPUT} и
 * {@code NORMALIZED} честно перечисляет недостающее — это и есть постановка задачи для СЛАУ.
 */
class CalculationJournalTest {

    private static final double PRESSURE = 25.0;
    private static final double ENTHALPY = 3300.0;
    private static final double FLOW = 1.0;

    /** Участок трассы: схема, труба и две связи вокруг неё. */
    private record SteamLine(Scheme scheme, Pipe pipe, Connection fromBoiler,
                             Connection toTurbine) {
    }

    /**
     * Собирает участок в два шага, как загрузчик схемы: сначала элементы, затем связи
     * «выход → вход». Состояние в связи пока не задано.
     */
    private static SteamLine buildSteamLine() {
        Boiler boiler = new Boiler("Котёл");
        Turbine turbine = new Turbine("Турбина", 1);
        Pipe pipe = new Pipe("Паропровод свежего пара", SpaceType.STEAM);
        Scheme scheme = new Scheme().add(boiler).add(pipe).add(turbine);
        Connection fromBoiler = scheme.link(boiler.steamOut(), pipe.in(), "свежий пар");
        Connection toTurbine = scheme.link(pipe.out(), turbine.steamIn(), "свежий пар");
        return new SteamLine(scheme, pipe, fromBoiler, toTurbine);
    }

    /**
     * Схема с состоянием перед трубой и зафиксированным этапом {@code INPUT}: значения
     * заносятся в связи до создания журнала, поэтому считаются входными данными.
     */
    private static CalculationJournal inputJournal(SteamLine line) {
        line.fromBoiler().withState(PRESSURE, ENTHALPY).withFlow(FLOW);
        CalculationJournal journal = CalculationJournal.of(line.scheme());
        journal.capture(Stage.INPUT);
        return journal;
    }

    @Test
    @DisplayName("Снимок INPUT берёт значения из связей и помечает их исходными данными")
    void inputSnapshotKeepsValuesAndOrigins() {
        SteamLine line = buildSteamLine();
        line.fromBoiler().withState(PRESSURE, ENTHALPY).withFlow(FLOW);
        CalculationJournal journal = CalculationJournal.of(line.scheme());

        SchemeState input = journal.capture(Stage.INPUT);

        assertEquals(Stage.INPUT, input.stage());
        assertEquals(PRESSURE, input.value(line.fromBoiler(), Quantity.PRESSURE), 1e-9);
        assertEquals(ENTHALPY, input.value(line.fromBoiler(), Quantity.ENTHALPY), 1e-9);
        assertEquals(FLOW, input.value(line.fromBoiler(), Quantity.FLOW), 1e-9);
        assertEquals(new Origin(Stage.INPUT, CalculationJournal.SCHEME_INPUT_OWNER),
                input.origin(line.fromBoiler(), Quantity.FLOW));
        assertNull(input.value(line.toTurbine(), Quantity.PRESSURE),
                "за трубой ещё ничего не известно");
        assertEquals(3, input.filled());
        assertEquals(6, input.total());
        assertSame(input, journal.state(Stage.INPUT));
        assertSame(input, journal.last());
        assertTrue(journal.describe().contains("INPUT (входные данные): 3 из 6 величин"),
                journal::describe);
    }

    @Test
    @DisplayName("Запись журнала видна в связи, а в снапшоте — с этапом и владельцем")
    void normalizationWritesThroughToConnection() {
        SteamLine line = buildSteamLine();
        CalculationJournal journal = inputJournal(line);

        journal.writeState(Stage.NORMALIZED, line.pipe().name(), line.toTurbine(),
                PRESSURE, ENTHALPY);

        assertEquals(PRESSURE, line.toTurbine().getPressure(), 1e-9,
                "значение записано в саму связь");
        assertEquals(ENTHALPY, line.toTurbine().getEnthalpy(), 1e-9);

        SchemeState normalized = journal.capture(Stage.NORMALIZED);
        assertEquals(PRESSURE, normalized.value(line.toTurbine(), Quantity.PRESSURE), 1e-9);
        assertEquals(new Origin(Stage.NORMALIZED, "Паропровод свежего пара"),
                normalized.origin(line.toTurbine(), Quantity.PRESSURE));
        assertNull(normalized.value(line.toTurbine(), Quantity.FLOW),
                "расход труба не меняет");
        assertEquals(Stage.INPUT,
                normalized.origin(line.fromBoiler(), Quantity.PRESSURE).stage(),
                "состояние до трубы осталось входными данными");
    }

    @Test
    @DisplayName("Diff этапа показывает только то, что добавила труба")
    void normalizationDiffContainsPipeValuesOnly() {
        SteamLine line = buildSteamLine();
        CalculationJournal journal = inputJournal(line);
        SchemeState input = journal.state(Stage.INPUT);

        journal.writeState(Stage.NORMALIZED, line.pipe().name(), line.toTurbine(),
                PRESSURE, ENTHALPY);
        SchemeState normalized = journal.capture(Stage.NORMALIZED);

        StateDiff diff = normalized.diff(input);

        assertEquals(2, diff.size());
        assertEquals(List.of(Quantity.PRESSURE, Quantity.ENTHALPY),
                diff.changes().stream().map(StateDiff.Change::quantity).toList());
        for (StateDiff.Change change : diff.changes()) {
            assertSame(line.toTurbine(), change.connection());
            assertNull(change.before());
            assertEquals(new Origin(Stage.NORMALIZED, line.pipe().name()), change.origin());
        }
        assertTrue(diff.describe().contains("Изменения INPUT → NORMALIZED: 2"), diff::describe);
    }

    @Test
    @DisplayName("Давление, заданное на INPUT, нельзя переписать на следующем этапе")
    void inputValueCannotBeOverwritten() {
        SteamLine line = buildSteamLine();
        CalculationJournal journal = inputJournal(line);

        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> journal.write(Stage.NORMALIZED, line.pipe().name(), line.fromBoiler(),
                        Quantity.PRESSURE, PRESSURE - 1.0));

        String message = conflict.getMessage();
        assertTrue(message.contains("давление"), message);
        assertTrue(message.contains("INPUT"), message);
        assertTrue(message.contains("NORMALIZED"), message);
        assertTrue(message.contains("Паропровод свежего пара"), message);
        assertEquals(PRESSURE, line.fromBoiler().getPressure(), 1e-9,
                "значение в связи не изменилось");
        assertEquals(PRESSURE,
                journal.state(Stage.INPUT).value(line.fromBoiler(), Quantity.PRESSURE), 1e-9,
                "снимок INPUT тоже не изменился");
    }

    @Test
    @DisplayName("Повторная запись того же числа — не конфликт, другого — двойное вычисление")
    void repeatedWritesFollowConflictRules() {
        SteamLine line = buildSteamLine();
        CalculationJournal journal = inputJournal(line);
        String pipe = line.pipe().name();

        journal.writeState(Stage.NORMALIZED, pipe, line.toTurbine(), PRESSURE, ENTHALPY);
        journal.writeState(Stage.NORMALIZED, "Турбина", line.toTurbine(), PRESSURE, ENTHALPY);

        SchemeState normalized = journal.capture(Stage.NORMALIZED);
        assertEquals(new Origin(Stage.NORMALIZED, pipe),
                normalized.origin(line.toTurbine(), Quantity.ENTHALPY),
                "идемпотентная запись не переписывает владельца");

        SteamLine fresh = buildSteamLine();
        CalculationJournal third = inputJournal(fresh);
        third.writeState(Stage.NORMALIZED, fresh.pipe().name(), fresh.toTurbine(),
                PRESSURE, ENTHALPY);

        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> third.write(Stage.NORMALIZED, "Турбина", fresh.toTurbine(),
                        Quantity.ENTHALPY, ENTHALPY - 100.0));

        String message = conflict.getMessage();
        assertTrue(message.contains(fresh.pipe().name()), message);
        assertTrue(message.contains("Турбина"), message);
        assertTrue(message.contains("энтальпия"), message);
        assertEquals(ENTHALPY, fresh.toTurbine().getEnthalpy(), 1e-9);
    }

    @Test
    @DisplayName("Порядок этапов: первый снимок — INPUT, назад и дважды нельзя, писать — только в текущий")
    void stageOrderIsEnforced() {
        SteamLine line = buildSteamLine();
        CalculationJournal journal = CalculationJournal.of(line.scheme());

        assertThrows(IllegalStateException.class, () -> journal.capture(Stage.NORMALIZED),
                "журнал должен начинаться с INPUT");

        journal.capture(Stage.INPUT);

        assertThrows(IllegalStateException.class, () -> journal.capture(Stage.INPUT));
        assertThrows(IllegalStateException.class, () -> journal.write(Stage.INPUT, "Котёл",
                line.fromBoiler(), Quantity.PRESSURE, PRESSURE));
        assertThrows(IllegalArgumentException.class, () -> journal.state(Stage.RESOLVED));
        assertEquals(Stage.NORMALIZED, journal.pendingStage());

        journal.capture(Stage.RESOLVED);
        assertEquals(Stage.SOLVED, journal.pendingStage(), "пропуск этапа разрешён");
    }

    @Test
    @DisplayName("После RESULT журнал закрыт: записывать некуда")
    void journalIsClosedAfterResult() {
        SteamLine line = buildSteamLine();
        CalculationJournal journal = CalculationJournal.of(line.scheme());
        journal.capture(Stage.INPUT);
        journal.capture(Stage.RESULT);

        assertFalse(journal.isOpen());
        assertThrows(IllegalStateException.class, journal::pendingStage);
        assertThrows(IllegalStateException.class, () -> journal.write(Stage.RESULT, "Турбина",
                line.toTurbine(), Quantity.FLOW, FLOW));
        assertFalse(journal.describe().contains("текущий этап"), journal::describe);
    }

    @Test
    @DisplayName("Снапшот — копия: правка связи после снимка его не меняет")
    void snapshotIsDetachedFromConnections() {
        SteamLine line = buildSteamLine();
        line.fromBoiler().withState(PRESSURE, ENTHALPY);
        CalculationJournal journal = CalculationJournal.of(line.scheme());
        SchemeState input = journal.capture(Stage.INPUT);

        line.fromBoiler().withState(30.0, 3400.0);

        assertEquals(PRESSURE, input.value(line.fromBoiler(), Quantity.PRESSURE), 1e-9);
        assertEquals(ENTHALPY, input.value(line.fromBoiler(), Quantity.ENTHALPY), 1e-9);
        assertThrows(UnsupportedOperationException.class, () -> input.connections().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> input.stateOf(line.fromBoiler()).readings().clear());
    }

    @Test
    @DisplayName("Missing перечисляет незаданные величины: связи по порядку, величины по порядку")
    void missingListsUnfilledQuantities() {
        SteamLine line = buildSteamLine();
        line.fromBoiler().withState(PRESSURE, ENTHALPY).withFlow(FLOW);
        CalculationJournal journal = CalculationJournal.of(line.scheme());

        assertEquals(3, journal.missing().size(),
                "до первого снимка отчёт строится по текущим значениям связей");

        SchemeState input = journal.capture(Stage.INPUT);
        List<SchemeState.Missing> missing = input.missing();

        assertEquals(3, missing.size(), "за трубой не задано ни одной величины");
        assertSame(line.toTurbine(), missing.get(0).connection());
        assertEquals(Quantity.PRESSURE, missing.get(0).quantity());
        assertEquals(Quantity.ENTHALPY, missing.get(1).quantity());
        assertEquals(Quantity.FLOW, missing.get(2).quantity());
        assertTrue(missing.get(0).describe()
                        .contains("Паропровод свежего пара.out → Турбина.steamIn"),
                missing.get(0)::describe);
        assertTrue(input.describe().contains("не задано: 3 величин"), input::describe);
    }

    @Test
    @DisplayName("Связь другой схемы — ошибка, а не молчаливое состояние")
    void foreignConnectionIsRejected() {
        SteamLine line = buildSteamLine();
        SteamLine foreign = buildSteamLine();
        CalculationJournal journal = CalculationJournal.of(line.scheme());
        journal.capture(Stage.INPUT);

        assertThrows(IllegalArgumentException.class, () -> journal.write(Stage.NORMALIZED,
                "Турбина", foreign.toTurbine(), Quantity.PRESSURE, PRESSURE));
        assertThrows(IllegalArgumentException.class,
                () -> journal.state(Stage.INPUT).value(foreign.toTurbine(), Quantity.PRESSURE));
    }

    @Test
    @DisplayName("Разница снапшотов считается только вперёд и только для одной схемы")
    void diffRequiresForwardStagesOfSameScheme() {
        SteamLine line = buildSteamLine();
        CalculationJournal journal = inputJournal(line);
        SchemeState input = journal.state(Stage.INPUT);

        journal.writeState(Stage.NORMALIZED, line.pipe().name(), line.toTurbine(),
                PRESSURE, ENTHALPY);
        SchemeState normalized = journal.capture(Stage.NORMALIZED);

        assertThrows(IllegalArgumentException.class, () -> input.diff(input),
                "разница этапа с самим собой не определена");
        assertThrows(IllegalArgumentException.class, () -> input.diff(normalized),
                "разница считается только вперёд по этапам");

        SteamLine other = buildSteamLine();
        CalculationJournal otherJournal = inputJournal(other);
        assertThrows(IllegalArgumentException.class,
                () -> normalized.diff(otherJournal.state(Stage.INPUT)),
                "снапшоты разных схем несравнимы");
    }
}

