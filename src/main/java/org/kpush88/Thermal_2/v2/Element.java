package org.kpush88.Thermal_2.v2;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class Element {

    private Vertex heatingMediumVertex;
    private Vertex heatedMediumVertex;

}
