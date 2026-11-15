package org.kpush88.Thermal_2.turbine3;

/**
 * Результат детального расчёта одной лопаточной решётки (разделы 3.1–3.3 методички).
 *
 * <p>Единицы: скорость — м/с, угол — градусы, давление — МПа, объём — м³/кг, площадь — м²,
 * длина — м, теплоперепад и потери — кДж/кг.
 */
public record RowDetail(
        RowKind rowKind,
        double heatDropKJkg,
        double theoreticalVelocityMS,
        double actualVelocityMS,
        double inletVelocityMS,
        double inletAngleDeg,
        double exitAngleDeg,
        double mach,
        double pressureMPa,
        double volumeM3kg,
        double areaM2,
        double heightM,
        double chordM,
        double pitchM,
        int bladeCount,
        double lossKJkg,
        double flowCoefficient,
        double speedCoefficient) {

    public RowDetail {
        if (rowKind == null) {
            throw new IllegalArgumentException("тип решётки не задан");
        }
        requireNonNegative(heatDropKJkg, "теплоперепад решётки");
        requirePositive(theoreticalVelocityMS, "теоретическая скорость");
        requirePositive(actualVelocityMS, "действительная скорость");
        requireNonNegative(inletVelocityMS, "скорость на входе");
        requireAngle(inletAngleDeg, "угол входа");
        requireAngle(exitAngleDeg, "угол выхода");
        requireNonNegative(mach, "число Маха");
        requirePositive(pressureMPa, "давление");
        requirePositive(volumeM3kg, "удельный объём");
        requirePositive(areaM2, "площадь");
        requirePositive(heightM, "высота");
        requirePositive(chordM, "хорда");
        requirePositive(pitchM, "шаг");
        if (bladeCount < 1) {
            throw new IllegalArgumentException("число лопаток должно быть положительным: " + bladeCount);
        }
        requireNonNegative(lossKJkg, "потеря");
        requireUnitInterval(flowCoefficient, "коэффициент расхода");
        requireUnitInterval(speedCoefficient, "коэффициент скорости");
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен быть положительным: " + value);
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!(value >= 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " не может быть отрицательным: " + value);
        }
    }

    private static void requireUnitInterval(double value, String name) {
        if (!(value > 0.0 && value <= 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен лежать в (0, 1]: " + value);
        }
    }

    private static void requireAngle(double value, String name) {
        if (!(value >= 0.0 && value <= 90.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен лежать в [0, 90]°: " + value);
        }
    }
}
