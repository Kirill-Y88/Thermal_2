package org.kpush88.Thermal_2.turbine2;

import java.util.ArrayList;
import java.util.List;

/**
 * Предварительное разбиение отсека на ступени по разделу 2.5 методички.
 *
 * <p><b>Что считается.</b> Для каждого отсека выбираются диаметры первой и последней ступеней,
 * по упрощённому уравнению неразрывности находится высота рабочей лопатки последней ступени,
 * затем число ступеней и теплоперепады. Диаметры, характеристическое число {@code U/Ca} и
 * степень реактивности меняются по длине отсека линейно — это и есть «закон изменения», о
 * котором говорит раздел 2.5.1.
 *
 * <p><b>Почему коэффициент 12.3.</b> В методичке теплоперепад ступени записан как
 * {@code h₀ = 12.3·d²/(U/Ca)²}. Число 12.3 получается из {@code (π·n/60)²/(2·1000)} при
 * {@code n = 3000 об/мин}. Здесь оно масштабируется как {@code 12.3·(n/3000)²}, чтобы расчёт
 * оставался размерным при другой частоте вращения.
 *
 * <p><b>Коэффициент {@code K₀}.</b> Методичка принимает {@code K₀ = 1} только для первой
 * нерегулируемой ступени ЧВД; все остальные ступени, включая первые ступени ЧСД и ЧНД, идут с
 * {@code K₀ = 0.96}. Это не «поправка на отсек», а поправка на то, что ступень стоит после
 * предыдущей ступени, а не сразу за регулирующей.
 *
 * <p><b>Возврат тепла.</b> Сначала число ступеней оценивается при {@code q = 0.02}, затем
 * {@code q = K_t·(1−η_oi)·h₀_отсека·(z−1)/z}. Сумма теплоперепадов корректируется на
 * {@code Δ = (Σh₀_цели − Σh₀)/z}, чтобы точно выйти на перепад отсека с возвратом тепла.
 *
 * <p>Класс пакетный: снаружи используется только {@link TurbineDesign#preliminaryStages()}.
 */
final class StageLayoutCalculator {

    /** Коэффициент теплоперепада при 3000 об/мин из формулы 2.5. */
    private static final double SPEED_FACTOR_AT_3000 = 12.3;

    /** Частота, для которой записан коэффициент 12.3. */
    private static final double REFERENCE_RPM = 3000.0;

    /** Начальное значение возврата тепла по методичке (не 0.02 %, а 2 %). */
    private static final double INITIAL_RECOVERY = 0.02;

    /** Степень реактивности в корневом сечении последней ступени. */
    private static final double ROOT_REACTION = 0.05;

    /** Коэффициент для ступеней, кроме первой нерегулируемой ступени ЧВД. */
    private static final double K0 = 0.96;

    private StageLayoutCalculator() {
    }

