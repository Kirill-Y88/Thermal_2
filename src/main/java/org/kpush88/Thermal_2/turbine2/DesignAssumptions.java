package org.kpush88.Thermal_2.turbine2;

/**
 * Эмпирические коэффициенты и проектные допущения предварительного расчёта.
 *
 * <p>Запись отделяет <b>требования варианта</b> ({@link DesignInput}) от чисел, которые в
 * методичке выбираются по таблицам и рекомендациям: КПД, коэффициенты расхода, степень
 * реактивности, запас по прочности. Значения {@link #reference()} соответствуют опорному
 * варианту {@code university/Курс турб.docx} (турбина 8 МВт, 28 бар, 430 °C, 0.04 бар).
 *
 * <p>Все величины безразмерные, кроме явно подписанных: теплоперепады — кДж/кг, скорость —
 * м/с, напряжение — МПа, плотность — кг/м³, угол — градусы.
 *
 * @param economicPowerFactor        доля номинальной мощности {@code N_эк/N_э^н} (0.8)
 * @param electricalEfficiency       относительный электрический КПД {@code η_оэ} (0.81)
 * @param mechanicalEfficiency       механический КПД {@code η_м} (0.99)
 * @param generatorEfficiency        КПД генератора {@code η_г} (0.97)
 * @param valvePressureFactor        доля давления после стопорных клапанов (0.97)
 * @param exhaustVelocityMS          скорость на выхлопе {@code c_вп}, м/с (100)
 * @param exhaustLossCoefficient     коэффициент потери с выходной скоростью (0.1)
 * @param regulatingHeatDropKJkg     теплоперепад регулирующей ступени {@code h₀^рс}, кДж/кг (175)
 * @param regulatingSpeedRatio       {@code (U/Ca)_opt} регулирующей ступени (0.26)
 * @param regulatingReaction         степень реактивности {@code ρ_т} (0.06)
 * @param regulatingNozzleAngleDeg   эффективный угол сопловой решётки {@code α₁эф}, ° (11)
 * @param nozzleFlowCoefficient      коэффициент расхода сопловой решётки {@code μ₁} (0.97)
 * @param admissionCoefficient       коэффициент парциальности {@code a} (0.33)
 * @param limitPowerExtractionFactor коэффициент {@code m} в формуле предельной мощности (1.0)
 * @param limitPowerTaperFactor      коэффициент {@code k_разгр} (2.2)
 * @param allowableBladeStressMPa    допускаемое напряжение {@code [σ]}, МПа (420)
 * @param bladeDensityKgM3           плотность материала лопатки, кг/м³ (8000)
 * @param limitPowerExitHeatDropKJkg потеря с выходной скоростью {@code Δh_вс}, кДж/кг (16)
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

    public DesignAssumptions {
        requireUnitInterval(economicPowerFactor, "доля экономической мощности");
        requireEfficiency(electricalEfficiency, "относительный электрический КПД η_оэ");
        requireEfficiency(mechanicalEfficiency, "механический КПД η_м");
        requireEfficiency(generatorEfficiency, "КПД генератора η_г");
        requireUnitInterval(valvePressureFactor, "доля давления после клапанов");
        requireNonNegative(exhaustVelocityMS, "скорость на выхлопе c_вп");
        requireNonNegative(exhaustLossCoefficient, "коэффициент потери с выходной скоростью");
        requirePositive(regulatingHeatDropKJkg, "теплоперепад регулирующей ступени h₀^рс");
        requireUnitInterval(regulatingSpeedRatio, "отношение U/Ca регулирующей ступени");
        requireReaction(regulatingReaction);
        if (!(regulatingNozzleAngleDeg > 0.0 && regulatingNozzleAngleDeg < 90.0)
                || !Double.isFinite(regulatingNozzleAngleDeg)) {
            throw new IllegalArgumentException("эффективный угол сопловой решётки α₁эф должен "
                    + "лежать в (0, 90)°: " + regulatingNozzleAngleDeg);
        }
        requireUnitInterval(nozzleFlowCoefficient, "коэффициент расхода сопловой решётки μ₁");
        requireUnitInterval(admissionCoefficient, "коэффициент парциальности a");
        requirePositive(limitPowerExtractionFactor, "коэффициент m предельной мощности");
        requirePositive(limitPowerTaperFactor, "коэффициент k_разгр");
        requirePositive(allowableBladeStressMPa, "допускаемое напряжение [σ]");
        requirePositive(bladeDensityKgM3, "плотность материала лопатки");
        requirePositive(limitPowerExitHeatDropKJkg, "потеря с выходной скоростью Δh_вс");
    }

    /**
     * Допущения опорного варианта методички.
     *
     * <p>Это не «универсальные» коэффициенты: они собраны для турбины 8 МВт с параметрами
     * 28 бар / 430 °C / 0.04 бар. Для другой машины их нужно пересматривать, поэтому значения
     * вынесены в отдельную запись, а не зашиты в расчёт.
     *
     * @return допущения опорного варианта
     */
    public static DesignAssumptions reference() {
        return new DesignAssumptions(
                0.8,
                CourseProjectEfficiency.DEFAULT_ELECTRICAL_EFFICIENCY,
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
                CourseProjectEfficiency.DEFAULT_EXIT_HEAT_DROP);
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
            throw new IllegalArgumentException(name + " должна лежать в (0, 1]: " + value);
        }
    }

    private static void requireReaction(double value) {
        if (!(value >= 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("степень реактивности ρ_т должна лежать в "
                    + "[0, 1): " + value);
        }
    }
}
