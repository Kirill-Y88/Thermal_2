package org.kpush88.Thermal_2.turbine4;

/**
 * Стадия расчёта.
 *
 * <p>Порядок констант соответствует порядку выполнения этапов: сначала
 * предварительный расчёт, затем детальный. Оркестратор проверяет, что план
 * этапов не нарушает этот порядок.
 */
public enum CalculationStage {

    PRELIMINARY("предварительный"),
    DETAILED("детальный");

    private final String label;

    CalculationStage(String label) {
        this.label = label;
    }

    /** Название для отчёта и сообщений об ошибках. */
    public String label() {
        return label;
    }
}
