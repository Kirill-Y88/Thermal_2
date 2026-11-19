package org.kpush88.Thermal_2.turbine4;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessFlowTest {

    @Test
    @DisplayName("стадия расчёта сохраняется при создании графа")
    void calculationStageIsStored() {
        ProcessFlow flow = new ProcessFlow(CalculationStage.DETAILED);

        assertEquals(CalculationStage.DETAILED, flow.getCalculationStage());
    }

    @Test
    @DisplayName("граф без стадии расчёта не создаётся")
    void nullCalculationStageRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessFlow(null));
    }

    @Test
    @DisplayName("регистрация присваивает идентификатор")
    void registerAssignsId() {
        ProcessFlow flow = flow();
        FlowPoint point = flow.register(actual());

        assertNotNull(point.getId());
        assertSame(point, flow.getPointsById().get(point.getId()));
    }

    @Test
    @DisplayName("повторный идентификатор отклоняется")
    void duplicateIdRejected() {
        ProcessFlow flow = flow();
        FlowPoint first = flow.register(actual());
        FlowPoint second = actual();
        second.setId(first.getId());

        assertThrows(IllegalArgumentException.class, () -> flow.register(second));
    }

    @Test
    @DisplayName("фактическая цепочка строится и читается по порядку")
    void actualChainOrder() {
        ProcessFlow flow = flow();
        FlowPoint a = flow.appendActual(actual());
        FlowPoint b = flow.appendActual(actual());
        FlowPoint c = flow.appendActual(actual());

        assertEquals(List.of(a, b, c), flow.actualPath());
        assertSame(a, b.getParent());
        assertSame(b, c.getParent());
    }

    @Test
    @DisplayName("вставка в середину фактической цепочки")
    void insertInMiddle() {
        ProcessFlow flow = flow();
        FlowPoint a = flow.appendActual(actual());
        FlowPoint c = flow.appendActual(actual());
        FlowPoint b = flow.insertActualAfter(a, actual());

        assertEquals(List.of(a, b, c), flow.actualPath());
        assertSame(a, b.getParent());
        assertSame(b, c.getParent());
    }

    @Test
    @DisplayName("изоэнтропная цепочка не входит в фактическую")
    void isentropicChainIsSeparate() {
        ProcessFlow flow = flow();
        FlowPoint actual = flow.appendActual(actual());
        FlowPoint is1 = flow.linkIsentropic(actual, isentropic());
        FlowPoint is2 = flow.linkIsentropic(is1, isentropic());

        assertEquals(List.of(actual), flow.actualPath());
        assertTrue(flow.allPoints().containsAll(List.of(actual, is1, is2)));
        flow.validateGraph();
    }

    @Test
    @DisplayName("поиск границы цилиндра и точек ступени")
    void findsBoundaryAndStagePoints() {
        ProcessFlow flow = flow();
        Stage stage = new Stage();
        stage.setName("ЧВД-1");
        stage.setNumber(1);
        stage.setKind(StageKind.NON_CONTROL_STAGE);

        FlowPoint hpIn = flow.appendActual(actual(PointCylinderType.HP_IN));
        FlowPoint stagePoint = flow.appendActual(stageActual(stage,
                StageFlowPointType.MOVING_BLADE_OUT, RowKind.MOVING));
        FlowPoint hpOut = flow.appendActual(actual(PointCylinderType.HP_OUT));

        assertSame(hpIn, flow.boundary(PointCylinderType.HP_IN));
        assertSame(hpOut, flow.boundary(PointCylinderType.HP_OUT));
        assertEquals(List.of(stagePoint), flow.pointsOf(stage));
        assertEquals(List.of(stagePoint), flow.pointsOf(StageFlowPointType.MOVING_BLADE_OUT));
        flow.validateGraph();
    }

    @Test
    @DisplayName("цикл в фактической цепочке отклоняется")
    void cycleRejected() {
        ProcessFlow flow = flow();
        FlowPoint a = actual();
        flow.register(a);
        flow.setRoot(a);
        a.setNextActual(a);

        assertThrows(IllegalStateException.class, flow::validateGraph);
    }

    @Test
    @DisplayName("несвязанная зарегистрированная точка отклоняется")
    void orphanRejected() {
        ProcessFlow flow = flow();
        flow.appendActual(actual());
        flow.register(actual());

        assertThrows(IllegalStateException.class, flow::validateGraph);
    }

    @Test
    @DisplayName("nextActual должен указывать на фактическую точку")
    void actualEdgeRequiresActualTarget() {
        ProcessFlow flow = flow();
        FlowPoint actual = flow.appendActual(actual());
        actual.setNextActual(isentropic());
        flow.register(actual.getNextActual());
        actual.getNextActual().setParent(actual);

        assertThrows(IllegalStateException.class, flow::validateGraph);
    }

    @Test
    @DisplayName("nextIsentropic должен указывать на изоэнтропную точку")
    void isentropicEdgeRequiresIsentropicTarget() {
        ProcessFlow flow = flow();
        FlowPoint actual = flow.appendActual(actual());
        actual.setNextIsentropic(actual());

        assertThrows(IllegalStateException.class, flow::validateGraph);
    }

    @Test
    @DisplayName("тип точки ступени требует заданную ступень")
    void stagePointRequiresStage() {
        ProcessFlow flow = flow();
        FlowPoint point = actual();
        point.getFlowPointMetadata().setStageFlowPointType(StageFlowPointType.MOVING_BLADE_OUT);
        flow.appendActual(point);

        assertThrows(IllegalStateException.class, flow::validateGraph);
    }

    private static ProcessFlow flow() {
        return new ProcessFlow(CalculationStage.PRELIMINARY);
    }

    private static FlowPoint actual() {
        FlowPoint point = FlowPoint.actual(state());
        point.setMassFlow(10.0);
        return point;
    }

    private static FlowPoint actual(PointCylinderType cylinderType) {
        FlowPoint point = actual();
        point.getFlowPointMetadata().setPointCylinderType(cylinderType);
        return point;
    }

    private static FlowPoint stageActual(Stage stage, StageFlowPointType type, RowKind rowKind) {
        FlowPoint point = actual();
        point.getFlowPointMetadata().setStage(stage);
        point.getFlowPointMetadata().setStageFlowPointType(type);
        point.getFlowPointMetadata().setRowKind(rowKind);
        return point;
    }

    private static FlowPoint isentropic() {
        return FlowPoint.isentropic(state());
    }

    private static Point state() {
        return new Point(1.0, 300.0, 3000.0, 6.8, 0.18, 1.0);
    }
}

