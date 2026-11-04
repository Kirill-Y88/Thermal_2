package org.kpush88.Thermal_2.turbine2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Закрутка 3.4 и прочностные расчёты 4.1–4.3 на опорном варианте.
 *
 * <p>Проверяются не только итоговые числа, но и балансы: сумма расходов участков равна расходу
 * ступени, {@code R_a = R_aI + R_aII}, {@code σ = σ_раст + σ_изг}.
 */
class TwistAndStrengthTest {

    private static final DesignInput.SectionHeatDrops DROPS =
            new DesignInput.SectionHeatDrops(339.19, 375.21, 347.35);

    private static final DesignInput INPUT = DesignInput.ofBar(8.0, 28.0, 430.0, 0.04,
            3000.0, CourseProjectEfficiency.RegulatingKind.DOUBLE_ROW, DROPS);

    private final TurbineDesign design = TurbineDesign.builder(INPUT).build();
    private final PreliminaryResult preliminary = design.preliminary();
    private final PreliminaryStages stages = design.preliminaryStages(preliminary);

    @Test
    @DisplayName("3.4: три сечения r = 0.583 / 0.700 / 0.817 м, сумма расходов = G")
    void twistsLastStage() {
        TwistResult twist = design.lastStageTwist(preliminary, stages);

        assertEquals(16, twist.stageNumber());
        assertEquals(3, twist.sections().size());
        assertEquals(0.5833, twist.sections().get(0).radiusM(), 1e-4);
        assertEquals(0.7000, twist.sections().get(1).radiusM(), 1e-9);
        assertEquals(0.8167, twist.sections().get(2).radiusM(), 1e-4);
        assertEquals(0.194, twist.sections().get(0).reaction(), 1e-3);
        assertEquals(0.440, twist.sections().get(1).reaction(), 1e-9);
        assertEquals(0.589, twist.sections().get(2).reaction(), 1e-3);

        assertEquals(6.9616, twist.totalFlowKgS(), 1e-4);
        assertEquals(6.9616, twist.designFlowKgS(), 1e-4);
        assertEquals(0.0, twist.flowDeviation(), 1e-12);
        assertEquals(642.92, twist.totalPowerKW(), 0.5);
        assertEquals(0.7762, twist.profileEfficiency(), 1e-4);
    }

    @Test
    @DisplayName("3.4: расход и мощность растут от корня к периферии, α₁ постоянен на дозвуке")
    void distributesFlowRadially() {
        TwistResult twist = design.lastStageTwist(preliminary, stages);

        for (int index = 1; index < twist.sections().size(); index++) {
            assertTrue(twist.sections().get(index).flowKgS()
                    > twist.sections().get(index - 1).flowKgS());
            assertTrue(twist.sections().get(index).circumferentialVelocityMS()
                    > twist.sections().get(index - 1).circumferentialVelocityMS());
            assertTrue(twist.sections().get(index).powerKW()
                    > twist.sections().get(index - 1).powerKW());
        }
        assertEquals(twist.sections().get(1).nozzleExitAngleDeg(),
                twist.sections().get(2).nozzleExitAngleDeg(), 1e-12,
                "на дозвуковых участках угол выхода постоянен по закону закрутки");
        double sum = twist.sections().stream()
                .mapToDouble(TwistResult.Section::flowKgS).sum();
        assertEquals(twist.totalFlowKgS(), sum, 1e-9);
    }

    @Test
    @DisplayName("4.1: R_aI = 672.4 Н, R_aII = 3731.4 Н, R_aΣ = 70.46 кН")
    void calculatesAxialForce() {
        StrengthResults.AxialForce axial = design.strength(preliminary, stages)
                .axialForce();

        assertEquals(672.4, axial.bladeForceN(), 0.5);
        assertEquals(3731.4, axial.discForceN(), 0.5);
        assertEquals(4403.8, axial.totalForceN(), 0.5);
        assertEquals(70461.0, axial.totalRotorForceN(), 10.0);
        assertEquals(0.0139, axial.pressureDifferenceMPa(), 1e-3);
        assertEquals(0.009817, axial.reliefAreaM2(), 1e-6);
        assertEquals(0.0109, axial.clearanceAreaM2(), 1e-4);
        assertEquals(1.206, axial.pressureRatioAlpha(), 1e-3);
        assertEquals(0.53, axial.pressureFactor(), 1e-12);

        assertEquals(axial.bladeForceN() + axial.discForceN(),
                axial.totalForceN(), 1e-9);
        assertEquals(axial.totalForceN() * stages.totalStages(),
                axial.totalRotorForceN(), 1e-9);
    }

    @Test
    @DisplayName("4.2: σ_раст = 109.62, σ_изг = 2.12, σ = 111.74 МПа, K = 3.40")
    void checksBladeStrength() {
        StrengthResults.BladeStrength blade = design.strength(preliminary, stages)
                .blade();

        assertEquals(109.62, blade.tensileStressMPa(), 0.02);
        assertEquals(2.12, blade.bendingStressMPa(), 0.03);
        assertEquals(111.74, blade.totalStressMPa(), 0.03);
        assertEquals(3.401, blade.safetyFactor(), 1e-3);
        assertEquals(380.0, blade.yieldStrengthMPa(), 1e-12);
        assertTrue(blade.safetyFactor() >= 1.65,
                "по методичке коэффициент запаса лопатки должен быть не меньше 1.65");
        assertEquals(blade.tensileStressMPa() + blade.bendingStressMPa(),
                blade.totalStressMPa(), 1e-9);
        assertEquals(blade.yieldStrengthMPa() / blade.totalStressMPa(),
                blade.safetyFactor(), 1e-12);
    }

    @Test
    @DisplayName("4.3: ΔP = 0.284 МПа, σ = 22.19 МПа, Δ = 0.000566 м")
    void checksDiaphragmStrength() {
        StrengthResults.DiaphragmStrength diaphragm = design.strength(preliminary,
                stages).diaphragm();

        assertEquals(0.284, diaphragm.pressureDifferenceMPa(), 1e-3);
        assertEquals(22.19, diaphragm.maximumStressMPa(), 0.02);
        assertEquals(0.000566, diaphragm.deflectionM(), 1e-6);
        assertEquals(16.22, diaphragm.safetyFactor(), 0.01);
        assertTrue(diaphragm.safetyFactor() >= 2.0,
                "по методичке коэффициент запаса диафрагмы должен быть не меньше 2");
        assertEquals(0.922, diaphragm.outerDiameterM(), 1e-12);
        assertEquals(0.1, diaphragm.thicknessM(), 1e-12);
    }

    @Test
    @DisplayName("Публичное API без аргументов совпадает с расчётом по готовым 2.1–2.5")
    void exposesConvenienceOverloads() {
        assertEquals(design.regulatingStageDetail(preliminary).internalPowerKW(),
                design.regulatingStageDetail().internalPowerKW(), 1e-9);
        assertEquals(design.firstStageDetail(preliminary, stages).internalPowerKW(),
                design.firstStageDetail().internalPowerKW(), 1e-9);
        assertEquals(design.lastStageDetails(preliminary, stages).get(2).internalPowerKW(),
                design.lastStageDetails().get(2).internalPowerKW(), 1e-9);
        assertEquals(design.lastStageTwist(preliminary, stages).totalPowerKW(),
                design.lastStageTwist().totalPowerKW(), 1e-9);
        assertEquals(design.strength(preliminary, stages).blade().totalStressMPa(),
                design.strength().blade().totalStressMPa(), 1e-9);
    }
}