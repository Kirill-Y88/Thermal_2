package org.kpush88.Thermal_2.turbine4;

/**
 * Аналитические формулы курсового проекта (разделы 2.1–2.4 методички) в виде статических
 * методов без состояния.
 *
 * <p>Класс служит единой точкой для формул предварительного расчёта: экономическая мощность и
 * расход (2.1), регулирующая ступень (2.2), КПД и внутренняя работа (2.3), предельная мощность
 * (2.4). У каждого метода в javadoc указан раздел-источник и сама формула. Формулы 2.3, для
 * которых уже есть записи {@link CourseProjectEfficiency.SectionEstimate} и
 * {@link CourseProjectEfficiency.RegulatingEstimate}, делегируют в
 * {@link CourseProjectEfficiency}, чтобы не дублировать расчёт.
 *
 * <p>Единицы: давление — МПа, температура — °C, энтальпия и теплоперепад — кДж/кг, мощность —
 * кВт (в 2.1 и 2.3) или МВт (в 2.4), расход — кг/с, удельный объём — м³/кг, частота вращения —
 * об/мин, диаметр и высота — м, площадь — м². Переводы единиц выполняются внутри методов.
 */
public final class CourseProjectFormulas {

    /** Доля экономической мощности по умолчанию (разд. 2.1: 0.7…0.8). */
    public static final double DEFAULT_ECONOMIC_POWER_FACTOR = 0.8;

    /** Относительный электрический КПД по умолчанию (табл. 1 методички). */
    public static final double DEFAULT_ELECTRICAL_EFFICIENCY =
            CourseProjectEfficiency.DEFAULT_ELECTRICAL_EFFICIENCY;

    /** Доля давления после клапанов по умолчанию (разд. 2.1: P₀′ = 0.97·P₀). */
    public static final double DEFAULT_VALVE_PRESSURE_FACTOR = 0.97;

    /** Скорость пара в выхлопном патрубке по умолчанию, м/с (разд. 2.1). */
    public static final double DEFAULT_EXHAUST_VELOCITY_MS = 100.0;

    /** Коэффициент потери в выхлопном патрубке по умолчанию (разд. 2.1: λ = 0.1). */
    public static final double DEFAULT_EXHAUST_LOSS_COEFFICIENT = 0.1;

    /** Потеря с выходной скоростью по умолчанию, кДж/кг (разд. 2.3). */
    public static final double DEFAULT_EXIT_HEAT_DROP =
            CourseProjectEfficiency.DEFAULT_EXIT_HEAT_DROP;

    private static final double KJ_PER_KG_TO_J_PER_KG = 1000.0;
    private static final double PERCENT = 100.0;
    private static final double SECONDS_PER_MINUTE = 60.0;
    private static final double LOW_PRESSURE_BASE = 0.87;
    private static final double LOW_PRESSURE_REFERENCE_DROP = 400.0;
    private static final double LOW_PRESSURE_DROP_SCALE = 10000.0;

    private CourseProjectFormulas() {
    }

    // --- 2.1. Экономическая мощность, давления, перепад и расход ---

    /**
     * Экономическая мощность турбины.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.1,
     * формула {@code N_эк = (0.7…0.8)·N_эн}; в проекте принято 0.8.
     *
     * @param nominalPowerKW номинальная мощность, кВт
     * @param factor        доля экономической мощности, (0, 1]
     * @return экономическая мощность, кВт
     */
    public static double economicPower(double nominalPowerKW, double factor) {
        requirePositive(nominalPowerKW, "номинальная мощность");
        requireUnitInterval(factor, "доля экономической мощности");
        return factor * nominalPowerKW;
    }

    /**
     * Давление свежего пара после клапанов.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.1,
     * формула {@code P₀′ = 0.97·P₀}.
     *
     * @param inletPressureMPa давление перед клапанами, МПа
     * @param valveFactor      доля давления после клапанов, (0, 1]
     * @return давление после клапанов, МПа
     */
    public static double valvePressure(double inletPressureMPa, double valveFactor) {
        requirePositive(inletPressureMPa, "давление перед клапанами");
        requireUnitInterval(valveFactor, "доля давления после клапанов");
        return valveFactor * inletPressureMPa;
    }

