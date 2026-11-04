package org.kpush88.Thermal_2.turbine2;

/**
 * Допущения детального расчёта проточной части: разделы 2.5, 3.1–3.4 и 4.1–4.3 методички
 * {@code university/Курс турб.docx}.
 *
 * <p>Запись отделяет <b>эмпирические данные</b> от результатов расчёта. Здесь нет ни одного
 * вычисленного теплоперепада или диаметра: только то, что в методичке выбирается по прототипу,
 * таблицам и рекомендациям. Значения {@link #reference()} соответствуют опорному варианту
 * (турбина 8 МВт, 28 бар, 430 °C, 0.04 бар, 3000 об/мин).
 *
 * <p><b>Почему допущения разбиты на три группы.</b> Диаметры и числа ступеней относятся к
 * газодинамике отсека, хорды и коэффициенты — к решёткам, а плотность и допускаемые напряжения —
 * к прочности. Если держать всё в одной записи из сорока полей, ей невозможно пользоваться.
 *
 * <p>Единицы: длина — м, площадь — м², напряжение — МПа, плотность — кг/м³, угол — градусы.
 *
 * @param chvd     допущения ЧВД
 * @param chsd     допущения ЧСД
 * @param chnd     допущения ЧНД
 * @param geometry геометрия решёток и уплотнений
 * @param strength прочностные данные
 */
