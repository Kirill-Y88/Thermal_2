package org.kpush88.Thermal_2.turbine4;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ступень турбины, к которой относится точка процесса расширения.
 *
 * <p>Служит идентификатором ступени в {@link FlowPointMetadata}: номер и тип ступени
 * позволяют отличать несколько нерегулируемых ступеней друг от друга и группировать
 * точки одной ступени.
 */
@Getter
@Setter
@NoArgsConstructor
public class Stage {

    private String name;
    private int number;
    private StageKind kind;

}