    /**
     * Давление за последней ступенью с учётом потерь в выхлопном патрубке.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.1,
     * формула {@code P_к′ = P_к·(1 + λ·(c_вп/100)²)}.
     *
     * @param exhaustPressureMPa давление в конденсаторе, МПа
     * @param exhaustVelocityMS  скорость пара в выхлопном патрубке, м/с
     * @param lossCoefficient    коэффициент λ
     * @return давление за последней ступенью, МПа
     */
    public static double exhaustPressureWithLoss(double exhaustPressureMPa,
                                                 double exhaustVelocityMS,
                                                 double lossCoefficient) {
        requirePositive(exhaustPressureMPa, "давление в конденсаторе");
        requireNonNegative(exhaustVelocityMS, "скорость в выхлопном патрубке");
        requireNonNegative(lossCoefficient, "коэффициент потери в выхлопном патрубке");
        double velocityRatio = exhaustVelocityMS / PERCENT;
        return exhaustPressureMPa * (1.0 + lossCoefficient * velocityRatio * velocityRatio);
    }

    /**
     * Располагаемый теплоперепад турбины по h-s диаграмме.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.1,
     * формула {@code H₀′ = h₀′ − h₂s}.
     *
     * @param inletEnthalpyKJkg             энтальпия h₀′, кДж/кг
     * @param isentropicExhaustEnthalpyKJkg энтальпия h₂s, кДж/кг
     * @return располагаемый теплоперепад, кДж/кг
     */
    public static double availableHeatDrop(double inletEnthalpyKJkg,
                                            double isentropicExhaustEnthalpyKJkg) {
        requireFinite(inletEnthalpyKJkg, "энтальпия h₀′");
        requireFinite(isentropicExhaustEnthalpyKJkg, "энтальпия h₂s");
        double heatDrop = inletEnthalpyKJkg - isentropicExhaustEnthalpyKJkg;
        if (!(heatDrop > 0.0)) {
            throw new IllegalArgumentException("располагаемый теплоперепад H₀′ не положителен: "
                    + heatDrop + " кДж/кг");
        }
        return heatDrop;
    }

    /**
     * Предварительный расход пара на турбину.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.1,
     * формула {@code G′ = N_эк/(H₀′·η_оэ)}.
     *
     * @param economicPowerKW       экономическая мощность, кВт
     * @param availableHeatDropKJkg располагаемый теплоперепад, кДж/кг
     * @param electricalEfficiency  относительный электрический КПД η_оэ
     * @return предварительный расход, кг/с
     */
    public static double estimatedFlow(double economicPowerKW, double availableHeatDropKJkg,
                                       double electricalEfficiency) {
        return CourseProjectEfficiency.flow(economicPowerKW, availableHeatDropKJkg,
                electricalEfficiency);
    }

    // --- 2.2. Регулирующая ступень ---

    /**
     * Фиктивная скорость регулирующей ступени.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.2,
     * формула {@code C_a = √(2·h₀^рс·10³)}.
     *
     * @param regulatingHeatDropKJkg теплоперепад регулирующей ступени, кДж/кг
     * @return фиктивная скорость, м/с
     */
    public static double fictiveVelocity(double regulatingHeatDropKJkg) {
        requirePositive(regulatingHeatDropKJkg, "теплоперепад регулирующей ступени");
        return Math.sqrt(2.0 * regulatingHeatDropKJkg * KJ_PER_KG_TO_J_PER_KG);
    }

    /**
     * Окружная скорость регулирующей ступени.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.2,
     * формула {@code U = C_a·(U/C_a)_opt}.
     *
     * @param fictiveVelocityMS фиктивная скорость C_a, м/с
     * @param speedRatio        оптимальное отношение (U/C_a), (0, 1]
     * @return окружная скорость, м/с
     */
    public static double circumferentialVelocity(double fictiveVelocityMS, double speedRatio) {
        requirePositive(fictiveVelocityMS, "фиктивная скорость");
        requireUnitInterval(speedRatio, "отношение U/C_a");
        return fictiveVelocityMS * speedRatio;
    }

