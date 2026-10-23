package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.Port;
import org.kpush88.Thermal_2.v3.Turbine;
import org.kpush88.Thermal_2.v3.thermo.WaterSteam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Турбина: отсеки расширяют пар последовательно, каждый — по изоэнтропе с поправкой на ηoi.
 *
 * <p>Модель идёт от входа к выходам в порядке падения давления. Для очередного отсека по
 * состоянию <b>перед ним</b> ({@code p, h}) берётся энтропия, и энтальпия на выходе считается как
 * {@code h_вых = h_вх − ηoi·(h_вх − h_изоэнтропы(p_вых, s_вх))}. Уже найденная энтальпия
 * становится входом следующего отсека, поэтому теплоперепады отсеков складываются в процесс
 * расширения, а не считаются независимо от одного и того же состояния.
 *
 * <p>Почему именно так. Давления отборов задаёт схема (режим отбора, подогреватели, вакуум), и
 * турбина расширяет пар <b>до</b> этих давлений, а не наоборот: давления — исходные данные,
 * энтальпии — результат модели. Но состояние перед вторым отсеком — это состояние после первого,
 * и если считать все отборы от свежего пара, турбина «расширяет» один и тот же пар несколько раз:
 * выхлоп получается перегретым на десятки кДж/кг, а суммарная мощность — завышенной. Порядок
 * отсеков задаёт не порядок портов, а давления: порты нумеруются по ходу расширения, но модель
 * сортирует выходы по давлению и не зависит от того, как их перечислили.
 *
 * <p>Неизвестное давление — это не отсек: выход без давления пропускается, потому что расширяться
 * «до неизвестного давления» нельзя, а выдумывать его модель не станет. Известная энтальпия
 * выхода (ГУ или расчёт подогревателя) сильнее выведенной: тогда именно она служит началом
 * следующего отсека, а не пересчитанное значение — так заданное состояние отбора не теряется.
 *
 * <p>Неподключённый отбор — это нулевой отбор: порт без связи пропускается
 * ({@link RuleSupport#connection} возвращает {@code null} для порта без единственной связи), а не
 * считается ошибкой.
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

        double pressure = inletPressure;
        double enthalpy = inletEnthalpy;
        int writes = 0;
        for (Section section : sections(element, turbine, access, inletPressure, inlet)) {
            double entropy = steam.entropyPH(pressure, enthalpy);
            double isentropic = steam.enthalpyPS(section.pressure(), entropy);
            double expanded = enthalpy - efficiency * (enthalpy - isentropic);
            writes += RuleSupport.fill(access, section.outlet(), Quantity.ENTHALPY, expanded);
            // Известная энтальпия сильнее выведенной: тогда следующий отсек начинается с неё.
            enthalpy = access.read(section.outlet(), Quantity.ENTHALPY);
            pressure = section.pressure();
        }
        return writes;
    }

    /**
     * Отсеки в порядке хода расширения — по убыванию давления.
     *
     * <p>Выход без известного давления отсеком не является, а давление отбора выше свежего пара
     * означает, что он не отбор: там пар не расширяется. Сортировка устойчива, поэтому при равных
     * давлениях сохраняется порядок объявления портов.
     */
    private static List<Section> sections(Element element, Turbine turbine, StateAccess access,
                                          double inletPressure, Connection inlet) {
        List<Section> sections = new ArrayList<>();
        for (Port port : outlets(turbine)) {
            Connection out = RuleSupport.connection(element, port);
            if (out == null) {
                continue;
            }
            Double pressure = access.read(out, Quantity.PRESSURE);
            if (pressure == null) {
                continue;
            }
            if (pressure > inletPressure) {
                throw new IllegalStateException("«" + element.name() + "», порт " + port.label()
                        + ": " + access.describe(out, Quantity.PRESSURE) + ", а перед турбиной "
                        + access.describe(inlet, Quantity.PRESSURE)
                        + ": пар не может расширяться до более высокого давления");
            }
            sections.add(new Section(out, pressure));
        }
        sections.sort(Comparator.comparingDouble(Section::pressure).reversed());
        return sections;
    }

    /** Выход турбины с известным давлением — это отсек, заканчивающийся на этом выходе. */
    private record Section(Connection outlet, double pressure) {
    }

    /** Выходы в порядке хода расширения: отборы, затем выхлоп. */
    private static List<Port> outlets(Turbine turbine) {
        List<Port> ports = new ArrayList<>(turbine.extractionOuts());
        ports.add(turbine.exhaustOut());
        return ports;
    }
}
