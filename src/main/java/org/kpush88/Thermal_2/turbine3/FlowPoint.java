package org.kpush88.Thermal_2.turbine3;

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

    private FlowPointType pointType;

    /** Фактическая точка процесса: тип уточняется позже (UNRESOLVED → конкретный). */
    public static FlowPoint actual(Point state) {
        FlowPoint point = new FlowPoint();
        point.setPoint(state);
        point.setPointType(FlowPointType.UNRESOLVED);
        return point;
    }

    /** Изоэнтропная вспомогательная точка: не входит в карту ступени. */
    public static FlowPoint isentropic(Point state) {
        FlowPoint point = new FlowPoint();
        point.setPoint(state);
        point.setPointType(FlowPointType.ISENTROPIC);
        return point;
    }
}
