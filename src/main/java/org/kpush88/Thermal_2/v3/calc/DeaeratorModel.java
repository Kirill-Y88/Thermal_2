package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Deaerator;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.thermo.WaterSteam;

/**
 * Деаэратор: рабочее давление задаёт состояние деаэрированной воды.
 *
 * <p>Вода на выходе деаэратора — насыщенная при рабочем давлении аппарата: пар барботирует
 * через воду, и та уходит при температуре насыщения. Поэтому модель знает выход целиком, не
 * зная расходов:
 * <ol>
 *   <li>{@code p_вых = p_д} — рабочее давление аппарата;</li>
 *   <li>{@code h_вых = h′(p_д)} — насыщенная жидкость при этом давлении.</li>
 * </ol>
 *
 * <p>Обе величины <b>задаются</b> моделью (обычная запись в журнал, как у котла), а не
 * дополняются: {@code p_д} — параметр конструкции, и он определяет состояние, а не оценивает
 * его. Если на выходе уже стоит другое значение, это противоречие исходных данных, и журнал
 * сообщает о нём с обоими происхождениями.
 *
 * <p><b>Паровая сторона не трогается.</b> Давление отбора — величина режима, и в деаэратор пар
 * приходит дросселированным: {@code p_отбора > p_д}. Равенство было бы схемой без потерь в
 * подводящей линии. Модель проверяет только физическую возможность (при {@code p_отбора ≤ p_д}
 * пар не поступит в аппарат) и не переписывает давление отбора рабочим. Дросселирование —
 * процесс при постоянной энтальпии, поэтому энергетический баланс аппарата от разницы давлений
 * не страдает: в него входит {@code h_отбора} с линии отбора.
 *
 * <p>Чего модель <b>не</b> делает — не считает, сколько пара нужно на нагрев. Это баланс
 * энергии аппарата, то есть уравнение относительно расхода, и его место в системе балансов
 * ({@link MixerModel} проверяет его же, когда расходы появились: энтальпия выхода, найденная
 * смешением, обязана совпасть с {@code h′(p_д)}).
 */
final class DeaeratorModel implements ElementModel {

    private final WaterSteam steam = new WaterSteam();

    @Override
    public String title() {
        return "деаэратор: рабочее давление и насыщенная вода";
    }

    @Override
    public Stage stage() {
        return Stage.RESOLVED;
    }

    @Override
    public boolean supports(Element element) {
        return element instanceof Deaerator;
    }

    @Override
    public int resolve(Element element, StateAccess access) {
        Deaerator deaerator = (Deaerator) element;
        Double pressure = deaerator.getPressure();
        if (pressure == null) {
            return 0;
        }
        Connection waterOut = RuleSupport.connection(element, deaerator.waterOut());
        if (waterOut == null) {
            return 0;
        }

        Connection heating = RuleSupport.connection(element, deaerator.steamIn());
        if (heating != null) {
            Double extractionPressure = access.read(heating, Quantity.PRESSURE);
            if (extractionPressure != null && extractionPressure <= pressure) {
                throw new IllegalStateException("«" + element.name() + "»: "
                        + access.describe(heating, Quantity.PRESSURE) + ", а рабочее давление "
                        + "деаэратора " + pressure + " МПа — пар не поступит в аппарат: "
                        + "давление отбора должно быть выше рабочего");
            }
        }

        int writes = access.write(waterOut, Quantity.PRESSURE, pressure) ? 1 : 0;
        writes += access.write(waterOut, Quantity.ENTHALPY,
                steam.saturatedLiquidEnthalpy(pressure)) ? 1 : 0;
        return writes;
    }
}