    /**
     * Считает проточную часть всех трёх отсеков.
     *
     * @param input       исходные данные
     * @param assumptions допущения 2.5
     * @param hs          hs-диаграмма
     * @param preliminary результат 2.1–2.4
     * @return разбиение по ступеням
     */
    static PreliminaryStages calculate(DesignInput input, StageAssumptions assumptions,
                                       HsDiagram hs, PreliminaryResult preliminary) {
        PreliminaryResult.Expansion expansion = preliminary.expansion();
        double flow = expansion.refinedFlowKgS();
        double speedFactor = SPEED_FACTOR_AT_3000
                * Math.pow(input.rotationRpm() / REFERENCE_RPM, 2.0);
        double turbineEfficiency = expansion.turbineEfficiency();

        SectionState chvdIn = new SectionState(
                expansion.regulatingStageOutletPressureMPa(),
                expansion.regulatingStageOutletEnthalpyKJkg());
        SectionState chvdOut = new SectionState(expansion.chvdOutletPressureMPa(),
                chvdIn.enthalpyKJkg() - expansion.chvd().internalWork());
        SectionState chsdIn = chvdOut;
        SectionState chsdOut = new SectionState(expansion.chsdOutletPressureMPa(),
                chsdIn.enthalpyKJkg() - expansion.chsd().internalWork());
        SectionState chndIn = chsdOut;
        SectionState chndOut = new SectionState(
                preliminary.energy().exhaustPressureWithLossMPa(),
                chndIn.enthalpyKJkg() - expansion.chnd().internalWork());

        PreliminaryStages.SectionLayout chvd = section("ЧВД", assumptions.chvd(),
                expansion.chvd(), chvdIn, chvdOut, expansion.chvdOutletVolumeM3kg(),
                true, flow, 1, input.rotationRpm(), speedFactor, turbineEfficiency,
                expansion.exhaustVolumeM3kg(), preliminary.powerLimit().exitVelocityMS(), hs);
        PreliminaryStages.SectionLayout chsd = section("ЧСД", assumptions.chsd(),
                expansion.chsd(), chsdIn, chsdOut, expansion.chsdOutletVolumeM3kg(),
                false, flow, 1, input.rotationRpm(), speedFactor, turbineEfficiency,
                expansion.exhaustVolumeM3kg(), preliminary.powerLimit().exitVelocityMS(), hs);
        PreliminaryStages.SectionLayout chnd = section("ЧНД", assumptions.chnd(),
                expansion.chnd(), chndIn, chndOut, expansion.exhaustVolumeM3kg(),
                false, flow, preliminary.powerLimit().exhaustCount(), input.rotationRpm(),
                speedFactor, turbineEfficiency,
                expansion.exhaustVolumeM3kg(), preliminary.powerLimit().exitVelocityMS(), hs);

        int totalStages = chvd.stageCount() + chsd.stageCount() + chnd.stageCount();
        double totalHeatDrop = chvd.totalHeatDropKJkg() + chsd.totalHeatDropKJkg()
                + chnd.totalHeatDropKJkg();
        double internalWork = chvd.internalWorkKJkg() + chsd.internalWorkKJkg()
                + chnd.internalWorkKJkg();
        double recovery = chvd.recoveryHeatKJkg() + chsd.recoveryHeatKJkg()
                + chnd.recoveryHeatKJkg();
        return new PreliminaryStages(chvd, chsd, chnd, totalStages, totalHeatDrop,
                internalWork, flow * internalWork, recovery);
    }

    /** Состояние на входе или выходе отсека. */

