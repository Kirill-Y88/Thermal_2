package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Scheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Снапшот всей схемы на одном этапе: состояние каждого сечения и происхождение величин.
 *
 * <p>Снапшот — это <b>копия</b>, а не вид на живые связи: после {@link CalculationJournal#capture}
 * значения можно менять дальше, но зафиксированный этап останется прежним. Поэтому по журналу
 * видно, что было известно на входе и что добавил каждый следующий этап, — именно то, чего не
 * даёт набор изменяемых полей с флагами.
 *
 * <p>Ключ — сама связь (сравнение по идентичности, как и везде в схеме), поэтому снапшот
 * относится к одной схеме; сравнение снапшотов разных схем — ошибка. Для сериализации в
 * JSON такой ключ не годится, и это осознанно отложено: снапшот пока живёт в памяти. Если
 * понадобится отчёт в файл, ключом станет пара «элемент.порт → элемент.порт» — она уже есть
 * у связи ({@link Connection#getPortsUnames()}).
 *
 * <p>Снапшоты создаёт только журнал: он один знает, какой этап открыт и что в него писали.
 */
public final class SchemeState {

    /** Незаданная величина в конкретном сечении — строка отчёта {@link #missing()}. */
    public record Missing(Connection connection, Quantity quantity) {

        public Missing {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(quantity, "quantity");
        }

        /** Подпись «связь «…» […]: давление, МПа не задано» для сообщений. */
        public String describe() {
            return "связь «" + connection.getName() + "» [" + connection.getPortsUnames()
                    + "]: " + quantity.describe() + " не задано";
        }
    }

    private final Scheme scheme;
    private final Stage stage;
    private final Map<Connection, ConnectionState> states;

    /**
     * Собирает снапшот из состояний сечений. Пакетный: снимок делает журнал, который
     * гарантирует, что набор связей совпадает со схемой и что значения не изменятся.
     */
    SchemeState(Scheme scheme, Stage stage, Map<Connection, ConnectionState> states) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.stage = Objects.requireNonNull(stage, "stage");
        Map<Connection, ConnectionState> copy = new LinkedHashMap<>();
        for (Connection connection : scheme.getConnections()) {
            copy.put(connection, states.getOrDefault(connection, ConnectionState.empty()));
        }
        for (Connection connection : states.keySet()) {
            if (!copy.containsKey(connection)) {
                throw new IllegalArgumentException("снапшот содержит связь «"
                        + connection.getName() + "», не принадлежащую схеме");
            }
        }
        this.states = Collections.unmodifiableMap(copy);
    }

    /** Схема, к которой относится снапшот. */
    public Scheme scheme() {
        return scheme;
    }

    /** Этап, на котором сделан снапшот. */
    public Stage stage() {
        return stage;
    }

    /** Связи снапшота в порядке создания схемы (только чтение). */
    public List<Connection> connections() {
        return List.copyOf(states.keySet());
    }

    /** Состояние сечения; связь обязана принадлежать схеме снапшота. */
    public ConnectionState stateOf(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        ConnectionState state = states.get(connection);
        if (state == null) {
            throw new IllegalArgumentException("связь «" + connection.getName()
                    + "» не принадлежит схеме снапшота");
        }
        return state;
    }

    /** Значение величины в сечении; {@code null} — не задано. */
    public Double value(Connection connection, Quantity quantity) {
        return stateOf(connection).value(quantity);
    }

    /** Происхождение величины в сечении; {@code null} — не задано. */
    public Origin origin(Connection connection, Quantity quantity) {
        return stateOf(connection).origin(quantity);
    }

    /** Величина в сечении задана. */
    public boolean has(Connection connection, Quantity quantity) {
        return stateOf(connection).has(quantity);
    }

    /** Все незаданные величины: связи в порядке схемы, величины в порядке объявления. */
    public List<Missing> missing() {
        List<Missing> missing = new ArrayList<>();
        for (Map.Entry<Connection, ConnectionState> entry : states.entrySet()) {
            for (Quantity quantity : entry.getValue().missing()) {
                missing.add(new Missing(entry.getKey(), quantity));
            }
        }
        return Collections.unmodifiableList(missing);
    }

    /** Сколько величин задано (из {@link #total()}). */
    public int filled() {
        int filled = 0;
        for (ConnectionState state : states.values()) {
            filled += state.readings().size();
        }
        return filled;
    }

    /** Сколько всего величин в снапшоте: по три на каждую связь. */
    public int total() {
        return states.size() * Quantity.values().length;
    }

    /**
     * Разница с более ранним снапшотом: что появилось или изменилось к этому этапу.
     *
     * @throws IllegalArgumentException если снапшот относится к другой схеме или сделан не
     *                                  позже указанного, — тогда «разница этапа» смысла не имеет
     */
    public StateDiff diff(SchemeState earlier) {
        Objects.requireNonNull(earlier, "earlier");
        if (earlier.scheme != scheme) {
            throw new IllegalArgumentException("нельзя сравнить снапшоты разных схем");
        }
        if (!stage.isAfter(earlier.stage)) {
            throw new IllegalArgumentException("снапшот " + stage + " не позже " + earlier.stage
                    + ": разница считается только вперёд по этапам");
        }
        List<StateDiff.Change> changes = new ArrayList<>();
        for (Map.Entry<Connection, ConnectionState> entry : states.entrySet()) {
            Connection connection = entry.getKey();
            ConnectionState earlierState = earlier.states.get(connection);
            for (Quantity quantity : Quantity.values()) {
                Double before = earlierState != null ? earlierState.value(quantity) : null;
                Double after = entry.getValue().value(quantity);
                if (!Objects.equals(before, after)) {
                    changes.add(new StateDiff.Change(connection, quantity, before, after,
                            entry.getValue().origin(quantity)));
                }
            }
        }
        return new StateDiff(earlier.stage, stage, changes);
    }

    /**
     * Многострочный отчёт: заголовок с числом заполненных величин, по строке на сечение с
     * известными значениями и итог по незаданным.
     */
    public String describe() {
        StringBuilder text = new StringBuilder("Снапшот " + stage.name() + " ("
                + stage.title() + "): заполнено " + filled() + " из " + total() + " величин");
        for (Map.Entry<Connection, ConnectionState> entry : states.entrySet()) {
            ConnectionState state = entry.getValue();
            if (state.readings().isEmpty()) {
                continue;
            }
            text.append(System.lineSeparator()).append("  «").append(entry.getKey().getName())
                    .append("» [").append(entry.getKey().getPortsUnames()).append("]: ");
            boolean first = true;
            for (Quantity quantity : Quantity.values()) {
                ConnectionState.Reading reading = state.reading(quantity);
                if (reading == null) {
                    continue;
                }
                if (!first) {
                    text.append(", ");
                }
                text.append(quantity.symbol()).append('=').append(reading.describe());
                first = false;
            }
        }
        List<Missing> missing = missing();
        if (!missing.isEmpty()) {
            text.append(System.lineSeparator()).append("  не задано: ").append(missing.size())
                    .append(" величин");
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return describe();
    }
}
