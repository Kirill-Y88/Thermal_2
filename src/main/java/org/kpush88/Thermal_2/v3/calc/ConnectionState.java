package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Значения трёх величин в одном сечении и происхождение каждого из них.
 *
 * <p>Связь ({@link Connection}) хранит только текущие значения: перезапись стирает историю.
 * Снапшот этапа ({@link SchemeState}) хранит копию значений вместе с {@link Origin}, поэтому
 * по нему видно, что было известно на входе, что перенесла труба и что посчитал аппарат.
 * Копия неизменяема: запись в связь после {@code capture} на снапшот не влияет.
 *
 * <p>Ключей ровно три ({@link Quantity}), поэтому хранилище — {@link EnumMap}, а порядок
 * обхода задан объявлением величин и одинаков во всех отчётах.
 */
public final class ConnectionState {

    /** Значение величины и откуда оно взялось. */
    public record Reading(double value, Origin origin) {

        public Reading {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("значение величины должно быть конечным: "
                        + value);
            }
            Objects.requireNonNull(origin, "origin");
        }

        /** Подпись «значение (этап · владелец)» для отчётов. */
        public String describe() {
            return value + " (" + origin.describe() + ")";
        }
    }

    private static final ConnectionState EMPTY = new ConnectionState(new EnumMap<>(Quantity.class));

    private final Map<Quantity, Reading> readings;

    private ConnectionState(Map<Quantity, Reading> readings) {
        this.readings = Collections.unmodifiableMap(readings);
    }

    /** Состояние, в котором не известно ни одной величины. */
    public static ConnectionState empty() {
        return EMPTY;
    }

    /**
     * Снимает текущие значения связи: все непустые поля получают указанное происхождение.
     *
     * <p>Так журнал заполняется при создании: всё, что записано в связи до начала расчёта,
     * считается входными данными. Дальше значения меняются только через журнал
     * ({@link CalculationJournal#write}), и происхождение у них уже своё.
     */
    public static ConnectionState current(Connection connection, Origin origin) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(origin, "origin");
        Map<Quantity, Reading> readings = new EnumMap<>(Quantity.class);
        for (Quantity quantity : Quantity.values()) {
            Double value = quantity.read(connection);
            if (value != null) {
                readings.put(quantity, new Reading(value, origin));
            }
        }
        return new ConnectionState(readings);
    }

    /** Величина с происхождением; {@code null} — величина не задана. */
    public Reading reading(Quantity quantity) {
        Objects.requireNonNull(quantity, "quantity");
        return readings.get(quantity);
    }

    /** Значение величины; {@code null} — не задано. */
    public Double value(Quantity quantity) {
        Reading reading = reading(quantity);
        return reading != null ? reading.value() : null;
    }

    /** Происхождение величины; {@code null} — величина не задана. */
    public Origin origin(Quantity quantity) {
        Reading reading = reading(quantity);
        return reading != null ? reading.origin() : null;
    }

    /** Величина задана. */
    public boolean has(Quantity quantity) {
        return reading(quantity) != null;
    }

    /** Все три величины заданы — состояние в сечении известно полностью. */
    public boolean isComplete() {
        return readings.size() == Quantity.values().length;
    }

    /** Незаданные величины в порядке объявления. */
    public List<Quantity> missing() {
        List<Quantity> missing = new ArrayList<>();
        for (Quantity quantity : Quantity.values()) {
            if (!readings.containsKey(quantity)) {
                missing.add(quantity);
            }
        }
        return Collections.unmodifiableList(missing);
    }

    /** Заданные величины с происхождением (только чтение). */
    public Map<Quantity, Reading> readings() {
        return readings;
    }

    /**
     * Копия с новой величиной. Пакетная: менять состояние может только журнал, который перед
     * этим проверил конфликт и записал значение в связь.
     */
    ConnectionState with(Quantity quantity, double value, Origin origin) {
        Map<Quantity, Reading> copy = new EnumMap<>(Quantity.class);
        copy.putAll(readings);
        copy.put(quantity, new Reading(value, origin));
        return new ConnectionState(copy);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ConnectionState state && readings.equals(state.readings);
    }

    @Override
    public int hashCode() {
        return readings.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (Quantity quantity : Quantity.values()) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(quantity.symbol()).append('=')
                    .append(text(value(quantity)));
        }
        return text.toString();
    }

    private static String text(Double value) {
        return value != null ? String.valueOf(value) : "—";
    }
}
