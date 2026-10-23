package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.PipeTee;

import java.util.List;

/**
 * Тройник: перенос состояния через разветвление и давление при слиянии.
 *
 * <p>Тройник — участок трассы, поэтому среда в нём не меняется. При разветвлении (один вход,
 * два выхода) это буквально означает, что оба выхода получают то же давление и энтальпию:
 * деление расхода состояние не меняет. При слиянии (два входа, один выход) энтальпию
 * определяет смешение и без расходов её не найти — это делает {@link MixerModel} на этапе
 * {@link Stage#SOLVED}; здесь переносится только давление, и лишь когда входы согласованы.
 *
 * <p>Разные давления на входе слияния — не ошибка: давления выравнивает гидравлика, которой в
 * модели пока нет. Поэтому давление на выходе в этом случае не выдумывается, а остаётся в
 * {@code missing()}; выдуманное среднее было бы правдоподобным, но неверным.
 */
final class TeeModel implements ElementModel {

    @Override
    public String title() {
        return "тройник без потерь";
    }

    @Override
    public Stage stage() {
        return Stage.NORMALIZED;
    }

    @Override
    public boolean supports(Element element) {
        return element instanceof PipeTee;
    }

    @Override
    public int resolve(Element element, StateAccess access) {
        PipeTee tee = (PipeTee) element;
        List<Connection> in = element.getInPutConnections(tee.in());
        List<Connection> out = element.getOutPutConnections(tee.out());
        if (in.isEmpty() || out.isEmpty()) {
            return 0;
        }
        if (in.size() == 1) {
            int writes = 0;
            for (Connection target : out) {
                for (Quantity quantity : RuleSupport.STATE) {
                    writes += RuleSupport.align(access, in.get(0), target, quantity,
                            "разветвление не меняет состояние среды");
                }
            }
            return writes;
        }
        if (out.size() == 1) {
            return pressure(access, in, out.get(0));
        }
        return 0;
    }

    /** Давление слияния: переносится, только если все входы уже имеют одно и то же давление. */
    private static int pressure(StateAccess access, List<Connection> in, Connection out) {
        Double common = null;
        for (Connection source : in) {
            Double value = access.read(source, Quantity.PRESSURE);
            if (value == null) {
                return 0;
            }
            if (common == null) {
                common = value;
            } else if (!RuleSupport.same(common, value)) {
                return 0;
            }
        }
        Double present = access.read(out, Quantity.PRESSURE);
        if (present == null) {
            return access.write(out, Quantity.PRESSURE, common) ? 1 : 0;
        }
        if (!RuleSupport.same(present, common)) {
            throw new IllegalStateException("«" + access.owner() + "»: давление слияния "
                    + access.describe(out, Quantity.PRESSURE) + " не совпадает с давлением на "
                    + "входах " + access.describe(in.get(0), Quantity.PRESSURE)
                    + ": тройник без потерь не меняет давление");
        }
        return 0;
    }
}
