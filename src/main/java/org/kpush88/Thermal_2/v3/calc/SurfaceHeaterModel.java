package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.SurfaceHeater;
import org.kpush88.Thermal_2.v3.thermo.WaterSteam;

/**
 * Поверхностный подогреватель: греющий пар конденсируется, вода недогревается до насыщения.
 *
 * <p>Модель замыкает оба канала аппарата по параметрам, которые в нём действительно заданы:
 * <ol>
 *   <li><b>Дренаж</b> — насыщенная жидкость при давлении греющего пара:
 *       {@code h_др = h′(p_пара)}. Пар в аппарате конденсируется, поэтому дренаж не может быть
 *       ни перегретым, ни кипящим. Давление дренажа заполняется тем же давлением, но только
 *       если оно ещё неизвестно: в каскаде дренаж уходит в аппарат с другим давлением, и
 *       распоряжается там гидравлика, а не эта модель.</li>
 *   <li><b>Вода</b> — нагрев до температуры насыщения греющего пара минус недогрев:
 *       {@code h_вых = h(p_воды, t_нас(p_пара) − Δt)}. Давление воды берётся её собственное:
 *       жидкость сжата, и энтальпия зависит от него, пусть и слабо.</li>
 * </ol>
 *
 * <p>Давление воды через аппарат переносится без изменений — как в трубе: гидравлические
 * потери в модели не учитываются, и водяной тракт иначе рвался бы на каждом подогревателе.
 * Это утверждение проверяется ({@link RuleSupport#align}), а не просто перезаписывается.
 *
 * <p>Чего модель <b>не</b> делает. Она не считает, сколько греющего пара нужно, чтобы так
 * нагреть воду: это баланс энергии аппарата, то есть уравнение относительно расхода, и его
 * место — в системе балансов. Здесь состояние выхода известно <b>до</b> баланса, потому что
 * его задаёт недогрев, — именно поэтому баланс потом превращается в уравнение для расхода
 * отбора, а не наоборот.
 */
final class SurfaceHeaterModel implements ElementModel {

    private final WaterSteam steam = new WaterSteam();

    @Override
    public String title() {
        return "поверхностный подогреватель: конденсация и недогрев";
    }

    @Override
    public Stage stage() {
        return Stage.RESOLVED;
    }

    @Override
    public boolean supports(Element element) {
        return element instanceof SurfaceHeater;
    }

    @Override
    public int resolve(Element element, StateAccess access) {
        SurfaceHeater heater = (SurfaceHeater) element;
        Connection heating = RuleSupport.connection(element, heater.steamIn());
        Connection drain = RuleSupport.connection(element, heater.drainOut());
        Connection waterIn = RuleSupport.connection(element, heater.waterIn());
        Connection waterOut = RuleSupport.connection(element, heater.waterOut());

        int writes = 0;
        if (waterIn != null && waterOut != null) {
            writes += RuleSupport.align(access, waterIn, waterOut, Quantity.PRESSURE,
                    "поверхностный подогреватель не меняет давление воды: гидравлические "
                            + "потери в модели не учитываются");
        }

        Double heatingPressure = heating != null ? access.read(heating, Quantity.PRESSURE) : null;
        if (heatingPressure == null) {
            return writes;
        }

        if (drain != null) {
            writes += RuleSupport.fill(access, drain, Quantity.PRESSURE, heatingPressure);
            writes += RuleSupport.fill(access, drain, Quantity.ENTHALPY,
                    steam.saturatedLiquidEnthalpy(heatingPressure));
        }

        Double subcooling = heater.getSubcooling();
        if (subcooling == null || waterOut == null) {
            return writes;
        }
        Double waterPressure = access.read(waterOut, Quantity.PRESSURE);
        if (waterPressure == null && waterIn != null) {
            waterPressure = access.read(waterIn, Quantity.PRESSURE);
        }
        if (waterPressure == null) {
            return writes;
        }

        double saturation = steam.saturationTemperature(heatingPressure);
        if (subcooling >= saturation) {
            throw new IllegalStateException("«" + element.name() + "»: недогрев " + subcooling
                    + " K не меньше температуры насыщения греющего пара "
                    + saturation + " °C (t_нас при "
                    + access.describe(heating, Quantity.PRESSURE)
                    + ") — при таком недогреве вода на выходе была бы холоднее входа");
        }
        writes += RuleSupport.fill(access, waterOut, Quantity.ENTHALPY,
                steam.enthalpyPT(waterPressure, saturation - subcooling));
        return writes;
    }
}
