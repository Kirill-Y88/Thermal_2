package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.FlowChannel;

import java.util.List;

/**
 * Массовый баланс канала: сколько вошло, столько и вышло.
 *
 * <p>Правило не зависит от вида аппарата, потому что канал потока
 * ({@link FlowChannel}) — это и есть готовая спецификация баланса: котёл, турбина,
 * подогреватель, насос и тройник считаются одной моделью. Так и должно быть: масса не
 * рождается и не исчезает внутри аппарата, а каналы уже описывают, какие порты относятся к
 * одному протоку.
 *
 * <p>Без системы уравнений модель делает только то, что следует из одного уравнения с одним
 * неизвестным: если во всём канале не хватает ровно одного расхода, баланс его и определяет.
 * Когда неизвестных больше, модель молчит — распределение расходов по отборам турбины решает
 * СЛАУ, а не догадка. Если все расходы известны, баланс проверяется: невязка означает, что
 * исходные данные противоречат конструкции, и об этом лучше узнать до решения системы.
 *
 * <p>Отрицательный расход — не «сходящийся» ответ: модель течения односторонняя, и знак минус
 * означает, что поток в схеме направлен против портов. Поэтому он отвергается с указанием
 * канала, а не записывается в сечение.
 */
final class MassBalance implements ElementModel {

    @Override
    public String title() {
        return "массовый баланс";
    }

    @Override
    public Stage stage() {
        return Stage.SOLVED;
    }

    @Override
    public boolean supports(Element element) {
        return true;
    }

    @Override
    public int resolve(Element element, StateAccess access) {
        int writes = 0;
        for (FlowChannel channel : element.flowChannels()) {
            List<Connection> in = RuleSupport.connections(element, channel.in());
            List<Connection> out = RuleSupport.connections(element, channel.out());
            if (in == null || out == null || in.isEmpty() || out.isEmpty()) {
                continue;
            }
            writes += balance(element, channel, access, in, out);
        }
        return writes;
    }

    private static int balance(Element element, FlowChannel channel, StateAccess access,
                               List<Connection> in, List<Connection> out) {
        int unknownIn = unknownCount(access, in);
        int unknownOut = unknownCount(access, out);

        if (unknownIn == 0 && unknownOut == 0) {
            double sumIn = sum(access, in);
            double sumOut = sum(access, out);
            if (!RuleSupport.balanced(sumIn, sumOut)) {
                throw new IllegalStateException("«" + element.name() + "», канал "
                        + channel.describe() + ": на входах " + sumIn + " кг/с, на выходах "
                        + sumOut + " кг/с — массовый баланс не сходится");
            }
            return 0;
        }
        if (unknownIn == 0 && unknownOut == 1) {
            Connection target = unknown(access, out);
            double flow = sum(access, in) - sum(access, out);
            return write(element, channel, access, target, flow, "выходе");
        }
        if (unknownOut == 0 && unknownIn == 1) {
            Connection target = unknown(access, in);
            double flow = sum(access, out) - sum(access, in);
            return write(element, channel, access, target, flow, "входе");
        }
        return 0;
    }

    private static int write(Element element, FlowChannel channel, StateAccess access,
                             Connection target, double flow, String side) {
        if (flow < 0) {
            if (RuleSupport.balanced(flow, 0)) {
                flow = 0;
            } else {
                throw new IllegalStateException("«" + element.name() + "», канал "
                        + channel.describe() + ": баланс даёт отрицательный расход на " + side
                        + " = " + flow + " кг/с — поток направлен против портов, а модель "
                        + "течения односторонняя");
            }
        }
        return access.write(target, Quantity.FLOW, flow) ? 1 : 0;
    }

    private static int unknownCount(StateAccess access, List<Connection> connections) {
        int count = 0;
        for (Connection connection : connections) {
            if (!access.known(connection, Quantity.FLOW)) {
                count++;
            }
        }
        return count;
    }

    /** Единственный неизвестный расход канала; вызывается, когда он ровно один. */
    private static Connection unknown(StateAccess access, List<Connection> connections) {
        for (Connection connection : connections) {
            if (!access.known(connection, Quantity.FLOW)) {
                return connection;
            }
        }
        throw new IllegalStateException("в канале нет неизвестного расхода");
    }

    /** Сумма известных расходов: неизвестные при сложении пропускаются. */
    private static double sum(StateAccess access, List<Connection> connections) {
        double sum = 0;
        for (Connection connection : connections) {
            Double flow = access.read(connection, Quantity.FLOW);
            if (flow != null) {
                sum += flow;
            }
        }
        return sum;
    }
}
