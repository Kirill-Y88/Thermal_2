package org.kpush88.Thermal_2.v2;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public abstract class Element {

    private Set<Connection> inputConnections;
    private Set<Connection> outputConnections;


}
