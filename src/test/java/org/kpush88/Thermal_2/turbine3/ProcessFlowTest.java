package org.kpush88.Thermal_2.turbine3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessFlowTest {

    @Test
    @DisplayName("регистрация присваивает идентификатор")
    void registerAssignsId() {
        ProcessFlow flow = new ProcessFlow();
        FlowPoint point = flow.register(FlowPoint.actual(state()));
        assertNotNull(point.getId());
        assertEquals(point, flow.getPointsById().get(point.getId()));
    }

    @Test
    @DisplayName("реальная цепочка строится и читается по порядку")
    void actualChainOrder() {
        ProcessFlow flow = new ProcessFlow();
        FlowPoint a = flow.appendActual(typed(FlowPointType.NOZZLE_BLADE_IN));
        FlowPoint b = flow.appendActual(typed(FlowPointType.NOZZLE_BLADE_OUT));
        FlowPoint c = flow.appendActual(typed(FlowPointType.MOVING_BLADE_OUT));
        assertEquals(List.of(a, b, c), flow.actualPath());
        assertEquals(a, b.getParent());
        assertEquals(b, c.getParent());
    }

    @Test
    @DisplayName("вставка в середину реальной цепочки")
    void insertInMiddle() {
        ProcessFlow flow = new ProcessFlow();
        FlowPoint a = flow.appendActual(typed(FlowPointType.NOZZLE_BLADE_IN));
        FlowPoint c = flow.appendActual(typed(FlowPointType.NOZZLE_BLADE_OUT));
        FlowPoint b = flow.insertActualAfter(a, typed(FlowPointType.MOVING_BLADE_OUT));
        assertEquals(List.of(a, b, c), flow.actualPath());
        assertEquals(a, b.getParent());
        assertEquals(b, c.getParent());
    }

    @Test
    @DisplayName("изоэнтропная цепочка не входит в реальную")
    void isentropicChainIsSeparate() {
        ProcessFlow flow = new ProcessFlow();
        FlowPoint actual = flow.appendActual(typed(FlowPointType.NOZZLE_BLADE_IN));
        FlowPoint is1 = flow.linkIsentropic(actual, FlowPoint.isentropic(state()));
        FlowPoint is2 = flow.linkIsentropic(is1, FlowPoint.isentropic(state()));
        assertEquals(List.of(actual), flow.actualPath());
        assertTrue(flow.allPoints().containsAll(List.of(actual, is1, is2)));
        flow.validateGraph();
    }

    @Test
    @DisplayName("цикл в графе отклоняется")
    void cycleRejected() {
        ProcessFlow flow = new ProcessFlow();
        FlowPoint a = typed(FlowPointType.NOZZLE_BLADE_IN);
        flow.register(a);
        flow.setRoot(a);
        a.setNextIsentropic(a);
        assertThrows(IllegalStateException.class, flow::validateGraph);
    }

    private static FlowPoint typed(FlowPointType type) {
        FlowPoint point = FlowPoint.actual(state());
        point.setPointType(type);
        return point;
    }

    private static Point state() {
        return new Point(1.0, 300.0, 3000.0, 6.8, 0.18, 1.0);
    }
}
