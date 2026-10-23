package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.Port;
import org.kpush88.Thermal_2.v3.Turbine;
import org.kpush88.Thermal_2.v3.thermo.WaterSteam;

import java.util.ArrayList;
import java.util.List;

/**
 * Турбина: каждый отсек расширяет пар по изоэнтропе с поправкой на ηoi.
 *
 * <p>Модель работает от входа к выходам: по состоянию перед турбиной ({@code p, h}) берётся
 * энтропия, и для каждого отбора и выхлопа, у которых <b>уже известно давление</b>, считается
 * энтальпия {@code h = h_вх − ηoi·(h_вх − h_изоэнтропы(p_вых, s_вх))}. Так устроен и реальный
 * расчёт: турбина расширяет пар до давления, которое задаёт схема (режим отбора, давление в
 * конденсаторе), а не наоборот. Поэтому давления отборов — исходные данные, а энтальпии —
 * результат модели.
 *
 * <p>Отсеки не «сцепляются» друг с другом через промежуточные энтальпии: энтропия входа одна
 * для всех выходов, потому что состояние перед каждым отсеком — это состояние перед турбиной
 * лишь при отсутствии промежуточного перегрева и потерь в тракте отбора. Как только появятся
 * промежуточный перегрев и отсеки со своими ηoi, модель станет последовательной — но и тогда
 * изменится только тело {@link #resolve}, а место в конвейере останется прежним.
 *
 * <p>Неподключённый отбор — это нулевой отбор: порт без связи пропускается
 * ({@link RuleSupport#connection} возвращает {@code null} для порта без единственной связи), а
 * не считается ошибкой. Давление, которого нет, оставляет энтальпию в
 * {@link SchemeState#missing()} — выдумывать её модель не станет.
 */
final class TurbineModel implements ElementModel {

    private final WaterSteam steam = new WaterSteam();

    @Override
    public String title() {
        return "турбина: изоэнтропное расширение";
    }

    @Override
    public Stage stage() {
        return Stage.RESOLVED;
    }

    @Override
    public boolean supports(Element element) {
        return element instanceof Turbine;
    }

    @Override
    public int resolve(Element element, StateAccess access) {
        Turbine turbine = (Turbine) element;
        Double efficiency = turbine.getInternalEfficiency();
        if (efficiency == null) {
            return 0;
        }
        Connection inlet = RuleSupport.connection(element, turbine.steamIn());
        if (inlet == null) {
            return 0;
        }
        Double inletPressure = access.read(inlet, Quantity.PRESSURE);
        Double inletEnthalpy = access.read(inlet, Quantity.ENTHALPY);
        if (inletPressure == null || inletEnthalpy == null) {
            return 0;
        }

        double entropy = steam.entropyPH(inletPressure, inletEnthalpy);
        int writes = 0;
        for (Port port : outlets(turbine)) {
            Connection out = RuleSupport.connection(element, port);
            if (out == null) {
                continue;
            }
            Double outPressure = access.read(out, Quantity.PRESSURE);
            if (outPressure == null) {
                continue;
            }
            if (outPressure > inletPressure) {
                throw new IllegalStateException("«" + element.name() + "», порт " + port.label()
                        + ": " + access.describe(out, Quantity.PRESSURE) + ", а перед турбиной "
                        + access.describe(inlet, Quantity.PRESSURE)
                        + ": пар не может расширяться до более высокого давления");
            }
            double isentropic = steam.enthalpyPS(outPressure, entropy);
            double enthalpy = inletEnthalpy - efficiency * (inletEnthalpy - isentropic);
            writes += RuleSupport.fill(access, out, Quantity.ENTHALPY, enthalpy);
        }
        return writes;
    }

    /** Выходы в порядке хода расширения: отборы, затем выхлоп. */
    private static List<Port> outlets(Turbine turbine) {
        List<Port> ports = new ArrayList<>(turbine.extractionOuts());
        ports.add(turbine.exhaustOut());
        return ports;
    }
}
