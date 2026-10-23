package org.kpush88.Thermal_2.v3;

import lombok.Getter;
import lombok.Setter;

/**
 * Связь тепловой схемы — участок трассы между выходом одного элемента и входом другого.
 *
 * <p>Связь помнит <b>порты</b>, которые она соединяет ({@link #portOut} и {@link #portIn}),
 * и хранит состояние рабочего тела в сечении ({@link #pressure}, {@link #enthalpy},
 * {@link #flow}). Элементы не хранятся отдельными полями: они выводятся из портов
 * ({@link #getElementOut()}, {@link #getElementIn()}), поэтому связь не может указывать на
 * один элемент, а лежать в списке другого.
 *
 * <p>Проверки, которые возможны только здесь, выполняются в конструкторе: направление
 * портов ({@code OUT} → {@code IN}), разные элементы на концах и <b>один контур</b> у обоих
 * концов ({@link SpaceType}). Последнее и есть то, чего не было в v2: роль среды зависела от
 * аппарата, и её совпадение на стыке нельзя было ни потребовать, ни проверить. Контур —
 * свойство места, поэтому «пар входит в водяной порт» отсекается сразу.
 *
 * <p>Пара «давление + энтальпия» полностью определяет состояние, поэтому отдельного поля
 * температуры нет — она при необходимости считается по IF97. {@code null} в полях
 * состояния означает «неизвестно» (в отличие от {@code 0.0}) — это использует резолвер
 * состояний.
 *
 * <p>Равенство и хеш-код сознательно не переопределяются: связь лежит в списках портов и
 * служит вершиной графа потоков, а состояние меняется в ходе расчёта. Сравнение — по
 * идентичности объекта.
 */
@Getter
public class Connection {

    /** Подпись связи для отчётов: среда, начинающая участок («свежий пар», «дренаж ПВД»). */
    private final String uname;

    /** Выходной порт элемента-источника; {@code null} — связь приходит из-за границы схемы. */
    private final Port portOut;

    /** Входной порт элемента-приёмника; {@code null} — связь уходит за границу схемы. */
    private final Port portIn;

    /** Давление рабочего тела, МПа; {@code null} — неизвестно. */
    @Setter
    private Double pressure;

    /** Энтальпия рабочего тела, кДж/кг; {@code null} — неизвестно. */
    @Setter
    private Double enthalpy;

    /** Расход рабочего тела, кг/с; {@code null} — неизвестно. */
    @Setter
    private Double flow;

    /**
     * Создаёт связь. Хотя бы один конец должен быть задан: связь без портов смысла не имеет.
     * Регистрация связи — {@link Scheme#link}, {@link Scheme#openOut} и {@link Scheme#openIn}:
     * только они добавляют её к портам элементов.
     *
     * @param uname   подпись связи (может быть {@code null})
     * @param portOut выходной порт источника ({@code null} — начало схемы)
     * @param portIn  входной порт приёмника ({@code null} — конец схемы)
     * @throws IllegalArgumentException если направление портов не «выход → вход», оба конца
     *                                  принадлежат одному элементу или контуры не совпадают
     */
    Connection(String uname, Port portOut, Port portIn) {
        if (portOut == null && portIn == null) {
            throw new IllegalArgumentException("связь должна соединять хотя бы один порт");
        }
        if (portOut != null && !portOut.isOutput()) {
            throw new IllegalArgumentException("связь «" + label(uname)
                    + "» начинается с порта " + portOut.label() + ": это "
                    + portOut.direction().title() + ", а нужен выход");
        }
        if (portIn != null && !portIn.isInput()) {
            throw new IllegalArgumentException("связь «" + label(uname)
                    + "» заканчивается на порту " + portIn.label() + ": это "
                    + portIn.direction().title() + ", а нужен вход");
        }
        if (portOut != null && portIn != null) {
            if (portOut.element() == portIn.element()) {
                throw new IllegalArgumentException("связь «" + label(uname)
                        + "» замкнута на элементе «" + portOut.element().name()
                        + "»: вход и выход не могут быть одним элементом");
            }
            if (portOut.space() != portIn.space()) {
                throw new IllegalArgumentException("связь «" + label(uname) + "» соединяет "
                        + portOut.space().title() + " (" + portOut.label() + ") с "
                        + portIn.space().title() + " (" + portIn.label()
                        + "): контур меняется только внутри элемента");
            }
        }
        this.uname = uname;
        this.portOut = portOut;
        this.portIn = portIn;
    }

    // ---------- концы связи ----------

    /** Элемент-источник; {@code null} для связи, приходящей из-за границы схемы. */
    public Element getElementOut() {
        return portOut != null ? portOut.element() : null;
    }

    /** Элемент-приёмник; {@code null} для связи, уходящей за границу схемы. */
    public Element getElementIn() {
        return portIn != null ? portIn.element() : null;
    }

    /** Контур связи — контур её концов (у связи он всегда один). */
    public SpaceType getSpace() {
        return portOut != null ? portOut.space() : portIn.space();
    }

    /** Задаёт состояние потока в сечении: давление (МПа) и энтальпию (кДж/кг). */
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

    /** У связи нет второго конца: это начало или конец схемы. */
    public boolean isOpen() {
        return portOut == null || portIn == null;
    }

    /** Состояние известно полностью: заданы и давление, и энтальпия. */
    public boolean hasState() {
        return pressure != null && enthalpy != null;
    }

    /**
     * Подпись «элемент_элемент» для отчётов; не идентификатор — одноимённые связи разных
     * участков трассы неразличимы. Неизвестный конец обозначается «?», а не падением.
     */
    public String getElementsUnames() {
        String out = portOut != null ? portOut.element().name() : "?";
        String in = portIn != null ? portIn.element().name() : "?";
        return out + "_" + in;
    }

    /** Подпись «элемент.порт → элемент.порт» — точное место стыка. */
    public String getPortsUnames() {
        String out = portOut != null ? portOut.label() : "?";
        String in = portIn != null ? portIn.label() : "?";
        return out + " → " + in;
    }

    /** Имя связи для отчётов и сообщений об ошибках. */
    public String getName() {
        return uname != null ? uname : "связь " + getElementsUnames();
    }

    private static String label(String uname) {
        return uname != null ? uname : "без имени";
    }

    @Override
    public String toString() {
        return getName() + " [" + getPortsUnames() + "]: p=" + pressure + " МПа, h="
                + enthalpy + " кДж/кг, G=" + flow + " кг/с";
    }
}