public record StageAssumptions(
        SectionAssumptions chvd,
        SectionAssumptions chsd,
        SectionAssumptions chnd,
        GeometryAssumptions geometry,
        StrengthAssumptions strength) {

    public StageAssumptions {
        if (chvd == null || chsd == null || chnd == null || geometry == null
                || strength == null) {
            throw new IllegalArgumentException("допущения детального расчёта заданы не полностью");
        }
    }

    /**
     * Допущения опорного варианта методички.
     *
     * @return допущения для турбины 8 МВт
     */
    public static StageAssumptions reference() {
        return new StageAssumptions(
                new SectionAssumptions(6, 0.879, 0.863, 0.01575, 0.863,
                        0.39, 0.42, 0.05, 0.1344, 0.7, 0.7, 0.003,
                        4.8e-4, 0.0, 0.909),
                new SectionAssumptions(6, 0.915, 0.863, 0.0518, 0.863,
                        0.42, 0.43, 0.151, 0.268, 0.7, 1.0, 0.004,
                        3.2e-4, 0.0, 0.998),
                new SectionAssumptions(4, 1.008, 0.863, 0.144, 1.05,
                        0.43, 0.46, 0.298, 0.44, 1.0, 1.0, 0.005,
                        2.8e-4, 4.0, 1.4),
                GeometryAssumptions.reference(),
                StrengthAssumptions.reference());
    }

    /**
     * Допущения одного отсека: геометрия первой и последней ступеней, закон изменения
     * характеристического числа и коэффициент возврата тепла.
     *
     * <p>Диаметры промежуточных ступеней меняются линейно от первой к последней. Так же
     * линейно меняется {@code U/Ca}; это соответствует таблицам 2.1, 2.4 и 2.7 методички.
     *
     * @param stageCountHint             число ступеней по прототипу; {@code 0} — подбирать
     * @param firstMeanDiameterM         средний диаметр первой ступени, м
     * @param firstRootDiameterM         корневой диаметр первой ступени, м
     * @param firstBladeHeightM          высота рабочей лопатки первой ступени, м
     * @param lastRootDiameterM          корневой диаметр последней ступени, м
     * @param firstSpeedRatio            {@code (U/Ca)_1}
     * @param lastSpeedRatio             {@code (U/Ca)_z}
     * @param firstReaction              степень реактивности первой ступени
     * @param lastReaction               степень реактивности последней ступени
     * @param firstAdmission             парциальность первой ступени
     * @param lastAdmission              парциальность последней ступени
     * @param axialClearanceM            перекрыша {@code Δ} между соплом и рабочим колесом, м
     * @param recoveryFactor             коэффициент возврата тепла {@code K_t}
     * @param lastDiameterToHeightRatio  отношение {@code d_срz/l_2z} для ЧНД; {@code 0} — не
     *                                   использовать формулу выхлопа
     * @param lastMeanDiameterHintM      начальное приближение диаметра последней ступени, м
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
                throw new IllegalArgumentException("число ступеней по прототипу не может быть "
                        + "отрицательным: " + stageCountHint);
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
                throw new IllegalArgumentException("средний диаметр первой ступени "
                        + firstMeanDiameterM + " м должен быть больше корневого "
                        + firstRootDiameterM + " м");
            }
        }
    }


    /**
     * Геометрия решёток и уплотнений.
     *
     * @param nozzleAngleDeg              эффективный угол сопловой решётки {@code α₁эф}, °
     * @param nozzleChordM                хорда сопловой решётки, м
     * @param nozzlePitchRatio            относительный шаг сопловой решётки
     * @param rotorChordM                 хорда рабочей решётки, м
     * @param rotorPitchRatio             относительный шаг рабочей решётки
     * @param secondRotorChordM           хорда рабочей решётки второго венца, м
     * @param secondRotorPitchRatio       относительный шаг второго венца
     * @param turningChordM               хорда поворотной решётки, м
     * @param turningPitchRatio           относительный шаг поворотной решётки
     * @param nozzleSpeedCoefficient      коэффициент скорости сопла {@code φ}
     * @param rotorSpeedCoefficient       коэффициент скорости рабочей решётки {@code ψ}
     * @param nozzleFlowCoefficient       коэффициент расхода сопла {@code μ₁}
     * @param rotorFlowCoefficient        коэффициент расхода рабочей решётки {@code μ₂}
     * @param sealAxialClearanceM         осевой зазор бандажного уплотнения, м
     * @param sealRadialClearanceM        радиальный зазор бандажного уплотнения, м
     * @param sealRidges                  число гребней бандажного уплотнения
     * @param diaphragmLeakAreaM2         площадь зазора диафрагменного уплотнения, м²
     * @param diaphragmLeakCoefficient    коэффициент расхода диафрагменного уплотнения
     * @param diaphragmRidges             число гребней диафрагменного уплотнения
     * @param exitVelocityRecovery        коэффициент использования выходной скорости {@code χ}
     * @param frictionCoefficient         коэффициент трения {@code K_тр}
     * @param bucketLossCoefficient       коэффициент {@code k_В} парциальных потерь
     * @param segmentLossCoefficient      коэффициент {@code k_сегм}
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
                throw new IllegalArgumentException("угол сопловой решётки должен лежать в "
                        + "(0, 90)°: " + nozzleAngleDeg);
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
                throw new IllegalArgumentException("число гребней бандажного уплотнения "
                        + "должно быть не меньше 1: " + sealRidges);
            }
            requirePositive(diaphragmLeakAreaM2, "площадь зазора диафрагменного уплотнения");
            requireUnitInterval(diaphragmLeakCoefficient, "коэффициент расхода уплотнения");
            if (diaphragmRidges < 1) {
                throw new IllegalArgumentException("число гребней диафрагменного уплотнения "
                        + "должно быть не меньше 1: " + diaphragmRidges);
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


    /**
     * Прочностные данные для 4.1–4.3.
     *
     * @param bladeDensityKgM3            плотность материала лопатки, кг/м³
     * @param bladeProfileAreaRatio       отношение площадей корневого и периферийного сечений
     * @param bladeSectionModulusM3       минимальный момент сопротивления профиля, м³
     * @param bladeYieldStrengthMPa       предел текучести материала лопатки, МПа
     * @param diaphragmThicknessM         толщина диафрагмы, м
     * @param diaphragmOuterDiameterM     внешний диаметр диафрагмы, м
     * @param diaphragmStressCoefficient  коэффициент {@code K_σ}
     * @param diaphragmDeflectionCoefficient коэффициент {@code K_Δ}
     * @param diaphragmElasticModulusMPa  модуль упругости диафрагмы, МПа
     * @param diaphragmYieldStrengthMPa   предел текучести диафрагмы, МПа
     * @param discPressureFactor          коэффициент {@code k} для давления на диск
     * @param rotorSealDiameterM          диаметр ротора под диафрагменным уплотнением, м
     * @param reliefHoleDiameterM         диаметр разгрузочного отверстия, м
     * @param reliefHoleCount             число разгрузочных отверстий
     * @param discClearanceM              зазор между диском и диафрагмой, м
     * @param reliefFlowCoefficient       коэффициент расхода разгрузочных отверстий
     * @param discFlowCoefficient         коэффициент расхода корневого зазора
     */
    public record StrengthAssumptions(
            double bladeDensityKgM3,
            double bladeProfileAreaRatio,
            double bladeSectionModulusM3,
            double bladeYieldStrengthMPa,
            double diaphragmThicknessM,
            double diaphragmOuterDiameterM,
            double diaphragmStressCoefficient,
            double diaphragmDeflectionCoefficient,
            double diaphragmElasticModulusMPa,
            double diaphragmYieldStrengthMPa,
            double discPressureFactor,
            double rotorSealDiameterM,
            double reliefHoleDiameterM,
            int reliefHoleCount,
            double discClearanceM,
            double reliefFlowCoefficient,
            double discFlowCoefficient) {

        public StrengthAssumptions {
            requirePositive(bladeDensityKgM3, "плотность материала лопатки");
            requireRatio(bladeProfileAreaRatio, "отношение площадей профиля μ_п");
            requirePositive(bladeSectionModulusM3, "момент сопротивления профиля W_min");
            requirePositive(bladeYieldStrengthMPa, "предел текучести лопатки");
            requirePositive(diaphragmThicknessM, "толщина диафрагмы");
            requirePositive(diaphragmOuterDiameterM, "внешний диаметр диафрагмы");
            requirePositive(diaphragmStressCoefficient, "коэффициент K_σ");
            requirePositive(diaphragmDeflectionCoefficient, "коэффициент K_Δ");
            requirePositive(diaphragmElasticModulusMPa, "модуль упругости диафрагмы");
            requirePositive(diaphragmYieldStrengthMPa, "предел текучести диафрагмы");
            requireUnitInterval(discPressureFactor, "коэффициент давления на диск k");
            requirePositive(rotorSealDiameterM, "диаметр ротора под уплотнением");
            requirePositive(reliefHoleDiameterM, "диаметр разгрузочного отверстия");
            if (reliefHoleCount < 0) {
                throw new IllegalArgumentException("число разгрузочных отверстий не может быть "
                        + "отрицательным: " + reliefHoleCount);
            }
            requireNonNegative(discClearanceM, "зазор между диском и диафрагмой");
            requireUnitInterval(reliefFlowCoefficient, "коэффициент расхода отверстий");
            requireUnitInterval(discFlowCoefficient, "коэффициент расхода корневого зазора");
        }


        static StrengthAssumptions reference() {
            return new StrengthAssumptions(
                    8000.0,
                    0.2,
                    1.28e-6,
                    380.0,
                    0.1,
                    0.922,
                    920.0,
                    580.0,
                    2.1e5,
                    360.0,
                    0.53,
                    0.3,
                    0.05,
                    5,
                    0.004,
                    0.4,
                    0.3);
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

