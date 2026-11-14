package org.kpush88.Thermal_2.turbine3;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
public class Turbine {

    private String name;
    private List<Cylinder> cylinders;


}
