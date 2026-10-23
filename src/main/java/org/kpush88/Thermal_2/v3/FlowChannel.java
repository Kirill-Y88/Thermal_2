package org.kpush88.Thermal_2.v3;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Канал потока внутри элемента: какие входные порты питают какие выходные.
 *
 * <p>Канал — это то, что граф потоков ({@link Scheme#flowGraph()}) превращает в рёбра
 * «входная связь → выходная связь», и одновременно готовая спецификация массового баланса:
 * сумма расходов на входах канала равна сумме расходов на выходах. Поэтому каналов ровно
 * столько, сколько независимых протоков рабочего тела в аппарате, а не столько, сколько
 * уравнений: тепло передаётся между каналами внутри аппарата и рёбрами графа не является.
 *
 * <p>У большинства элементов каналы совпадают с контурами портов и выводятся
 * автоматически ({@link Element#flowChannels()}): у поверхностного подогревателя паровая
 * сторона ({@code steamIn}, {@code drainIn} → {@code drainOut}) и водяная
 * ({@code waterIn} → {@code waterOut}) — разные каналы. Явно канал объявляют там, где
 * контур меняется внутри элемента: котёл, конденсатор, деаэратор, смешивающий подогреватель.
 *
 * <p>Поле {@code space} — контур канала, то есть контур его выхода (среда, которая канал
 * покидает). Для канала, пересекающего контуры, это контур выходного потока.
 *
 * @param space контур канала (контур выхода)
 * @param in    входные порты канала в порядке объявления
 * @param out   выходные порты канала в порядке объявления
 */
public record FlowChannel(SpaceType space, List<Port> in, List<Port> out) {

    public FlowChannel {
        if (space == null) {
            throw new IllegalArgumentException("у канала потока должен быть контур");
        }
        in = List.copyOf(in);
        out = List.copyOf(out);
    }

    /** Канал пересекает контуры: среда меняет сторону внутри элемента. */
    public boolean crossesSpaces() {
        for (Port port : in) {
            if (port.space() != space) {
                return true;
            }
        }
        for (Port port : out) {
            if (port.space() != space) {
                return true;
            }
        }
        return false;
    }

    /** Подпись «входы → выходы» для отчётов и сообщений об ошибках. */
    public String describe() {
        return ports(in) + " → " + ports(out);
    }

    private static String ports(List<Port> ports) {
        if (ports.isEmpty()) {
            return "—";
        }
        return ports.stream().map(Port::name).collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return "канал (" + space.title() + "): " + describe();
    }
}
