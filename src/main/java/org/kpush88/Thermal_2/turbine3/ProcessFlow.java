package org.kpush88.Thermal_2.turbine3;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
public class ProcessFlow {

    private Turbine turbine;

    private FlowPoint root;
    // опционально — реестр всех узлов, чтобы не терять точки
    private Map<Long, FlowPoint> pointsById = new HashMap<>();

}