    /**
     * Средний диаметр регулирующей ступени.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.2,
     * формула {@code d_рс = 60·U/(π·n)}.
     *
     * @param circumferentialVelocityMS окружная скорость, м/с
     * @param rotationRpm               частота вращения, об/мин
     * @return средний диаметр, м
     */
    public static double regulatingMeanDiameter(double circumferentialVelocityMS,
                                                double rotationRpm) {
        requirePositive(circumferentialVelocityMS, "окружная скорость");
        requirePositive(rotationRpm, "частота вращения");
        return SECONDS_PER_MINUTE * circumferentialVelocityMS / (Math.PI * rotationRpm);
    }

    /**
     * Теплоперепад сопловой решётки регулирующей ступени.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.2,
     * формула {@code h₀^сопл = (1 − ρ)·h₀^рс}.
     *
     * @param regulatingHeatDropKJkg теплоперепад регулирующей ступени, кДж/кг
     * @param reaction               степень реактивности ρ, [0, 1)
     * @return теплоперепад сопловой решётки, кДж/кг
     */
    public static double nozzleHeatDrop(double regulatingHeatDropKJkg, double reaction) {
        requirePositive(regulatingHeatDropKJkg, "теплоперепад регулирующей ступени");
        requireReaction(reaction);
        return (1.0 - reaction) * regulatingHeatDropKJkg;
    }

    /**
     * Теоретическая скорость истечения из сопловой решётки.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.2,
     * формула {@code C_1t = √(2·h₀^сопл·10³)}.
     *
     * @param nozzleHeatDropKJkg теплоперепад сопловой решётки, кДж/кг
     * @return теоретическая скорость, м/с
     */
    public static double nozzleTheoreticalVelocity(double nozzleHeatDropKJkg) {
        requirePositive(nozzleHeatDropKJkg, "теплоперепад сопловой решётки");
        return Math.sqrt(2.0 * nozzleHeatDropKJkg * KJ_PER_KG_TO_J_PER_KG);
    }

    /**
     * Площадь выхода сопловой решётки.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.2,
     * формула {@code F₁ = G·V_1t/(μ₁·C_1t)}.
     *
     * @param flowKgS          расход, кг/с
     * @param nozzleVolumeM3kg удельный объём за сопловой решёткой, м³/кг
     * @param flowCoefficient  коэффициент расхода μ₁, (0, 1]
     * @param nozzleVelocityMS теоретическая скорость C_1t, м/с
     * @return площадь выхода, м²
     */
    public static double nozzleArea(double flowKgS, double nozzleVolumeM3kg,
                                    double flowCoefficient, double nozzleVelocityMS) {
        requirePositive(flowKgS, "расход через сопловую решётку");
        requirePositive(nozzleVolumeM3kg, "удельный объём за сопловой решёткой");
        requireUnitInterval(flowCoefficient, "коэффициент расхода μ₁");
        requirePositive(nozzleVelocityMS, "теоретическая скорость C_1t");
        return flowKgS * nozzleVolumeM3kg / (flowCoefficient * nozzleVelocityMS);
    }

    /**
     * Произведение степени парциальности на высоту сопловой решётки.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.2,
     * формула {@code e·l₁ = F₁/(π·d·sin α₁эф)}.
     *
     * @param nozzleAreaM2   площадь выхода сопловой решётки, м²
     * @param meanDiameterM  средний диаметр ступени, м
     * @param nozzleAngleDeg эффективный угол α₁эф, градусы
     * @return произведение e·l₁, м
     */
    public static double admissionLength(double nozzleAreaM2, double meanDiameterM,
                                         double nozzleAngleDeg) {
        requirePositive(nozzleAreaM2, "площадь сопловой решётки");
        requirePositive(meanDiameterM, "средний диаметр ступени");
        requireAngle(nozzleAngleDeg);
        double angle = Math.toRadians(nozzleAngleDeg);
        return nozzleAreaM2 / (Math.PI * meanDiameterM * Math.sin(angle));
    }