    /**
     * Считает один отсек: геометрию последней ступени, число ступеней и теплоперепады.
     */
    private static PreliminaryStages.SectionLayout section(String name,
                                                           StageAssumptions.SectionAssumptions assumptions,
                                                           CourseProjectEfficiency.SectionEstimate estimate,
                                                           SectionState inlet,
                                                           SectionState outlet,
                                                           double outletIsentropicVolumeM3kg,
                                                           boolean firstSection,
                                                           double flowKgS,
                                                           int exhaustCount,
                                                           double rpm,
                                                           double speedFactor,
                                                           double turbineEfficiency,
                                                           double exhaustIsentropicVolumeM3kg,
                                                           double exhaustVelocityMS,
                                                           HsDiagram hs) {
        double h0Section = estimate.heatDrop();
        double d1 = assumptions.firstMeanDiameterM();
        double l2First = assumptions.firstBladeHeightM();
        double rootFirst = d1 - l2First;
        if (!(rootFirst > 0.0)) {
            throw new IllegalArgumentException("корневой диаметр первой ступени " + name
                    + " не положителен: " + rootFirst);
        }
        double e1 = assumptions.firstAdmission();
        double ez = assumptions.lastAdmission();

        double h01Raw = speedFactor * d1 * d1
                / (assumptions.firstSpeedRatio() * assumptions.firstSpeedRatio());
        double nozzleHeatDrop = (1.0 - assumptions.firstReaction()) * h01Raw;
        double p21 = hs.pressureForIsentropicDrop(inlet.pressureMPa(),
                inlet.enthalpyKJkg(), h01Raw);
        double v21 = hs.volumePH(p21, inlet.enthalpyKJkg() - h01Raw);

        double dz;
        double l2Last;
        double rootLast;
        if (assumptions.lastDiameterToHeightRatio() > 0.0) {
            double geometryFlow = flowKgS / Math.max(1, exhaustCount);
            dz = geometryFlow * exhaustIsentropicVolumeM3kg
                    * assumptions.lastDiameterToHeightRatio()
                    / (Math.PI * exhaustVelocityMS);
            dz = Math.max(dz, assumptions.lastMeanDiameterHintM());
            l2Last = dz / assumptions.lastDiameterToHeightRatio();
            rootLast = dz - l2Last;
        } else {
            double continuity = d1 * l2First * e1 * outletIsentropicVolumeM3kg
                    / (v21 * ez);
            l2Last = (-rootFirst + Math.sqrt(rootFirst * rootFirst + 4.0 * continuity))
                    / 2.0;
            dz = rootFirst + l2Last;
            if (dz < assumptions.lastMeanDiameterHintM()) {
                dz = assumptions.lastMeanDiameterHintM();
                l2Last = dz - rootFirst;
            }
            rootLast = rootFirst;
        }
        if (!(rootLast > 0.0) || !(l2Last > 0.0)) {
            throw new IllegalArgumentException("геометрия последней ступени " + name
                    + " получилась нефизичной: d = " + dz + " м, l₂ = " + l2Last + " м");
        }

        double lastSpeedRatio = assumptions.lastSpeedRatio();
        int stages = estimateStageCount(assumptions.stageCountHint(), d1, dz,
                assumptions.firstSpeedRatio(), lastSpeedRatio, h0Section, speedFactor);
        double rawMean = 0.0;
        for (int index = 0; index < stages; index++) {
            double fraction = fraction(index, stages);
            double diameter = d1 + (dz - d1) * fraction;
            double ratio = assumptions.firstSpeedRatio()
                    + (lastSpeedRatio - assumptions.firstSpeedRatio()) * fraction;
            rawMean += speedFactor * diameter * diameter / (ratio * ratio);
        }
        rawMean /= stages;

        double recoveryCoefficient = assumptions.recoveryFactor() * (1.0 - turbineEfficiency)
                * h0Section * (stages - 1) / stages;
        double targetHeatDrop = h0Section * (1.0 + recoveryCoefficient);
        return buildSection(name, assumptions, estimate, inlet, outlet, d1, l2First,
                rootFirst, dz, l2Last, rootLast, stages, rawMean, targetHeatDrop,
                firstSection, rpm, speedFactor);
    }


    /**
     * Оценивает число ступеней по среднему теплоперепаду. Начальное приближение — подсказка
     * прототипа; после первого прохода подсказка заменяется расчётным числом, чтобы не
     * «подгонять» результат под прототип.
     */
    private static int estimateStageCount(int hint, double d1, double dz,
                                          double firstRatio, double lastRatio,
                                          double h0Section, double speedFactor) {
        int stages = hint > 0 ? hint : 6;
        for (int iteration = 0; iteration < 6; iteration++) {
            double rawMean = 0.0;
            for (int index = 0; index < stages; index++) {
                double fraction = fraction(index, stages);
                double diameter = d1 + (dz - d1) * fraction;
                double ratio = firstRatio + (lastRatio - firstRatio) * fraction;
                rawMean += speedFactor * diameter * diameter / (ratio * ratio);
            }
            rawMean /= stages;
            int estimate = (int) Math.round(h0Section * (1.0 + INITIAL_RECOVERY) / rawMean);
            estimate = Math.max(1, estimate);
            if (estimate == stages) {
                return stages;
            }
            stages = estimate;
        }
        return stages;
    }

    /** Доля расстояния от первой ступени до последней: 0 у первой, 1 у последней. */
    private static double fraction(int index, int stages) {
        return stages <= 1 ? 0.0 : (double) index / (stages - 1);
    }

