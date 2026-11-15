package org.kpush88.Thermal_2.turbine3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FlowPointTest {

    private static final Point STATE = new Point(2.7, 430.0, 3302.0, 6.7, 0.1, 1.0);

    @Test
    @DisplayName("фабрика actual создаёт точку с типом UNRESOLVED")
    void actualFactorySetsUnresolvedType() {
        FlowPoint point = FlowPoint.actual(STATE);
        assertEquals(FlowPointType.UNRESOLVED, point.getPointType());
        assertSame(STATE, point.getPoint());
    }

    @Test
    @DisplayName("фабрика isentropic создаёт точку с типом ISENTROPIC")
    void isentropicFactorySetsIsentropicType() {
        FlowPoint point = FlowPoint.isentropic(STATE);
        assertEquals(FlowPointType.ISENTROPIC, point.getPointType());
    }
}
