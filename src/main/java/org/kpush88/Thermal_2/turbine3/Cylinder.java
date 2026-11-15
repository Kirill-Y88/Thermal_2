package org.kpush88.Thermal_2.turbine3;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Cylinder {

    private String name;
    private List<Stage> stages = new ArrayList<>();
    private CylinderType type;

}
