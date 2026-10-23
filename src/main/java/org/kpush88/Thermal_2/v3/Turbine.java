package org.kpush88.Thermal_2.v3;

import java.util.ArrayList;
import java.util.List;

/**
 * Турбина: свежий пар расширяется, отбирается в регенеративные подогреватели и уходит в
 * конденсатор.
 *
 * <p>Порты: вход {@code steamIn}, по порту на каждый отбор ({@code extraction1Out},
 * {@code extraction2Out}, …) и выхлоп {@code exhaustOut}. У каждого отбора <b>свой порт</b>,
 * потому что у каждого отбора свои параметры: давление и расход — состояние связи, а связь
 * подключается к одному порту. Общий порт-коллектор означал бы один узел с одними
 * параметрами на все отборы и заставил бы гадать, чьё давление записывать в связь; поэтому
 * число отборов — свойство конструкции турбины (параметр конструктора), а не следствие того,
 * сколько труб успели подключить.
 *
 * <p>Порты объявлены в порядке хода расширения: первый отбор — самый горячий, выхлоп —
 * последним. Порядок задан объявлением, поэтому не зависит от порядка вызовов
 * {@link Scheme#link}; {@link Element#getOutPutConnections()} перечисляет отборы в том же
 * порядке.
 *
 * <p>Каждый отбор необязателен (кратность 0…1): отбор может быть не подключён — тогда расход
 * через него нулевой, а «второй патрубок» на том же узле невозможен. У конденсационной
 * турбины отборов нет вовсе ({@code extractionCount = 0}).
 *
 * <p>Канал потока один (весь пар — один контур): вход питает все выходы, поэтому в графе
 * потоков из свежего пара выходит столько рёбер, сколько объявлено отборов и выхлоп. Доли
 * расходов по отборам — предмет расчёта, а не конструкции.
 */
public class Turbine extends Device {

    private final Port steamIn;

    /** Отборы в порядке хода расширения; номер отбора — индекс + 1. */
    private final List<Port> extractionOuts;

    private final Port exhaustOut;

    /**
     * @param uname           имя турбины для отчётов
     * @param extractionCount число отборов: у каждого отбора будет свой порт
     */
    public Turbine(String uname, int extractionCount) {
        this(null, uname, extractionCount);
    }

    public Turbine(Long id, String uname, int extractionCount) {
        super(id, uname);
        if (extractionCount < 0) {
            throw new IllegalArgumentException("у турбины «" + name() + "» не может быть отборов: "
                    + extractionCount);
        }
        steamIn = inPort("steamIn", SpaceType.STEAM);
        List<Port> declared = new ArrayList<>(extractionCount);
        for (int number = 1; number <= extractionCount; number++) {
            declared.add(outPort("extraction" + number + "Out", SpaceType.STEAM, 0, 1));
        }
        extractionOuts = List.copyOf(declared);
        exhaustOut = outPort("exhaustOut", SpaceType.STEAM);
    }

    /** Свежий пар. */
    public Port steamIn() {
        return steamIn;
    }

    /** Отборы в порядке хода расширения (только чтение, может быть пустым). */
    public List<Port> extractionOuts() {
        return extractionOuts;
    }

    /**
     * Порт отбора номер {@code number} (нумерация с 1: первый отбор — самый горячий).
     *
     * @throws IllegalArgumentException если отбора с таким номером у турбины нет
     */
    public Port extractionOut(int number) {
        if (number < 1 || number > extractionOuts.size()) {
            throw new IllegalArgumentException("у турбины «" + name() + "» нет отбора №" + number
                    + ": объявлено отборов — " + extractionOuts.size());
        }
        return extractionOuts.get(number - 1);
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