    /**
     * Собирает ступени отсека: распределяет теплоперепад, диаметры, реактивность и давления.
     */
    private static PreliminaryStages.SectionLayout buildSection(String name,
                                                                 StageAssumptions.SectionAssumptions assumptions,
                                                                 CourseProjectEfficiency.SectionEstimate estimate,
                                                                 SectionState inlet,
                                                                 SectionState outlet,
                                                                 double d1,
                                                                 double l2First,
                                                                 double rootFirst,
                                                                 double dz,
                                                                 double l2Last,
                                                                 double rootLast,
                                                                 int stages,
                                                                 double rawMean,
                                                                 double targetHeatDrop,
                                                                 boolean firstSection,
                                                                 double rpm,
                                                                 double speedFactor) {
        List<PreliminaryStages.StageLayout> stageList = new ArrayList<>(stages);
        double sumRaw = 0.0;
        for (int index = 0; index < stages; index++) {
            double fraction = fraction(index, stages);
            double diameter = d1 + (dz - d1) * fraction;
            double ratio = assumptions.firstSpeedRatio()
                    + (assumptions.lastSpeedRatio() - assumptions.firstSpeedRatio()) * fraction;
            double k0 = firstSection && index == 0 ? 1.0 : K0;
            sumRaw += k0 * speedFactor * diameter * diameter / (ratio * ratio);
        }
        double correction = (targetHeatDrop - sumRaw) / stages;
        double cumulative = 0.0;
        for (int index = 0; index < stages; index++) {
            double fraction = fraction(index, stages);
            double diameter = d1 + (dz - d1) * fraction;
            double ratio = assumptions.firstSpeedRatio()
                    + (assumptions.lastSpeedRatio() - assumptions.firstSpeedRatio()) * fraction;
            double reaction = assumptions.firstReaction()
                    + (assumptions.lastReaction() - assumptions.firstReaction()) * fraction;
            double admission = assumptions.firstAdmission()
                    + (assumptions.lastAdmission() - assumptions.firstAdmission()) * fraction;
            double k0 = firstSection && index == 0 ? 1.0 : K0;
            double heatDrop = k0 * speedFactor * diameter * diameter / (ratio * ratio)
                    + correction;
            if (!(heatDrop > 0.0) || !Double.isFinite(heatDrop)) {
                throw new IllegalArgumentException("теплоперепад ступени " + (index + 1)
                        + " отсека " + name + " не положителен: " + heatDrop);
            }
            cumulative += heatDrop;
            double root = rootFirst + (rootLast - rootFirst) * fraction;
            double bladeHeight = diameter - root;
            double nozzleHeight = bladeHeight - assumptions.axialClearanceM();
            if (!(bladeHeight > 0.0) || !(nozzleHeight > 0.0)) {
                throw new IllegalArgumentException("высота лопатки ступени " + (index + 1)
                        + " отсека " + name + " не положительна: l₂ = " + bladeHeight
                        + " м, l₁ = " + nozzleHeight + " м");
            }
            double pressure = inlet.pressureMPa() * Math.pow(
                    outlet.pressureMPa() / inlet.pressureMPa(), cumulative / targetHeatDrop);
            if (index == stages - 1) {
                pressure = outlet.pressureMPa();
            }
            double circumferential = Math.PI * diameter * rpm / 60.0;
            stageList.add(new PreliminaryStages.StageLayout(index + 1,
                    name + "-" + (index + 1), firstSection && index == 0, diameter, root,
                    root + 2.0 * bladeHeight, nozzleHeight, bladeHeight, heatDrop,
                    reaction, circumferential, ratio, admission, pressure));
        }
        double recoveryHeat = targetHeatDrop - estimate.heatDrop();
        return new PreliminaryStages.SectionLayout(name, stages, targetHeatDrop, rawMean,
                recoveryHeat, recoveryHeat / estimate.heatDrop(), d1, dz, l2First,
                l2Last, rootLast, inlet.pressureMPa(), outlet.pressureMPa(),
                inlet.enthalpyKJkg(), outlet.enthalpyKJkg(), estimate.internalWork(),
                estimate.efficiency(), stageList);
    }

    private record SectionState(double pressureMPa, double enthalpyKJkg) {

        SectionState {
            if (!(pressureMPa > 0.0) || !Double.isFinite(pressureMPa)) {
                throw new IllegalArgumentException("давление состояния не положительно: "
                        + pressureMPa);
            }
            if (!(enthalpyKJkg > 0.0) || !Double.isFinite(enthalpyKJkg)) {
                throw new IllegalArgumentException("энтальпия состояния не положительна: "
                        + enthalpyKJkg);
            }
        }
    }
}

