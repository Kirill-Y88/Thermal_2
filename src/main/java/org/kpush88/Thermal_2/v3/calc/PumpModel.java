package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.Pump;

/**
 * Насос: давление повышается, энтальпия пока не меняется.
 *
 * <p>Работа насоса в тепловом балансе не учитывается ({@link Pump}), поэтому энтальпия на
 * напоре равна энтальпии на всасывании — эту величину модель и переносит. Давление напора из
 * энтальпии не следует: его определяет характеристика насоса или режим тракта, поэтому здесь
 * оно не вычисляется и честно остаётся в отчёте {@code missing()}.
 *
 * <p>Когда появится приращение давления (и работа насоса), изменится только эта модель:
 * этап {@link Stage#RESOLVED} и место в конвейере останутся прежними.
 */
final class PumpModel implements ElementModel {

    @Override
    public String title() {
        return "насос без учёта работы";
    }

    @Override
    public Stage stage() {
        return Stage.RESOLVED;
    }

    @Override
    public boolean supports(Element element) {
        return element instanceof Pump;
    }

    @Override
    public int resolve(Element element, StateAccess access) {
        Pump pump = (Pump) element;
        Connection in = RuleSupport.connection(element, pump.waterIn());
        Connection out = RuleSupport.connection(element, pump.waterOut());
        if (in == null || out == null) {
            return 0;
        }
        return RuleSupport.align(access, in, out, Quantity.ENTHALPY,
                "работа насоса в тепловом балансе не учитывается, поэтому энтальпия не меняется");
    }
}
