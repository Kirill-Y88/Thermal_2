package org.kpush88.Thermal_2.v1.thermo;

import java.util.Locale;

/**
 * Термодинамическое состояние точки схемы (для отчёта и контроля расчёта).
 *
 * @param name            имя точки («Свежий пар», «Отбор ПНД» и т.п.)
 * @param pressure        давление, МПа
 * @param temperature     температура, °C
 * @param enthalpy        энтальпия, кДж/кг
 * @param entropy         энтропия, кДж/(кг·K)
 * @param vapourFraction  степень сухости (для влажного пара; 1.0 — перегретый/сухой)
 */
public record SteamState(
        String name,
        double pressure,
        double temperature,
        double enthalpy,
        double entropy,
        double vapourFraction) {

    /** Точка, заданная давлением и температурой (перегретый пар или жидкость). */
    public static SteamState ofPT(String name, WaterSteam steam, double pMPa, double tC) {
        double enthalpy = steam.enthalpyPT(pMPa, tC);
        return new SteamState(name, pMPa, tC,
                enthalpy,
                steam.entropyPT(pMPa, tC),
                steam.vapourFractionPH(pMPa, enthalpy));
    }

    /** Точка, заданная давлением и энтальпией (состояние после расширения/насыщения). */
    public static SteamState ofPH(String name, WaterSteam steam, double pMPa, double enthalpy) {
        return new SteamState(name, pMPa,
                steam.temperaturePH(pMPa, enthalpy),
                enthalpy,
                steam.entropyPH(pMPa, enthalpy),
                steam.vapourFractionPH(pMPa, enthalpy));
    }

    /** Строка таблицы отчёта. */
    public String format() {
        String dryness = Double.isNaN(vapourFraction)
                ? "  1.000(перегр)"
                : String.format(Locale.ROOT, "%12.3f", vapourFraction);
        return String.format(Locale.ROOT,
                "  %-24s p=%7.3f МПа  t=%7.1f °C  h=%8.2f кДж/кг  s=%6.3f  x=%s",
                name, pressure, temperature, enthalpy, entropy, dryness);
    }
}