    /**
     * Предварительная степень парциальности.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.2,
     * формула {@code e = a·√(e·l₁·100)}.
     *
     * @param admissionLengthM     произведение e·l₁, м
     * @param admissionCoefficient коэффициент a, (0, 1]
     * @return степень парциальности e, (0, 1]
     */
    public static double admission(double admissionLengthM, double admissionCoefficient) {
        requirePositive(admissionLengthM, "произведение e·l₁");
        requireUnitInterval(admissionCoefficient, "коэффициент парциальности a");
        double admission = admissionCoefficient * Math.sqrt(admissionLengthM * PERCENT);
        if (!(admission > 0.0 && admission <= 1.0)) {
            throw new IllegalArgumentException("предварительная парциальность e = " + admission
                    + " вышла из (0, 1]");
        }
        return admission;
    }

    /**
     * Высота сопловой решётки.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.2,
     * формула {@code l₁ = e·l₁/e}.
     *
     * @param admissionLengthM произведение e·l₁, м
     * @param admission        степень парциальности e, (0, 1]
     * @return высота сопловой решётки, м
     */
    public static double nozzleHeight(double admissionLengthM, double admission) {
        requirePositive(admissionLengthM, "произведение e·l₁");
        requireUnitInterval(admission, "степень парциальности e");
        return admissionLengthM / admission;
    }

    // --- 2.3. КПД, внутренняя работа и уточнённый расход ---

    /**
     * КПД регулирующей ступени.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code η_рс = base − (2·10⁻⁴/G′)·√(P₀′·10⁶/V₀′)}, где base = 0.8 для
     * двухвенечной и 0.85 для одновенечной ступени.
     *
     * @param kind        тип регулирующей ступени
     * @param flowKgS     расход, кг/с
     * @param pressureMPa давление перед ступенью, МПа
     * @param volumeM3kg  удельный объём перед ступенью, м³/кг
     * @return КПД регулирующей ступени
     */
    public static double regulatingEfficiency(StageKind kind, double flowKgS,
                                              double pressureMPa, double volumeM3kg) {
        return CourseProjectEfficiency.regulatingEfficiency(kind, flowKgS, pressureMPa,
                volumeM3kg);
    }

    /**
     * Внутренняя работа регулирующей ступени.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code h_i^рс = h₀^рс·η_oi^рс}.
     *
     * @param regulatingHeatDropKJkg теплоперепад регулирующей ступени, кДж/кг
     * @param efficiency             КПД ступени
     * @return внутренняя работа, кДж/кг
     */
    public static double regulatingInternalWork(double regulatingHeatDropKJkg,
                                                double efficiency) {
        return sectionInternalWork(regulatingHeatDropKJkg, efficiency);
    }

    /**
     * Средний удельный объём участка.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code V_ср = √(V_вх·V_вых)}.
     *
     * @param inletVolumeM3kg  удельный объём на входе, м³/кг
     * @param outletVolumeM3kg удельный объём на выходе, м³/кг
     * @return средний удельный объём, м³/кг
     */
    public static double meanVolume(double inletVolumeM3kg, double outletVolumeM3kg) {
        return CourseProjectEfficiency.meanVolume(inletVolumeM3kg, outletVolumeM3kg);
    }

    /**
     * КПД ЧВД (и основа для ЧСД).
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code (0.925 − 0.5/(G′·V_ср))·(1 − (h₀ − 600)/20000)}.
     *
     * @param flowKgS        расход, кг/с
     * @param heatDropKJkg   теплоперепад отсека, кДж/кг
     * @param meanVolumeM3kg средний удельный объём, м³/кг
     * @return КПД ЧВД
     */
    public static double highPressureEfficiency(double flowKgS, double heatDropKJkg,
                                                double meanVolumeM3kg) {
        return CourseProjectEfficiency.section(CourseProjectEfficiency.Section.CHVD,
                flowKgS, heatDropKJkg, meanVolumeM3kg).efficiency();
    }

