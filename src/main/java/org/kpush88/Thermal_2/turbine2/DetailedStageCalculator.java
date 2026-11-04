package org.kpush88.Thermal_2.turbine2;

import java.util.ArrayList;
import java.util.List;

/**
 * Детальный расчёт проточной части по разделам 3.1–3.4 методички
 * {@code university/Курс турб.docx}.
 *
 * <p>Класс не хранит состояния. Давления за решётками находятся по
 * {@link HsDiagram#pressureForIsentropicDrop(double, double, double)}, а удельные объёмы — по
 * {@link HsDiagram#volumePH(double, double)}; это позволяет считать любой вариант, а не только
 * опорный, для которого нарисована hs-диаграмма.
 *
 * <p><b>Соглашение о среднем диаметре.</b> В методичке {@code d_ср} — диаметр на середине
 * рабочей лопатки, поэтому корневой диаметр равен {@code d_ср − l₂}, а периферийный —
 * {@code d_ср + l₂}. Это же соглашение используется в {@link PreliminaryStages.StageLayout}.
 *
 * <p>Класс package-private: наружу результаты выдаются методами {@link TurbineDesign}.
 */
final class DetailedStageCalculator {

    /** Показатель изоэнтропы для перегретого пара по методичке. */
    private static final double K = 1.3;

    /** Доля теплоперепада каждого из трёх последующих венцов регулирующей ступени. */
    private static final double REGULATING_ROW_REACTION = 0.02;

    /** Минимально допустимая парциальность регулирующей ступени. */
    private static final double MIN_ADMISSION = 0.3;

    private DetailedStageCalculator() {
    }

