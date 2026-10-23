package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Scheme;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Снимок исходных данных схемы: к нему возвращается внешняя итерация перед каждым пересчётом.
 *
 * <p>Журнал расчёта ({@link CalculationJournal}) пишет вычисленные величины не только в себя, но и
 * в связи ({@link Quantity#write}) — иначе состояние схемы и журнал разошлись бы. Побочный эффект
 * в том, что после итерации в связях лежат уже посчитанные расходы, а следующий журнал примет их
 * за входные данные ({@link Stage#INPUT}) и откажется записывать другие значения. Поэтому каждая
 * итерация начинается с возврата к исходным данным: то, что задал пользователь
 * ({@code withState}, {@code withFlow}, {@code setPressure}), остаётся, а всё посчитанное
 * отбрасывается.
 *
 * <p>Это не расчёт в обход журнала, а именно сброс: значения не вычисляются и не подменяются,
 * а возвращаются к тем, которые были до первой итерации. Прямое присваивание здесь единственно
 * возможное — {@link Quantity#write} не умеет писать «неизвестно» ({@code null}), а именно это и
 * требуется для величин, которых во входных данных не было.
 *
 * <p>Снимок нужен дважды: перед каждым шагом внешней итерации ({@link CycleSolver}) и в конце
 * расчёта — чтобы схема осталась исходными данными, а не результатом.
 */
final class InputSnapshot {

    private final Map<Connection, Values> values = new IdentityHashMap<>();

    private record Values(Double pressure, Double enthalpy, Double flow) {
    }

    /** Запоминает состояние всех связей схемы. */
    static InputSnapshot of(Scheme scheme) {
        InputSnapshot snapshot = new InputSnapshot();
        for (Connection connection : scheme.getConnections()) {
            snapshot.values.put(connection, new Values(connection.getPressure(),
                    connection.getEnthalpy(), connection.getFlow()));
        }
        return snapshot;
    }

    /** Возвращает связи в то состояние, которое было при снятии снимка. */
    void restore() {
        for (Map.Entry<Connection, Values> entry : values.entrySet()) {
            Connection connection = entry.getKey();
            Values value = entry.getValue();
            connection.setPressure(value.pressure());
            connection.setEnthalpy(value.enthalpy());
            connection.setFlow(value.flow());
        }
    }
}
