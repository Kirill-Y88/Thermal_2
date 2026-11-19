package org.kpush88.Thermal_2.turbine4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Контекст одного запуска расчёта.
 *
 * <p>Содержит исходные данные, общую h—s диаграмму и графы уже выполненных
 * этапов. Контекст создаётся оркестратором и живёт только на время одного
 * расчёта, поэтому этапы могут быть stateless и переиспользоваться.
 */
public final class CalculationContext {

    private final CalculationType calculationType;
    private final CalculationInput input;
    private final HsDiagram hsDiagram = new HsDiagram();

    private final Map<CalculationStage, ProcessFlow> flows =
            new EnumMap<>(CalculationStage.class);

    private final List<String> notes = new ArrayList<>();

    CalculationContext(CalculationType calculationType, CalculationInput input) {
        if (calculationType == null) {
            throw new IllegalArgumentException("тип расчёта не задан");
        }
        if (input == null) {
            throw new IllegalArgumentException("исходные данные расчёта не заданы");
        }
        CalculationType inputType = input.calculationType();
        if (inputType == null) {
            throw new IllegalArgumentException("тип исходных данных не задан");
        }
        if (inputType != calculationType) {
            throw new IllegalArgumentException("исходные данные относятся к "
                    + inputType.label() + " расчёту, а запускается "
                    + calculationType.label());
        }
        this.calculationType = calculationType;
        this.input = input;
    }

    public CalculationType calculationType() {
        return calculationType;
    }

    public CalculationInput input() {
        return input;
    }

    /**
     * Получить исходные данные нужного типа.
     *
     * @param type ожидаемый тип исходных данных
     * @param <T>  тип исходных данных
     * @return исходные данные, приведённые к {@code type}
     * @throws IllegalStateException если переданы данные другого типа
     */
    public <T extends CalculationInput> T requireInput(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("тип исходных данных не задан");
        }
        if (!type.isInstance(input)) {
            throw new IllegalStateException("расчёт " + calculationType.label()
                    + " ожидает исходные данные " + type.getSimpleName()
                    + ", а получил " + input.getClass().getSimpleName());
        }
        return type.cast(input);
    }

    /** Общая h—s диаграмма для всех этапов расчёта. */
    public HsDiagram hsDiagram() {
        return hsDiagram;
    }

    public boolean hasFlow(CalculationStage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("стадия расчёта не задана");
        }
        return flows.containsKey(stage);
    }

    /**
     * Граф указанной стадии.
     *
     * @param stage стадия расчёта
     * @return граф, построенный соответствующим этапом
     * @throws IllegalStateException если этап ещё не выполнен
     */
    public ProcessFlow flow(CalculationStage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("стадия расчёта не задана");
        }
        ProcessFlow flow = flows.get(stage);
        if (flow == null) {
            throw new IllegalStateException("этап " + stage.label()
                    + " ещё не выполнен");
        }
        return flow;
    }

    /** Графы всех выполненных этапов в порядке стадий. */
    public Map<CalculationStage, ProcessFlow> flows() {
        return Collections.unmodifiableMap(flows);
    }

    /** Добавить примечание к результату расчёта. */
    public void note(String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("примечание не задано");
        }
        notes.add(note);
    }

    public List<String> notes() {
        return List.copyOf(notes);
    }

    /** Вызывается только оркестратором. */
    void accept(CalculationRun run, ProcessFlow flow) {
        CalculationStage stage = run.getCalculationStage();
        ProcessFlow previous = flows.putIfAbsent(stage, flow);
        if (previous != null) {
            throw new IllegalStateException("этап " + stage.label()
                    + " уже зарегистрировал граф процесса");
        }
    }

    CalculationResult toResult() {
        return new CalculationResult(calculationType, input, flows, notes);
    }
}