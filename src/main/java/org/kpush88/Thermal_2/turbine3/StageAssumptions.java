package org.kpush88.Thermal_2.turbine3;

/**
 * Допущения детального расчёта проточной части: геометрия отсеков и решёток
 * (разделы 2.5, 3.1–3.3 методички).
 *
 * <p>Здесь только величины, которые в методичке выбираются по прототипу и таблицам; вычисленные
 * диаметры и теплоперепады сюда не входят. Значения {@link #reference()} соответствуют опорному
 * варианту (8 МВт, 28 бар, 430 °C, 0.04 бар, 3000 об/мин).
 */
public record StageAssumptions(
        SectionAssumptions chvd,
        SectionAssumptions chsd,
        SectionAssumptions chnd,
        GeometryAssumptions geometry) {

    public StageAssumptions {
        if (chvd == null || chsd == null || chnd == null || geometry == null) {
            throw new IllegalArgumentException("допущения детального расчёта заданы не полностью");
        }
    }

    /** Допущения опорного варианта методички. */
    public static StageAssumptions reference() {
        return new StageAssumptions(
                new SectionAssumptions(6, 0.879, 0.863, 0.01575, 0.863,
                        0.39, 0.42, 0.05, 0.1344, 0.7, 0.7, 0.003, 4.8e-4, 0.0, 0.909),
                new SectionAssumptions(6, 0.915, 0.863, 0.0518, 0.863,
                        0.42, 0.43, 0.151, 0.268, 0.7, 1.0, 0.004, 3.2e-4, 0.0, 0.998),
                new SectionAssumptions(4, 1.008, 0.863, 0.144, 1.05,
                        0.43, 0.46, 0.298, 0.44, 1.0, 1.0, 0.005, 2.8e-4, 4.0, 1.4),
                GeometryAssumptions.reference());
    }

    /**
     * Допущения одного отсека: геометрия первой и последней ступеней и закон изменения
     * характеристического числа. Промежуточные ступени распределяются линейно между ними.
     */
    public record SectionAssumptions(
            int stageCountHint,
            double firstMeanDiameterM,
            double firstRootDiameterM,
            double firstBladeHeightM,
            double lastRootDiameterM,
            double firstSpeedRatio,
            double lastSpeedRatio,
            double firstReaction,
            double lastReaction,
            double firstAdmission,
            double lastAdmission,
            double axialClearanceM,
            double recoveryFactor,
            double lastDiameterToHeightRatio,
            double lastMeanDiameterHintM) {

        public SectionAssumptions {
            if (stageCountHint < 0) {
                throw new IllegalArgumentException("число ступеней не может быть отрицательным: "
                        + stageCountHint);
            }
            requirePositive(firstMeanDiameterM, "средний диаметр первой ступени");
            requirePositive(firstRootDiameterM, "корневой диаметр первой ступени");
            requirePositive(firstBladeHeightM, "высота лопатки первой ступени");
            requirePositive(lastRootDiameterM, "корневой диаметр последней ступени");
            requireRatio(firstSpeedRatio, "U/Ca первой ступени");
            requireRatio(lastSpeedRatio, "U/Ca последней ступени");
            requireReaction(firstReaction, "степень реактивности первой ступени");
            requireReaction(lastReaction, "степень реактивности последней ступени");
            requireAdmission(firstAdmission, "парциальность первой ступени");
            requireAdmission(lastAdmission, "парциальность последней ступени");
            requireNonNegative(axialClearanceM, "перекрыша Δ");
            requirePositive(recoveryFactor, "коэффициент возврата тепла K_t");
            requireNonNegative(lastDiameterToHeightRatio, "отношение d_срz/l_2z");
            requirePositive(lastMeanDiameterHintM, "начальный диаметр последней ступени");
            if (!(firstMeanDiameterM > firstRootDiameterM)) {
                throw new IllegalArgumentException("средний диаметр первой ступени должен быть "
                        + "больше корневого: " + firstMeanDiameterM + " ≤ " + firstRootDiameterM);
            }
        }
    }

    /**
     * Геометрия решёток и уплотнений, выбираемая по прототипу.
     */
    public record GeometryAssumptions(
            double nozzleAngleDeg,
            double nozzleChordM,
            double nozzlePitchRatio,
            double rotorChordM,
            double rotorPitchRatio,
            double secondRotorChordM,
            double secondRotorPitchRatio,
            double turningChordM,
            double turningPitchRatio,
            double nozzleSpeedCoefficient,
            double rotorSpeedCoefficient,
            double nozzleFlowCoefficient,
            double rotorFlowCoefficient,
            double sealAxialClearanceM,
            double sealRadialClearanceM,
            int sealRidges,
            double diaphragmLeakAreaM2,
            double diaphragmLeakCoefficient,
            int diaphragmRidges,
            double exitVelocityRecovery,
            double frictionCoefficient,
            double bucketLossCoefficient,
            double segmentLossCoefficient) {

        public GeometryAssumptions {
            if (!(nozzleAngleDeg > 0.0 && nozzleAngleDeg < 90.0)
                    || !Double.isFinite(nozzleAngleDeg)) {
                throw new IllegalArgumentException("угол сопловой решётки должен лежать в (0, 90)°: "
                        + nozzleAngleDeg);
            }
            requirePositive(nozzleChordM, "хорда сопловой решётки");
            requirePositive(nozzlePitchRatio, "относительный шаг сопловой решётки");
            requirePositive(rotorChordM, "хорда рабочей решётки");
            requirePositive(rotorPitchRatio, "относительный шаг рабочей решётки");
            requirePositive(secondRotorChordM, "хорда второго венца");
            requirePositive(secondRotorPitchRatio, "относительный шаг второго венца");
            requirePositive(turningChordM, "хорда поворотной решётки");
            requirePositive(turningPitchRatio, "относительный шаг поворотной решётки");
            requireUnitInterval(nozzleSpeedCoefficient, "коэффициент скорости сопла φ");
            requireUnitInterval(rotorSpeedCoefficient, "коэффициент скорости рабочей решётки ψ");
            requireUnitInterval(nozzleFlowCoefficient, "коэффициент расхода сопла μ₁");
            requireUnitInterval(rotorFlowCoefficient, "коэффициент расхода μ₂");
            requireNonNegative(sealAxialClearanceM, "осевой зазор уплотнения");
            requireNonNegative(sealRadialClearanceM, "радиальный зазор уплотнения");
            if (sealRidges < 1) {
                throw new IllegalArgumentException("число гребней бандажного уплотнения должно "
                        + "быть не меньше 1: " + sealRidges);
            }
            requirePositive(diaphragmLeakAreaM2, "площадь зазора диафрагменного уплотнения");
            requireUnitInterval(diaphragmLeakCoefficient, "коэффициент расхода уплотнения");
            if (diaphragmRidges < 1) {
                throw new IllegalArgumentException("число гребней диафрагменного уплотнения должно "
                        + "быть не меньше 1: " + diaphragmRidges);
            }
            requireNonNegative(exitVelocityRecovery, "коэффициент использования выходной скорости");
            requirePositive(frictionCoefficient, "коэффициент трения K_тр");
            requireNonNegative(bucketLossCoefficient, "коэффициент k_В");
            requireNonNegative(segmentLossCoefficient, "коэффициент k_сегм");
        }

        static GeometryAssumptions reference() {
            return new GeometryAssumptions(
                    11.0,
                    0.0606,
                    0.8,
                    0.0259,
                    0.65,
                    0.0261,
                    0.46,
                    0.0259,
                    0.65,
                    0.97,
                    0.957,
                    0.97,
                    0.94,
                    0.0005,
                    0.0006,
                    2,
                    0.000565,
                    0.7,
                    7,
                    1.0,
                    1.0e-3,
                    0.065,
                    0.25);
        }
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

    private static void requireRatio(double value, String name) {
        if (!(value > 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен лежать в (0, 1): " + value);
        }
    }

    private static void requireUnitInterval(double value, String name) {
        if (!(value > 0.0 && value <= 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен лежать в (0, 1]: " + value);
        }
    }

    private static void requireReaction(double value, String name) {
        if (!(value >= 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должна лежать в [0, 1): " + value);
        }
    }

    private static void requireAdmission(double value, String name) {
        if (!(value > 0.0 && value <= 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должна лежать в (0, 1]: " + value);
        }
    }
}
