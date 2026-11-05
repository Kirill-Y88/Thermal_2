package org.kpush88.Thermal_2.turbine2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Проектировочный расчёт турбины по разделам 2.1–2.5, 3.1–3.4 и 4.1–4.3 методички
 * {@code university/Курс турб.docx}.
 *
 * <p><b>Что считается.</b>
 * <ol>
 *   <li>2.1 — экономическая мощность, давления перед турбиной и за ней, располагаемый перепад
 *       {@code H₀′} и предварительный расход {@code G′};</li>
 *   <li>2.2 — регулирующая ступень: фиктивная и окружная скорости, средний диаметр, теплоперепад
 *       сопловой решётки, давление и объём за ней, площадь, парциальность и высота сопла;</li>
 *   <li>2.3 — КПД регулирующей ступени и отсеков по {@link CourseProjectEfficiency}, внутренняя
 *       работа и уточнённый расход;</li>
 *   <li>2.4 — предельная мощность одной выхлопной камеры и число выхлопов;</li>
 *   <li>2.5 — предварительное число ступеней, диаметры, высоты лопаток и распределение
 *       теплоперепада;</li>
 *   <li>3.1 — детальный расчёт регулирующей ступени;</li>
 *   <li>3.2 — детальный расчёт первой нерегулируемой ступени;</li>
 *   <li>3.2–3.3 — детальный расчёт всех нерегулируемых ступеней;</li>
 *   <li>3.3 — детальный расчёт последних трёх ступеней;</li>
 *   <li>3.4 — закрутка последней ступени по трём радиальным участкам;</li>
 *   <li>4.1–4.3 — осевое усилие, прочность рабочей лопатки и диафрагмы.</li>
 * </ol>
 *
 * <p>Методы 3.x и 4.x имеют две формы: без аргументов (заново выполняют 2.1–2.5) и с готовыми
 * {@link PreliminaryResult} и {@link PreliminaryStages}. Вторая форма нужна, чтобы не повторять
 * предварительный расчёт при построении сводного проекта.
 *
 * <p><b>Откуда берутся давления на границах отсеков.</b> Методичка читает их с hs-диаграммы.
 * Здесь они находятся обратной задачей: по известному теплоперепаду отсека
 * {@link HsDiagram#pressureForIsentropicDrop(double, double, double)} даёт давление в конце
 * изоэнтропы, а {@link HsDiagram#volumePH(double, double)} — удельный объём. Поэтому расчёт не
 * зависит от рисунка и работает для любого варианта, а не только для опорного.
 *
 * <p><b>Почему средний объём берётся от разных состояний.</b> В методичке
 * {@code V_ср = √(V_вх·V_вых)}, где {@code V_вх} — действительный объём за предыдущим отсеком,
 * а {@code V_вых} — объём в конце изоэнтропы текущего отсека. Это не термодинамическое
 * осреднение, а эмпирическая формула; класс повторяет её, чтобы КПД совпадали с документом
 * (0.7085, 0.8776, 0.7828), а не заменяет «более правильной» оценкой.
 *
 * <p>Класс неизменяемый: все поля задаются builder'ом, {@link #preliminary()} — чистая функция.
 */
public final class TurbineDesign {

    private final DesignInput input;
    private final DesignAssumptions assumptions;
    private final StageAssumptions stageAssumptions;
    private final HsDiagram hs;

    private TurbineDesign(Builder builder) {
        this.input = builder.input;
        this.assumptions = builder.assumptions;
        this.stageAssumptions = builder.stageAssumptions;
        this.hs = builder.hs;
    }

    /**
     * Начало построения расчёта.
     *
     * @param input исходные данные варианта
     * @return builder
     */
    public static Builder builder(DesignInput input) {
        return new Builder(input);
    }

    /** Исходные данные варианта. */
    public DesignInput input() {
        return input;
    }

    /** Принятые допущения. */
    public DesignAssumptions assumptions() {
        return assumptions;
    }

    /** Допущения разбиения на ступени (раздел 2.5). */
    public StageAssumptions stageAssumptions() {
        return stageAssumptions;
    }

    /**
     * Выполняет предварительный расчёт 2.1–2.4.
     *
     * @return результат с промежуточными величинами и примечаниями
     */
    public PreliminaryResult preliminary() {
        PreliminaryResult.EnergyEstimate energy = energyEstimate();
        PreliminaryResult.RegulatingStage regulating = regulatingStage(energy);
        List<String> notes = new ArrayList<>();
        PreliminaryResult.Expansion expansion = expansion(energy, regulating, notes);
        PreliminaryResult.PowerLimit powerLimit = powerLimit(energy, expansion, notes);
        return new PreliminaryResult(energy, regulating, expansion, powerLimit, notes);
    }

    /**
     * Выполняет расчёт 2.1–2.5: к предварительному результату добавляется разбиение на ступени.
     *
     * @return число ступеней, диаметры, высоты лопаток и распределение теплоперепада
     */
    public PreliminaryStages preliminaryStages() {
        return preliminaryStages(preliminary());
    }

    /**
     * Разбиение на ступени по уже посчитанному результату 2.1–2.4. Нужно, чтобы не повторять
     * расчёт при добавлении детальных разделов 3.x поверх того же {@link PreliminaryResult}.
     *
     * @param preliminary результат 2.1–2.4
     * @return разбиение по ступеням
     */
    public PreliminaryStages preliminaryStages(PreliminaryResult preliminary) {
        if (preliminary == null) {
            throw new IllegalArgumentException("результат 2.1–2.4 не задан");
        }
        return StageLayoutCalculator.calculate(input, stageAssumptions, hs, preliminary);
    }

    // --- 3.1–3.4. Детальный расчёт проточной части ---

    /**
     * Раздел 3.1: детальный расчёт регулирующей ступени.
     *
     * @return треугольники скоростей, потери и КПД
     */
    public RegulatingStageDetail regulatingStageDetail() {
        return regulatingStageDetail(preliminary());
    }

    /**
     * Раздел 3.1 по готовому результату 2.1–2.4.
     *
     * @param preliminary результат 2.1–2.4
     * @return детальный расчёт регулирующей ступени
     */
    public RegulatingStageDetail regulatingStageDetail(PreliminaryResult preliminary) {
        if (preliminary == null) {
            throw new IllegalArgumentException("результат 2.1–2.4 не задан");
        }
        return DetailedStageCalculator.regulatingStage(preliminary, stageAssumptions, hs);
    }

    /**
     * Раздел 3.2: детальный расчёт первой нерегулируемой ступени ЧВД.
     *
     * @return треугольники скоростей, потери и КПД
     */
    public StageDetail firstStageDetail() {
        PreliminaryResult preliminary = preliminary();
        return firstStageDetail(preliminary, preliminaryStages(preliminary));
    }

    /**
     * Раздел 3.2 по готовому результату 2.1–2.4; разбиение 2.5 вычисляется внутри.
     *
     * @param preliminary результат 2.1–2.4
     * @return детальный расчёт первой нерегулируемой ступени
     */
    public StageDetail firstStageDetail(PreliminaryResult preliminary) {
        if (preliminary == null) {
            throw new IllegalArgumentException("результат 2.1–2.4 не задан");
        }
        return firstStageDetail(preliminary, preliminaryStages(preliminary));
    }

    /**
     * Раздел 3.2 по готовым результатам 2.1–2.5.
     *
     * @param preliminary результат 2.1–2.4
     * @param stages      разбиение 2.5
     * @return детальный расчёт первой нерегулируемой ступени
     */
    public StageDetail firstStageDetail(PreliminaryResult preliminary,
                                        PreliminaryStages stages) {
        requireCalculated(preliminary, stages);
        return DetailedStageCalculator.firstNonRegulated(preliminary, stages,
                stageAssumptions, hs);
    }

    /**
     * Детальный расчёт всех нерегулируемых ступеней турбины (разделы 3.2–3.3).
     *
     * <p>Регулирующая ступень не входит: для неё есть отдельный
     * {@link #regulatingStageDetail()}.
     *
     * @return все нерегулируемые ступени в порядке прохождения пара
     */
    public List<StageDetail> allStageDetails() {
        PreliminaryResult preliminary = preliminary();
        return allStageDetails(preliminary, preliminaryStages(preliminary));
    }

    /**
     * Детальный расчёт всех нерегулируемых ступеней по готовому результату 2.1–2.4;
     * разбиение 2.5 вычисляется внутри.
     *
     * @param preliminary результат 2.1–2.4
     * @return все нерегулируемые ступени
     */
    public List<StageDetail> allStageDetails(PreliminaryResult preliminary) {
        if (preliminary == null) {
            throw new IllegalArgumentException("результат 2.1–2.4 не задан");
        }
        return allStageDetails(preliminary, preliminaryStages(preliminary));
    }

    /**
     * Детальный расчёт всех нерегулируемых ступеней по готовым результатам 2.1–2.5.
     *
     * @param preliminary результат 2.1–2.4
     * @param stages      разбиение 2.5
     * @return все нерегулируемые ступени
     */
    public List<StageDetail> allStageDetails(PreliminaryResult preliminary,
                                             PreliminaryStages stages) {
        requireCalculated(preliminary, stages);
        return DetailedStageCalculator.allStages(preliminary, stages, stageAssumptions, hs);
    }

    /**
     * Раздел 3.3: детальный расчёт последних трёх ступеней ЧНД.
     *
     * @return ступени 14, 15 и 16 в порядке прохождения пара
     */
    public List<StageDetail> lastStageDetails() {
        PreliminaryResult preliminary = preliminary();
        return lastStageDetails(preliminary, preliminaryStages(preliminary));
    }

    /**
     * Раздел 3.3 по готовому результату 2.1–2.4; разбиение 2.5 вычисляется внутри.
     *
     * @param preliminary результат 2.1–2.4
     * @return три последние ступени
     */
    public List<StageDetail> lastStageDetails(PreliminaryResult preliminary) {
        if (preliminary == null) {
            throw new IllegalArgumentException("результат 2.1–2.4 не задан");
        }
        return lastStageDetails(preliminary, preliminaryStages(preliminary));
    }

    /**
     * Раздел 3.3 по готовым результатам 2.1–2.5.
     *
     * @param preliminary результат 2.1–2.4
     * @param stages      разбиение 2.5
     * @return три последние ступени
     */
    public List<StageDetail> lastStageDetails(PreliminaryResult preliminary,
                                              PreliminaryStages stages) {
        requireCalculated(preliminary, stages);
        return DetailedStageCalculator.lastStages(preliminary, stages, stageAssumptions, hs);
    }

    /**
     * Раздел 3.4: закрутка последней ступени по трём радиальным участкам.
     *
     * @return три сечения, расход и мощность
     */
    public TwistResult lastStageTwist() {
        PreliminaryResult preliminary = preliminary();
        return lastStageTwist(preliminary, preliminaryStages(preliminary));
    }

    /**
     * Раздел 3.4 по готовому результату 2.1–2.4; разбиение 2.5 вычисляется внутри.
     *
     * @param preliminary результат 2.1–2.4
     * @return закрутка последней ступени
     */
    public TwistResult lastStageTwist(PreliminaryResult preliminary) {
        if (preliminary == null) {
            throw new IllegalArgumentException("результат 2.1–2.4 не задан");
        }
        return lastStageTwist(preliminary, preliminaryStages(preliminary));
    }

    /**
     * Раздел 3.4 по готовым результатам 2.1–2.5.
     *
     * @param preliminary результат 2.1–2.4
     * @param stages      разбиение 2.5
     * @return закрутка последней ступени
     */
    public TwistResult lastStageTwist(PreliminaryResult preliminary,
                                      PreliminaryStages stages) {
        requireCalculated(preliminary, stages);
        List<StageDetail> last = DetailedStageCalculator.lastStages(preliminary, stages,
                stageAssumptions, hs);
        return DetailedStageCalculator.twist(last.get(last.size() - 1), stageAssumptions,
                hs, input.rotationRpm());
    }

    // --- 4.1–4.3. Прочностные расчёты ---

    /**
     * Разделы 4.1–4.3: осевое усилие, прочность лопатки и диафрагмы.
     *
     * @return результаты прочностных расчётов
     */
    public StrengthResults strength() {
        PreliminaryResult preliminary = preliminary();
        return strength(preliminary, preliminaryStages(preliminary));
    }

    /**
     * Разделы 4.1–4.3 по готовому результату 2.1–2.4; разбиение 2.5 вычисляется внутри.
     *
     * @param preliminary результат 2.1–2.4
     * @return результаты прочностных расчётов
     */
    public StrengthResults strength(PreliminaryResult preliminary) {
        if (preliminary == null) {
            throw new IllegalArgumentException("результат 2.1–2.4 не задан");
        }
        return strength(preliminary, preliminaryStages(preliminary));
    }

    /**
     * Разделы 4.1–4.3 по готовым результатам 2.1–2.5.
     *
     * @param preliminary результат 2.1–2.4
     * @param stages      разбиение 2.5
     * @return результаты прочностных расчётов
     */
    public StrengthResults strength(PreliminaryResult preliminary,
                                    PreliminaryStages stages) {
        requireCalculated(preliminary, stages);
        StageDetail first = DetailedStageCalculator.firstNonRegulated(preliminary,
                stages, stageAssumptions, hs);
        List<StageDetail> last = DetailedStageCalculator.lastStages(preliminary, stages,
                stageAssumptions, hs);
        return StrengthCalculator.calculate(first, last.get(last.size() - 1),
                preliminary.expansion().refinedFlowKgS(), stageAssumptions,
                stages.totalStages());
    }

    private static void requireCalculated(PreliminaryResult preliminary,
                                          PreliminaryStages stages) {
        if (preliminary == null) {
            throw new IllegalArgumentException("результат 2.1–2.4 не задан");
        }
        if (stages == null) {
            throw new IllegalArgumentException("разбиение 2.5 не задано");
        }
    }

    // --- 2.1. Экономическая мощность, давления, перепад и расход ---

    private PreliminaryResult.EnergyEstimate energyEstimate() {
        double p0 = input.inletPressureMPa();
        double p0Prime = assumptions.valvePressureFactor() * p0;
        double exitRatio = assumptions.exhaustVelocityMS() / 100.0;
        double pkPrime = input.exhaustPressureMPa()
                * (1.0 + assumptions.exhaustLossCoefficient() * exitRatio * exitRatio);
        if (!(pkPrime < p0Prime)) {
            throw new IllegalArgumentException("после клапанов давление P₀′ = " + p0Prime
                    + " МПа должно быть выше P_к′ = " + pkPrime + " МПа");
        }

        double h0Prime = hs.enthalpyPT(p0Prime, input.inletTemperatureC());
        double s0Prime = hs.entropyPT(p0Prime, input.inletTemperatureC());
        double h2s = hs.enthalpyPS(pkPrime, s0Prime);
        double availableDrop = h0Prime - h2s;
        if (!(availableDrop > 0.0)) {
            throw new IllegalArgumentException("располагаемый перепад H₀′ не положителен: "
                    + availableDrop + " кДж/кг; проверьте P₀, t₀ и P_к");
        }
        double v0Prime = hs.volumePH(p0Prime, h0Prime);
        double economicPower = assumptions.economicPowerFactor()
                * input.nominalPowerMW() * 1000.0;
        double flow = CourseProjectEfficiency.flow(economicPower, availableDrop,
                assumptions.electricalEfficiency());
        return new PreliminaryResult.EnergyEstimate(economicPower, p0, p0Prime,
                input.exhaustPressureMPa(), pkPrime, h0Prime, s0Prime, v0Prime, h2s,
                availableDrop, flow);
    }

    // --- 2.2. Регулирующая ступень ---

    private PreliminaryResult.RegulatingStage regulatingStage(
            PreliminaryResult.EnergyEstimate energy) {
        double heatDrop = assumptions.regulatingHeatDropKJkg();
        if (!(heatDrop < energy.availableHeatDropKJkg())) {
            throw new IllegalArgumentException("теплоперепад регулирующей ступени "
                    + heatDrop + " кДж/кг должен быть меньше располагаемого перепада турбины "
                    + energy.availableHeatDropKJkg() + " кДж/кг");
        }
        double fictiveVelocity = Math.sqrt(2.0 * heatDrop * 1000.0);
        double circumferential = fictiveVelocity * assumptions.regulatingSpeedRatio();
        double meanDiameter = 60.0 * circumferential / (Math.PI * input.rotationRpm());
        if (!(meanDiameter > 0.0)) {
            throw new IllegalArgumentException("средний диаметр регулирующей ступени не "
                    + "положителен: " + meanDiameter + " м (n = " + input.rotationRpm()
                    + " об/мин)");
        }
        double nozzleHeatDrop = (1.0 - assumptions.regulatingReaction()) * heatDrop;
        double nozzleVelocity = Math.sqrt(2.0 * nozzleHeatDrop * 1000.0);
        double nozzlePressure = hs.pressureForIsentropicDrop(
                energy.inletPressureAtNozzlesMPa(), energy.inletEnthalpyKJkg(), nozzleHeatDrop);
        double nozzleVolume = hs.volumePH(nozzlePressure,
                energy.inletEnthalpyKJkg() - nozzleHeatDrop);
        double angle = Math.toRadians(assumptions.regulatingNozzleAngleDeg());
        double nozzleArea = energy.estimatedFlowKgS() * nozzleVolume
                / (assumptions.nozzleFlowCoefficient() * nozzleVelocity);
        double admissionLength = nozzleArea / (Math.PI * meanDiameter * Math.sin(angle));
        double admission = assumptions.admissionCoefficient()
                * Math.sqrt(admissionLength * 100.0);
        if (!(admission > 0.0 && admission <= 1.0)) {
            throw new IllegalArgumentException("предварительная парциальность e = " + admission
                    + " вышла из (0, 1]: уменьшите теплоперепад регулирующей ступени или "
                    + "проверьте расход и диаметр");
        }
        double nozzleHeight = admissionLength / admission;
        if (!(nozzleHeight > 0.0)) {
            throw new IllegalArgumentException("высота сопловой решётки не положительна: "
                    + nozzleHeight + " м");
        }
        return new PreliminaryResult.RegulatingStage(input.regulatingKind(), heatDrop,
                fictiveVelocity, circumferential, meanDiameter,
                assumptions.regulatingReaction(), nozzleHeatDrop, nozzleVelocity,
                nozzlePressure, nozzleVolume, nozzleArea, admission, nozzleHeight,
                assumptions.regulatingNozzleAngleDeg());
    }


    // --- 2.3. КПД элементов, внутренняя работа и уточнённый расход ---

    private PreliminaryResult.Expansion expansion(PreliminaryResult.EnergyEstimate energy,
                                                  PreliminaryResult.RegulatingStage regulating,
                                                  List<String> notes) {
        double flow = energy.estimatedFlowKgS();
        double p0 = energy.inletPressureAtNozzlesMPa();
        double h0 = energy.inletEnthalpyKJkg();
        double v0 = energy.inletVolumeM3kg();

        double p2rs = hs.pressureForIsentropicDrop(p0, h0, regulating.heatDropKJkg());
        CourseProjectEfficiency.RegulatingEstimate regulatingEstimate =
                switch (input.regulatingKind()) {
                    case DOUBLE_ROW -> CourseProjectEfficiency.regulatingDoubleRow(
                            flow, p0, v0, regulating.heatDropKJkg());
                    case SINGLE_ROW -> CourseProjectEfficiency.regulatingSingleRow(
                            flow, p0, v0, regulating.heatDropKJkg());
                };
        double h2rs = h0 - regulatingEstimate.internalWork();
        double v2rs = hs.volumePH(p2rs, h2rs);

        double chvdDrop = input.sectionHeatDrops().chvdKJkg();
        double pChvd = hs.pressureForIsentropicDrop(p2rs, h2rs, chvdDrop);
        double vChvd = hs.volumePH(pChvd, h2rs - chvdDrop);
        CourseProjectEfficiency.SectionEstimate chvd = CourseProjectEfficiency.section(
                CourseProjectEfficiency.Section.CHVD, flow, chvdDrop,
                CourseProjectEfficiency.meanVolume(v2rs, vChvd));
        double hChvd = h2rs - chvd.internalWork();
        double vChvdReal = hs.volumePH(pChvd, hChvd);

        double chsdDrop = input.sectionHeatDrops().chsdKJkg();
        double pChsd = hs.pressureForIsentropicDrop(pChvd, hChvd, chsdDrop);
        double hChsdIs = hChvd - chsdDrop;
        double vChsd = hs.volumePH(pChsd, hChsdIs);
        HsDiagram.HeatDropSplit chsdSplit = hs.splitDrop(pChvd, hChvd, pChsd);
        double chsdY1 = 1.0 - hs.vapourFractionPH(pChvd, hChvd);
        double chsdY2 = 1.0 - hs.vapourFractionPH(pChsd, hChsdIs);
        double chsdWetness = CourseProjectEfficiency.wetnessFactor(chsdY1, chsdY2,
                chsdSplit.wet(), chsdDrop);
        CourseProjectEfficiency.SectionEstimate chsd = CourseProjectEfficiency.section(
                CourseProjectEfficiency.Section.CHSD, flow, chsdDrop,
                CourseProjectEfficiency.meanVolume(vChvdReal, vChsd), chsdWetness, 0.0);
        double hChsd = hChvd - chsd.internalWork();
        double vChsdReal = hs.volumePH(pChsd, hChsd);

        double pk = energy.exhaustPressureWithLossMPa();
        double chndDrop = hs.isentropicDrop(pChsd, hChsd, pk);
        if (!(chndDrop > 0.0)) {
            throw new IllegalArgumentException("располагаемый перепад ЧНД не положителен: "
                    + chndDrop + " кДж/кг (p = " + pChsd + " → " + pk + " МПа)");
        }
        double hChndIs = hChsd - chndDrop;
        double vChnd = hs.volumePH(pk, hChndIs);
        HsDiagram.HeatDropSplit chndSplit = hs.splitDrop(pChsd, hChsd, pk);
        double chndY1 = 1.0 - hs.vapourFractionPH(pChsd, hChsd);
        double chndY2 = 1.0 - hs.vapourFractionPH(pk, hChndIs);
        double chndWetness = CourseProjectEfficiency.wetnessFactor(chndY1, chndY2,
                chndSplit.wet(), chndDrop);
        double exitLoss = CourseProjectEfficiency.exitVelocityLoss(
                assumptions.limitPowerExitHeatDropKJkg(), chndDrop);
        CourseProjectEfficiency.SectionEstimate chnd = CourseProjectEfficiency.section(
                CourseProjectEfficiency.Section.CHND, flow, chndDrop,
                CourseProjectEfficiency.meanVolume(vChsdReal, vChnd), chndWetness, exitLoss);
        double vChndActual = hs.volumePH(pk, hChsd - chnd.internalWork());

        double internalWork = regulatingEstimate.internalWork() + chvd.internalWork()
                + chsd.internalWork() + chnd.internalWork();
        double turbineEfficiency = CourseProjectEfficiency.turbineEfficiency(internalWork,
                energy.availableHeatDropKJkg());
        double refinedFlow = energy.economicPowerKW() / (internalWork
                * assumptions.mechanicalEfficiency() * assumptions.generatorEfficiency());

        double controlChnd = input.sectionHeatDrops().chndKJkg();
        if (Math.abs(chndDrop - controlChnd) > 1.0) {
            notes.add(String.format(Locale.ROOT,
                    "перепад ЧНД по P_к′ (%.2f кДж/кг) отличается от контрольного (%.2f "
                            + "кДж/кг) больше чем на 1 кДж/кг; в расчёте использован перепад "
                            + "по давлению конденсатора",
                    chndDrop, controlChnd));
        }
        if (Math.abs(refinedFlow - flow) > 0.05 * flow) {
            notes.add(String.format(Locale.ROOT,
                    "уточнённый расход G = %.3f кг/с отличается от предварительного G′ = "
                            + "%.3f кг/с больше чем на 5%%; проверьте η_оэ и допущения",
                    refinedFlow, flow));
        }
        return new PreliminaryResult.Expansion(regulatingEstimate, chvd, chsd, chnd,
                p2rs, h2rs, v2rs, pChvd, vChvd, pChsd, vChsd, vChnd, vChndActual,
                internalWork, refinedFlow, turbineEfficiency);
    }

    // --- 2.4. Предельная мощность и число выхлопов ---

    private PreliminaryResult.PowerLimit powerLimit(PreliminaryResult.EnergyEstimate energy,
                                                    PreliminaryResult.Expansion expansion,
                                                    List<String> notes) {
        double exitVelocity = Math.sqrt(
                2.0 * assumptions.limitPowerExitHeatDropKJkg() * 1000.0);
        double revolutionsPerSecond = input.rotationRpm() / 60.0;
        double limitPower = assumptions.limitPowerExtractionFactor() / (2.0 * Math.PI)
                * assumptions.limitPowerTaperFactor()
                * expansion.turbineEfficiency()
                * (assumptions.allowableBladeStressMPa() * exitVelocity
                * energy.availableHeatDropKJkg() * 1000.0)
                / (assumptions.bladeDensityKgM3() * revolutionsPerSecond
                * revolutionsPerSecond * expansion.exhaustActualVolumeM3kg());
        if (!(limitPower > 0.0) || !Double.isFinite(limitPower)) {
            throw new IllegalArgumentException("предельная мощность не положительна: "
                    + limitPower + " МВт; проверьте допущения 2.4");
        }
        int exhaustCount = Math.max(1,
                (int) Math.ceil(input.nominalPowerMW() / limitPower));
        if (exhaustCount > 1) {
            notes.add(String.format(Locale.ROOT,
                    "предельная мощность одной выхлопной камеры %.2f МВт меньше номинальной "
                            + "%.2f МВт: требуется выхлопов — %d",
                    limitPower, input.nominalPowerMW(), exhaustCount));
        }
        return new PreliminaryResult.PowerLimit(exitVelocity,
                expansion.exhaustActualVolumeM3kg(), limitPower, exhaustCount);
    }

    // --- Builder ---

    /**
     * Builder расчёта: позволяет заменить допущения или реализацию hs-диаграммы.
     */
    public static final class Builder {

        private final DesignInput input;
        private DesignAssumptions assumptions = DesignAssumptions.reference();
        private StageAssumptions stageAssumptions = StageAssumptions.reference();
        private HsDiagram hs = new HsDiagram();

        private Builder(DesignInput input) {
            if (input == null) {
                throw new IllegalArgumentException("исходные данные расчёта не заданы");
            }
            this.input = input;
        }

        /** Заменить допущения. */
        public Builder assumptions(DesignAssumptions assumptions) {
            if (assumptions == null) {
                throw new IllegalArgumentException("допущения расчёта не заданы");
            }
            this.assumptions = assumptions;
            return this;
        }

        /** Заменить допущения разбиения на ступени (раздел 2.5). */
        public Builder stageAssumptions(StageAssumptions stageAssumptions) {
            if (stageAssumptions == null) {
                throw new IllegalArgumentException("допущения разбиения на ступени не заданы");
            }
            this.stageAssumptions = stageAssumptions;
            return this;
        }

        /** Заменить реализацию hs-диаграммы (например, для тестовой подмены). */
        public Builder hs(HsDiagram hs) {
            if (hs == null) {
                throw new IllegalArgumentException("hs-диаграмма не задана");
            }
            this.hs = hs;
            return this;
        }

        /** Собрать расчёт. */
        public TurbineDesign build() {
            return new TurbineDesign(this);
        }
    }
}
