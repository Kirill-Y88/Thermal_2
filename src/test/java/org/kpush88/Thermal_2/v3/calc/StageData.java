package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Данные одного этапа расчёта: снапшот схемы, вклад этапа и незаданные величины.
 *
 * <p><b>Снапшот</b> ({@link #state()}) — всё состояние схемы на этапе: значение любой величины в
 * любом сечении и её происхождение. <b>Вклад этапа</b> ({@link #contribution()}) — разница с
 * предыдущим этапом, то есть ответ на вопрос «что именно добавил этот этап». У первого этапа
 * ({@link Stage#INPUT}) сравнивать не с чем: его величины — это исходные данные, а не чей-то
 * вклад, поэтому вклада у него нет.
 *
 * <p><b>Зачем ещё один тип поверх журнала.</b> Журнал хранит снапшоты по отдельности, и чтобы
 * прочитать расчёт «по этапам», пришлось бы в каждом тесте сопоставлять соседние снапшоты и
 * считать разницы. Здесь это сделано один раз: {@link #of(CalculationJournal)} отдаёт этапы в
 * порядке расчёта, {@link #summary(List)} — сводку, {@link #table(List, List)} — таблицу
 * «сечение × этап».
 */
record StageData(Stage stage, SchemeState state, StateDiff contribution,
                 List<SchemeState.Missing> missing) {

    StageData {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(state, "state");
        missing = List.copyOf(missing);
    }

    /** Данные всех зафиксированных этапов журнала в порядке расчёта. */
    static List<StageData> of(CalculationJournal journal) {
        Objects.requireNonNull(journal, "journal");
        List<SchemeState> states = journal.states();
        List<StageData> data = new ArrayList<>(states.size());
        for (int index = 0; index < states.size(); index++) {
            data.add(collect(states, index));
        }
        return List.copyOf(data);
    }

    /** Данные одного этапа журнала. */
    static StageData of(CalculationJournal journal, Stage stage) {
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(stage, "stage");
        List<SchemeState> states = journal.states();
        SchemeState state = journal.state(stage);
        for (int index = 0; index < states.size(); index++) {
            if (states.get(index) == state) {
                return collect(states, index);
            }
        }
        throw new IllegalStateException("этап " + stage.name() + " не найден в журнале");
    }

    private static StageData collect(List<SchemeState> states, int index) {
        SchemeState state = states.get(index);
        SchemeState earlier = index == 0 ? null : states.get(index - 1);
        return new StageData(state.stage(), state,
                earlier == null ? null : state.diff(earlier), state.missing());
    }

    /** Значение величины в сечении на этом этапе; {@code null} — ещё не задана. */
    Double value(Connection connection, Quantity quantity) {
        return state.value(connection, quantity);
    }

    /** Происхождение величины в сечении на этом этапе; {@code null} — ещё не задана. */
    Origin origin(Connection connection, Quantity quantity) {
        return state.origin(connection, quantity);
    }

    /** Величина в сечении задана уже на этом этапе. */
    boolean has(Connection connection, Quantity quantity) {
        return state.has(connection, quantity);
    }

    /** Изменения, которые внёс этап; у {@link Stage#INPUT} — пусто. */
    List<StateDiff.Change> changes() {
        return contribution == null ? List.of() : contribution.changes();
    }

    /** Сколько величин появилось на этапе. */
    int added() {
        return changes().size();
    }

    /** Строка отчёта: сколько величин известно, что добавил этап и чего ещё не хватает. */
    String describe() {
        String contributionText = contribution == null
                ? "входные данные"
                : "добавлено величин: " + added();
        return stage.name() + " (" + stage.title() + "): " + state.filled() + " из "
                + state.total() + " величин, " + contributionText + ", не задано "
                + missing.size();
    }

    @Override
    public String toString() {
        return describe();
    }

    /** Сводка по этапам: по строке на этап. */
    static String summary(List<StageData> stages) {
        StringBuilder text = new StringBuilder("Этапы расчёта: " + stages.size());
        for (StageData data : stages) {
            text.append(System.lineSeparator()).append("  ").append(data.describe());
        }
        return text.toString();
    }

    /** Таблица величин в сечениях по этапам: строка — сечение и величина, столбец — этап. */
    static String table(List<StageData> stages, List<Connection> connections) {
        StringBuilder text = new StringBuilder("Величины по этапам:");
        text.append(System.lineSeparator())
                .append(String.format(Locale.ROOT, "%-46s", "сечение, величина"));
        for (StageData data : stages) {
            text.append(String.format(Locale.ROOT, "%16s", data.stage().name()));
        }
        for (Connection connection : connections) {
            for (Quantity quantity : Quantity.values()) {
                text.append(System.lineSeparator())
                        .append(String.format(Locale.ROOT, "%-46s", label(connection, quantity)));
                for (StageData data : stages) {
                    text.append(String.format(Locale.ROOT, "%16s",
                            cell(data.value(connection, quantity))));
                }
            }
        }
        return text.toString();
    }

    /** Подпись строки: «имя связи» → куда ведёт, величина. */
    private static String label(Connection connection, Quantity quantity) {
        Element target = connection.getElementIn();
        return "«" + connection.getName() + "»"
                + (target == null ? "" : " → " + target.name())
                + ", " + quantity.symbol();
    }

    private static String cell(Double value) {
        return value == null ? "—" : String.format(Locale.ROOT, "%.4f", value);
    }
}
