package org.kpush88.Thermal_2.turbine4;

import com.hummeling.if97.IF97;
import com.hummeling.if97.OutOfRangeException;

/**
 * Параметры воды и водяного пара по IAPWS-IF97 в единицах теплового расчёта турбины.
 *
 * <p>Давление — МПа, температура — °C, энтальпия — кДж/кг, энтропия — кДж/(кг·K),
 * удельный объём — м³/кг. Библиотека работает в кельвинах, перевод выполняется здесь и только
 * здесь. Класс неизменяемый и потокобезопасный: IF97 не хранит состояния между вызовами.
 */
public final class HsDiagram {

    private static final double KELVIN = 273.15;
    private static final double MPA = 1.0e6;
    private static final double MIN_SATURATION_PRESSURE = 0.000612;
    private static final double MAX_SATURATION_PRESSURE = 22.0;
    private static final int BISECTION_STEPS = 100;
    private static final double ENTROPY_TOLERANCE = 1.0e-9;

    private final IF97 if97 = new IF97(IF97.UnitSystem.DEFAULT);

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
     * Степень сухости по давлению и энтальпии: 1 в перегретом паре, 0…1 в двухфазной области.
     * Выше критического давления двухфазной области нет, там возвращается 1.
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

    /** Локальный показатель изоэнтропы {@code k = a²/(p·v)}. */
    public double isentropicExponentPH(double pMPa, double enthalpy) {
        double volume = volumePH(pMPa, enthalpy);
        double sound = speedOfSoundPH(pMPa, enthalpy);
        return sound * sound / (pMPa * MPA * volume);
    }

    /** Энтальпия в конце изоэнтропного расширения до pOut, кДж/кг. */
    public double isentropicEnthalpy(double pIn, double hIn, double pOut) {
        return enthalpyPS(pOut, entropyPH(pIn, hIn));
    }

    /** Располагаемый (изоэнтропный) теплоперепад, кДж/кг. */
    public double isentropicDrop(double pIn, double hIn, double pOut) {
        return hIn - isentropicEnthalpy(pIn, hIn, pOut);
    }

    /** Располагаемый теплоперепад от состояния (pIn, tIn), кДж/кг. */
    public double isentropicDropPT(double pIn, double tIn, double pOut) {
        return isentropicDrop(pIn, enthalpyPT(pIn, tIn), pOut);
    }