    /**
     * Раздел 3.1: детальный расчёт регулирующей ступени.
     *
     * @param preliminary результат 2.1–2.4
     * @param assumptions допущения решёток
     * @param hs          свойства пара
     * @return треугольники скоростей, потери и КПД
     */
    static RegulatingStageDetail regulatingStage(PreliminaryResult preliminary,
                                                  StageAssumptions assumptions,
                                                  HsDiagram hs) {
        PreliminaryResult.RegulatingStage stage = preliminary.regulatingStage();
        PreliminaryResult.EnergyEstimate energy = preliminary.energy();
        StageAssumptions.GeometryAssumptions geometry = assumptions.geometry();
        double flow = preliminary.expansion().refinedFlowKgS();

        double h0 = stage.heatDropKJkg();
        double diameter = stage.meanDiameterM();
        double u = stage.circumferentialVelocityMS();
        double rho = stage.reaction();
        double h0Nozzle = (1.0 - rho) * h0;
        double h0FirstRotor = REGULATING_ROW_REACTION * h0;
        double h0Turning = REGULATING_ROW_REACTION * h0;
        double h0SecondRotor = REGULATING_ROW_REACTION * h0;
        double p0 = energy.inletPressureAtNozzlesMPa();
        double h0Inlet = energy.inletEnthalpyKJkg();
        double alphaNozzle = Math.toRadians(geometry.nozzleAngleDeg());

        // --- Сопловая решётка ---
        double p1 = hs.pressureForIsentropicDrop(p0, h0Inlet, h0Nozzle);
        double v1 = hs.volumePH(p1, h0Inlet - h0Nozzle);
        double c1t = Math.sqrt(2.0 * h0Nozzle * 1000.0);
        double a1 = soundSpeed(p1, v1);
        double m1 = c1t / a1;

        double chord1 = geometry.nozzleChordM();
        double mu1 = geometry.nozzleFlowCoefficient();
        double area1 = flow * v1 / (mu1 * c1t);
        double admissionLength = area1 / (Math.PI * diameter * Math.sin(alphaNozzle));
        double admissionOptimal = 0.3 * Math.sqrt(admissionLength * 100.0);
        double admission = Math.max(MIN_ADMISSION, admissionOptimal);
        if (!(admission <= 1.0)) {
            throw new IllegalArgumentException("парциальность регулирующей ступени вышла из "
                    + "(0, 1]: e_opt = " + admissionOptimal);
        }
        double nozzleHeight = admissionLength / admission;
        for (int iteration = 0; iteration < 2; iteration++) {
            double ratio = chord1 / nozzleHeight;
            mu1 = clamp(0.982 - 0.005 * ratio, 0.90, 0.99);
            area1 = flow * v1 / (mu1 * c1t);
            nozzleHeight = area1 / (Math.PI * diameter * admission
                    * Math.sin(alphaNozzle));
        }
        double phi1 = clamp(0.98 - 0.009 * (chord1 / nozzleHeight), 0.85, 0.99);
        double c1 = phi1 * c1t;
        double nozzlePitch = geometry.nozzlePitchRatio() * chord1;
        int nozzleCount = bladeCount(Math.PI * diameter * admission / nozzlePitch);
        nozzlePitch = Math.PI * diameter * admission / nozzleCount;
        double nozzleLoss = c1t * c1t / 2000.0 * (1.0 - phi1 * phi1);
        double w1 = triangleSide(c1, u, alphaNozzle);
        double beta1 = Math.asin(clamp(c1 * Math.sin(alphaNozzle) / w1, -1.0, 1.0));
        RegulatingStageDetail.NozzleRow nozzle = new RegulatingStageDetail.NozzleRow(
                c1t, c1, geometry.nozzleAngleDeg(), m1, p1, v1, area1, nozzleHeight,
                chord1, nozzlePitch, nozzleCount, nozzleLoss, mu1, phi1);

        // --- Рабочая решётка первого венца ---
        double p2 = hs.pressureForIsentropicDrop(p1, h0Inlet - h0Nozzle, h0FirstRotor);
        double v2 = hs.volumePH(p2, h0Inlet - h0Nozzle - h0FirstRotor);
        double w2t = Math.sqrt(2.0 * h0FirstRotor * 1000.0 + w1 * w1);
        double a2 = soundSpeed(p2, v2);
        double m2 = w2t / a2;
        double bladeHeight1 = nozzleHeight + axialClearance(assumptions);
        double chord2 = geometry.rotorChordM();
        double ratio2 = chord2 / bladeHeight1;
        double mu2 = clamp(0.982 - 0.005 * ratio2, 0.90, 0.99);
        double psi2 = clamp(0.957 - 0.011 * ratio2, 0.85, 0.99);
        double area2 = flow * v2 / (mu2 * w2t);
        double sinBeta2 = clamp(area2 / (Math.PI * diameter * admission * bladeHeight1),
                -1.0, 1.0);
        double beta2 = Math.asin(sinBeta2);
        double w2 = psi2 * w2t;
        double c2 = triangleSide(w2, u, beta2);
        double alpha2 = exitAngle(w2, u, beta2);
        double rotorLoss1 = w2t * w2t / 2000.0 * (1.0 - psi2 * psi2);
        double rotorPitch1 = geometry.rotorPitchRatio() * chord2;
        int rotorCount1 = bladeCount(Math.PI * diameter / rotorPitch1);
        rotorPitch1 = Math.PI * diameter / rotorCount1;
        RegulatingStageDetail.RotorRow firstRotor = new RegulatingStageDetail.RotorRow(
                h0FirstRotor, w1, Math.toDegrees(beta1), w2t, w2, Math.toDegrees(beta2),
                c2, Math.toDegrees(alpha2), m2, p2, v2, area2, bladeHeight1, chord2,
                rotorPitch1, rotorCount1, rotorLoss1, mu2, psi2);

        // --- Поворотная решётка ---
        double pTurning = hs.pressureForIsentropicDrop(p2,
                h0Inlet - h0Nozzle - h0FirstRotor, h0Turning);
        double vTurning = hs.volumePH(pTurning,
                h0Inlet - h0Nozzle - h0FirstRotor - h0Turning);
        double cTurningT = Math.sqrt(2.0 * h0Turning * 1000.0);
        double aTurning = soundSpeed(pTurning, vTurning);
        double mTurning = cTurningT / aTurning;
        double bladeHeightTurning = bladeHeight1 + axialClearance(assumptions);
        double chordTurning = geometry.turningChordM();
        double ratioTurning = chordTurning / bladeHeightTurning;
        double muTurning = clamp(0.982 - 0.005 * ratioTurning, 0.90, 0.99);
        double phiTurning = clamp(0.98 - 0.009 * ratioTurning, 0.85, 0.99);
        double areaTurning = flow * vTurning / (muTurning * cTurningT);
        double sinAlphaTurning = areaTurning
                / (Math.PI * diameter * admission * bladeHeightTurning);
        if (!(sinAlphaTurning > 0.0 && sinAlphaTurning < 1.0)) {
            throw new IllegalArgumentException("угол поворотной решётки не существует: "
                    + "sin α = " + sinAlphaTurning + "; проверьте высоту и перепад");
        }
        double alphaTurning = Math.asin(sinAlphaTurning);
        double cTurning = phiTurning * cTurningT;
        double wTurning = triangleSide(cTurning, u, alphaTurning);
        double betaTurning = Math.asin(clamp(
                cTurning * Math.sin(alphaTurning) / wTurning, -1.0, 1.0));
        double turningLoss = cTurningT * cTurningT / 2000.0
                * (1.0 - phiTurning * phiTurning);
        double turningPitch = geometry.turningPitchRatio() * chordTurning;
        int turningCount = bladeCount(Math.PI * diameter * admission / turningPitch);
        turningPitch = Math.PI * diameter * admission / turningCount;
        RegulatingStageDetail.TurningRow turning = new RegulatingStageDetail.TurningRow(
                h0Turning, wTurning, Math.toDegrees(betaTurning), cTurningT, cTurning,
                Math.toDegrees(alphaTurning), mTurning, pTurning, vTurning, areaTurning,
                bladeHeightTurning, chordTurning, turningPitch, turningCount, turningLoss,
                muTurning, phiTurning);

        // --- Рабочая решётка второго венца ---
        double pSecond = hs.pressureForIsentropicDrop(pTurning,
                h0Inlet - h0Nozzle - h0FirstRotor - h0Turning, h0SecondRotor);
        double vSecond = hs.volumePH(pSecond, h0Inlet - h0Nozzle - h0FirstRotor
                - h0Turning - h0SecondRotor);
        double wSecondT = Math.sqrt(2.0 * h0SecondRotor * 1000.0
                + wTurning * wTurning);
        double aSecond = soundSpeed(pSecond, vSecond);
        double mSecond = wSecondT / aSecond;
        double bladeHeightSecond = bladeHeightTurning + axialClearance(assumptions);
        double chordSecond = geometry.secondRotorChordM();
        double ratioSecond = chordSecond / bladeHeightSecond;
        double muSecond = clamp(0.982 - 0.005 * ratioSecond, 0.90, 0.99);
        double psiSecond = clamp(0.957 - 0.011 * ratioSecond, 0.85, 0.99);
        double areaSecond = flow * vSecond / (muSecond * wSecondT);
        double sinBetaSecond = areaSecond
                / (Math.PI * diameter * admission * bladeHeightSecond);
        if (!(sinBetaSecond > 0.0 && sinBetaSecond < 1.0)) {
            throw new IllegalArgumentException("угол второго венца не существует: sin β₂ = "
                    + sinBetaSecond);
        }
        double betaSecond = Math.asin(sinBetaSecond);
        double wSecond = psiSecond * wSecondT;
        double cSecond = triangleSide(wSecond, u, betaSecond);
        double alphaSecond = exitAngle(wSecond, u, betaSecond);
        double rotorLoss2 = wSecondT * wSecondT / 2000.0
                * (1.0 - psiSecond * psiSecond);
        double rotorPitch2 = geometry.secondRotorPitchRatio() * chordSecond;
        int rotorCount2 = bladeCount(Math.PI * diameter / rotorPitch2);
        rotorPitch2 = Math.PI * diameter / rotorCount2;
        RegulatingStageDetail.RotorRow secondRotor = new RegulatingStageDetail.RotorRow(
                h0SecondRotor, wTurning, Math.toDegrees(betaTurning), wSecondT, wSecond,
                Math.toDegrees(betaSecond), cSecond, Math.toDegrees(alphaSecond), mSecond,
                pSecond, vSecond, areaSecond, bladeHeightSecond, chordSecond,
                rotorPitch2, rotorCount2, rotorLoss2, muSecond, psiSecond);

        // --- Потери, КПД и мощность ---
        double exitLoss = cSecond * cSecond / 2000.0;
        double availableEnergy = h0; // для регулирующей ступени χ = 0
        double profileEfficiency = (availableEnergy - nozzleLoss - rotorLoss1
                - turningLoss - rotorLoss2 - exitLoss) / availableEnergy;
        double cFictive = Math.sqrt(2.0 * h0 * 1000.0);
        double speedRatio = u / cFictive;
        double friction = geometry.frictionCoefficient() * diameter * diameter / area1
                * Math.pow(speedRatio, 3.0) * availableEnergy;
        double partial = geometry.bucketLossCoefficient() * 2.0 / Math.sin(alphaNozzle)
                * (1.0 - admission) / admission * Math.pow(speedRatio, 3.0)
                + 4.0 * geometry.segmentLossCoefficient()
                * (chord2 * bladeHeight1 + 0.6 * chordSecond * bladeHeightSecond)
                / area1 * speedRatio * profileEfficiency;
        partial *= availableEnergy;
        double tipDiameter = diameter + bladeHeightSecond;
        double sealCoefficient = Math.PI * tipDiameter * equivalentClearance(geometry) / area1
                * (rho + 1.8 * nozzleHeight / tipDiameter * profileEfficiency);
        double sealLoss = sealCoefficient * availableEnergy;
        double totalLoss = nozzleLoss + rotorLoss1 + turningLoss + rotorLoss2 + exitLoss
                + friction + partial + sealLoss;
        double usedHeatDrop = h0 - totalLoss;
        if (!(usedHeatDrop > 0.0)) {
            throw new IllegalArgumentException("потери регулирующей ступени превысили "
                    + "располагаемый перепад: h_i = " + usedHeatDrop + " кДж/кг");
        }
        double internalEfficiency = usedHeatDrop / availableEnergy;
        double power = flow * usedHeatDrop;

        return new RegulatingStageDetail(h0, diameter, u, admission, h0Nozzle,
                h0FirstRotor, h0Turning, h0SecondRotor, cSecond, exitLoss,
                availableEnergy, profileEfficiency, friction, partial, sealLoss,
                usedHeatDrop, internalEfficiency, power, nozzle, firstRotor, turning,
                secondRotor);
    }

