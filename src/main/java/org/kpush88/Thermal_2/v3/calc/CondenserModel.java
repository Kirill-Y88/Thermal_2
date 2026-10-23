package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Condenser;
import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.thermo.WaterSteam;

/**
 * Конденсатор: выхлоп и дренажи становятся конденсатом на линии насыщения.
 *
 * <p>Давление в конденсаторе задаёт режим (его держит вакуумная установка), поэтому модель
 * берёт его из сечения выхлопа и переносит на конденсат: гидравлики аппарата в модели нет, и
 * конденсат уходит с тем же давлением. Энтальпия — насыщенная жидкость при этом давлении
 * {@code h = h′(p)}: тепло отводится охлаждающей водой ровно настолько, чтобы пар
 * сконденсировался. Если у конденсатора задано переохлаждение, энтальпия считается при
 * {@code t_нас(p) − Δt} — вода остаётся сжатой, поэтому давление в расчёте своё же.
 *
 * <p>Дренажи подогревателей, приходящие в {@code drainIn}, отдельной энтальпии не получают:
 * они конденсируются вместе с выхлопом и уходят тем же конденсатом, а их расход и тепло
 * учитывает баланс энергии аппарата (в нём же — расход охлаждающей воды). Здесь состояние
 * выхода известно без баланса, и именно поэтому баланс потом решается относительно расходов.
 */
final class CondenserModel implements ElementModel {

    private final WaterSteam steam = new WaterSteam();

    @Override
    public String title() {
        return "конденсатор: конденсат на линии насыщения";
    }

    @Override
    public Stage stage() {
        return Stage.RESOLVED;
    }

    @Override
    public boolean supports(Element element) {
        return element instanceof Condenser;
    }

    @Override
    public int resolve(Element element, StateAccess access) {
        Condenser condenser = (Condenser) element;
        Connection exhaust = RuleSupport.connection(element, condenser.exhaustIn());
        Connection condensate = RuleSupport.connection(element, condenser.condensateOut());
        Double pressure = exhaust != null ? access.read(exhaust, Quantity.PRESSURE) : null;
        if (pressure == null || condensate == null) {
            return 0;
        }

        double enthalpy = saturatedEnthalpy(element, condenser, pressure, access, exhaust);
        int writes = RuleSupport.fill(access, condensate, Quantity.PRESSURE, pressure);
        writes += RuleSupport.fill(access, condensate, Quantity.ENTHALPY, enthalpy);
        return writes;
    }

    /** Энтальпия конденсата: на линии насыщения или на заданное переохлаждение ниже неё. */
    private double saturatedEnthalpy(Element element, Condenser condenser, double pressure,
                                     StateAccess access, Connection exhaust) {
        Double subcooling = condenser.getSubcooling();
        if (subcooling == null) {
            return steam.saturatedLiquidEnthalpy(pressure);
        }
        double saturation = steam.saturationTemperature(pressure);
        if (subcooling >= saturation) {
            throw new IllegalStateException("«" + element.name() + "»: переохлаждение "
                    + subcooling + " K не меньше температуры насыщения " + saturation
                    + " °C (t_нас при " + access.describe(exhaust, Quantity.PRESSURE)
                    + ") — при таком переохлаждении конденсат был бы холоднее 0 °C");
        }
        return steam.enthalpyPT(pressure, saturation - subcooling);
    }
}
