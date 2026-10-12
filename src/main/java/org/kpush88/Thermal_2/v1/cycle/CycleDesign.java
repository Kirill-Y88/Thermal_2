package org.kpush88.Thermal_2.v1.cycle;

/**
 * Исходные данные тепловой схемы (граничные условия расчёта).
 *
 * <p>Все давления заданы как параметры схемы (в прототипе отборы выбираются
 * «по давлению», а не ищутся из условий равномерного подогрева).
 *
 * @param liveSteamPressure            давление свежего пара p0, МПа
 * @param liveSteamTemperature         температура свежего пара t0, °C
 * @param hpExtractionPressure         давление отбора на ПВД p1, МПа
 * @param deaeratorPressure            давление в деаэраторе p2, МПа
 * @param lpExtractionPressure         давление отбора на ПНД p3, МПа
 * @param condenserPressure            давление в конденсаторе pк, МПа
 * @param turbineEfficiency            внутренний относительный КПД отсека турбины ηoi
 * @param heaterUndercooling           недогрев воды в поверхностных подогревателях δt, °C
 * @param pumpEfficiency               КПД питательного насоса ηн
 * @param electromechanicalEfficiency  электромеханический КПД турбогенератора ηэм
 */
public record CycleDesign(
        double liveSteamPressure,
        double liveSteamTemperature,
        double hpExtractionPressure,
        double deaeratorPressure,
        double lpExtractionPressure,
        double condenserPressure,
        double turbineEfficiency,
        double heaterUndercooling,
        double pumpEfficiency,
        double electromechanicalEfficiency) {

    public CycleDesign {
        requirePositive(liveSteamPressure, "p0 (свежий пар)");
        requirePositive(liveSteamTemperature, "t0 (свежий пар)");
        requirePositive(hpExtractionPressure, "p1 (отбор ПВД)");
        requirePositive(deaeratorPressure, "p2 (деаэратор)");
        requirePositive(lpExtractionPressure, "p3 (отбор ПНД)");
        requirePositive(condenserPressure, "pк (конденсатор)");
        requireUnitInterval(turbineEfficiency, "ηoi");
        requireUnitInterval(pumpEfficiency, "ηн");
        requireUnitInterval(electromechanicalEfficiency, "ηэм");
        if (!(hpExtractionPressure < liveSteamPressure
                && deaeratorPressure < hpExtractionPressure
                && lpExtractionPressure < deaeratorPressure
                && condenserPressure < lpExtractionPressure)) {
            throw new IllegalArgumentException(
                    "Давления отборов должны убывать: pк < p3 < p2 < p1 < p0");
        }
        if (heaterUndercooling < 0.0) {
            throw new IllegalArgumentException("Недогрев δt не может быть отрицательным");
        }
    }

    /** Типовая схема: 24 МПа / 540 °C, три отбора, ηoi = 0.85, недогрев 5 °C. */
    public static CycleDesign reference() {
        return new CycleDesign(24.0, 540.0, 3.5, 0.6, 0.12, 0.004, 0.85, 5.0, 0.8, 0.985);
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0)) {
            throw new IllegalArgumentException("Параметр «" + name + "» должен быть > 0, задано " + value);
        }
    }

    private static void requireUnitInterval(double value, String name) {
        if (!(value > 0.0 && value < 1.0)) {
            throw new IllegalArgumentException("КПД «" + name + "» должен лежать в (0, 1), задано " + value);
        }
    }
}
