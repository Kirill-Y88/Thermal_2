package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.Pump;
import org.kpush88.Thermal_2.v3.thermo.WaterSteam;

/**
 * Насос: напор повышает давление, а работа уходит в энтальпию.
 *
 * <p>Две величины, которые знает насос, — приращение давления и КПД — дают обе стороны
 * процесса. Давление напора: {@code p_нап = p_вс + Δp}; вода несжимаема, поэтому работа
 * сжатия равна {@code v′·Δp}, а с учётом КПД энтальпия растёт на {@code Δh = v′·Δp/η}.
 * Удельный объём берётся по насыщенной жидкости при давлении всасывания: для воды
 * сжимаемостью можно пренебречь, и это единственное место расчёта, где объём входит в
 * уравнение напрямую.
 *
 * <p>Симметрия по давлению: если задано давление напора (например, его держит тракт), а
 * всасывание неизвестно, давление всасывания получается вычитанием напора — насос не обязан
 * ждать, пока состояние придёт со стороны конденсатора. Если же известны оба давления, их
 * разность проверяется: она обязана равняться напору, иначе это противоречие исходных данных,
 * о котором лучше узнать здесь, чем в отчёте.
 *
 * <p>Работа учитывается <b>всегда, когда напор задан</b>, даже если КПД не задан (тогда
 * η = 1 — идеальный процесс). Это не педантизм: если бы при отсутствии КПД модель переносила
 * энтальпию без изменений, то при следующем проходе, когда КПД «появился» бы, она пыталась бы
 * записать другое значение — расчёт стал бы зависеть от порядка проходов. Правило «есть
 * напор — есть работа» от порядка не зависит.
 *
 * <p>Без напора насос — тождественное звено: гидравлика тракта задаёт давление сама, а
 * энтальпия не меняется (работа неизвестна, потому что неизвестен напор). Это ровно то же
 * правило, что у трубы, и по той же причине: пока параметр не задан, модель не выдумывает.
 */
final class PumpModel implements ElementModel {

    /** МПа·м³/кг → кДж/кг: работа сжатия считается в тех же единицах, что и энтальпия. */
    private static final double MPA_M3_PER_KG_TO_KJ_PER_KG = 1000.0;

    private final WaterSteam steam = new WaterSteam();

    @Override
    public String title() {
        return "насос: напор и работа";
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
        Double rise = pump.getPressureRise();
        if (rise == null) {
            return RuleSupport.align(access, in, out, Quantity.ENTHALPY,
                    "напор насоса не задан, поэтому работа не учитывается и энтальпия "
                            + "не меняется");
        }
        return head(element, pump, access, in, out, rise);
    }

    /** Напор известен: давление с обеих сторон и работа насоса. */
    private int head(Element element, Pump pump, StateAccess access, Connection in, Connection out,
                     double rise) {
        int writes = 0;
        Double inletPressure = access.read(in, Quantity.PRESSURE);
        Double outletPressure = access.read(out, Quantity.PRESSURE);
        if (outletPressure != null && inletPressure == null) {
            inletPressure = outletPressure - rise;
            writes += RuleSupport.fill(access, in, Quantity.PRESSURE, inletPressure);
        }
        if (inletPressure != null && outletPressure == null) {
            outletPressure = inletPressure + rise;
            writes += RuleSupport.fill(access, out, Quantity.PRESSURE, outletPressure);
        }
        if (inletPressure == null || outletPressure == null) {
            return writes;
        }

        double actual = outletPressure - inletPressure;
        if (!RuleSupport.same(actual, rise)) {
            throw new IllegalStateException("«" + element.name() + "»: напор задан " + rise
                    + " МПа, а давления по сторонам различаются на " + actual + " МПа — "
                    + access.describe(in, Quantity.PRESSURE) + " и "
                    + access.describe(out, Quantity.PRESSURE));
        }

        Double inletEnthalpy = access.read(in, Quantity.ENTHALPY);
        if (inletEnthalpy == null) {
            return writes;
        }
        Double efficiency = pump.getEfficiency();
        double efficiencyValue = efficiency != null ? efficiency : 1.0;
        double work = steam.saturatedLiquidVolume(inletPressure) * rise
                * MPA_M3_PER_KG_TO_KJ_PER_KG / efficiencyValue;
        writes += RuleSupport.fill(access, out, Quantity.ENTHALPY, inletEnthalpy + work);
        return writes;
    }
}
