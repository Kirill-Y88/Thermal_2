package org.kpush88.Thermal_2.v1.thermo;

import com.hummeling.if97.IF97;
import com.hummeling.if97.OutOfRangeException;

/**
 * Термодинамические свойства воды и водяного пара по IAPWS-IF97.
 *
 * <p>Обёртка над библиотекой {@code com.hummeling:if97}. Все величины — в «инженерных»
 * единицах, удобных для теплового расчёта:
 * <ul>
 *   <li>давление {@code p} — МПа;</li>
 *   <li>температура {@code t} — °C (внутри библиотеки — K);</li>
 *   <li>энтальпия {@code h} — кДж/кг;</li>
 *   <li>энтропия {@code s} — кДж/(кг·K);</li>
 *   <li>удельный объём {@code v} — м³/кг.</li>
 * </ul>
 *
 * <p>Класс неизменяемый и потокобезопасный (IF97 не хранит состояния между вызовами).
 */
public final class WaterSteam {

    private static final double KELVIN = 273.15;

    private final IF97 if97 = new IF97(IF97.UnitSystem.DEFAULT);

    /** Энтальпия по давлению и температуре, кДж/кг. */
    public double enthalpyPT(double pMPa, double tC) {
        try {
            return if97.specificEnthalpyPT(pMPa, tC + KELVIN);
        } catch (OutOfRangeException e) {
            throw outOfRange(pMPa, tC, e);
        }
    }

    /** Энтропия по давлению и температуре, кДж/(кг·K). */
    public double entropyPT(double pMPa, double tC) {
        try {
            return if97.specificEntropyPT(pMPa, tC + KELVIN);
        } catch (OutOfRangeException e) {
            throw outOfRange(pMPa, tC, e);
        }
    }

    /**
     * Энтальпия по давлению и энтропии — используется для изоэнтропного расширения
     * («идеального» процесса в отсеке турбины).
     */
    public double enthalpyPS(double pMPa, double entropy) {
        try {
            return if97.specificEnthalpyPS(pMPa, entropy);
        } catch (OutOfRangeException e) {
            throw outOfRange(pMPa, Double.NaN, e);
        }
    }

    /** Энтропия по давлению и энтальпии (снятие энтропии после реального расширения). */
    public double entropyPH(double pMPa, double enthalpy) {
        try {
            return if97.specificEntropyPH(pMPa, enthalpy);
        } catch (OutOfRangeException e) {
            throw outOfRange(pMPa, Double.NaN, e);
        }
    }

    /**
     * Степень сухости пара по давлению и энтальпии: {@code 0} — кипящая жидкость,
     * {@code (0..1)} — влажный пар, {@code NaN} — перегретый пар (x не определён).
     *
     * <p>Вычисляется по {@code h'} и {@code h''} — это устойчиво и для недогретой
     * жидкости, и для перегретого пара (там обращение к «функции сухости» IF97
     * вышло бы за область определения), и для закритического давления, где линии
     * насыщения нет: функция тогда возвращает {@code NaN}.
     */
    public double vapourFractionPH(double pMPa, double enthalpy) {
        double hLiquid;
        double hVapour;
        try {
            hLiquid = if97.specificEnthalpySaturatedLiquidP(pMPa);
            hVapour = if97.specificEnthalpySaturatedVapourP(pMPa);
        } catch (OutOfRangeException e) {
            return Double.NaN;
        }
        if (enthalpy <= hLiquid) {
            return 0.0;
        }
        if (enthalpy >= hVapour) {
            return Double.NaN;
        }
        return (enthalpy - hLiquid) / (hVapour - hLiquid);
    }

    /** Температура по давлению и энтальпии, °C. */
    public double temperaturePH(double pMPa, double enthalpy) {
        try {
            return if97.temperaturePH(pMPa, enthalpy) - KELVIN;
        } catch (OutOfRangeException e) {
            throw outOfRange(pMPa, Double.NaN, e);
        }
    }

    /** Температура насыщения при заданном давлении, °C. */
    public double saturationTemperature(double pMPa) {
        try {
            return if97.saturationTemperatureP(pMPa) - KELVIN;
        } catch (OutOfRangeException e) {
            throw outOfRange(pMPa, Double.NaN, e);
        }
    }

    /** Энтальпия сухого насыщенного пара h'', кДж/кг. */
    public double saturatedVapourEnthalpy(double pMPa) {
        try {
            return if97.specificEnthalpySaturatedVapourP(pMPa);
        } catch (OutOfRangeException e) {
            throw outOfRange(pMPa, Double.NaN, e);
        }
    }

    /** Энтальпия насыщенной жидкости h', кДж/кг. */
    public double saturatedLiquidEnthalpy(double pMPa) {
        try {
            return if97.specificEnthalpySaturatedLiquidP(pMPa);
        } catch (OutOfRangeException e) {
            throw outOfRange(pMPa, Double.NaN, e);
        }
    }

    /** Удельный объём насыщенной жидкости v', м³/кг. */
    public double saturatedLiquidVolume(double pMPa) {
        try {
            return if97.specificVolumeSaturatedLiquidP(pMPa);
        } catch (OutOfRangeException e) {
            throw outOfRange(pMPa, Double.NaN, e);
        }
    }

    /**
     * Энтальпия недогретой жидкости на выходе поверхностного подогревателя:
     * вода при давлении {@code pMPa} и температуре {@code (tнасыщ − недогрев)}.
     */
    public double subcooledLiquidEnthalpy(double pMPa, double subcoolingC) {
        return enthalpyPT(pMPa, saturationTemperature(pMPa) - subcoolingC);
    }

    private static IllegalArgumentException outOfRange(double pMPa, double tC, Exception cause) {
        return new IllegalArgumentException(
                "Точка вне области IF97: p=" + pMPa + " МПа, t=" + tC + " °C", cause);
    }
}
