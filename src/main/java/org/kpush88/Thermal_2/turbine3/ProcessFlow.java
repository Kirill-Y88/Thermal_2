package org.kpush88.Thermal_2.turbine3;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
public class ProcessFlow {

    private List<Cylinder> cylinders;
    private List<Stage> stages;
    private Turbine turbine;

    private FlowPoint root;
    // опционально — реестр всех узлов, чтобы не терять точки
    private Map<Long, FlowPoint> pointsById;

}
