package org.kpush88.Thermal_2.turbine2;

/**
 * Прочностные расчёты по разделам 4.1–4.3 методички {@code university/Курс турб.docx}.
 *
 * <p>4.1 — осевое усилие по первой нерегулируемой ступени с распространением на все ступени;
 * 4.2 — растяжение и изгиб лопатки последней ступени; 4.3 — напряжение и прогиб диафрагмы
 * первой нерегулируемой ступени. Класс не хранит состояния.
 */
final class StrengthCalculator {

    private StrengthCalculator() {
    }

    /**
     * Выполняет все три прочностных расчёта.
     *
     * @param first       первая нерегулируемая ступень
     * @param last        последняя ступень
     * @param flowKgS     расход пара, кг/с
     * @param assumptions прочностные допущения
     * @param stageCount  число нерегулируемых ступеней
     * @return результаты 4.1–4.3
     */
    static StrengthResults calculate(StageDetail first, StageDetail last,
                                     double flowKgS, StageAssumptions assumptions,
                                     int stageCount) {
        StrengthResults.AxialForce axial = axialForce(first, flowKgS, assumptions,
                stageCount);
        StrengthResults.BladeStrength blade = blade(last, flowKgS, assumptions);
        StrengthResults.DiaphragmStrength diaphragm = diaphragm(first, assumptions);
        return new StrengthResults(axial, blade, diaphragm, stageCount);
    }

    /** 4.1: осевое усилие на лопатки, диск и ротор. */
    private static StrengthResults.AxialForce axialForce(StageDetail stage,
                                                         double flowKgS,
                                                         StageAssumptions assumptions,
                                                         int stageCount) {
        StageAssumptions.StrengthAssumptions strength = assumptions.strength();
        StageAssumptions.GeometryAssumptions geometry = assumptions.geometry();
        double alpha1 = Math.toRadians(stage.nozzleExitAngleDeg());
        double alpha2 = Math.toRadians(stage.rotorExitAbsoluteAngleDeg());
        double pressure1 = stage.nozzlePressureMPa();
        double pressure2 = stage.rotorPressureMPa();
        double pressureDifference = pressure1 - pressure2;
        if (!(pressureDifference > 0.0)) {
            throw new IllegalArgumentException("давление за соплом " + pressure1
                    + " МПа должно быть выше давления за рабочей решёткой " + pressure2);
        }
        double bladeForce = flowKgS * (stage.nozzleActualVelocityMS() * Math.sin(alpha1)
                - stage.rotorExitAbsoluteVelocityMS() * Math.sin(alpha2))
                + Math.PI * stage.meanDiameterM() * stage.bladeHeightM()
                * pressureDifference * 1.0e6;
        double rootDiameter = stage.meanDiameterM() - stage.bladeHeightM();
        double sealDiameter = strength.rotorSealDiameterM();
        double reliefArea = Math.PI * strength.reliefHoleDiameterM()
                * strength.reliefHoleDiameterM() / 4.0 * strength.reliefHoleCount();
        double clearanceArea = Math.PI * rootDiameter * strength.discClearanceM();
        if (!(reliefArea > 0.0) || !(clearanceArea > 0.0)) {
            throw new IllegalArgumentException("площади разгрузки и корневого зазора должны "
                    + "быть положительными; проверьте допущения прочности");
        }
        double alpha = strength.reliefFlowCoefficient() * reliefArea
                / (strength.discFlowCoefficient() * clearanceArea);
        double beta = geometry.diaphragmLeakCoefficient()
                * geometry.diaphragmLeakAreaM2() * (1.0 - stage.reaction())
                / (strength.discFlowCoefficient() * clearanceArea
                * geometry.diaphragmRidges() * stage.reaction());
        double discPressureDifference = strength.discPressureFactor() * pressureDifference;
        double discForce = Math.PI / 4.0
                * (rootDiameter * rootDiameter - sealDiameter * sealDiameter)
                * discPressureDifference * 1.0e6;
        if (!(bladeForce > 0.0) || !(discForce > 0.0)) {
            throw new IllegalArgumentException("осевое усилие не положительно: R_aI = "
                    + bladeForce + " Н, R_aII = " + discForce + " Н");
        }
        double total = bladeForce + discForce;
        return new StrengthResults.AxialForce(bladeForce, discForce, total,
                total * stageCount, pressureDifference, reliefArea, clearanceArea,
                alpha, beta, strength.discPressureFactor());
    }

