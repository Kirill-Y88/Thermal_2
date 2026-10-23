package org.kpush88.Thermal_2.v2;

import lombok.Getter;
import lombok.Setter;

/**
 * Вершина графа тепловой схемы — соединение (патрубок), через которое рабочее тело
 * переходит от одного элемента к другому.
 *
 * <p>Соединение — это сечение трассы, поэтому именно в нём хранится состояние потока:
 * давление {@link #pressure}, энтальпия {@link #enthalpy} и расход {@link #flow}.
 * Пара «давление + энтальпия» полностью определяет состояние рабочего тела, поэтому
 * отдельного поля температуры здесь нет — она при необходимости считается по IF97.
 *
 * <p>Соединение принадлежит ровно одному элементу как его вход или выход
 * (см. {@link Element#getInputConnections()}, {@link Element#getOutputConnections()}),
 * а связь между элементами задаётся рёбрами {@link SchemeGraph}: ребро показывает,
 * из какого соединения в какое идёт среда. По ребру состояние переносится без
 * изменений — физика (нагрев, расширение, потери в трубе) живёт только внутри
 * элементов.
 *
 * <p>Равенство и хеш-код сознательно <b>не</b> переопределяются (поэтому здесь нет
 * Lombok-аннотации {@code @Data}): соединение лежит в {@code LinkedHashSet} элемента
 * и служит ключом {@code LinkedHashMap} графа, а поля состояния меняются в ходе
 * расчёта — {@code hashCode} по изменяемым полям сломал бы коллекции сразу после
 * первого расчёта. Сравнение идёт по идентичности объекта, как и положено вершине.
 */
@Getter
@Setter
public class Connection {

    /** Идентификатор для хранения схемы (файл/БД); в расчёте не участвует. */
    protected Long id;

    /** Человекочитаемое имя: «свежий пар», «дренаж ПВД», «питательная вода». */
    protected String uname;

    /** Среда соединения: греющая или нагреваемая сторона элемента. */
    protected HeatTransferType transferType;

    /** Давление рабочего тела, МПа. */
    protected double pressure;

    /** Энтальпия рабочего тела, кДж/кг. */
    protected double enthalpy;

    /** Расход рабочего тела, кг/с. */
    protected double flow;

    public Connection() {
    }

    public Connection(String uname, HeatTransferType transferType) {
        this.uname = uname;
        this.transferType = transferType;
    }

    public Connection(Long id, String uname, HeatTransferType transferType) {
        this.id = id;
        this.uname = uname;
        this.transferType = transferType;
    }

    /** Задаёт состояние потока в соединении: давление (МПа) и энтальпию (кДж/кг). */
    public Connection withState(double pressure, double enthalpy) {
        this.pressure = pressure;
        this.enthalpy = enthalpy;
        return this;
    }

    /** Задаёт расход рабочего тела, кг/с. */
    public Connection withFlow(double flow) {
        this.flow = flow;
        return this;
    }

    /** Имя соединения для отчётов и сообщений об ошибках. */
    public String getName() {
        if (uname != null) {
            return uname;
        }
        return id != null ? "соединение #" + id : "соединение";
    }

    @Override
    public String toString() {
        return getName() + " (" + transferType
                + ", p=" + pressure + " МПа, h=" + enthalpy + " кДж/кг, G=" + flow + " кг/с)";
    }
}
