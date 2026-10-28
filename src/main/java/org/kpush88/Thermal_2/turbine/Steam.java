package org.kpush88.Thermal_2.turbine;

import com.hummeling.if97.IF97;
import com.hummeling.if97.OutOfRangeException;

/**
 * Свойства воды и водяного пара по IAPWS-IF97 для расчёта проточной части.
 *
 * <p>Обёртка над {@code com.hummeling:if97} в инженерных единицах теплового расчёта: давление
 * — МПа, температура — °C, энтальпия — кДж/кг, энтропия — кДж/(кг·K), удельный объём — м³/кг.
 * Внутри библиотеки температура задаётся в кельвинах, поэтому перевод делается здесь и только
 * здесь.
 *
 * <p><b>Почему своя обёртка, а не {@code v3.thermo.WaterSteam}.</b> Двойник должен считать
 * проточную часть, не зная ни про схему, ни про её расчётный слой: физика ступени проверяется
 * отдельно от цикла, и её тесты не должны падать из-за правок в {@code v3}. Поэтому здесь
 * повторены функции, нужные циклу, и добавлены те, которых циклу не требовалось, а ступени
 * нужны обязательно:
 * <ul>
 *   <li>{@link #volumePH} — удельный объём по {@code p} и {@code h}: из него и скорости звука
 *       складывается пропускная способность;</li>
 *   <li>{@link #speedOfSoundPH} — скорость звука: она даёт локальный показатель изоэнтропы
 *       {@link #isentropicExponentPH};</li>
 *   <li>{@link #vapourFractionPH} — степень сухости: ею контролируется влажность в последних
 *       ступенях и эрозионный предел {@code x_к}.</li>
 * </ul>
 *
 * <p><b>Показатель изоэнтропы не 1.3.</b> В уравнениях пропускной способности показатель
 * {@code k} — это не табличная константа для перегретого пара, а локальное свойство:
 * <pre>
 *     k = a² / (p·v)
 * </pre>
 * На входе в турбину (14 МПа, 3461 кДж/кг) это 1.2796 — близко к привычному 1.3. А в последнем
 * отсеке (0.004 МПа, 2148 кДж/кг) — 3.1986, потому что скорость звука во влажном паре считается
 * по двухфазной смеси и заметно ниже, чем у идеального газа. При «табличном» k = 1.3 критическое
 * отношение давлений вышло бы 0.546 вместо настоящих 0.340, то есть расход через последнюю
 * ступень был бы завышен в полтора раза. Поэтому {@code k} здесь всегда берётся из IF97, а не
 * из справочника.
 *
 * <p>Класс неизменяемый и потокобезопасный: IF97 не хранит состояния между вызовами, поэтому
 * один экземпляр можно держать в расчёте и вызывать на каждой ступени.
 */
public final class Steam {

    /** Сдвиг шкалы Кельвина: библиотека работает в K, расчёт — в °C. */
    private static final double KELVIN = 273.15;

    /** Перевод МПа в Па — только для формул, где давление умножается на объём. */
    static final double MPA = 1.0e6;

    private final IF97 if97 = new IF97(IF97.UnitSystem.DEFAULT);

    /** Энтальпия по давлению и температуре, кДж/кг. */
    public double enthalpyPT(double pMPa, double tC) {
        try {
            return if97.specificEnthalpyPT(pMPa, tC + KELVIN);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, t=" + tC + " °C", e);
        }
    }

    /** Энтальпия по давлению и энтропии, кДж/кг — конец изоэнтропного расширения. */
    public double enthalpyPS(double pMPa, double entropy) {
        try {
            return if97.specificEnthalpyPS(pMPa, entropy);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, s=" + entropy + " кДж/(кг·K)", e);
        }
    }

    /** Энтропия по давлению и энтальпии, кДж/(кг·K). */
    public double entropyPH(double pMPa, double enthalpy) {
        try {
            return if97.specificEntropyPH(pMPa, enthalpy);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, h=" + enthalpy + " кДж/кг", e);
        }
    }

