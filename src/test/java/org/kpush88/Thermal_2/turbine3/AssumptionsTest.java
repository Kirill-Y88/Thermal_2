package org.kpush88.Thermal_2.turbine3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssumptionsTest {

    @Test
    @DisplayName("допущения предварительного расчёта опорного варианта корректны")
    void designAssumptionsReferenceIsValid() {
        DesignAssumptions assumptions = DesignAssumptions.reference();
        assertEquals(175.0, assumptions.regulatingHeatDropKJkg(), 1.0e-12);
        assertEquals(0.81, assumptions.electricalEfficiency(), 1.0e-12);
        assertEquals(0.06, assumptions.regulatingReaction(), 1.0e-12);
    }

    @Test
    @DisplayName("допущения детального расчёта опорного варианта корректны")
    void stageAssumptionsReferenceIsValid() {
        StageAssumptions assumptions = StageAssumptions.reference();
        assertEquals(6, assumptions.chvd().stageCountHint());
        assertEquals(6, assumptions.chsd().stageCountHint());
        assertEquals(4, assumptions.chnd().stageCountHint());
        assertEquals(0.0606, assumptions.geometry().nozzleChordM(), 1.0e-12);
    }
}