    /** Давление, при котором изоэнтропный перепад от (pIn, hIn) равен drop, МПа. */
    public double pressureForIsentropicDrop(double pIn, double hIn, double drop) {
        if (!(pIn > MIN_SATURATION_PRESSURE) || !Double.isFinite(pIn)) {
            throw new IllegalArgumentException("давление на входе должно быть выше "
                    + MIN_SATURATION_PRESSURE + " МПа: " + pIn);
        }
        if (!(drop > 0.0) || !Double.isFinite(drop)) {
            throw new IllegalArgumentException("изоэнтропный теплоперепад должен быть "
                    + "положительным: " + drop + " кДж/кг");
        }
        double total = isentropicDrop(pIn, hIn, MIN_SATURATION_PRESSURE);
        if (drop >= total) {
            throw new IllegalArgumentException("теплоперепад " + drop + " кДж/кг не укладывается "
                    + "в изоэнтропу до " + MIN_SATURATION_PRESSURE + " МПа: " + total + " кДж/кг");
        }
        double low = MIN_SATURATION_PRESSURE;
        double high = pIn;
        for (int step = 0; step < BISECTION_STEPS; step++) {
            double middle = 0.5 * (low + high);
            if (isentropicDrop(pIn, hIn, middle) > drop) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return 0.5 * (low + high);
    }

    /** Энтальпия после реального расширения с внутренним относительным КПД, кДж/кг. */
    public double expand(double pIn, double hIn, double pOut, double efficiency) {
        return hIn - efficiency * isentropicDrop(pIn, hIn, pOut);
    }

    /** Энтальпия после реального расширения от состояния (pIn, tIn), кДж/кг. */
    public double expandPT(double pIn, double tIn, double pOut, double efficiency) {
        return expand(pIn, enthalpyPT(pIn, tIn), pOut, efficiency);
    }

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

    /** Давление, при котором изоэнтропа с энтропией entropy пересекает линию насыщенного пара. */
    public double saturationPressureByEntropy(double entropy) {
        double top = saturatedVapourEntropy(MIN_SATURATION_PRESSURE);
        double bottom = saturatedVapourEntropy(MAX_SATURATION_PRESSURE);
        if (entropy >= top) {
            throw new IllegalArgumentException("энтропия выше s″ при " + MIN_SATURATION_PRESSURE
                    + " МПа: " + entropy + " ≥ " + top);
        }
        if (entropy <= bottom) {
            throw new IllegalArgumentException("энтропия ниже s″ при " + MAX_SATURATION_PRESSURE
                    + " МПа: " + entropy + " ≤ " + bottom);
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

    /** Часть изоэнтропного перепада в перегретом паре, кДж/кг. */
    public double superheatedDrop(double pIn, double hIn, double pOut) {
        return splitDrop(pIn, hIn, pOut).superheated();
    }

    /** Часть изоэнтропного перепада во влажном паре, кДж/кг. */
    public double wetDrop(double pIn, double hIn, double pOut) {
        return splitDrop(pIn, hIn, pOut).wet();
    }

    /** Разбивает изоэнтропный теплоперепад линией насыщения. */
    public HeatDropSplit splitDrop(double pIn, double hIn, double pOut) {
        double heatDrop = isentropicDrop(pIn, hIn, pOut);
        if (!(heatDrop >= 0.0)) {
            throw new IllegalArgumentException("располагаемый теплоперепад отрицательный: "
                    + heatDrop + " кДж/кг");
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

    /** Часть действительного перепада в перегретом паре, кДж/кг. */
    public double superheatedDrop(double pIn, double hIn, double pOut, double hOut) {
        return splitDrop(pIn, hIn, pOut, hOut).superheated();
    }

    /** Часть действительного перепада во влажном паре, кДж/кг. */
    public double wetDrop(double pIn, double hIn, double pOut, double hOut) {
        return splitDrop(pIn, hIn, pOut, hOut).wet();
    }

    /** Разбивает действительный теплоперепад линией насыщения. */
    public HeatDropSplit splitDrop(double pIn, double hIn, double pOut, double hOut) {
        double heatDrop = hIn - hOut;
        if (!(heatDrop >= 0.0)) {
            throw new IllegalArgumentException("действительный теплоперепад отрицательный: "
                    + heatDrop + " кДж/кг");
        }
        boolean inletWet = vapourFractionPH(pIn, hIn) < 1.0;
        boolean outletWet = vapourFractionPH(pOut, hOut) < 1.0;
        if (inletWet) {
            if (!outletWet) {
                throw new IllegalArgumentException("вход во влажном паре, а выход — перегретый");
            }
            return new HeatDropSplit(0.0, heatDrop);
        }
        if (!outletWet) {
            return new HeatDropSplit(heatDrop, 0.0);
        }
        double inletEntropy = entropyPH(pIn, hIn);
        double outletEntropy = entropyPH(pOut, hOut);
        if (outletEntropy < inletEntropy - ENTROPY_TOLERANCE) {
            throw new IllegalArgumentException("энтропия в конце расширения ниже начальной");
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

    /** Разбиение теплоперепада линией насыщения. */
    public record HeatDropSplit(double superheated, double wet) {

        public HeatDropSplit {
            if (!(superheated >= 0.0) || !(wet >= 0.0)) {
                throw new IllegalArgumentException("части теплоперепада не могут быть "
                        + "отрицательными: " + superheated + ", " + wet);
            }
        }

        public double total() {
            return superheated + wet;
        }

        public double wetFraction() {
            double total = total();
            return total > 0.0 ? wet / total : 0.0;
        }

        public boolean isFullySuperheated() {
            return wet <= 0.0;
        }

        public boolean isFullyWet() {
            return superheated <= 0.0;
        }
    }

    /** Перегрев над линией насыщения, K (в двухфазной области — 0). */
    public double superheat(double pMPa, double enthalpy) {
        return temperaturePH(pMPa, enthalpy) - saturationTemperature(pMPa);
    }

    /** Влажный пар (есть жидкая фаза). */
    public boolean isWet(double pMPa, double enthalpy) {
        return vapourFractionPH(pMPa, enthalpy) < 1.0;
    }

    /** Перегретый пар (или сухой насыщенный). */
    public boolean isSuperheated(double pMPa, double enthalpy) {
        return vapourFractionPH(pMPa, enthalpy) >= 1.0;
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
