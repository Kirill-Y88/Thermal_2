package org.kpush88.Thermal_2.turbine3;

/** Тип регулирующей ступени. */
public enum RegulatingKind {

    /** Одновенечная регулирующая ступень. */
    ONE_ROW("одновенечная"),

    /** Двухвенечная регулирующая ступень. */
    TWO_ROW("двухвенечная");

    private final String label;

    RegulatingKind(String label) {
        this.label = label;
    }

    /** Название для отчёта. */
    public String label() {
        return label;
    }
}