    /**
     * Раздел 3.2: детальный расчёт первой нерегулируемой ступени ЧВД.
     *
     * @param preliminary результат 2.1–2.4
     * @param stages      разбиение 2.5
     * @param assumptions допущения
     * @param hs          свойства пара
     * @return треугольники скоростей, потери и КПД
     */
    static StageDetail firstNonRegulated(PreliminaryResult preliminary,
                                         PreliminaryStages stages,
                                         StageAssumptions assumptions,
                                         HsDiagram hs) {
        PreliminaryStages.StageLayout stage = stages.chvd().stages().get(0);
        double p0 = preliminary.expansion().regulatingStageOutletPressureMPa();
        double h0 = preliminary.expansion().regulatingStageOutletEnthalpyKJkg();
        return nonRegulatedStage(stage, 1, 1, p0, h0, 0.0, 0.0,
                preliminary.expansion().refinedFlowKgS(), false, false, assumptions, hs);
    }

    /**
     * Раздел 3.3: детальный расчёт последних трёх ступеней ЧНД.
     *
     * <p>Ступени считаются последовательно: выходная скорость предыдущей ступени становится
     * входной скоростью следующей ({@code C₀ = C₂}), а использованный теплоперепад вычитается
     * из энтальпии. Поэтому и {@code h₀*}, и влажность на входе следующей ступени получаются
     * из фактического, а не предварительного процесса. Первая ступень ЧНД стартует с
     * {@code C₀ = 0}: вход отсека в 2.5 задан статическими параметрами.
     *
     * @param preliminary результат 2.1–2.4
     * @param stages      разбиение 2.5
     * @param assumptions допущения
     * @param hs          свойства пара
     * @return три последние ступени в порядке прохождения пара
     */
    static List<StageDetail> lastStages(PreliminaryResult preliminary,
                                        PreliminaryStages stages,
                                        StageAssumptions assumptions,
                                        HsDiagram hs) {
        PreliminaryStages.SectionLayout chnd = stages.chnd();
        int offset = stages.chvd().stageCount() + stages.chsd().stageCount();
        double flow = preliminary.expansion().refinedFlowKgS();
        double p0 = chnd.inletPressureMPa();
        double h0 = chnd.inletEnthalpyKJkg();
        // В 2.5 вход ЧНД задан статическими параметрами, поэтому первая ступень отсека
        // стартует с C₀ = 0; далее выходная скорость переносится внутри ЧНД.
        double c0 = 0.0;
        double chi0 = assumptions.geometry().exitVelocityRecovery();
        List<StageDetail> all = new ArrayList<>(chnd.stageCount());
        for (int index = 0; index < chnd.stageCount(); index++) {
            PreliminaryStages.StageLayout stage = chnd.stages().get(index);
            boolean last = index == chnd.stageCount() - 1;
            StageDetail detail = nonRegulatedStage(stage, offset + index + 1,
                    index + 1, p0, h0, c0, chi0, flow, true, last, assumptions, hs);
            all.add(detail);
            // Статическая энтальпия на выходе: вход + изменение кинетической энергии − h_i.
            p0 = detail.rotorPressureMPa();
            h0 = h0 + c0 * c0 / 2000.0 - detail.exitVelocityMS() * detail.exitVelocityMS() / 2000.0
                    - detail.usedHeatDropKJkg();
            c0 = detail.rotorExitAbsoluteVelocityMS();
        }
        if (all.size() < 3) {
            throw new IllegalArgumentException("для раздела 3.3 нужно не меньше трёх "
                    + "ступеней ЧНД, а их " + all.size());
        }
        return List.copyOf(all.subList(all.size() - 3, all.size()));
    }

