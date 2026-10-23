package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.Port;

import java.util.ArrayList;
import java.util.List;

/**
 * Общие операции моделей: выбор связей с портов, перенос значения между сечениями и допуски.
 *
 * <p>Здесь собрано то, что иначе пришлось бы повторять в каждой модели: «взять единственную
 * связь с порта», «собрать связи канала, заметив неподключённый обязательный порт» и
 * «скопировать известное значение в неизвестное, а расхождение двух известных — ошибка».
 * Последняя операция и есть физика участка без потерь, поэтому она одна на трубу, тройник и
 * насос — меняется только причина, которая попадает в сообщение.
 *
 * <p>Два допуска, а не один: при тождественном переносе значение просто копируется, поэтому
 * любое расхождение — ошибка модели, и допуск там машинный. Балансы же проверяются по
 * величинам, заданным вручную, поэтому для них допуск шире: иначе округлённые исходные данные
 * выглядели бы противоречием.
 */
final class RuleSupport {

    /** Величины состояния, которые переносятся через участок трассы и через насос. */
    static final Quantity[] STATE = {Quantity.PRESSURE, Quantity.ENTHALPY};

    /** Допуск тождественного переноса: значение копируется, а не вычисляется заново. */
    private static final double IDENTITY = 1e-9;

    /** Допуск проверки баланса: исходные данные задаются вручную и округляются. */
    private static final double BALANCE = 1e-6;

    /** Абсолютный порог для величин около нуля, где относительный допуск смысла не имеет. */
    private static final double ABSOLUTE = 1e-9;

    private RuleSupport() {
    }

    /**
     * Единственная связь на порту.
     *
     * @return связь или {@code null}, если порт не подключён либо обслуживает коллектор
     */
    static Connection connection(Element element, Port port) {
        List<Connection> connections = port.isInput()
                ? element.getInPutConnections(port)
                : element.getOutPutConnections(port);
        return connections.size() == 1 ? connections.get(0) : null;
    }

    /**
     * Связи канала потока в порядке портов и подключения.
     *
     * <p>Порт без связей у необязательного патрубка — это нулевой поток: у турбины
     * неподключённый отбор означает, что отбора нет. Тот же пустой порт у обязательного
     * патрубка означает неполную схему, и тогда баланс канала считать нельзя.
     *
     * @return связи или {@code null}, если обязательный порт канала не подключён
     */
    static List<Connection> connections(Element element, List<Port> ports) {
        List<Connection> connections = new ArrayList<>();
        for (Port port : ports) {
            List<Connection> onPort = port.isInput()
                    ? element.getInPutConnections(port)
                    : element.getOutPutConnections(port);
            if (onPort.isEmpty() && port.isRequired()) {
                return null;
            }
            connections.addAll(onPort);
        }
        return connections;
    }

    /**
     * Переносит величину между двумя сечениями участка без потерь: известное значение
     * копируется в неизвестное, а два разных известных значения — противоречие.
     *
     * @param reason почему значения обязаны совпадать (попадает в сообщение)
     * @return сколько величин записано
     * @throws IllegalStateException если оба значения известны и различаются
     */
    static int align(StateAccess access, Connection first, Connection second, Quantity quantity,
                     String reason) {
        Double a = access.read(first, quantity);
        Double b = access.read(second, quantity);
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return access.write(first, quantity, b) ? 1 : 0;
        }
        if (b == null) {
            return access.write(second, quantity, a) ? 1 : 0;
        }
        if (!same(a, b)) {
            throw new IllegalStateException("«" + access.owner() + "»: " + quantity.title()
                    + " по разные стороны различается — " + access.describe(first, quantity)
                    + " и " + access.describe(second, quantity) + ": " + reason);
        }
        return 0;
    }

    /** Значения совпадают с точностью тождественного переноса. */
    static boolean same(double a, double b) {
        return close(a, b, IDENTITY);
    }

    /** Значения совпадают с точностью проверки баланса. */
    static boolean balanced(double a, double b) {
        return close(a, b, BALANCE);
    }

    /**
     * Записывает вычисленное значение, если величина ещё неизвестна.
     *
     * <p>В отличие от {@link #align} — тождественного переноса, где расхождение двух известных
     * значений означает ошибку модели, — здесь значение выведено из параметров аппарата
     * (энтальпия отбора по ηoi, дренаж по давлению греющего пара), а известное значение пришло
     * из исходных данных. Сверять их с точностью переноса нельзя: параметры и ГУ задаются
     * округлёнными, и проверка давала бы ложные противоречия. Поэтому приоритет у уже
     * известного: модель дополняет состояние и не спорит с ним.
     *
     * <p>Такое «дополнение только неизвестного» ещё и сохраняет монотонность проходов: за один
     * проход величина либо появляется, либо нет, но никогда не перезаписывается — поэтому
     * повторные проходы гарантированно заканчиваются.
     *
     * @return 1, если величина записана; 0 — уже была известна
     */
    static int fill(StateAccess access, Connection connection, Quantity quantity, double value) {
        if (access.known(connection, quantity)) {
            return 0;
        }
        return access.write(connection, quantity, value) ? 1 : 0;
    }

    private static boolean close(double a, double b, double relative) {
        if (Double.compare(a, b) == 0) {
            return true;
        }
        double scale = Math.max(Math.abs(a), Math.abs(b));
        return Math.abs(a - b) <= Math.max(relative * scale, ABSOLUTE);
    }
}