    /** Температура по давлению и энтальпии, °C. */
    public double temperaturePH(double pMPa, double enthalpy) {
        try {
            return if97.temperaturePH(pMPa, enthalpy) - KELVIN;
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, h=" + enthalpy + " кДж/кг", e);
        }
    }

    /**
     * Удельный объём по давлению и энтальпии, м³/кг.
     *
     * <p>Главная величина пропускной способности: расход через горло — это
     * {@code G = F·c/v}, поэтому объём входит в уравнение напрямую. Во влажном паре IF97
     * возвращает объём смеси, то есть величину, которая и стоит в уравнении неразрывности.
     */
    public double volumePH(double pMPa, double enthalpy) {
        try {
            return if97.specificVolumePH(pMPa, enthalpy);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, h=" + enthalpy + " кДж/кг", e);
        }
    }

    /** Плотность по давлению и энтальпии, кг/м³. */
    public double densityPH(double pMPa, double enthalpy) {
        return 1.0 / volumePH(pMPa, enthalpy);
    }

    /**
     * Скорость звука по давлению и энтальпии, м/с.
     *
     * <p>Во влажном паре IF97 считает её по двухфазной смеси (с учётом сжимаемости паровой фазы
     * и межфазного обмена), поэтому она резко падает при переходе через линию насыщения.
     * Именно это и делает пропускную способность последних ступеней непохожей на
     * «идеальногазовую».
     */
    public double speedOfSoundPH(double pMPa, double enthalpy) {
        try {
            return if97.speedOfSoundPH(pMPa, enthalpy);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, h=" + enthalpy + " кДж/кг", e);
        }
    }

    /**
     * Степень сухости по давлению и энтальпии, доля: 1 в перегретом паре, 0…1 в двухфазной
     * области.
     *
     * <p><b>Ловушка библиотеки.</b> {@code IF97.vapourFractionPH} не ограничивает результат
     * единицей: в перегретом паре он продолжает формулу {@code (h − h′)/(h″ − h′)} и при
     * 14 МПа, 3461 кДж/кг выдаёт 1.7711 — число, которое выглядит как «перегретая сухость»,
     * но физического смысла не имеет. Поэтому границы области насыщения проверяются здесь:
     * выше {@code h″} возвращается 1, ниже {@code h′} — 0. Выше критического давления
     * двухфазной области нет вовсе, там тоже возвращается 1.
     */
    public double vapourFractionPH(double pMPa, double enthalpy) {
        try {
            if (enthalpy >= if97.specificEnthalpySaturatedVapourP(pMPa)) {
                return 1.0;
            }
            if (enthalpy <= if97.specificEnthalpySaturatedLiquidP(pMPa)) {
                return 0.0;
            }
            return if97.vapourFractionPH(pMPa, enthalpy);
        } catch (OutOfRangeException e) {
            if (pMPa > 22.064) {
                return 1.0;
            }
            throw outOfRange("p=" + pMPa + " МПа, h=" + enthalpy + " кДж/кг", e);
        }
    }

    /**
     * Локальный показатель изоэнтропы {@code k = a²/(p·v)}, безразмерный.
     *
     * <p>Стоит в уравнении пропускной способности и в выражении для критического отношения
     * давлений. Считается по скорости звука, а не берётся из таблиц: во влажном паре он
     * отличается от 1.3 втрое (см. описание класса).
     */
    public double isentropicExponentPH(double pMPa, double enthalpy) {
        double volume = volumePH(pMPa, enthalpy);
        double sound = speedOfSoundPH(pMPa, enthalpy);
        return sound * sound / (pMPa * MPA * volume);
    }

    /** Температура насыщения при заданном давлении, °C. */
    public double saturationTemperature(double pMPa) {
        try {
            return if97.saturationTemperatureP(pMPa) - KELVIN;
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа", e);
        }
    }

    /** Энтальпия насыщенной жидкости h′, кДж/кг. */
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

    /** Удельный объём насыщенной жидкости v′, м³/кг. */
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
