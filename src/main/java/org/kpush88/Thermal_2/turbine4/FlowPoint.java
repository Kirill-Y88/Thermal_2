package org.kpush88.Thermal_2.turbine4;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FlowPoint {

    private Long id;

    private Point point;
    private FlowPoint parent;
    private FlowPoint nextActual;
    private FlowPoint nextIsentropic;

    /** Расход пара, кг/с. */
    private Double massFlow;

    private FlowPointMetadata flowPointMetadata;

    /** Фактическая точка процесса. */
    public static FlowPoint actual(Point state) {
        return withType(state, PointType.ACTUAL);
    }

    /** Изоэнтропная вспомогательная точка: не входит в фактическую цепочку. */
    public static FlowPoint isentropic(Point state) {
        return withType(state, PointType.ISENTROPIC);
    }

    private static FlowPoint withType(Point state, PointType type) {
        if (state == null) {
            throw new IllegalArgumentException("параметры точки не заданы");
        }
        FlowPoint point = new FlowPoint();
        point.setPoint(state);
        FlowPointMetadata metadata = new FlowPointMetadata();
        metadata.setPointType(type);
        point.setFlowPointMetadata(metadata);
        return point;
    }

}
