package org.kpush88.Thermal_2.turbine3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Предварительный расчёт турбины: разделы 2.1–2.4 методички.
 *
 * <p>Класс без состояния: {@link #calculate} возвращает {@link PreliminaryResult}, а все
 * промежуточные величины собраны в записях результата для проверки.
 */
public final class PreliminaryCalculator {

    private PreliminaryCalculator() {
    }

    /** Выполняет предварительный расчёт 2.1–2.4. */
    public static PreliminaryResult calculate(DesignInput input, DesignAssumptions assumptions,
                                              HsDiagram hs) {
        PreliminaryResult.EnergyEstimate energy = energyEstimate(input, assumptions, hs);
        PreliminaryResult.RegulatingStage regulating = regulatingStage(input, assumptions, hs, energy);
        List<String> notes = new ArrayList<>();
        PreliminaryResult.Expansion expansion = expansion(input, assumptions, hs, energy,
                regulating, notes);
        PreliminaryResult.PowerLimit powerLimit = powerLimit(input, assumptions, energy, expansion,
                notes);
        return new PreliminaryResult(energy, regulating, expansion, powerLimit, notes);
    }

    // --- 2.1. Экономическая мощность, давления, перепад и расход ---

    private static PreliminaryResult.EnergyEstimate energyEstimate(DesignInput input,
                                                                   DesignAssumptions assumptions,
                                                                   HsDiagram hs) {
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
                    + availableDrop + " кДж/кг");
        }
        double v0Prime = hs.volumePH(p0Prime, h0Prime);
        double economicPower = assumptions.economicPowerFactor() * input.nominalPowerMW() * 1000.0;
        double flow = CourseProjectEfficiency.flow(economicPower, availableDrop,
                assumptions.electricalEfficiency());
        return new PreliminaryResult.EnergyEstimate(economicPower, p0, p0Prime,
                input.exhaustPressureMPa(), pkPrime, h0Prime, s0Prime, v0Prime, h2s,
                availableDrop, flow);
    }

    // --- 2.2. Регулирующая ступень ---

    private static PreliminaryResult.RegulatingStage regulatingStage(
            DesignInput input, DesignAssumptions assumptions, HsDiagram hs,
            PreliminaryResult.EnergyEstimate energy) {
        double heatDrop = assumptions.regulatingHeatDropKJkg();
        if (!(heatDrop < energy.availableHeatDropKJkg())) {
            throw new IllegalArgumentException("теплоперепад регулирующей ступени должен быть "
                    + "меньше располагаемого перепада турбины");
        }
        double fictiveVelocity = Math.sqrt(2.0 * heatDrop * 1000.0);
        double circumferential = fictiveVelocity * assumptions.regulatingSpeedRatio();
        double meanDiameter = 60.0 * circumferential / (Math.PI * input.rotationRpm());
        if (!(meanDiameter > 0.0)) {
            throw new IllegalArgumentException("средний диаметр регулирующей ступени не "
                    + "положителен: " + meanDiameter + " м");
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
                    + " вышла из (0, 1]");
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

    private static PreliminaryResult.Expansion expansion(
            DesignInput input, DesignAssumptions assumptions, HsDiagram hs,
            PreliminaryResult.EnergyEstimate energy,
            PreliminaryResult.RegulatingStage regulating, List<String> notes) {
        double flow = energy.estimatedFlowKgS();
        double p0 = energy.inletPressureAtNozzlesMPa();
        double h0 = energy.inletEnthalpyKJkg();
        double v0 = energy.inletVolumeM3kg();

        double p2rs = hs.pressureForIsentropicDrop(p0, h0, regulating.heatDropKJkg());
        CourseProjectEfficiency.RegulatingEstimate regulatingEstimate =
                switch (input.regulatingKind()) {
                    case TWO_ROW -> CourseProjectEfficiency.regulatingDoubleRow(
                            flow, p0, v0, regulating.heatDropKJkg());
                    case ONE_ROW -> CourseProjectEfficiency.regulatingSingleRow(
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
            throw new IllegalArgumentException("располагаемый перепад ЧНД не положителен");
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
                    "перепад ЧНД по P_к′ (%.2f кДж/кг) отличается от контрольного (%.2f кДж/кг) "
                            + "больше чем на 1 кДж/кг; использован перепад по давлению конденсатора",
                    chndDrop, controlChnd));
        }
        if (Math.abs(refinedFlow - flow) > 0.05 * flow) {
            notes.add(String.format(Locale.ROOT,
                    "уточнённый расход G = %.3f кг/с отличается от предварительного G′ = %.3f кг/с "
                            + "больше чем на 5%%",
                    refinedFlow, flow));
        }
        return new PreliminaryResult.Expansion(regulatingEstimate, chvd, chsd, chnd,
                p2rs, h2rs, v2rs, pChvd, vChvd, pChsd, vChsd, vChnd, vChndActual,
                internalWork, refinedFlow, turbineEfficiency);
    }

    // --- 2.4. Предельная мощность и число выхлопов ---

    private static PreliminaryResult.PowerLimit powerLimit(
            DesignInput input, DesignAssumptions assumptions,
            PreliminaryResult.EnergyEstimate energy,
            PreliminaryResult.Expansion expansion, List<String> notes) {
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
                    + limitPower);
        }
        int exhaustCount = Math.max(1, (int) Math.ceil(input.nominalPowerMW() / limitPower));
        if (exhaustCount > 1) {
            notes.add(String.format(Locale.ROOT,
                    "предельная мощность одной выхлопной камеры %.2f МВт меньше номинальной "
                            + "%.2f МВт: требуется выхлопов — %d",
                    limitPower, input.nominalPowerMW(), exhaustCount));
        }
        return new PreliminaryResult.PowerLimit(exitVelocity,
                expansion.exhaustActualVolumeM3kg(), limitPower, exhaustCount);
    }
}