    /**
     * Влажностная поправка.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code k_вл = 1 − 0.4·(y₁ + y₂)·h₀^вл/h₀}.
     *
     * @param initialWetness     начальная влажность y₁, [0, 1)
     * @param finalWetness       конечная влажность y₂, [0, 1)
     * @param wetHeatDropKJkg    теплоперепад влажной зоны, кДж/кг
     * @param sectionHeatDropKJkg теплоперепад отсека, кДж/кг
     * @return влажностная поправка, (0, 1]
     */
    public static double wetnessFactor(double initialWetness, double finalWetness,
                                       double wetHeatDropKJkg, double sectionHeatDropKJkg) {
        return CourseProjectEfficiency.wetnessFactor(initialWetness, finalWetness,
                wetHeatDropKJkg, sectionHeatDropKJkg);
    }

    /**
     * КПД ЧСД с влажностной поправкой.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code η_ЧСД = η_ЧВД·k_вл}.
     *
     * @param flowKgS        расход, кг/с
     * @param heatDropKJkg   теплоперепад отсека, кДж/кг
     * @param meanVolumeM3kg средний удельный объём, м³/кг
     * @param wetnessFactor  влажностная поправка
     * @return КПД ЧСД
     */
    public static double chsdEfficiency(double flowKgS, double heatDropKJkg,
                                        double meanVolumeM3kg, double wetnessFactor) {
        return CourseProjectEfficiency.section(CourseProjectEfficiency.Section.CHSD,
                flowKgS, heatDropKJkg, meanVolumeM3kg, wetnessFactor, 0.0).efficiency();
    }

    /**
     * Базовый КПД ЧНД без влажностной поправки и потери с выходной скоростью.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code 0.87·(1 − (h₀ − 400)/10000)}. В источнике напечатано 0.871;
     * для совместимости с {@link CourseProjectEfficiency} принято 0.87.
     *
     * @param heatDropKJkg теплоперепад ЧНД, кДж/кг
     * @return базовый КПД
     */
    public static double lowPressureEfficiency(double heatDropKJkg) {
        requirePositive(heatDropKJkg, "теплоперепад ЧНД");
        double efficiency = LOW_PRESSURE_BASE
                * (1.0 - (heatDropKJkg - LOW_PRESSURE_REFERENCE_DROP) / LOW_PRESSURE_DROP_SCALE);
        requireEfficiency(efficiency, "базовый КПД ЧНД");
        return efficiency;
    }

    /**
     * Относительная потеря с выходной скоростью.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code ξ_вс = Δh_вс/h₀^ЧНД}.
     *
     * @param exitHeatDropKJkg    потеря с выходной скоростью, кДж/кг
     * @param sectionHeatDropKJkg теплоперепад ЧНД, кДж/кг
     * @return относительная потеря, [0, 1)
     */
    public static double exitVelocityLoss(double exitHeatDropKJkg,
                                          double sectionHeatDropKJkg) {
        return CourseProjectEfficiency.exitVelocityLoss(exitHeatDropKJkg, sectionHeatDropKJkg);
    }

    /**
     * КПД ЧНД с влажностной поправкой и потерей с выходной скоростью.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code η_ЧНД = 0.87·(1 − (h₀ − 400)/10000)·k_вл − ξ_вс}.
     *
     * @param heatDropKJkg  теплоперепад ЧНД, кДж/кг
     * @param wetnessFactor влажностная поправка
     * @param exitLoss      относительная потеря с выходной скоростью
     * @return КПД ЧНД
     */
    public static double chndEfficiency(double heatDropKJkg, double wetnessFactor,
                                        double exitLoss) {
        requireWetnessFactor(wetnessFactor);
        requireExitLoss(exitLoss);
        double efficiency = lowPressureEfficiency(heatDropKJkg) * wetnessFactor - exitLoss;
        requireEfficiency(efficiency, "КПД ЧНД");
        return efficiency;
    }

