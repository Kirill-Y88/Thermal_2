package org.kpush88.Thermal_2.turbine3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Предварительный расчёт турбины: разделы 2.1–2.4 методички {@code university/Курс турб.docx}.
 *
 * <p>Класс без состояния: {@link #calculate} возвращает {@link PreliminaryResult}, а все
 * промежуточные величины собраны в записях результата для проверки. Сами формулы вынесены в
 * {@link CourseProjectFormulas}; здесь остаются только связи между этапами, обращение к
 * h-s диаграмме и построение графа процесса расширения. КПД отсеков и регулирующей ступени
 * рассчитываются через {@link CourseProjectEfficiency} (разд. 2.3).
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
        double p0Prime = CourseProjectFormulas.valvePressure(p0,
                assumptions.valvePressureFactor());
        double pkPrime = CourseProjectFormulas.exhaustPressureWithLoss(
                input.exhaustPressureMPa(), assumptions.exhaustVelocityMS(),
                assumptions.exhaustLossCoefficient());
        if (!(pkPrime < p0Prime)) {
            throw new IllegalArgumentException("после клапанов давление P₀′ = " + p0Prime
                    + " МПа должно быть выше P_к′ = " + pkPrime + " МПа");
        }
        Point inletState = hs.pointPT(p0Prime, input.inletTemperatureC());
        double h0Prime = inletState.enthalpyKJkg();
        double s0Prime = inletState.entropyKJkgK();
        double h2s = hs.enthalpyPS(pkPrime, s0Prime);
        double availableDrop = CourseProjectFormulas.availableHeatDrop(h0Prime, h2s);
        double economicPower = CourseProjectFormulas.economicPower(
                input.nominalPowerMW() * 1000.0, assumptions.economicPowerFactor());
        double flow = CourseProjectFormulas.estimatedFlow(economicPower, availableDrop,
                assumptions.electricalEfficiency());
        FlowPoint inlet = FlowPoint.actual(inletState);
        inlet.setMassFlow(flow);
        FlowPoint isentropicExhaust = FlowPoint.isentropic(hs.pointPH(pkPrime, h2s));
        isentropicExhaust.setMassFlow(flow);
        return new PreliminaryResult.EnergyEstimate(inlet, isentropicExhaust, economicPower, p0,
                input.exhaustPressureMPa(), pkPrime);
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
        double fictiveVelocity = CourseProjectFormulas.fictiveVelocity(heatDrop);
        double circumferential = CourseProjectFormulas.circumferentialVelocity(
                fictiveVelocity, assumptions.regulatingSpeedRatio());
        double meanDiameter = CourseProjectFormulas.regulatingMeanDiameter(
                circumferential, input.rotationRpm());
        double nozzleHeatDrop = CourseProjectFormulas.nozzleHeatDrop(
                heatDrop, assumptions.regulatingReaction());
        double nozzleVelocity = CourseProjectFormulas.nozzleTheoreticalVelocity(nozzleHeatDrop);
        double nozzlePressure = hs.pressureForIsentropicDrop(
                energy.inletPressureAtNozzlesMPa(), energy.inletEnthalpyKJkg(), nozzleHeatDrop);
        double nozzleVolume = hs.volumePH(nozzlePressure,
                energy.inletEnthalpyKJkg() - nozzleHeatDrop);
        double nozzleArea = CourseProjectFormulas.nozzleArea(
                energy.estimatedFlowKgS(), nozzleVolume,
                assumptions.nozzleFlowCoefficient(), nozzleVelocity);
        double admissionLength = CourseProjectFormulas.admissionLength(
                nozzleArea, meanDiameter, assumptions.regulatingNozzleAngleDeg());
        double admission = CourseProjectFormulas.admission(admissionLength,
                assumptions.admissionCoefficient());
        double nozzleHeight = CourseProjectFormulas.nozzleHeight(admissionLength, admission);
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

        double p2rs = hs.pressureForIsentropicDrop(p0, h0, regulating.heatDropKJkg());
        CourseProjectEfficiency.RegulatingEstimate regulatingEstimate =
                switch (input.regulatingKind()) {
                    case TWO_ROW -> CourseProjectEfficiency.regulatingDoubleRow(
                            flow, p0, energy.inletVolumeM3kg(), regulating.heatDropKJkg());
                    case ONE_ROW -> CourseProjectEfficiency.regulatingSingleRow(
                            flow, p0, energy.inletVolumeM3kg(), regulating.heatDropKJkg());
                };
        double regulatingWork = CourseProjectFormulas.regulatingInternalWork(
                regulating.heatDropKJkg(), regulatingEstimate.efficiency());
        double h2rs = h0 - regulatingWork;
        FlowPoint rsOutlet = FlowPoint.actual(hs.pointPH(p2rs, h2rs));

        double chvdDrop = input.sectionHeatDrops().chvdKJkg();
        double pChvd = hs.pressureForIsentropicDrop(p2rs, h2rs, chvdDrop);
        FlowPoint chvdIsentropic = FlowPoint.isentropic(hs.pointPH(pChvd, h2rs - chvdDrop));
        CourseProjectEfficiency.SectionEstimate chvd = CourseProjectEfficiency.section(
                CourseProjectEfficiency.Section.CHVD, flow, chvdDrop,
                CourseProjectFormulas.meanVolume(rsOutlet.getPoint().volumeM3kg(),
                        chvdIsentropic.getPoint().volumeM3kg()));
        double chvdWork = CourseProjectFormulas.sectionInternalWork(chvdDrop,
                chvd.efficiency());
        double hChvd = h2rs - chvdWork;
        FlowPoint chvdOutlet = FlowPoint.actual(hs.pointPH(pChvd, hChvd));

        double chsdDrop = input.sectionHeatDrops().chsdKJkg();
        double pChsd = hs.pressureForIsentropicDrop(pChvd, hChvd, chsdDrop);
        double hChsdIs = hChvd - chsdDrop;
        FlowPoint chsdIsentropic = FlowPoint.isentropic(hs.pointPH(pChsd, hChsdIs));
        HsDiagram.HeatDropSplit chsdSplit = hs.splitDrop(pChvd, hChvd, pChsd);
        double chsdWetness = CourseProjectFormulas.wetnessFactor(
                chvdOutlet.getPoint().wetness(), chsdIsentropic.getPoint().wetness(),
                chsdSplit.wet(), chsdDrop);
        CourseProjectEfficiency.SectionEstimate chsd = CourseProjectEfficiency.section(
                CourseProjectEfficiency.Section.CHSD, flow, chsdDrop,
                CourseProjectFormulas.meanVolume(chvdOutlet.getPoint().volumeM3kg(),
                        chsdIsentropic.getPoint().volumeM3kg()), chsdWetness, 0.0);
        double chsdWork = CourseProjectFormulas.sectionInternalWork(chsdDrop,
                chsd.efficiency());
        double hChsd = hChvd - chsdWork;
        FlowPoint chsdOutlet = FlowPoint.actual(hs.pointPH(pChsd, hChsd));

        double pk = energy.exhaustPressureWithLossMPa();
        double chndDrop = hs.isentropicDrop(pChsd, hChsd, pk);
        if (!(chndDrop > 0.0)) {
            throw new IllegalArgumentException("располагаемый перепад ЧНД не положителен");
        }
        double hChndIs = hChsd - chndDrop;
        FlowPoint chndIsentropic = FlowPoint.isentropic(hs.pointPH(pk, hChndIs));
        HsDiagram.HeatDropSplit chndSplit = hs.splitDrop(pChsd, hChsd, pk);
        double chndWetness = CourseProjectFormulas.wetnessFactor(
                chsdOutlet.getPoint().wetness(), chndIsentropic.getPoint().wetness(),
                chndSplit.wet(), chndDrop);
        double exitLoss = CourseProjectFormulas.exitVelocityLoss(
                assumptions.limitPowerExitHeatDropKJkg(), chndDrop);
        CourseProjectEfficiency.SectionEstimate chnd = CourseProjectEfficiency.section(
                CourseProjectEfficiency.Section.CHND, flow, chndDrop,
                CourseProjectFormulas.meanVolume(chsdOutlet.getPoint().volumeM3kg(),
                        chndIsentropic.getPoint().volumeM3kg()), chndWetness, exitLoss);
        double chndWork = CourseProjectFormulas.sectionInternalWork(chndDrop,
                chnd.efficiency());
        FlowPoint exhaust = FlowPoint.actual(hs.pointPH(pk, hChsd - chndWork));

        FlowPoint regulatingStageIsentropic =
                FlowPoint.isentropic(hs.pointPH(p2rs, h0 - regulating.heatDropKJkg()));
        FlowPoint exhaustIsentropic = energy.isentropicExhaust();
        ProcessFlow processFlow = new ProcessFlow();
        processFlow.appendActual(energy.inlet());
        processFlow.appendActual(rsOutlet);
        processFlow.appendActual(chvdOutlet);
        processFlow.appendActual(chsdOutlet);
        processFlow.appendActual(exhaust);
        processFlow.linkIsentropic(energy.inlet(), regulatingStageIsentropic);
        processFlow.linkIsentropic(regulatingStageIsentropic, exhaustIsentropic);
        processFlow.linkIsentropic(rsOutlet, chvdIsentropic);
        processFlow.linkIsentropic(chvdOutlet, chsdIsentropic);
        processFlow.linkIsentropic(chsdOutlet, chndIsentropic);
        for (FlowPoint point : List.of(energy.inlet(), rsOutlet, chvdOutlet, chsdOutlet, exhaust,
                regulatingStageIsentropic, chvdIsentropic, chsdIsentropic, chndIsentropic,
                exhaustIsentropic)) {
            point.setMassFlow(flow);
        }
        ExpansionProcess process = new ExpansionProcess(processFlow,
                new ExpansionProcess.Nodes(energy.inlet(), rsOutlet, chvdOutlet, chsdOutlet,
                        exhaust, regulatingStageIsentropic, chvdIsentropic, chsdIsentropic,
                        chndIsentropic, exhaustIsentropic));

        double internalWork = CourseProjectFormulas.totalInternalWork(
                regulatingWork, chvdWork, chsdWork, chndWork);
        double turbineEfficiency = CourseProjectFormulas.turbineEfficiency(internalWork,
                energy.availableHeatDropKJkg());
        double refinedFlow = CourseProjectFormulas.refinedFlow(energy.economicPowerKW(),
                internalWork, assumptions.mechanicalEfficiency(),
                assumptions.generatorEfficiency());

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
        return new PreliminaryResult.Expansion(process, regulatingEstimate, chvd, chsd, chnd,
                internalWork, refinedFlow, turbineEfficiency);
    }

    // --- 2.4. Предельная мощность и число выхлопов ---

    private static PreliminaryResult.PowerLimit powerLimit(
            DesignInput input, DesignAssumptions assumptions,
            PreliminaryResult.EnergyEstimate energy,
            PreliminaryResult.Expansion expansion, List<String> notes) {
        double exitVelocity = CourseProjectFormulas.exitVelocity(
                assumptions.limitPowerExitHeatDropKJkg());
        double limitPower = CourseProjectFormulas.limitPower(
                assumptions.limitPowerExtractionFactor(),
                assumptions.limitPowerTaperFactor(),
                expansion.turbineEfficiency(),
                assumptions.allowableBladeStressMPa(),
                exitVelocity,
                energy.availableHeatDropKJkg(),
                assumptions.bladeDensityKgM3(),
                input.rotationRpm(),
                expansion.exhaustActualVolumeM3kg());
        int exhaustCount = CourseProjectFormulas.exhaustCount(
                input.nominalPowerMW(), limitPower);
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
