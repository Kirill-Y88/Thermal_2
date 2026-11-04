package org.kpush88.Thermal_2.turbine2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Детальный расчёт 3.1–3.4 на опорном варианте методички.
 *
 * <p>Давления и объёмы берутся из IF97, поэтому числа отличаются от hs-диаграммы в последних
 * знаках. Для регулирующей ступени дополнительно проверяется исправленная формула поворотной
 * решётки: в методичке {@code √(2·3.5·10³)} напечатано как 240.9 м/с, правильное значение —
 * 83.67 м/с.
 */
class DetailedStageTest {

    private static final DesignInput.SectionHeatDrops DROPS =
            new DesignInput.SectionHeatDrops(339.19, 375.21, 347.35);

    private static final DesignInput INPUT = DesignInput.ofBar(8.0, 28.0, 430.0, 0.04,
            3000.0, CourseProjectEfficiency.RegulatingKind.DOUBLE_ROW, DROPS);

    private final TurbineDesign design = TurbineDesign.builder(INPUT).build();
    private final PreliminaryResult preliminary = design.preliminary();
    private final PreliminaryStages stages = design.preliminaryStages(preliminary);

    @Test
    @DisplayName("3.1: e = 0.3, C₁t = 573.6 м/с, W₁ = 387.8 м/с, C₁ = 537.7 м/с")
    void calculatesRegulatingStageNozzleAndFirstRotor() {
        RegulatingStageDetail detail = design.regulatingStageDetail(preliminary);

        assertEquals(0.979, detail.meanDiameterM(), 1e-3);
        assertEquals(153.82, detail.circumferentialVelocityMS(), 0.01);
        assertEquals(0.3, detail.admission(), 1e-12);
        assertEquals(164.5, detail.nozzleHeatDropKJkg(), 1e-9);
        assertEquals(573.59, detail.nozzle().theoreticalVelocityMS(), 0.01);
        assertEquals(537.72, detail.nozzle().actualVelocityMS(), 0.02);
        assertEquals(0.955, detail.nozzle().mach(), 1e-3);
        assertEquals(0.0128, detail.nozzle().heightM(), 1e-4);
        assertEquals(19, detail.nozzle().bladeCount());

        assertEquals(387.84, detail.firstRotor().inletVelocityMS(), 0.02);
        assertEquals(15.34, detail.firstRotor().inletAngleDeg(), 0.02);
        assertEquals(396.76, detail.firstRotor().theoreticalVelocityMS(), 0.02);
        assertEquals(372.5, detail.firstRotor().actualVelocityMS(), 0.1);
        assertEquals(9.31, detail.firstRotor().lossKJkg(), 0.01);
    }

    @Test
    @DisplayName("3.1: поворотная решётка считается по C₁t = √7000 = 83.67 м/с")
    void correctsTurningRowArithmetic() {
        RegulatingStageDetail detail = design.regulatingStageDetail(preliminary);

        assertEquals(83.67, detail.turning().theoreticalVelocityMS(), 1e-2);
        assertEquals(80.96, detail.turning().actualVelocityMS(), 0.02);
        assertEquals(3.5, detail.turningHeatDropKJkg(), 1e-12);
        assertTrue(detail.turning().exitAngleDeg() > 60.0,
                "при малом перепаде поворотная решётка должна иметь большой угол выхода");
        assertEquals(55, detail.turning().bladeCount());
    }

    @Test
    @DisplayName("3.1: η_ол = 0.812, η_oi = 0.592, h_i = 103.56 кДж/кг")
    void balancesRegulatingStageLosses() {
        RegulatingStageDetail detail = design.regulatingStageDetail(preliminary);
        double losses = detail.nozzle().lossKJkg() + detail.firstRotor().lossKJkg()
                + detail.turning().lossKJkg() + detail.secondRotor().lossKJkg()
                + detail.exitLossKJkg() + detail.frictionLossKJkg()
                + detail.partialLossKJkg() + detail.sealLeakLossKJkg();

        assertEquals(0.8122, detail.profileEfficiency(), 1e-4);
        assertEquals(0.5917, detail.internalEfficiency(), 1e-4);
        assertEquals(103.56, detail.usedHeatDropKJkg(), 0.03);
        assertEquals(720.9, detail.internalPowerKW(), 0.2);
        assertEquals(detail.heatDropKJkg() - losses, detail.usedHeatDropKJkg(), 1e-9);
        assertEquals(detail.usedHeatDropKJkg() / detail.availableEnergyKJkg(),
                detail.internalEfficiency(), 1e-12);
    }

