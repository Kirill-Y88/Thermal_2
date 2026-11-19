package org.kpush88.Thermal_2.turbine4;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Результат выполненного расчёта: графы всех этапов и примечания.
 *
 * @param calculationType тип расчёта
 * @param input           исходные данные
 * @param flows           графы по стадиям
 * @param notes           примечания этапов
 */
public record CalculationResult(
        CalculationType calculationType,
        CalculationInput input,
        Map<CalculationStage, ProcessFlow> flows,
        List<String> notes) {

    public CalculationResult {
        if (calculationType == null) {
            throw new IllegalArgumentException("тип расчёта не задан");
        }
        if (input == null) {
            throw new IllegalArgumentException("исходные данные расчёта не заданы");
        }
        if (flows == null) {
            throw new IllegalArgumentException("результаты этапов не заданы");
        }
        if (notes == null) {
            throw new IllegalArgumentException("примечания этапов не заданы");
        }

        EnumMap<CalculationStage, ProcessFlow> copy =
                new EnumMap<>(CalculationStage.class);
        copy.putAll(flows);
        flows = Collections.unmodifiableMap(copy);
        notes = List.copyOf(notes);
    }

    /**
     * Граф указанной стадии.
     *
     * @param stage стадия расчёта
     * @return граф, построенный соответствующим этапом
     * @throws IllegalArgumentException если граф стадии отсутствует
     */
    public ProcessFlow flow(CalculationStage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("стадия расчёта не задана");
        }
        ProcessFlow flow = flows.get(stage);
        if (flow == null) {
            throw new IllegalArgumentException("граф стадии "
                    + stage.label() + " отсутствует");
        }
        return flow;
    }

    /** Последний посчитанный граф в порядке стадий. */
    public ProcessFlow resultFlow() {
        ProcessFlow result = null;
        for (ProcessFlow flow : flows.values()) {
            result = flow;
        }
        if (result == null) {
            throw new IllegalStateException("ни один этап не выполнен");
        }
        return result;
    }
}