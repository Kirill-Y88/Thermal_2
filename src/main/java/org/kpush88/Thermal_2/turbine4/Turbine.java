package org.kpush88.Thermal_2.turbine4;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Turbine {

    private String name;
    private List<Cylinder> cylinders = new ArrayList<>();

}
