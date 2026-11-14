package org.kpush88.Thermal_2.turbine3;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.EnumMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
public class Stage {

    private String name;
    private Map<FlowPointType, FlowPoint> flowPointMap = new EnumMap<>(FlowPointType.class);;

    // будет содержать механические характеристики которые в зависимости от расчета - конструкторского или поверочного
    // будут выбираться в процессе расчета или известны сразу



    public void addFlowPoint(FlowPoint point) {
        if (point.getPointType() == null || !point.getPointType().isConcrete()) {
            throw new IllegalArgumentException("...");
        }
        flowPointMap.put(point.getPointType(), point);
    }


    
}
