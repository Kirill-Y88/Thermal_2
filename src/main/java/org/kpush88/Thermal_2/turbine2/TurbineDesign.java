package org.kpush88.Thermal_2.turbine2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Проектировочный расчёт турбины: каркас и предварительные этапы 2.1–2.4 методички
 * {@code university/Курс турб.docx}.
 *
 * <p><b>Что уже считается.</b>
 * <ol>
 *   <li>2.1 — экономическая мощность, давления перед турбиной и за ней, располагаемый перепад
 *       {@code H₀′} и предварительный расход {@code G′};</li>
 *   <li>2.2 — регулирующая ступень: фиктивная и окружная скорости, средний диаметр, теплоперепад
 *       сопловой решётки, давление и объём за ней, площадь, парциальность и высота сопла;</li>
 *   <li>2.3 — КПД регулирующей ступени и отсеков по {@link CourseProjectEfficiency}, внутренняя
 *       работа и уточнённый расход;</li>
 *   <li>2.4 — предельная мощность одной выхлопной камеры и число выхлопов.</li>
 * </ol>
 *
 * <p><b>Чего пока нет.</b> Разделы 2.5 (предварительное число ступеней и коррекция диаметров),
 * 3.1–3.4 (детальный расчёт решёток и закрутка последней ступени) и 4.1–4.3 (прочность) в этот
 * каркас не входят. API {@link PreliminaryResult} для них не меняется: они добавятся отдельными
 * методами и записями, а не полями «на будущее».
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
    private final HsDiagram hs;

    private TurbineDesign(Builder builder) {
        this.input = builder.input;
        this.assumptions = builder.assumptions;
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
