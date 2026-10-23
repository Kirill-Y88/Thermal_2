package org.kpush88.Thermal_2.v3.thermo;

import com.hummeling.if97.IF97;
import com.hummeling.if97.OutOfRangeException;

/**
 * Термодинамические свойства воды и водяного пара по IAPWS-IF97 для расчётного слоя v3.
 *
 * <p>Обёртка над библиотекой {@code com.hummeling:if97} в «инженерных» единицах теплового
 * расчёта:
 * <ul>
 *   <li>давление {@code p} — МПа;</li>
 *   <li>температура {@code t} — °C;</li>
 *   <li>энтальпия {@code h} — кДж/кг;</li>
 *   <li>энтропия {@code s} — кДж/(кг·K);</li>
 *   <li>удельный объём {@code v} — м³/кг.</li>
 * </ul>
 * Внутри библиотеки температура задаётся в кельвинах, поэтому перевод делается здесь и только
 * здесь: модели работают в градусах Цельсия и не должны помнить про сдвиг на 273.15.
 *
 * <p><b>Почему свой пакет, а не {@code v1.thermo}.</b> Поколения v1…v4 развиваются
 * независимо: у каждого своя доменная модель и свой расчётный слой, и v3 не должен зависеть от
 * прототипа v1 — иначе правка старого кода меняла бы поведение нового. Обёртка намеренно узкая:
 * в ней только то, что нужно моделям v3 (состояние по {@code p} и {@code t}, изоэнтропное
 * расширение, насыщение), а не весь набор функций IF97.
 *
 * <p>Класс неизменяемый и потокобезопасный: IF97 не хранит состояния между вызовами, поэтому
 * один экземпляр можно держать в модели и вызывать при каждом проходе.
 */
public final class WaterSteam {

    private static final double KELVIN = 273.15;

    private final IF97 if97 = new IF97(IF97.UnitSystem.DEFAULT);

    /** Энтальпия по давлению и температуре, кДж/кг. */
    public double enthalpyPT(double pMPa, double tC) {
        try {
            return if97.specificEnthalpyPT(pMPa, tC + KELVIN);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, t=" + tC + " °C", e);
        }
    }

    /**
     * Энтальпия по давлению и энтропии, кДж/кг — состояние в конце изоэнтропного расширения.
     *
     * <p>Это и есть «идеальный» отсек турбины: внутренний относительный КПД применяется к
     * разности энтальпий уже после этой точки, поэтому изоэнтропа считается отдельным вызовом,
     * а не прячется внутри модели.
     */
    public double enthalpyPS(double pMPa, double entropy) {
        try {
            return if97.specificEnthalpyPS(pMPa, entropy);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, s=" + entropy + " кДж/(кг·K)", e);
        }
    }

    /** Энтропия по давлению и энтальпии, кДж/(кг·K) — начало расширения. */
    public double entropyPH(double pMPa, double enthalpy) {
        try {
            return if97.specificEntropyPH(pMPa, enthalpy);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, h=" + enthalpy + " кДж/кг", e);
        }
    }

    /** Температура по давлению и энтальпии, °C — для отчётов и проверок. */
    public double temperaturePH(double pMPa, double enthalpy) {
        try {
            return if97.temperaturePH(pMPa, enthalpy) - KELVIN;
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, h=" + enthalpy + " кДж/кг", e);
        }
    }

    /** Температура насыщения при заданном давлении, °C. */
    public double saturationTemperature(double pMPa) {
        try {
            return if97.saturationTemperatureP(pMPa) - KELVIN;
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа", e);
        }
    }

    /** Энтальпия насыщенной жидкости h′, кДж/кг — состояние конденсата и дренажа. */
    public double saturatedLiquidEnthalpy(double pMPa) {
        try {
            return if97.specificEnthalpySaturatedLiquidP(pMPa);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа", e);
        }
    }

    /** Энтальпия сухого насыщенного пара h″, кДж/кг. */
    public double saturatedVapourEnthalpy(double pMPa) {
        try {
            return if97.specificEnthalpySaturatedVapourP(pMPa);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа", e);
        }
    }

    /**
     * Удельный объём насыщенной жидкости v′, м³/кг.
     *
     * <p>Нужен работе насоса: вода несжимаема, поэтому {@code Δh ≈ v′·Δp} — единственное место
     * теплового расчёта, где объём входит в уравнение напрямую.
     */
    public double saturatedLiquidVolume(double pMPa) {
        try {
            return if97.specificVolumeSaturatedLiquidP(pMPa);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа", e);
        }
    }

    private static IllegalArgumentException outOfRange(String point, Exception cause) {
        return new IllegalArgumentException("точка вне области IF97: " + point, cause);
    }
}
