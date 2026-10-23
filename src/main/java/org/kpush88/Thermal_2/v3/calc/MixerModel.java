package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Deaerator;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.FlowChannel;
import org.kpush88.Thermal_2.v3.MixingHeater;
import org.kpush88.Thermal_2.v3.PipeTee;

import java.util.List;

/**
 * Энтальпия после смешения: поток на выходе уносит ровно ту энергию, которую принесли входы.
 *
 * <p>Смешение — единственный вид теплового баланса, который не требует ни IF97, ни параметров
 * конструкции: {@code h_вых = Σ(G_i·h_i) / ΣG_i}. Поэтому модель работает там, где потоки
 * действительно сливаются в один: в смешивающем подогревателе, в деаэраторе и в тройнике,
 * который собирает два потока. Теплообмен через стенку (поверхностный подогреватель) и
 * внешний подвод тепла (котёл) сюда не входят — у них есть источник тепла, и баланс
 * записывается их собственными моделями.
 *
 * <p>Модель стоит на этапе {@link Stage#SOLVED}, потому что для неё нужны расходы: без них
 * средняя энтальпия не определена. Если хоть один входной расход или энтальпия неизвестны,
 * модель молчит — недостающее останется в {@code missing()}, а не превратится в догадку.
 * Известная энтальпия выхода проверяется по тому же балансу: расхождение означает, что
 * входные данные противоречат уравнению энергии.
 */
final class MixerModel implements ElementModel {

    @Override
    public String title() {
        return "смешение";
    }

    @Override
    public Stage stage() {
        return Stage.SOLVED;
    }

    @Override
    public boolean supports(Element element) {
        if (element instanceof MixingHeater || element instanceof Deaerator) {
            return true;
        }
        if (!(element instanceof PipeTee tee)) {
            return false;
        }
        return element.getInPutConnections(tee.in()).size() > 1
                && element.getOutPutConnections(tee.out()).size() == 1;
    }

    @Override
    public int resolve(Element element, StateAccess access) {
        List<FlowChannel> channels = element.flowChannels();
        if (channels.size() != 1) {
            return 0;
        }
        FlowChannel channel = channels.get(0);
        List<Connection> in = RuleSupport.connections(element, channel.in());
        List<Connection> out = RuleSupport.connections(element, channel.out());
        if (in == null || out == null || in.isEmpty() || out.size() != 1) {
            return 0;
        }

        double sumFlow = 0;
        double sumEnergy = 0;
        for (Connection source : in) {
            Double flow = access.read(source, Quantity.FLOW);
            Double enthalpy = access.read(source, Quantity.ENTHALPY);
            if (flow == null || enthalpy == null) {
                return 0;
            }
            sumFlow += flow;
            sumEnergy += flow * enthalpy;
        }
        if (sumFlow <= 0) {
            return 0;
        }

        Connection target = out.get(0);
        double mixed = sumEnergy / sumFlow;
        Double present = access.read(target, Quantity.ENTHALPY);
        if (present == null) {
            return access.write(target, Quantity.ENTHALPY, mixed) ? 1 : 0;
        }
        if (!RuleSupport.balanced(present, mixed)) {
            throw new IllegalStateException("«" + access.owner() + "», канал "
                    + channel.describe() + ": " + access.describe(target, Quantity.ENTHALPY)
                    + ", а баланс энергии входов даёт " + mixed
                    + " кДж/кг при расходе " + sumFlow + " кг/с");
        }
        return 0;
    }
}
