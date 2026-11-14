package org.kpush88.Thermal_2.turbine3;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
public class FlowPoint {

    private Long id;

    private Point point;
    private FlowPoint parent;
    private FlowPoint nextActual;
    private FlowPoint nextIsentropic;

    // расход пара кг/с
    private Double massFlow;

    //private Stage stage;
    private FlowPointType pointType;


}