    /** 4.2: растяжение и изгиб рабочей лопатки последней ступени. */
    private static StrengthResults.BladeStrength blade(StageDetail stage,
                                                        double flowKgS,
                                                        StageAssumptions assumptions) {
        StageAssumptions.StrengthAssumptions strength = assumptions.strength();
        double theta = stage.meanDiameterM() / stage.bladeHeightM();
        double u = stage.circumferentialVelocityMS();
        double profileRatio = strength.bladeProfileAreaRatio();
        double tensile = 2.0 * strength.bladeDensityKgM3() * u * u / theta
                * (1.0 - (1.0 - profileRatio) * (3.0 * theta + 1.0) / (6.0 * theta))
                * 1.0e-6;
        if (!(tensile > 0.0)) {
            throw new IllegalArgumentException("напряжение растяжения не положительно: "
                    + tensile + " МПа");
        }
        double alpha1 = Math.toRadians(stage.nozzleExitAngleDeg());
        double alpha2 = Math.toRadians(stage.rotorExitAbsoluteAngleDeg());
        double bladesPerSector = stage.admission() * stage.rotorBladeCount();
        if (!(bladesPerSector > 0.0)) {
            throw new IllegalArgumentException("число лопаток в секторе не положительно");
        }
        double circumferential = flowKgS / bladesPerSector
                * (stage.nozzleActualVelocityMS() * Math.cos(alpha1)
                + stage.rotorExitAbsoluteVelocityMS() * Math.cos(alpha2));
        double pressureDifference = stage.nozzlePressureMPa() - stage.rotorPressureMPa();
        double axial = flowKgS / bladesPerSector
                * (stage.nozzleActualVelocityMS() * Math.sin(alpha1)
                - stage.rotorExitAbsoluteVelocityMS() * Math.sin(alpha2))
                + pressureDifference * 1.0e6 * stage.rotorPitchM() * stage.bladeHeightM();
        axial = Math.max(axial, 0.0);
        double bendingForce = Math.hypot(circumferential, axial);
        if (!(bendingForce > 0.0)) {
            throw new IllegalArgumentException("изгибающее усилие не положительно");
        }
        double bendingStress = bendingForce * stage.bladeHeightM() / 2.0
                / (strength.bladeSectionModulusM3() * 1.0e6);
        double totalStress = tensile + bendingStress;
        double safety = strength.bladeYieldStrengthMPa() / totalStress;
        return new StrengthResults.BladeStrength(tensile, circumferential, axial,
                bendingForce, bendingStress, totalStress,
                strength.bladeYieldStrengthMPa(), safety, stage.rotorBladeCount(),
                stage.rotorChordM(), stage.rotorPitchM());
    }

    /** 4.3: напряжение и прогиб диафрагмы первой нерегулируемой ступени. */
    private static StrengthResults.DiaphragmStrength diaphragm(StageDetail stage,
                                                                StageAssumptions assumptions) {
        StageAssumptions.StrengthAssumptions strength = assumptions.strength();
        double pressureDifference = stage.inletPressureMPa() - stage.nozzlePressureMPa();
        if (!(pressureDifference > 0.0)) {
            throw new IllegalArgumentException("перепад давления на диафрагме не "
                    + "положителен: " + pressureDifference + " МПа");
        }
        double geometryFactor = 0.1 * strength.diaphragmOuterDiameterM()
                / strength.diaphragmThicknessM();
        double stress = strength.diaphragmStressCoefficient() / 10.0
                * geometryFactor * geometryFactor * pressureDifference;
        double deflection = strength.diaphragmDeflectionCoefficient() * pressureDifference
                / strength.diaphragmElasticModulusMPa()
                * Math.pow(geometryFactor, 3.0) * strength.diaphragmOuterDiameterM();
        if (!(stress > 0.0) || !(deflection > 0.0)) {
            throw new IllegalArgumentException("расчёт диафрагмы дал неположительный "
                    + "результат: σ = " + stress + " МПа, Δ = " + deflection + " м");
        }
        double safety = strength.diaphragmYieldStrengthMPa() / stress;
        return new StrengthResults.DiaphragmStrength(pressureDifference, stress,
                deflection, strength.diaphragmYieldStrengthMPa(), safety,
                strength.diaphragmOuterDiameterM(), strength.diaphragmThicknessM());
    }
}
