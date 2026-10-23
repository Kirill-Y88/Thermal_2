package org.kpush88.Thermal_2.v4;

import java.util.List;

public interface Element {

    String getUname();
    Long getId();

    List<Connection> getOutputConnections(SpaceType spaceType);
    List<Connection> getInputConnections(SpaceType spaceType);

    ElementType getElementType();

}
