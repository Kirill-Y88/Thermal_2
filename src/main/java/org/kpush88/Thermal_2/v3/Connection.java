package org.kpush88.Thermal_2.v3;

import lombok.Getter;
import lombok.Setter;

/**
 * Связь тепловой схемы — участок трассы между выходом одного элемента и входом другого.
 *
 * <p>В отличие от v2, где патрубок принадлежал одному элементу, а связь между патрубками
 * задавалась ребром графа, здесь связь — самостоятельный объект: она помнит оба конца
 * ({@link #elementOut} и {@link #elementIn}) и хранит состояние рабочего тела в сечении
 * ({@link #pressure}, {@link #enthalpy}, {@link #flow}). Поэтому состояние на стыке
 * существует в единственном экземпляре: элемент, записавший его в связь, тем самым задаёт
 * вход следующего элемента, и резолверу состояний не нужно ничего копировать.
 *
 * <p>Роль среды хранится для каждого конца отдельно ({@link #transferTypeOut} и
 * {@link #transferTypeIn}): роль — свойство среды <b>внутри</b> элемента, а не марка
 * рабочего тела. Конденсат покидает конденсатор с греющей стороны и входит в конденсатный
 * насос уже как нагреваемая среда, поэтому на одной и той же связи роли концов могут
 * различаться; схема не требует их совпадения.
 *
 * <p>Пара «давление + энтальпия» полностью определяет состояние, поэтому отдельного поля
 * температуры нет — она при необходимости считается по IF97. {@code null} в полях
 * состояния означает «неизвестно» (в отличие от {@code 0.0}) — это и будет использовать
 * резолвер.
 *
 * <p>Равенство и хеш-код сознательно не переопределяются: связь лежит в списках элементов
 * и может служить ключом коллекций, а состояние меняется в ходе расчёта. Сравнение — по
 * идентичности объекта.
 */
@Getter
public class Connection {

    /** Подпись связи для отчётов: среда, начинающая участок («свежий пар», «дренаж ПВД»). */
    private final String uname;

    /** Элемент, с выхода которого начинается связь; {@code null} — начало схемы (граница). */
    private final Element elementOut;

    /** Элемент, на вход которого приходит связь; {@code null} — конец схемы (граница). */
    private final Element elementIn;

    /** Роль среды внутри элемента {@link #elementOut}. */
    private final HeatTransferType transferTypeOut;

    /** Роль среды внутри элемента {@link #elementIn}. */
    private final HeatTransferType transferTypeIn;

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
     * Создаёт связь. Хотя бы один конец должен быть задан: связь без элементов смысла не
     * имеет. Регистрация связи — {@link Scheme#link}, {@link Scheme#openOut} и
     * {@link Scheme#openIn}: только они добавляют её в списки элементов.
     *
     * @param elementOut      элемент-источник ({@code null} — начало схемы)
     * @param transferTypeOut роль среды внутри элемента-источника
     * @param elementIn       элемент-приёмник ({@code null} — конец схемы)
     * @param transferTypeIn  роль среды внутри элемента-приёмника
     */
    public Connection(String uname, Element elementOut, HeatTransferType transferTypeOut,
                      Element elementIn, HeatTransferType transferTypeIn) {
        if (elementOut == null && elementIn == null) {
            throw new IllegalArgumentException("связь должна соединять хотя бы один элемент");
        }
        if (elementOut != null && elementOut == elementIn) {
            throw new IllegalArgumentException("связь «" + uname + "» замкнута на элементе "
                    + elementOut.name() + ": вход и выход не могут быть одним элементом");
        }
        if (elementOut != null && transferTypeOut == null) {
            throw new IllegalArgumentException("не задана роль среды на выходе элемента "
                    + elementOut.name());
        }
        if (elementIn != null && transferTypeIn == null) {
            throw new IllegalArgumentException("не задана роль среды на входе элемента "
                    + elementIn.name());
        }
        this.uname = uname;
        this.elementOut = elementOut;
        this.transferTypeOut = transferTypeOut;
        this.elementIn = elementIn;
        this.transferTypeIn = transferTypeIn;
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
        return elementOut == null || elementIn == null;
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
        String out = elementOut != null ? elementOut.name() : "?";
        String in = elementIn != null ? elementIn.name() : "?";
        return out + "_" + in;
    }

    /** Имя связи для отчётов и сообщений об ошибках. */
    public String getName() {
        return uname != null ? uname : "связь " + getElementsUnames();
    }

    @Override
    public String toString() {
        return getName() + " [" + getElementsUnames() + "]: p=" + pressure + " МПа, h="
                + enthalpy + " кДж/кг, G=" + flow + " кг/с";
    }
}
