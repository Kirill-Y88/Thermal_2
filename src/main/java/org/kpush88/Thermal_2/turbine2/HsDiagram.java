package org.kpush88.Thermal_2.turbine2;

import com.hummeling.if97.IF97;
import com.hummeling.if97.OutOfRangeException;

import java.util.Locale;

/**
 * Параметры hs-диаграммы воды и водяного пара по IAPWS-IF97 — термодинамическая основа
 * расчёта турбины второго поколения.
 *
 * <p>Обёртка над {@code com.hummeling:if97} в инженерных единицах теплового расчёта: давление —
 * МПа, температура — °C, энтальпия — кДж/кг, энтропия — кДж/(кг·K), удельный объём — м³/кг.
 * Внутри библиотеки температура задаётся в кельвинах, поэтому перевод делается здесь и только
 * здесь.
 *
 * <p><b>Зачем отдельный пакет.</b> {@code turbine2} развивается параллельно с {@code turbine} и
 * не зависит от него: у второго поколения своя доменная модель, и правка первого не должна менять
 * поведение второго (тот же принцип, что у поколений {@code v1…v4}). Поэтому здесь повторены
 * только нужные функции IF97, а {@code turbine.Steam} не импортируется.
 *
 * <p><b>Что даёт класс.</b> Всё, что нужно, чтобы читать и строить точки на hs-диаграмме:
 * <ul>
 *   <li>состояние по любым двум параметрам — {@code (p, t)}, {@code (p, h)}, {@code (p, s)};</li>
 *   <li>располагаемый (изоэнтропный) теплоперепад {@link #isentropicDrop} — перепад ступени;</li>
 *   <li>реальное расширение {@link #expand} с внутренним относительным КПД;</li>
 *   <li>параметры насыщения — {@code t_н}, {@code h′}, {@code h″}, {@code s′}, {@code s″},
 *       {@code v′}, {@code v″};</li>
 *   <li>локальный показатель изоэнтропы {@code k = a²/(p·v)} для пропускной способности;</li>
 *   <li>разбиение теплоперепада линией насыщения — {@link #splitDrop(double, double, double)} и
 *       {@link HeatDropSplit}: сколько перепада лежит в перегретом паре, а сколько во влажном.</li>
 * </ul>
 *
 * <p><b>Ловушка степени сухости.</b> {@code IF97.vapourFractionPH} не ограничивает результат
 * единицей: в перегретом паре он продолжает формулу {@code (h − h′)/(h″ − h′)} и выдаёт числа
 * больше единицы. Поэтому границы области насыщения проверяются здесь (см.
 * {@link #vapourFractionPH}).
 *
 * <p>Класс неизменяемый и потокобезопасный: IF97 не хранит состояния между вызовами, поэтому
 * один экземпляр можно держать в расчёте и вызывать на каждой ступени.
 */
public final class HsDiagram {

    /** Сдвиг шкалы Кельвина: библиотека работает в K, расчёт — в °C. */
    private static final double KELVIN = 273.15;

    /** Перевод МПа в Па — только для формул, где давление умножается на объём. */
    private static final double MPA = 1.0e6;

    /** Нижняя граница кривой насыщения IF97, МПа (тройная точка — 0.000611213). */
    private static final double MIN_SATURATION_PRESSURE = 0.000612;

    /**
     * Верхняя граница обратной задачи по насыщению, МПа. У самой критической точки IF97 даёт
     * немонотонный {@code s″} (при 22.06 МПа он меньше, чем при 22.064), поэтому 22.064 МПа не
     * используется: для турбинного пара такие энтропии не встречаются.
     */
    private static final double MAX_SATURATION_PRESSURE = 22.0;

    /** Число шагов бисекции: 100 сжимает интервал давления до машинной точности. */
    private static final int BISECTION_STEPS = 100;

    /** Допуск по энтропии, кДж/(кг·K): ниже него действительный процесс считается изоэнтропным. */
    private static final double ENTROPY_TOLERANCE = 1.0e-9;