    @Test
    @DisplayName("3.2: первая нерегулируемая ступень — C₁ = 322.6 м/с, W₂ = 192.2 м/с")
    void calculatesFirstNonRegulatedStage() {
        StageDetail detail = design.firstStageDetail(preliminary, stages);

        assertEquals(1, detail.globalNumber());
        assertEquals(1, detail.sectionNumber());
        assertEquals("ЧВД-1", detail.name());
        assertEquals(0.879, detail.meanDiameterM(), 1e-9);
        assertEquals(138.07, detail.circumferentialVelocityMS(), 0.01);
        assertEquals(344.22, detail.nozzleTheoreticalVelocityMS(), 0.01);
        assertEquals(322.61, detail.nozzleActualVelocityMS(), 0.01);
        assertEquals(11.0, detail.nozzleExitAngleDeg(), 1e-12);
        assertEquals(1.216, detail.nozzlePressureMPa(), 1e-3);
        assertEquals(0.00479, detail.nozzleAreaM2(), 1e-5);
        assertEquals(40, detail.nozzleBladeCount());

        assertEquals(188.92, detail.rotorInletVelocityMS(), 0.02);
        assertEquals(19.02, detail.rotorInletAngleDeg(), 0.02);
        assertEquals(204.76, detail.rotorTheoreticalVelocityMS(), 0.02);
        assertEquals(192.25, detail.rotorActualVelocityMS(), 0.02);
        assertEquals(69.25, detail.rotorExitAbsoluteVelocityMS(), 0.02);
        assertEquals(0.8384, detail.profileEfficiency(), 1e-4);
        assertEquals(0.7418, detail.internalEfficiency(), 1e-4);
    }

    @Test
    @DisplayName("3.2: E₀ = h₀ − Δh_вс, h_i = h₀ − все потери")
    void balancesFirstNonRegulatedStage() {
        StageDetail detail = design.firstStageDetail(preliminary, stages);
        double expectedEnergy = detail.heatDropKJkg() - detail.exitLossKJkg();
        double losses = detail.nozzleLossKJkg() + detail.rotorLossKJkg()
                + detail.exitLossKJkg() + detail.frictionLossKJkg()
                + detail.partialLossKJkg() + detail.bladeLeakLossKJkg()
                + detail.diaphragmLeakLossKJkg() + detail.wetnessLossKJkg();

        assertEquals(expectedEnergy, detail.availableEnergyKJkg(), 1e-9);
        assertEquals(59.96, detail.availableEnergyKJkg(), 0.02);
        assertEquals(44.48, detail.usedHeatDropKJkg(), 0.03);
        assertEquals(309.7, detail.internalPowerKW(), 0.2);
        assertEquals(detail.heatDropKJkg() - losses, detail.usedHeatDropKJkg(), 1e-9);
        assertEquals(0.0, detail.inletWetness(), 1e-12);
        assertEquals(0.0, detail.outletWetness(), 1e-12);
    }

    @Test
    @DisplayName("3.3: три последние ступени — №14, 15, 16 с растущей влажностью")
    void calculatesLastThreeStages() {
        List<StageDetail> last = design.lastStageDetails(preliminary, stages);

        assertEquals(3, last.size());
        assertEquals(14, last.get(0).globalNumber());
        assertEquals(15, last.get(1).globalNumber());
        assertEquals(16, last.get(2).globalNumber());
        assertEquals(80.43, last.get(0).heatDropKJkg(), 0.02);
        assertEquals(95.30, last.get(1).heatDropKJkg(), 0.02);
        assertEquals(110.72, last.get(2).heatDropKJkg(), 0.02);

        for (int index = 1; index < last.size(); index++) {
            assertTrue(last.get(index).meanDiameterM()
                    > last.get(index - 1).meanDiameterM());
            assertTrue(last.get(index).bladeHeightM()
                    > last.get(index - 1).bladeHeightM());
            assertTrue(last.get(index).inletWetness()
                    > last.get(index - 1).inletWetness());
            assertTrue(last.get(index).wetnessLossKJkg()
                    > last.get(index - 1).wetnessLossKJkg());
        }
        assertEquals(0.7407, last.get(2).internalEfficiency(), 1e-4);
        assertTrue(last.get(2).internalPowerKW() > 500.0);
    }

    @Test
    @DisplayName("3.3: у последней ступени χ = 0, поэтому E₀ = h₀")
    void usesZeroExitRecoveryForLastStage() {
        StageDetail last = design.lastStageDetails(preliminary, stages).get(2);

        assertEquals(last.heatDropKJkg(), last.availableEnergyKJkg(), 1e-9);
        assertEquals(last.heatDropKJkg() - last.nozzleLossKJkg()
                - last.rotorLossKJkg() - last.exitLossKJkg()
                - last.frictionLossKJkg() - last.partialLossKJkg()
                - last.bladeLeakLossKJkg() - last.diaphragmLeakLossKJkg()
                - last.wetnessLossKJkg(), last.usedHeatDropKJkg(), 1e-9);
    }
}
