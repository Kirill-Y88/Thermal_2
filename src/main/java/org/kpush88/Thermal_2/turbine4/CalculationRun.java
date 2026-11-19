package org.kpush88.Thermal_2.turbine4;

/** Один этап расчёта, который запускает {@link CalculationOrchestrator}. */
public interface CalculationRun {

    CalculationStage getCalculationStage();

    /**
     * Выполнить этап.
     *
     * <p>Реализация не должна изменять графы предыдущих этапов. Всё состояние
     * передаётся через контекст, а результат возвращается новым графом.
     *
     * @param context контекст расчёта с входными данными и результатами предыдущих этапов
     * @return непустой граф процесса, у которого стадия совпадает с
     *         {@link #getCalculationStage()}
     */
    ProcessFlow run(CalculationContext context);
}
