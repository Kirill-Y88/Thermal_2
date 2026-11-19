package org.kpush88.Thermal_2.turbine4;

/** Тип теплового расчёта турбины. */
public enum CalculationType {

    DESIGN("конструкторский"),
    VERIFICATION("поверочный");

    private final String label;

    CalculationType(String label) {
        this.label = label;
    }

    /** Название для отчёта и сообщений об ошибках. */
    public String label() {
        return label;
    }
}