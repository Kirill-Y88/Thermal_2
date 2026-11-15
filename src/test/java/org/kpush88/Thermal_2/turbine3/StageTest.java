package org.kpush88.Thermal_2.turbine3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageTest {

    @Test
    @DisplayName("в ступень нельзя добавить изоэнтропную или неразрешённую точку")
    void rejectsNonConcretePoints() {
        Stage stage = new Stage();
        assertThrows(IllegalArgumentException.class, () -> stage.addFlowPoint(null));
        assertThrows(IllegalArgumentException.class,
                () -> stage.addFlowPoint(FlowPoint.isentropic(state())));
        assertThrows(IllegalArgumentException.class,
                () -> stage.addFlowPoint(FlowPoint.actual(state())));
    }

    @Test
    @DisplayName("обязательные точки зависят от типа ступени")
    void requiredPointTypesDependOnKind() {
        Stage uncontrolled = new Stage();
        uncontrolled.setKind(StageKind.UNCONTROLLED);
        assertEquals(5, uncontrolled.requiredPointTypes().size());
        assertTrue(uncontrolled.requiredPointTypes().contains(FlowPointType.NOZZLE_BLADE_OUT));

        Stage regulating = new Stage();
        regulating.setKind(StageKind.REGULATING);
        assertEquals(10, regulating.requiredPointTypes().size());
        assertTrue(regulating.requiredPointTypes().contains(FlowPointType.REDIRECTING_BLADE_OUT));
    }

    @Test
    @DisplayName("пустая ступень не проходит проверку полноты")
    void emptyStageFailsValidation() {
        Stage stage = new Stage();
        stage.setKind(StageKind.UNCONTROLLED);
        assertThrows(IllegalStateException.class, stage::validateComplete);
    }

    @Test
    @DisplayName("заполненная ступень проходит проверку полноты")
    void completeStagePassesValidation() {
        assertDoesNotThrow(() -> completeStage().validateComplete());
    }

    static Stage completeStage() {
        Stage stage = new Stage();
        stage.setKind(StageKind.UNCONTROLLED);
        stage.setName("ЧВД-1");
        stage.setNumber(1);
        stage.setMeanDiameterM(0.879);
        stage.setRootDiameterM(0.863);
        stage.setBladeHeightM(0.01575);
        stage.setNozzleHeightM(0.01275);
        stage.setReaction(0.05);
        stage.setAdmission(0.7);
        stage.setSpeedRatio(0.39);
        stage.setHeatDropKJkg(62.37);
        stage.setUsedHeatDropKJkg(43.68);
        stage.setEfficiency(0.729);
        stage.setPowerKW(304.3);
        stage.getRows().add(new RowDetail(RowKind.NOZZLE, 59.25, 344.23, 322.6,
                0.0, 90.0, 11.0, 0.58, 1.209, 0.222, 0.0047, 0.01275,
                0.0606, 0.04848, 40, 7.2, 0.958, 0.937));
        for (FlowPointType type : stage.requiredPointTypes()) {
            FlowPoint point = FlowPoint.actual(state());
            point.setPointType(type);
            stage.addFlowPoint(point);
        }
        return stage;
    }

    static Point state() {
        return new Point(1.0, 300.0, 3000.0, 6.8, 0.18, 1.0);
    }
}
