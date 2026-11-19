package org.kpush88.Thermal_2.turbine4;

import java.util.Locale;

/**
 * Точка на h—s диаграмме: неизменяемое хранилище параметров состояния.
 *
 * <p>Класс ничего не вычисляет и не обращается к IF97. Он только хранит согласованный набор
 * величин, полученных от {@code HsDiagram} или другого источника, и даёт удобные признаки
 * состояния для отчётов и алгоритмов.
 *
 * <p>Единицы: давление — МПа, температура — °C, энтальпия — кДж/кг, энтропия — кДж/(кг·K),
 * удельный объём — м³/кг. Степень сухости {@code x} лежит в диапазоне {@code [0, 1]}, где
 * {@code x = 1} соответствует сухому насыщенному или перегретому пару.
 *
 * @param pressureMPa    давление, МПа
 * @param temperatureC   температура, °C
 * @param enthalpyKJkg   энтальпия, кДж/кг
 * @param entropyKJkgK   энтропия, кДж/(кг·K)
 * @param volumeM3kg     удельный объём, м³/кг
 * @param vapourFraction степень сухости {@code x}, 0…1
 */
public record Point(
        double pressureMPa,
        double temperatureC,
        double enthalpyKJkg,
        double entropyKJkgK,
        double volumeM3kg,
        double vapourFraction) {

    public Point {
        requirePositive(pressureMPa, "давление");
        requireFinite(temperatureC, "температура");
        requirePositive(enthalpyKJkg, "энтальпия");
        requireFinite(entropyKJkgK, "энтропия");
        requirePositive(volumeM3kg, "удельный объём");
        if (!(vapourFraction >= 0.0 && vapourFraction <= 1.0)
                || !Double.isFinite(vapourFraction)) {
            throw new IllegalArgumentException("степень сухости вне [0, 1]: " + vapourFraction);
        }
    }

    /**
     * Влажность {@code y = 1 − x}: доля жидкой фазы.
     *
     * @return влажность от 0 до 1
     */
    public double wetness() {
        return 1.0 - vapourFraction;
    }

    /** Влажный пар: есть жидкая фаза, {@code x < 1}. */
    public boolean isWet() {
        return vapourFraction < 1.0;
    }

    /** Перегретый или сухой насыщенный пар: {@code x = 1}. */
    public boolean isSuperheated() {
        return vapourFraction >= 1.0;
    }

    /**
     * Строка параметров точки для протокола.
     *
     * @return однострочное описание с единицами
     */
    public String describe() {
        return String.format(Locale.ROOT,
                "p = %.3f МПа, t = %.1f °C, h = %.2f кДж/кг, "
                        + "s = %.4f кДж/(кг·K), v = %.6f м³/кг, x = %.4f",
                pressureMPa, temperatureC, enthalpyKJkg, entropyKJkgK, volumeM3kg,
                vapourFraction);
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен быть положительным: " + value);
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должна быть конечной: " + value);
        }
    }
}