    /**
     * Внутренняя работа отсека.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code h_i = h₀·η_oi}.
     *
     * @param heatDropKJkg теплоперепад отсека, кДж/кг
     * @param efficiency   КПД отсека
     * @return внутренняя работа, кДж/кг
     */
    public static double sectionInternalWork(double heatDropKJkg, double efficiency) {
        requirePositive(heatDropKJkg, "теплоперепад отсека");
        requireEfficiency(efficiency, "КПД отсека");
        return heatDropKJkg * efficiency;
    }

    /**
     * Суммарная внутренняя работа турбины.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code h_i = h_i^рс + h_i^ЧВД + h_i^ЧСД + h_i^ЧНД}.
     *
     * @param regulatingWorkKJkg внутренняя работа регулирующей ступени, кДж/кг
     * @param chvdWorkKJkg       внутренняя работа ЧВД, кДж/кг
     * @param chsdWorkKJkg       внутренняя работа ЧСД, кДж/кг
     * @param chndWorkKJkg       внутренняя работа ЧНД, кДж/кг
     * @return суммарная внутренняя работа, кДж/кг
     */
    public static double totalInternalWork(double regulatingWorkKJkg, double chvdWorkKJkg,
                                           double chsdWorkKJkg, double chndWorkKJkg) {
        requirePositive(regulatingWorkKJkg, "внутренняя работа регулирующей ступени");
        requirePositive(chvdWorkKJkg, "внутренняя работа ЧВД");
        requirePositive(chsdWorkKJkg, "внутренняя работа ЧСД");
        requirePositive(chndWorkKJkg, "внутренняя работа ЧНД");
        return regulatingWorkKJkg + chvdWorkKJkg + chsdWorkKJkg + chndWorkKJkg;
    }

    /**
     * Уточнённый расход пара по внутренней работе.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code G = N_эк/(h_i·η_м·η_г)}.
     *
     * @param economicPowerKW      экономическая мощность, кВт
     * @param internalWorkKJkg     внутренняя работа турбины, кДж/кг
     * @param mechanicalEfficiency η_м
     * @param generatorEfficiency  η_г
     * @return уточнённый расход, кг/с
     */
    public static double refinedFlow(double economicPowerKW, double internalWorkKJkg,
                                     double mechanicalEfficiency, double generatorEfficiency) {
        requirePositive(economicPowerKW, "экономическая мощность");
        requirePositive(internalWorkKJkg, "внутренняя работа турбины");
        requireEfficiency(mechanicalEfficiency, "механический КПД");
        requireEfficiency(generatorEfficiency, "КПД генератора");
        return economicPowerKW / (internalWorkKJkg * mechanicalEfficiency
                * generatorEfficiency);
    }

    /**
     * КПД турбины по внутренней работе и располагаемому перепаду.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.3,
     * формула {@code η_oi = h_i/H₀′}.
     *
     * @param internalWorkKJkg      внутренняя работа турбины, кДж/кг
     * @param availableHeatDropKJkg располагаемый перепад, кДж/кг
     * @return КПД турбины
     */
    public static double turbineEfficiency(double internalWorkKJkg,
                                           double availableHeatDropKJkg) {
        return CourseProjectEfficiency.turbineEfficiency(internalWorkKJkg,
                availableHeatDropKJkg);
    }

    // --- 2.4. Предельная мощность и число выхлопов ---

    /**
     * Скорость выхода из последней ступени.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.4,
     * формула {@code C₂z = √(2·Δh_вс·10³)}.
     *
     * @param exitHeatDropKJkg потеря с выходной скоростью, кДж/кг
     * @return скорость выхода, м/с
     */
    public static double exitVelocity(double exitHeatDropKJkg) {
        requirePositive(exitHeatDropKJkg, "потеря с выходной скоростью");
        return Math.sqrt(2.0 * exitHeatDropKJkg * KJ_PER_KG_TO_J_PER_KG);
    }

