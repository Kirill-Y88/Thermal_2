package org.kpush88.Thermal_2.turbine3;

/**
 * Эмпирические коэффициенты предварительного расчёта (разделы 2.1–2.4 методички).
 *
 * <p>Значения {@link #reference()} соответствуют опорному варианту методички: турбина 8 МВт,
 * 28 бар, 430 °C, 0.04 бар.
 */
public record DesignAssumptions(
        double economicPowerFactor,
        double electricalEfficiency,
        double mechanicalEfficiency,
        double generatorEfficiency,
        double valvePressureFactor,
        double exhaustVelocityMS,
        double exhaustLossCoefficient,
        double regulatingHeatDropKJkg,
        double regulatingSpeedRatio,
        double regulatingReaction,
        double regulatingNozzleAngleDeg,
        double nozzleFlowCoefficient,
        double admissionCoefficient,
        double limitPowerExtractionFactor,
        double limitPowerTaperFactor,
        double allowableBladeStressMPa,
        double bladeDensityKgM3,
        double limitPowerExitHeatDropKJkg) {

    /** Относительный электрический КПД по умолчанию (табл. 1 методички). */
    public static final double DEFAULT_ELECTRICAL_EFFICIENCY = 0.81;

    /** Потеря с выходной скоростью для турбин малой мощности, кДж/кг. */
    public static final double DEFAULT_EXIT_HEAT_DROP = 16.0;

    public DesignAssumptions {
        requireUnitInterval(economicPowerFactor, "доля экономической мощности");
        requireEfficiency(electricalEfficiency, "η_оэ");
        requireEfficiency(mechanicalEfficiency, "η_м");
        requireEfficiency(generatorEfficiency, "η_г");
        requireUnitInterval(valvePressureFactor, "доля давления после клапанов");
        requireNonNegative(exhaustVelocityMS, "скорость на выхлопе");
        requireNonNegative(exhaustLossCoefficient, "коэффициент потери с выходной скоростью");
        requirePositive(regulatingHeatDropKJkg, "теплоперепад регулирующей ступени");
        requireUnitInterval(regulatingSpeedRatio, "U/Ca регулирующей ступени");
        requireReaction(regulatingReaction);
        if (!(regulatingNozzleAngleDeg > 0.0 && regulatingNozzleAngleDeg < 90.0)
                || !Double.isFinite(regulatingNozzleAngleDeg)) {
            throw new IllegalArgumentException("угол α₁эф должен лежать в (0, 90)°: "
                    + regulatingNozzleAngleDeg);
        }
        requireUnitInterval(nozzleFlowCoefficient, "коэффициент расхода сопла μ₁");
        requireUnitInterval(admissionCoefficient, "коэффициент парциальности a");
        requirePositive(limitPowerExtractionFactor, "m");
        requirePositive(limitPowerTaperFactor, "k_разгр");
        requirePositive(allowableBladeStressMPa, "[σ]");
        requirePositive(bladeDensityKgM3, "плотность материала лопатки");
        requirePositive(limitPowerExitHeatDropKJkg, "Δh_вс");
    }

    /** Допущения опорного варианта методички. */
    public static DesignAssumptions reference() {
        return new DesignAssumptions(
                0.8,
                DEFAULT_ELECTRICAL_EFFICIENCY,
                0.99,
                0.97,
                0.97,
                100.0,
                0.1,
                175.0,
                0.26,
                0.06,
                11.0,
                0.97,
                0.33,
                1.0,
                2.2,
                420.0,
                8000.0,
                DEFAULT_EXIT_HEAT_DROP);
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

    private static void requireEfficiency(double value, String name) {
        if (!(value > 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " вне области (0, 1): " + value);
        }
    }

    private static void requireUnitInterval(double value, String name) {
        if (!(value > 0.0 && value <= 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен лежать в (0, 1]: " + value);
        }
    }

    private static void requireReaction(double value) {
        if (!(value >= 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("степень реактивности должна лежать в [0, 1): " + value);
        }
    }
}
