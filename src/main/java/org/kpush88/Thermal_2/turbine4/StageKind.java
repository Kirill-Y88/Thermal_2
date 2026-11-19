package org.kpush88.Thermal_2.turbine4;

/** Тип ступени */
public enum StageKind {

    SINGLE_ROW_CONTROL_STAGE("одновенечная регулирующая"),
    TWO_ROW_CONTROL_STAGE("двухвенечная регулирующая"),
    NON_CONTROL_STAGE("нерегулируемая");

    private final String label;

    StageKind(String label) {
        this.label = label;
    }

    /** Название для отчёта и сообщений об ошибках. */
    public String label() {
        return label;
    }

}