    private static StageDetail nonRegulatedStage(PreliminaryStages.StageLayout stage,
                                                  int globalNumber,
                                                  int sectionNumber,
                                                  double p0,
                                                  double h0,
                                                  double c0,
                                                  double chi0,
                                                  double flow,
                                                  boolean deriveNozzleAngle,
                                                  boolean last,
                                                  StageAssumptions assumptions,
                                                  HsDiagram hs) {
        StageAssumptions.GeometryAssumptions geometry = assumptions.geometry();
        double h0Stage = stage.heatDropKJkg();
        // Кинетическая энергия на входе переходит в располагаемый перепад от параметров
        // торможения: h₀* = h₀ + χ₀·C₀²/2000. Для первой ступени отсека C₀ = 0.
        double kinetic = c0 * c0 / 2000.0;
        double h0Star = h0Stage + chi0 * kinetic;
        double inletStagnationEnthalpy = h0 + kinetic;
        double diameter = stage.meanDiameterM();
        double u = stage.circumferentialSpeedMS();
        double rho = stage.reaction();
        double admission = stage.admission();
        double h0Nozzle = (1.0 - rho) * h0Star;
        double h0Rotor = rho * h0Star;
        double fictive = Math.sqrt(2.0 * h0Star * 1000.0);
        double speedRatio = u / fictive;
        // Статическое состояние на входе задано p0 и h0; h₀* относится к торможению.
        double inletVolume = hs.volumePH(p0, h0);
        double inletWetness = wetness(hs, p0, h0);

        double p1 = hs.pressureForIsentropicDrop(p0, inletStagnationEnthalpy, h0Nozzle);
        double v1 = hs.volumePH(p1, inletStagnationEnthalpy - h0Nozzle);
        double c1t = Math.sqrt(2.0 * h0Nozzle * 1000.0);
        double a1 = soundSpeed(p1, v1);
        double m1 = c1t / a1;

        double chord1 = geometry.nozzleChordM();
        double nozzleHeight = stage.nozzleHeightM();
        double mu1 = geometry.nozzleFlowCoefficient();
        double area1 = flow * v1 / (mu1 * c1t);
        double alpha1;
        if (deriveNozzleAngle) {
            double sinAlpha = area1 / (Math.PI * diameter * admission * nozzleHeight);
            if (!(sinAlpha > 0.0 && sinAlpha < 1.0)) {
                throw new IllegalArgumentException("угол сопловой решётки ступени "
                        + stage.name() + " не существует: sin α = " + sinAlpha);
            }
            alpha1 = Math.asin(sinAlpha);
        } else {
            alpha1 = Math.toRadians(geometry.nozzleAngleDeg());
        }
        double ratio1 = chord1 / nozzleHeight;
        mu1 = clamp(0.982 - 0.005 * ratio1, 0.90, 0.99);
        area1 = flow * v1 / (mu1 * c1t);
        double phi1 = clamp(0.98 - 0.009 * ratio1, 0.85, 0.99);
        double c1 = phi1 * c1t;
        if (deriveNozzleAngle) {
            double sinAlpha = area1 / (Math.PI * diameter * admission * nozzleHeight);
            if (!(sinAlpha > 0.0 && sinAlpha < 1.0)) {
                throw new IllegalArgumentException("угол сопловой решётки ступени "
                        + stage.name() + " не существует: sin α = " + sinAlpha);
            }
            alpha1 = Math.asin(sinAlpha);
        }
        double nozzlePitch = geometry.nozzlePitchRatio() * chord1;
        int nozzleCount = bladeCount(Math.PI * diameter * admission / nozzlePitch);
        nozzlePitch = Math.PI * diameter * admission / nozzleCount;
        double nozzleLoss = c1t * c1t / 2000.0 * (1.0 - phi1 * phi1);
        double w1 = triangleSide(c1, u, alpha1);
        double beta1 = Math.asin(clamp(c1 * Math.sin(alpha1) / w1, -1.0, 1.0));

        double p2 = hs.pressureForIsentropicDrop(p1,
                inletStagnationEnthalpy - h0Nozzle, h0Rotor);
        double v2 = hs.volumePH(p2, inletStagnationEnthalpy - h0Nozzle - h0Rotor);
        double w2t = Math.sqrt(2.0 * h0Rotor * 1000.0 + w1 * w1);
        double a2 = soundSpeed(p2, v2);
        double m2 = w2t / a2;
        double bladeHeight = stage.bladeHeightM();
        double chord2 = geometry.rotorChordM();
        double ratio2 = chord2 / bladeHeight;
        double mu2 = clamp(0.982 - 0.005 * ratio2, 0.90, 0.99);
        double psi2 = clamp(0.957 - 0.011 * ratio2, 0.85, 0.99);
        double area2 = flow * v2 / (mu2 * w2t);
        double sinBeta2 = area2 / (Math.PI * diameter * admission * bladeHeight);
        if (!(sinBeta2 > 0.0 && sinBeta2 <= 1.0)) {
            throw new IllegalArgumentException("угол рабочей решётки ступени " + stage.name()
                    + " не существует: sin β₂ = " + sinBeta2);
        }
        double beta2 = Math.asin(sinBeta2);
        double w2 = psi2 * w2t;
        double c2 = triangleSide(w2, u, beta2);
        double alpha2 = exitAngle(w2, u, beta2);
        double rotorLoss = w2t * w2t / 2000.0 * (1.0 - psi2 * psi2);
        double rotorPitch = geometry.rotorPitchRatio() * chord2;
        int rotorCount = bladeCount(Math.PI * diameter / rotorPitch);
        rotorPitch = Math.PI * diameter / rotorCount;

        double exitLoss = c2 * c2 / 2000.0;
        double chi = last ? 0.0 : geometry.exitVelocityRecovery();
        double availableEnergy = h0Star - chi * exitLoss;
        double profileEfficiency = (h0Star - nozzleLoss - rotorLoss - exitLoss)
                / availableEnergy;
        double friction = geometry.frictionCoefficient() * diameter * diameter / area1
                * Math.pow(speedRatio, 3.0) * availableEnergy;
        double partial = 0.0;
        if (admission < 1.0) {
            partial = geometry.bucketLossCoefficient() / Math.sin(alpha1)
                    * (1.0 - admission) / admission * Math.pow(speedRatio, 3.0)
                    + geometry.segmentLossCoefficient() * chord2 * bladeHeight / area1
                    * speedRatio * profileEfficiency;
            partial *= availableEnergy;
        }
        double tipDiameter = diameter + bladeHeight;
        double equivalentClearance = equivalentClearance(geometry);
        double bladeLeakCoefficient = Math.PI * tipDiameter * equivalentClearance / area1
                * (rho + 1.8 * nozzleHeight / tipDiameter * profileEfficiency);
        double bladeLeakLoss = bladeLeakCoefficient * availableEnergy;
        double diaphragmLeakCoefficient = geometry.diaphragmLeakCoefficient()
                * geometry.diaphragmLeakAreaM2()
                / (mu1 * area1 * Math.sqrt(geometry.diaphragmRidges()))
                * profileEfficiency;
        double diaphragmLeakLoss = diaphragmLeakCoefficient * availableEnergy;
        double outletWetness = wetness(hs, p2, h0 - h0Stage);
        double wetnessCoefficient = 2.0 * speedRatio
                * (0.9 * inletWetness + 0.35 * Math.max(0.0, outletWetness - inletWetness));
        double wetnessLoss = wetnessCoefficient * availableEnergy;
        double totalLoss = nozzleLoss + rotorLoss + exitLoss + friction + partial
                + bladeLeakLoss + diaphragmLeakLoss + wetnessLoss;
        double usedHeatDrop = h0Star - totalLoss;
        if (!(usedHeatDrop > 0.0)) {
            throw new IllegalArgumentException("потери ступени " + stage.name()
                    + " превысили её перепад: h_i = " + usedHeatDrop + " кДж/кг");
        }
        double internalEfficiency = usedHeatDrop / availableEnergy;
        if (!(internalEfficiency < 1.0)) {
            throw new IllegalArgumentException("внутренний КПД ступени " + stage.name()
                    + " вышел из (0, 1): " + internalEfficiency);
        }
        double power = flow * usedHeatDrop;

        return new StageDetail(globalNumber, sectionNumber, stage.name(), diameter,
                stage.rootDiameterM(), bladeHeight, nozzleHeight, h0Stage, h0Star, c0,
                rho, admission, speedRatio, u, fictive, p0, h0, inletVolume, inletWetness,
                outletWetness,
                h0Nozzle, h0Rotor, c1t, c1, Math.toDegrees(alpha1), m1, mu1, phi1, p1, v1,
                area1, nozzleLoss, chord1, nozzlePitch, geometry.nozzlePitchRatio(),
                nozzleCount, w2t, w2, w1, Math.toDegrees(beta1), Math.toDegrees(beta2), c2,
                Math.toDegrees(alpha2), m2, mu2, psi2, p2, v2, area2, rotorLoss, chord2,
                rotorPitch, geometry.rotorPitchRatio(), rotorCount, c2, exitLoss,
                availableEnergy, profileEfficiency, friction, partial, bladeLeakLoss,
                diaphragmLeakLoss, wetnessLoss, totalLoss, usedHeatDrop, internalEfficiency,
                power);
    }

