package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Разница двух снапшотов схемы: что появилось или изменилось между этапами.
 *
 * <p>Это дешёвый ответ на вопрос «что сделал этап»: при запрете перезаписи
 * ({@link CalculationJournal#write}) величины между этапами только добавляются, поэтому
 * разница показывает вклад этапа — какие сечения заполнила труба, какие аппарат. Diff
 * сравнивает значения, а не происхождение: повторная запись того же числа на следующем этапе
 * изменением не считается (и журнал её как раз отбрасывает).
 */
public final class StateDiff {

    /** Изменение одной величины в одном сечении. */
    public record Change(Connection connection, Quantity quantity, Double before, Double after,
                         Origin origin) {

        public Change {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(origin, "origin");
        }

        /** Подпись «связь, величина: было → стало (этап · владелец)» для отчётов. */
        public String describe() {
            return "связь «" + connection.getName() + "», " + quantity.title() + ": "
                    + text(before) + " → " + text(after) + " " + quantity.unit()
                    + " (" + origin.describe() + ")";
        }
    }

    private final Stage from;
    private final Stage to;
    private final List<Change> changes;

    StateDiff(Stage from, Stage to, List<Change> changes) {
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        this.changes = Collections.unmodifiableList(new ArrayList<>(changes));
    }

    /** Этап, относительно которого считалась разница. */
    public Stage from() {
        return from;
    }

    /** Этап, снапшот которого сравнивался. */
    public Stage to() {
        return to;
    }

    /** Изменения в порядке связей схемы и объявления величин (только чтение). */
    public List<Change> changes() {
        return changes;
    }

    /** Изменений нет: этап ничего не добавил. */
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /** Число изменений. */
    public int size() {
        return changes.size();
    }

    /** Многострочный отчёт: заголовок и по строке на изменение. */
    public String describe() {
        StringBuilder text = new StringBuilder("Изменения " + from.name() + " → " + to.name()
                + ": " + changes.size());
        for (Change change : changes) {
            text.append(System.lineSeparator()).append("  ").append(change.describe());
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return describe();
    }

    private static String text(Double value) {
        return value != null ? String.valueOf(value) : "—";
    }
}
