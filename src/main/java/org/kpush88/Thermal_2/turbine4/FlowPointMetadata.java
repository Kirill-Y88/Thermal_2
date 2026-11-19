package org.kpush88.Thermal_2.turbine4;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FlowPointMetadata {

    private PointCylinderType pointCylinderType;
    private PointType pointType;
    private RowKind rowKind;
    private StageFlowPointType stageFlowPointType;
    private Stage stage;
    private CalculationStage calculationStage;


}