    /**
     * Предельная мощность однопоточной турбины.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.4,
     * формула {@code N_пр = m/(2π)·k_разгр·η_oi·[σ]·C₂z·H₀′·10³/(ρ·n²·V₂z)},
     * где n — частота вращения, 1/с.
     *
     * @param extractionFactor     коэффициент m
     * @param taperFactor          коэффициент k_разгр
     * @param turbineEfficiency    КПД турбины η_oi
     * @param allowableStressMPa   допускаемое напряжение [σ], МПа
     * @param exitVelocityMS       скорость выхода C₂z, м/с
     * @param availableHeatDropKJkg располагаемый перепад H₀′, кДж/кг
     * @param bladeDensityKgM3     плотность материала лопатки ρ, кг/м³
     * @param rotationRpm          частота вращения, об/мин
     * @param lastStageVolumeM3kg  удельный объём за последней ступенью V₂z, м³/кг
     * @return предельная мощность, МВт
     */
    public static double limitPower(double extractionFactor, double taperFactor,
                                    double turbineEfficiency, double allowableStressMPa,
                                    double exitVelocityMS, double availableHeatDropKJkg,
                                    double bladeDensityKgM3, double rotationRpm,
                                    double lastStageVolumeM3kg) {
        requirePositive(extractionFactor, "коэффициент m");
        requirePositive(taperFactor, "коэффициент k_разгр");
        requireEfficiency(turbineEfficiency, "КПД турбины");
        requirePositive(allowableStressMPa, "допускаемое напряжение [σ]");
        requirePositive(exitVelocityMS, "скорость выхода C₂z");
        requirePositive(availableHeatDropKJkg, "располагаемый перепад H₀′");
        requirePositive(bladeDensityKgM3, "плотность материала лопатки");
        requirePositive(rotationRpm, "частота вращения");
        requirePositive(lastStageVolumeM3kg, "удельный объём за последней ступенью");

        double revolutionsPerSecond = rotationRpm / SECONDS_PER_MINUTE;
        double limitPower = extractionFactor / (2.0 * Math.PI)
                * taperFactor
                * turbineEfficiency
                * (allowableStressMPa * exitVelocityMS * availableHeatDropKJkg
                * KJ_PER_KG_TO_J_PER_KG)
                / (bladeDensityKgM3 * revolutionsPerSecond * revolutionsPerSecond
                * lastStageVolumeM3kg);
        if (!(limitPower > 0.0) || !Double.isFinite(limitPower)) {
            throw new IllegalArgumentException("предельная мощность не положительна: "
                    + limitPower);
        }
        return limitPower;
    }

    /**
     * Число выхлопов по предельной мощности.
     *
     * <p><b>Источник:</b> {@code university/Курс турб.docx}, разд. 2.4,
     * формула {@code z_вых = max(1, ceil(N_эн/N_пр))}.
     *
     * @param nominalPowerMW номинальная мощность, МВт
     * @param limitPowerMW   предельная мощность одного выхлопа, МВт
     * @return число выхлопов, не меньше 1
     */
    public static int exhaustCount(double nominalPowerMW, double limitPowerMW) {
        requirePositive(nominalPowerMW, "номинальная мощность");
        requirePositive(limitPowerMW, "предельная мощность");
        return Math.max(1, (int) Math.ceil(nominalPowerMW / limitPowerMW));
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

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен быть конечным: " + value);
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
            throw new IllegalArgumentException("степень реактивности вне [0, 1): " + value);
        }
    }

    private static void requireAngle(double value) {
        if (!(value > 0.0 && value < 90.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("угол вне (0, 90)°: " + value);
        }
    }

    private static void requireWetnessFactor(double value) {
        if (!(value > 0.0 && value <= 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("влажностная поправка должна лежать в (0, 1]: "
                    + value);
        }
    }

    private static void requireExitLoss(double value) {
        if (!(value >= 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("относительная потеря с выходной скоростью "
                    + "должна лежать в [0, 1): " + value);
        }
    }
}
