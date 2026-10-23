package org.kpush88.Thermal_2.v3;

/**
 * Турбина: свежий пар расширяется, отбирается в регенеративные подогреватели и уходит в
 * конденсатор.
 *
 * <p>Порты: один вход {@code steamIn}, коллектор отборов {@code extractionOut} (0…N — отборы
 * могут отсутствовать) и обязательный {@code exhaustOut}. Отборы подключены к одному порту,
 * поэтому число отборов задаётся схемой, а не классом, и порядок связей на порту — это
 * порядок отборов по ходу расширения: первый отбор — самый горячий, последний — перед
 * выхлопом. Порядок сохраняется {@link Element#getOutPutConnections(Port)}.
 *
 * <p>Канал потока один (весь пар — один контур): вход питает все выходы, поэтому в графе
 * потоков из свежего пара выходит столько рёбер, сколько подключено отборов и выхлоп.
 * Доли расходов по отборам — предмет расчёта, а не конструкции.
 */
public class Turbine extends Device {

    private final Port steamIn = inPort("steamIn", SpaceType.STEAM);
    private final Port extractionOut = outPort("extractionOut", SpaceType.STEAM, 0, Integer.MAX_VALUE);
    private final Port exhaustOut = outPort("exhaustOut", SpaceType.STEAM);

    public Turbine(String uname) {
        super(uname);
    }

    public Turbine(Long id, String uname) {
        super(id, uname);
    }

    /** Свежий пар. */
    public Port steamIn() {
        return steamIn;
    }

    /** Коллектор отборов: порядок связей — порядок отборов по ходу расширения. */
    public Port extractionOut() {
        return extractionOut;
    }

    /** Выхлоп в конденсатор. */
    public Port exhaustOut() {
        return exhaustOut;
    }

    @Override
    public ElementType elementType() {
        return ElementType.TURBINE;
    }
}