    /**
     * Раздел 3.4: закрутка последней ступени по трём радиальным участкам.
     *
     * <p>Закон закрутки — постоянство угла выхода из сопловой решётки. Расход по участку
     * находится по площади кольца {@code 2πrΔr}, поэтому суммарный расход может отличаться от
     * расчётного; угол {@code α₁} корректируется так, чтобы сумма точно равнялась {@code G}.
     * Для сверхзвуковых участков используется критическое сечение.
     *
     * @param last        детальный расчёт последней ступени
     * @param assumptions допущения
     * @param hs          свойства пара
     * @param rotationRpm частота вращения, об/мин
     * @return три сечения и суммарные показатели
     */
    static TwistResult twist(StageDetail last, StageAssumptions assumptions,
                             HsDiagram hs, double rotationRpm) {
        StageAssumptions.GeometryAssumptions geometry = assumptions.geometry();
        double h0 = last.stagnationHeatDropKJkg();
        double rMean = last.meanDiameterM() / 2.0;
        // Высота делится на три равных участка; их середины отстоят от среднего радиуса
        // на l₂/3. В методичке напечатано Δr = l₂/2, но при l₂ = 0.35 м это дало бы 0.175 м,
        // тогда как в её же таблице стоит 0.117 м = l₂/3.
        double dr = last.bladeHeightM() / 3.0;
        double[] radii = {rMean - dr, rMean, rMean + dr};
        double alphaInitial = Math.toRadians(last.nozzleExitAngleDeg());
        double mu1 = geometry.nozzleFlowCoefficient();
        double mu2 = geometry.rotorFlowCoefficient();
        double phi = geometry.nozzleSpeedCoefficient();
        double psi = geometry.rotorSpeedCoefficient();
        double designFlow = last.internalPowerKW() / last.usedHeatDropKJkg();
        double[] reaction = new double[3];
        double[] nozzleDrop = new double[3];
        double[] rotorDrop = new double[3];
        double[] nozzlePressure = new double[3];
        double[] nozzleVolume = new double[3];
        double[] nozzleTheoretical = new double[3];
        double[] nozzleMach = new double[3];
        double[] baseFlow = new double[3];
        double[] criticalVelocity = new double[3];
        double[] criticalVolume = new double[3];
        double baseSum = 0.0;
        for (int index = 0; index < 3; index++) {
            reaction[index] = clamp(1.0 - (1.0 - last.reaction())
                    * Math.pow(rMean / radii[index], 2.0), 0.0, 0.95);
            nozzleDrop[index] = (1.0 - reaction[index]) * h0;
            rotorDrop[index] = reaction[index] * h0;
            nozzlePressure[index] = hs.pressureForIsentropicDrop(
                    last.inletPressureMPa(), last.inletEnthalpyKJkg(), nozzleDrop[index]);
            nozzleVolume[index] = hs.volumePH(nozzlePressure[index],
                    last.inletEnthalpyKJkg() - nozzleDrop[index]);
            nozzleTheoretical[index] = Math.sqrt(2.0 * nozzleDrop[index] * 1000.0);
            nozzleMach[index] = nozzleTheoretical[index]
                    / soundSpeed(nozzlePressure[index], nozzleVolume[index]);
            if (nozzleMach[index] < 1.0) {
                baseFlow[index] = 2.0 * Math.PI * radii[index] * dr * mu1
                        * nozzleTheoretical[index] * Math.sin(alphaInitial)
                        / nozzleVolume[index];
                criticalVelocity[index] = Double.NaN;
                criticalVolume[index] = Double.NaN;
            } else {
                double criticalPressure = nozzlePressure[index]
                        * Math.pow(2.0 / (K + 1.0), K / (K - 1.0));
                criticalVolume[index] = hs.volumePH(criticalPressure,
                        last.inletEnthalpyKJkg() - nozzleDrop[index]);
                criticalVelocity[index] = Math.sqrt(
                        K * criticalPressure * criticalVolume[index] * 1.0e6);
                baseFlow[index] = 2.0 * Math.PI * radii[index] * dr * mu1
                        * criticalVelocity[index] * Math.sin(alphaInitial)
                        / criticalVolume[index];
            }
            baseSum += baseFlow[index];
        }
        double sinEffective = Math.sin(alphaInitial) * designFlow / baseSum;
        if (!(sinEffective > 0.0 && sinEffective < 1.0)) {
            throw new IllegalArgumentException("не удаётся распределить расход по трём "
                    + "участкам: sin α₁ = " + sinEffective);
        }
        double alphaEffective = Math.asin(sinEffective);

        double[] flows = new double[3];
        double flowSum = 0.0;
        for (int index = 0; index < 3; index++) {
            flows[index] = baseFlow[index] * sinEffective / Math.sin(alphaInitial);
            flowSum += flows[index];
        }
        double flowCorrection = designFlow / flowSum;
        double flowTotal = 0.0;
        double powerTotal = 0.0;
        List<TwistResult.Section> sections = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            flows[index] *= flowCorrection;
            flowTotal += flows[index];
            double alphaSection = alphaEffective;
            if (nozzleMach[index] >= 1.0) {
                double sinOblique = Math.sin(alphaEffective) * criticalVelocity[index]
                        / nozzleTheoretical[index] * nozzleVolume[index]
                        / criticalVolume[index];
                alphaSection = Math.asin(clamp(sinOblique, 0.0, 0.999999));
            }
            double c1 = phi * nozzleTheoretical[index];
            double u = Math.PI * radii[index] * rotationRpm / 30.0;
            double w1 = triangleSide(c1, u, alphaSection);
            double beta1 = Math.asin(clamp(
                    c1 * Math.sin(alphaSection) / w1, -1.0, 1.0));
            double w2t = Math.sqrt(2.0 * rotorDrop[index] * 1000.0 + w1 * w1);
            double a2 = soundSpeed(last.rotorPressureMPa(), last.rotorVolumeM3kg());
            double m2 = w2t / a2;
            double sinBeta2 = flows[index] * last.rotorVolumeM3kg()
                    / (mu2 * 2.0 * Math.PI * radii[index] * dr * w2t);
            if (!(sinBeta2 > 0.0)) {
                throw new IllegalArgumentException("расход участка " + (index + 1)
                        + " не положителен");
            }
            double beta2 = Math.asin(clamp(sinBeta2, 0.0, 0.999999));
            double w2 = psi * w2t;
            double c2 = triangleSide(w2, u, beta2);
            double alpha2 = exitAngle(w2, u, beta2);
            double nozzleLoss = nozzleTheoretical[index] * nozzleTheoretical[index]
                    / 2000.0 * (1.0 - phi * phi);
            double rotorLoss = w2t * w2t / 2000.0 * (1.0 - psi * psi);
            double exitLoss = c2 * c2 / 2000.0;
            double eta = (h0 - nozzleLoss - rotorLoss - exitLoss) / h0;
            double power = flows[index] * u
                    * (w1 * Math.cos(beta1) + w2 * Math.cos(beta2)) / 1000.0;
            powerTotal += power;
            sections.add(new TwistResult.Section(index + 1, radii[index],
                    reaction[index], nozzleDrop[index], rotorDrop[index],
                    nozzleTheoretical[index], c1, nozzleMach[index],
                    nozzlePressure[index], nozzleVolume[index], flows[index], u,
                    Math.toDegrees(alphaSection), w1, Math.toDegrees(beta1), w2t, w2,
                    Math.toDegrees(beta2), c2, Math.toDegrees(alpha2), nozzleLoss,
                    rotorLoss, exitLoss, eta, power));
        }
        if (!(powerTotal > 0.0)) {
            throw new IllegalArgumentException("мощность закрутки не положительна");
        }
        return new TwistResult(last.globalNumber(), h0, last.meanDiameterM(),
                last.bladeHeightM(), Math.toDegrees(alphaEffective), flowTotal,
                designFlow, powerTotal, powerTotal / (designFlow * h0), sections);
    }

    static double soundSpeed(double pressureMPa, double volumeM3kg) {
        // В методичке давление в барах, поэтому там множитель 10⁵. Здесь давление в МПа,
        // то есть в 10 раз крупнее бара: 1 МПа = 10 бар, и множитель равен 10⁶.
        return Math.sqrt(K * pressureMPa * volumeM3kg * 1.0e6);
    }

    static double triangleSide(double first, double second, double angleRad) {
        double value = first * first + second * second
                - 2.0 * first * second * Math.cos(angleRad);
        return Math.sqrt(Math.max(value, 1.0e-12));
    }

    static double exitAngle(double relative, double u, double betaRad) {
        double tangent = relative * Math.sin(betaRad)
                / (relative * Math.cos(betaRad) - u);
        double angle = Math.atan(tangent);
        if (angle < 0.0) {
            angle += Math.PI;
        }
        return angle;
    }

    static int bladeCount(double raw) {
        if (!(raw > 0.0) || !Double.isFinite(raw)) {
            throw new IllegalArgumentException("число лопаток не положительно: " + raw);
        }
        return Math.max(1, (int) Math.round(raw));
    }

    static double axialClearance(StageAssumptions assumptions) {
        return assumptions.chvd().axialClearanceM();
    }

    static double equivalentClearance(StageAssumptions.GeometryAssumptions geometry) {
        double axial = geometry.sealAxialClearanceM() / 4.0;
        double radial = geometry.sealRadialClearanceM();
        // δ_экв = √((δ_a/4)² + 1.5·z·δ_r²). В методичке формула напечатана без знака
        // корня, но её же числовой пример 0.00034 м получается только с корнем.
        return Math.sqrt(axial * axial
                + 1.5 * geometry.sealRidges() * radial * radial);
    }

    static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static double wetness(HsDiagram hs, double pressureMPa, double enthalpyKJkg) {
        double quality = hs.vapourFractionPH(pressureMPa, enthalpyKJkg);
        return quality >= 1.0 ? 0.0 : Math.max(0.0, 1.0 - quality);
    }
}
