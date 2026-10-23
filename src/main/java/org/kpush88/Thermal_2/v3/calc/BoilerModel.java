package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Boiler;
import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.thermo.WaterSteam;

/**
 * Котёл: параметры свежего пара становятся состоянием на выходе.
 *
 * <p>Давление и температура пара — это и есть определение состояния за котлом, поэтому модель
 * не «дополняет» сечение, а <b>задаёт</b> его: {@code p = p_свежего}, {@code h = h(p, t)} по
 * IF97. Отсюда единственное во всём наборе место, где применяется не
 * {@link RuleSupport#fill «запись только неизвестного»}, а обычная запись в журнал: если на
 * выходе котла уже стоит другое значение, это противоречие исходных данных (два разных ГУ на
 * одно сечение), и журнал сообщает о нём с обоими происхождениями. Молча оставить «чужое»
 * значение здесь было бы хуже: тогда температура свежего пара перестала бы что-либо значить.
 *
 * <p>Питательная вода моделью не трогается: чтобы получить её состояние на выходе, нужен
 * расход тепла (баланс энергии аппарата), а это уравнение расхода, то есть СЛАУ. Давление
 * воды на выходе тоже не выводится — гидравлика котла не моделируется.
 */
final class BoilerModel implements ElementModel {

    private final WaterSteam steam = new WaterSteam();

    @Override
    public String title() {
        return "котёл: свежий пар по параметрам";
    }

    @Override
    public Stage stage() {
        return Stage.RESOLVED;
    }

    @Override
    public boolean supports(Element element) {
        return element instanceof Boiler;
    }

    @Override
    public int resolve(Element element, StateAccess access) {
        Boiler boiler = (Boiler) element;
        Double pressure = boiler.getSteamPressure();
        Double temperature = boiler.getSteamTemperature();
        if (pressure == null || temperature == null) {
            return 0;
        }
        Connection out = RuleSupport.connection(element, boiler.steamOut());
        if (out == null) {
            return 0;
        }
        double enthalpy = steam.enthalpyPT(pressure, temperature);
        int writes = access.write(out, Quantity.PRESSURE, pressure) ? 1 : 0;
        writes += access.write(out, Quantity.ENTHALPY, enthalpy) ? 1 : 0;
        return writes;
    }
}