    private final IF97 if97 = new IF97(IF97.UnitSystem.DEFAULT);

    // --- Состояние по двум параметрам ---

    /** Энтальпия по давлению и температуре, кДж/кг. */
    public double enthalpyPT(double pMPa, double tC) {
        try {
            return if97.specificEnthalpyPT(pMPa, tC + KELVIN);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, t=" + tC + " °C", e);
        }
    }

    /** Энтропия по давлению и температуре, кДж/(кг·K). */
    public double entropyPT(double pMPa, double tC) {
        try {
            return if97.specificEntropyPT(pMPa, tC + KELVIN);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, t=" + tC + " °C", e);
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

    /** Энтальпия по давлению и энтропии, кДж/кг — конец изоэнтропного расширения. */
    public double enthalpyPS(double pMPa, double entropy) {
        try {
            return if97.specificEnthalpyPS(pMPa, entropy);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, s=" + entropy + " кДж/(кг·K)", e);
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

    /** Температура по давлению и энтропии, °C. */
    public double temperaturePS(double pMPa, double entropy) {
        try {
            return if97.temperaturePS(pMPa, entropy) - KELVIN;
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, s=" + entropy + " кДж/(кг·K)", e);
        }
    }

    // --- Свойства в точке (p, h) ---

    /** Удельный объём по давлению и энтальпии, м³/кг. */
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
     * Степень сухости по давлению и энтальпии, доля: 1 в перегретом паре, 0…1 в двухфазной
     * области.
     *
     * <p><b>Ловушка библиотеки.</b> {@code IF97.vapourFractionPH} не ограничивает результат
     * единицей: в перегретом паре он продолжает формулу {@code (h − h′)/(h″ − h′)} и выдаёт
     * числа больше единицы. Поэтому границы области насыщения проверяются здесь: выше
     * {@code h″} возвращается 1, ниже {@code h′} — 0. Выше критического давления двухфазной
     * области нет вовсе, там тоже возвращается 1.
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

    /** Скорость звука по давлению и энтальпии, м/с. */
    public double speedOfSoundPH(double pMPa, double enthalpy) {
        try {
            return if97.speedOfSoundPH(pMPa, enthalpy);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа, h=" + enthalpy + " кДж/кг", e);
        }
    }

    /**
     * Локальный показатель изоэнтропы {@code k = a²/(p·v)}, безразмерный.
     *
     * <p>Стоит в уравнении пропускной способности: во влажном паре он отличается от табличных
     * 1.3 втрое, поэтому берётся из IF97, а не из справочника.
     */
    public double isentropicExponentPH(double pMPa, double enthalpy) {
        double volume = volumePH(pMPa, enthalpy);
        double sound = speedOfSoundPH(pMPa, enthalpy);
        return sound * sound / (pMPa * MPA * volume);
    }

    // --- Изоэнтропный теплоперепад и реальное расширение ---

    /** Энтальпия в конце изоэнтропного расширения до {@code pOut}, кДж/кг. */
    public double isentropicEnthalpy(double pIn, double hIn, double pOut) {
        return enthalpyPS(pOut, entropyPH(pIn, hIn));
    }

    /**
     * Располагаемый (изоэнтропный) теплоперепад, кДж/кг:
     * {@code H_s = h₁ − h(p₂, s₁)}.
     */
    public double isentropicDrop(double pIn, double hIn, double pOut) {
        return hIn - isentropicEnthalpy(pIn, hIn, pOut);
    }

    /** Располагаемый теплоперепад от состояния {@code (pIn, tIn)}, кДж/кг. */
    public double isentropicDropPT(double pIn, double tIn, double pOut) {
        return isentropicDrop(pIn, enthalpyPT(pIn, tIn), pOut);
    }

    /**
     * Энтальпия после реального расширения с внутренним относительным КПД {@code efficiency},
     * кДж/кг: {@code h₂ = h₁ − η·H_s}.
     */
    public double expand(double pIn, double hIn, double pOut, double efficiency) {
        return hIn - efficiency * isentropicDrop(pIn, hIn, pOut);
    }

    /** Энтальпия после реального расширения от состояния {@code (pIn, tIn)}, кДж/кг. */
    public double expandPT(double pIn, double tIn, double pOut, double efficiency) {
        return expand(pIn, enthalpyPT(pIn, tIn), pOut, efficiency);
    }

    // --- Насыщение ---

    /** Температура насыщения при заданном давлении, °C. */
    public double saturationTemperature(double pMPa) {
        try {
            return if97.saturationTemperatureP(pMPa) - KELVIN;
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа", e);
        }
    }

    /** Давление насыщения при заданной температуре, МПа. */
    public double saturationPressure(double tC) {
        try {
            return if97.saturationPressureT(tC + KELVIN);
        } catch (OutOfRangeException e) {
            throw outOfRange("t=" + tC + " °C", e);
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

    /** Энтропия насыщенной жидкости s′, кДж/(кг·K). */
    public double saturatedLiquidEntropy(double pMPa) {
        try {
            return if97.specificEntropySaturatedLiquidP(pMPa);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа", e);
        }
    }

    /** Энтропия сухого насыщенного пара s″, кДж/(кг·K). */
    public double saturatedVapourEntropy(double pMPa) {
        try {
            return if97.specificEntropySaturatedVapourP(pMPa);
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

    /** Удельный объём сухого насыщенного пара v″, м³/кг. */
    public double saturatedVapourVolume(double pMPa) {
        try {
            return if97.specificVolumeSaturatedVapourP(pMPa);
        } catch (OutOfRangeException e) {
            throw outOfRange("p=" + pMPa + " МПа", e);
        }
    }

    // --- Разбиение теплоперепада по линии насыщения ---

    /**
     * Давление, при котором изоэнтропа с энтропией {@code entropy} пересекает линию насыщенного
     * пара: {@code s″(p) = entropy}.
     *
     * <p>Нужно, чтобы найти точку конденсации: пока пар перегрет, изоэнтропа идёт по правой ветви
     * диаграммы, а в точке {@code p_н} упирается в границу насыщения и дальше идёт по двухфазной
     * области. Энтропия сухого насыщенного пара монотонно убывает с давлением, поэтому задача
     * решается бисекцией.
     *
     * <p>Рабочий диапазон — от {@code 0.000612} до {@code 22.0} МПа. У самой критической точки
     * IF97 даёт немонотонный {@code s″}, поэтому выше 22 МПа обратная задача не решается: для
     * турбинного пара такие энтропии не встречаются.
     *
     * @param entropy энтропия, кДж/(кг·K)
     * @return давление на линии насыщенного пара, МПа
     */
    public double saturationPressureByEntropy(double entropy) {
        double top = saturatedVapourEntropy(MIN_SATURATION_PRESSURE);
        double bottom = saturatedVapourEntropy(MAX_SATURATION_PRESSURE);
        if (entropy >= top) {
            throw new IllegalArgumentException("энтропия s = " + entropy + " кДж/(кг·K) выше s″ "
                    + "при " + MIN_SATURATION_PRESSURE + " МПа (" + top + "): изоэнтропа не "
                    + "пересекает линию насыщения");
        }
        if (entropy <= bottom) {
            throw new IllegalArgumentException("энтропия s = " + entropy + " кДж/(кг·K) ниже s″ "
                    + "при " + MAX_SATURATION_PRESSURE + " МПа (" + bottom + "): область у "
                    + "критической точки не поддерживается");
        }
        double low = MIN_SATURATION_PRESSURE;
        double high = MAX_SATURATION_PRESSURE;
        for (int step = 0; step < BISECTION_STEPS; step++) {
            double middle = 0.5 * (low + high);
            if (saturatedVapourEntropy(middle) > entropy) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return 0.5 * (low + high);
    }

    /**
     * Часть изоэнтропного теплоперепада, лежащая в перегретом паре, кДж/кг.
     *
     * <p>Это перепад от состояния {@code (pIn, hIn)} до точки, где изоэнтропа пересекает
     * насыщение: {@code H_перегр = h₁ − h″(p_н)}. Если пар на входе уже влажный — ноль, если весь
     * перепад укладывается до линии насыщения — весь перепад.
     */
    public double superheatedDrop(double pIn, double hIn, double pOut) {
        return splitDrop(pIn, hIn, pOut).superheated();
    }

    /**
     * Часть изоэнтропного теплоперепада, лежащая во влажном паре, кДж/кг:
     * {@code H_влажн = h″(p_н) − h(pOut, s₁)}.
     */
    public double wetDrop(double pIn, double hIn, double pOut) {
        return splitDrop(pIn, hIn, pOut).wet();
    }

    /**
     * Разбивает изоэнтропный теплоперепад линией насыщения — для предварительных расчётов.
     *
     * <p>{@code H_s = H_перегр + H_влажн}, где {@code H_перегр} — часть перепада в перегретом
     * паре, {@code H_влажн} — во влажном. Нужно для влажностной поправки к КПД отсека: по
     * методичке она относится только к части перепада, лежащей во влажном паре.
     *
     * <p>Состояние на входе задаётся {@code (pIn, hIn)}, конец изоэнтропы — давлением
     * {@code pOut}. Перепад не пересчитывается через КПД: это именно располагаемый перепад;
     * действительный вариант — {@link #splitDrop(double, double, double, double)}.
     *
     * @param pIn  давление на входе, МПа
     * @param hIn  энтальпия на входе, кДж/кг
     * @param pOut давление в конце изоэнтропного расширения, МПа
     * @return разбиение перепада на перегретую и влажную части, кДж/кг
     */
    public HeatDropSplit splitDrop(double pIn, double hIn, double pOut) {
        double heatDrop = isentropicDrop(pIn, hIn, pOut);
        if (!(heatDrop >= 0.0)) {
            throw new IllegalArgumentException("располагаемый теплоперепад отрицательный: "
                    + heatDrop + " кДж/кг (pIn = " + pIn + ", pOut = " + pOut + " МПа)");
        }
        if (vapourFractionPH(pIn, hIn) < 1.0) {
            return new HeatDropSplit(0.0, heatDrop);
        }
        double inletEntropy = entropyPH(pIn, hIn);
        if (inletEntropy >= saturatedVapourEntropy(MIN_SATURATION_PRESSURE)) {
            return new HeatDropSplit(heatDrop, 0.0);
        }
        double saturationPressure = saturationPressureByEntropy(inletEntropy);
        double saturationEnthalpy = saturatedVapourEnthalpy(saturationPressure);
        double superheated = Math.max(0.0, Math.min(heatDrop, hIn - saturationEnthalpy));
        return new HeatDropSplit(superheated, heatDrop - superheated);
    }

    /**
     * Часть действительного теплоперепада, лежащая в перегретом паре, кДж/кг. Конец процесса
     * задаётся парой {@code (pOut, hOut)}; см. {@link #splitDrop(double, double, double, double)}.
     */
    public double superheatedDrop(double pIn, double hIn, double pOut, double hOut) {
        return splitDrop(pIn, hIn, pOut, hOut).superheated();
    }

    /**
     * Часть действительного теплоперепада, лежащая во влажном паре, кДж/кг. Конец процесса
     * задаётся парой {@code (pOut, hOut)}; см. {@link #splitDrop(double, double, double, double)}.
     */
    public double wetDrop(double pIn, double hIn, double pOut, double hOut) {
        return splitDrop(pIn, hIn, pOut, hOut).wet();
    }

    /**
     * Разбивает действительный теплоперепад {@code h₁ − h₂} линией насыщения.
     *
     * <p>В отличие от изоэнтропного варианта, конец процесса задаётся парой {@code (pOut, hOut)}.
     * Действительная линия расширения на hs-диаграмме проводится как отрезок между
     * {@code (s₁, h₁)} и {@code (s₂, h₂)} — так её и строят по двум известным точкам. Пересечение
     * этого отрезка с линией насыщенного пара ищется бисекцией по давлению; при {@code s₂ = s₁}
     * метод совпадает с изоэнтропным.
     *
     * @param pIn  давление на входе, МПа
     * @param hIn  энтальпия на входе, кДж/кг
     * @param pOut давление в конце расширения, МПа
     * @param hOut энтальпия в конце действительного расширения, кДж/кг
     * @return разбиение перепада на перегретую и влажную части, кДж/кг
     */
    public HeatDropSplit splitDrop(double pIn, double hIn, double pOut, double hOut) {
        double heatDrop = hIn - hOut;
        if (!(heatDrop >= 0.0)) {
            throw new IllegalArgumentException("действительный теплоперепад отрицательный: "
                    + heatDrop + " кДж/кг (hIn = " + hIn + ", hOut = " + hOut + " кДж/кг)");
        }
        boolean inletWet = vapourFractionPH(pIn, hIn) < 1.0;
        boolean outletWet = vapourFractionPH(pOut, hOut) < 1.0;
        if (inletWet) {
            if (!outletWet) {
                throw new IllegalArgumentException("вход во влажном паре, а конец расширения — "
                        + "перегретый: расширение не может осушать пар");
            }
            return new HeatDropSplit(0.0, heatDrop);
        }
        if (!outletWet) {
            return new HeatDropSplit(heatDrop, 0.0);
        }
        double inletEntropy = entropyPH(pIn, hIn);
        double outletEntropy = entropyPH(pOut, hOut);
        if (outletEntropy < inletEntropy - ENTROPY_TOLERANCE) {
            throw new IllegalArgumentException("энтропия в конце расширения ниже начальной: s₁ = "
                    + inletEntropy + ", s₂ = " + outletEntropy + " кДж/(кг·K)");
        }
        if (outletEntropy - inletEntropy < ENTROPY_TOLERANCE) {
            return splitDrop(pIn, hIn, pOut);
        }
        double inletSaturationPressure = saturationPressureByEntropy(inletEntropy);
        double outletSaturationPressure = saturationPressureByEntropy(outletEntropy);
        double low = Math.min(inletSaturationPressure, outletSaturationPressure);
        double high = Math.max(inletSaturationPressure, outletSaturationPressure);
        double entropySpan = outletEntropy - inletEntropy;
        double enthalpySpan = hOut - hIn;
        for (int step = 0; step < BISECTION_STEPS; step++) {
            double middle = 0.5 * (low + high);
            double saturationEntropy = saturatedVapourEntropy(middle);
            double lineEnthalpy = hIn + (saturationEntropy - inletEntropy) / entropySpan
                    * enthalpySpan;
            if (lineEnthalpy - saturatedVapourEnthalpy(middle) > 0.0) {
                high = middle;
            } else {
                low = middle;
            }
        }
        double saturationEnthalpy = saturatedVapourEnthalpy(0.5 * (low + high));
        double superheated = Math.max(0.0, Math.min(heatDrop, hIn - saturationEnthalpy));
        return new HeatDropSplit(superheated, heatDrop - superheated);
    }

    /**
     * Разбиение теплоперепада линией насыщения.
     *
     * @param superheated часть перепада в перегретом паре, кДж/кг
     * @param wet         часть перепада во влажном паре, кДж/кг
     */
    public record HeatDropSplit(double superheated, double wet) {

        /** Проверка: обе части неотрицательны (заодно отсекает NaN). */
        public HeatDropSplit {
            if (!(superheated >= 0.0) || !(wet >= 0.0)) {
                throw new IllegalArgumentException("части теплоперепада не могут быть "
                        + "отрицательными: перегретая " + superheated + ", влажная " + wet);
            }
        }

        /** Полный разбитый перепад, кДж/кг. */
        public double total() {
            return superheated + wet;
        }

        /** Доля влажной части, 0…1. */
        public double wetFraction() {
            double total = total();
            return total > 0.0 ? wet / total : 0.0;
        }

        /** Весь перепад лежит в перегретом паре. */
        public boolean isFullySuperheated() {
            return wet <= 0.0;
        }

        /** Весь перепад лежит во влажном паре. */
        public boolean isFullyWet() {
            return superheated <= 0.0;
        }

        /** Строка для отчёта и логов. */
        public String describe() {
            return String.format(Locale.ROOT,
                    "H_перегр=%8.3f кДж/кг  H_влажн=%8.3f кДж/кг (доля влажного %.3f)",
                    superheated, wet, wetFraction());
        }
    }

    // --- Служебное ---

    /** Перегрев над линией насыщения, K (в двухфазной области — 0). */
    public double superheat(double pMPa, double enthalpy) {
        return temperaturePH(pMPa, enthalpy) - saturationTemperature(pMPa);
    }

    /** Влажный пар (есть жидкая фаза): {@code x < 1}. */
    public boolean isWet(double pMPa, double enthalpy) {
        return vapourFractionPH(pMPa, enthalpy) < 1.0;
    }

    /** Перегретый пар (или сухой): {@code x = 1}. */
    public boolean isSuperheated(double pMPa, double enthalpy) {
        return vapourFractionPH(pMPa, enthalpy) >= 1.0;
    }

    // --- Точка hs-диаграммы ---

    /**
     * Состояние на hs-диаграмме: давление, температура, энтальпия, энтропия, объём и степень
     * сухости.
     *
     * @param pressureMPa    давление, МПа
     * @param temperatureC   температура, °C
     * @param enthalpy       энтальпия, кДж/кг
     * @param entropy        энтропия, кДж/(кг·K)
     * @param volume         удельный объём, м³/кг
     * @param vapourFraction степень сухости, 0…1
     */
    public record Point(double pressureMPa, double temperatureC, double enthalpy, double entropy,
                        double volume, double vapourFraction) {

        /** Влажный пар (есть жидкая фаза). */
        public boolean isWet() {
            return vapourFraction < 1.0;
        }

        /** Перегретый пар (или сухой). */
        public boolean isSuperheated() {
            return vapourFraction >= 1.0;
        }

        /** Строка для отчёта и логов. */
        public String describe() {
            String dryness = vapourFraction >= 1.0
                    ? "  1.000(перегр)"
                    : String.format(Locale.ROOT, "%12.3f", vapourFraction);
            return String.format(Locale.ROOT,
                    "p=%7.3f МПа  t=%7.1f °C  h=%8.2f кДж/кг  s=%6.3f  v=%9.5f  x=%s",
                    pressureMPa, temperatureC, enthalpy, entropy, volume, dryness);
        }
    }

    /** Точка по давлению и температуре. */
    public Point pointPT(double pMPa, double tC) {
        double enthalpy = enthalpyPT(pMPa, tC);
        return new Point(pMPa, tC, enthalpy, entropyPT(pMPa, tC),
                volumePH(pMPa, enthalpy), vapourFractionPH(pMPa, enthalpy));
    }

    /** Точка по давлению и энтальпии. */
    public Point pointPH(double pMPa, double enthalpy) {
        return new Point(pMPa, temperaturePH(pMPa, enthalpy), enthalpy,
                entropyPH(pMPa, enthalpy), volumePH(pMPa, enthalpy),
                vapourFractionPH(pMPa, enthalpy));
    }

    private static IllegalArgumentException outOfRange(String point, Exception cause) {
        return new IllegalArgumentException("точка вне области IF97: " + point, cause);
    }
}
