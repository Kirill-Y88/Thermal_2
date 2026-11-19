package org.kpush88.Thermal_2.turbine4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Запускает этапы расчёта в порядке, заданном для типа расчёта.
 *
 * <p>Оркестратор не содержит формул и не создаёт этапы сам. План этапов
 * передаётся снаружи через {@link Builder}. Один экземпляр оркестратора можно
 * переиспользовать: состояние расчёта живёт в {@link CalculationContext}.
 */
public final class CalculationOrchestrator {

    private final Map<CalculationType, List<CalculationRun>> plans;

    private CalculationOrchestrator(Map<CalculationType, List<CalculationRun>> plans) {
        this.plans = validatePlans(plans);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Выполнить расчёт указанного типа.
     *
     * @param type  тип расчёта
     * @param input исходные данные
     * @return результаты всех выполненных этапов
     */
    public CalculationResult run(CalculationType type, CalculationInput input) {
        if (type == null) {
            throw new IllegalArgumentException("тип расчёта не задан");
        }
        if (input == null) {
            throw new IllegalArgumentException("исходные данные расчёта не заданы");
        }

        CalculationType inputType = input.calculationType();
        if (inputType == null) {
            throw new IllegalArgumentException("тип исходных данных не задан");
        }
        if (inputType != type) {
            throw new IllegalArgumentException("исходные данные относятся к "
                    + inputType.label() + " расчёту, а запускается "
                    + type.label());
        }

        List<CalculationRun> plan = plans.get(type);
        if (plan == null) {
            throw new IllegalStateException("для " + type.label()
                    + " расчёта не задан план этапов");
        }

        CalculationContext context = new CalculationContext(type, input);

        for (CalculationRun run : plan) {
            CalculationStage stage = run.getCalculationStage();
            ProcessFlow flow;
            try {
                flow = run.run(context);
            } catch (RuntimeException e) {
                throw new IllegalStateException("этап " + stage.label()
                        + " не выполнен", e);
            }

            validateFlow(run, flow);
            context.accept(run, flow);
        }

        return context.toResult();
    }

    /** Стадии, которые будут выполнены для указанного типа расчёта. */
    public List<CalculationStage> stages(CalculationType type) {
        if (type == null) {
            throw new IllegalArgumentException("тип расчёта не задан");
        }
        List<CalculationRun> plan = plans.get(type);
        if (plan == null) {
            throw new IllegalStateException("для " + type.label()
                    + " расчёта не задан план этапов");
        }

        List<CalculationStage> stages = new ArrayList<>(plan.size());
        for (CalculationRun run : plan) {
            stages.add(run.getCalculationStage());
        }
        return List.copyOf(stages);
    }

    private static void validateFlow(CalculationRun run, ProcessFlow flow) {
        CalculationStage expected = run.getCalculationStage();

        if (flow == null) {
            throw new IllegalStateException("этап " + expected.label()
                    + " не вернул граф процесса");
        }
        if (flow.getCalculationStage() == null) {
            throw new IllegalStateException("этап " + expected.label()
                    + " вернул граф без стадии расчёта");
        }
        if (flow.getCalculationStage() != expected) {
            throw new IllegalStateException("этап " + expected.label()
                    + " вернул граф стадии " + flow.getCalculationStage().label());
        }
        if (flow.getRoot() == null) {
            throw new IllegalStateException("этап " + expected.label()
                    + " вернул пустой граф процесса");
        }
        flow.validateGraph();
    }

    private static Map<CalculationType, List<CalculationRun>> validatePlans(
            Map<CalculationType, List<CalculationRun>> plans) {

        if (plans == null || plans.isEmpty()) {
            throw new IllegalArgumentException("планы этапов не заданы");
        }

        EnumMap<CalculationType, List<CalculationRun>> copy =
                new EnumMap<>(CalculationType.class);

        for (Map.Entry<CalculationType, List<CalculationRun>> entry : plans.entrySet()) {
            CalculationType type = entry.getKey();
            if (type == null) {
                throw new IllegalArgumentException("тип расчёта в плане не задан");
            }

            List<CalculationRun> runs = entry.getValue();
            if (runs == null || runs.isEmpty()) {
                throw new IllegalArgumentException("план " + type.label()
                        + " расчёта пуст");
            }

            EnumSet<CalculationStage> seen =
                    EnumSet.noneOf(CalculationStage.class);
            CalculationStage previous = null;
            List<CalculationRun> checked = new ArrayList<>(runs.size());

            for (CalculationRun run : runs) {
                if (run == null) {
                    throw new IllegalArgumentException("этап " + type.label()
                            + " расчёта не задан");
                }

                CalculationStage stage = run.getCalculationStage();
                if (stage == null) {
                    throw new IllegalArgumentException("у этапа " + type.label()
                            + " расчёта не задана стадия");
                }
                if (!seen.add(stage)) {
                    throw new IllegalArgumentException("стадия " + stage.label()
                            + " повторяется в плане " + type.label() + " расчёта");
                }
                if (previous != null && stage.ordinal() <= previous.ordinal()) {
                    throw new IllegalArgumentException("этап " + stage.label()
                            + " не может идти после " + previous.label()
                            + " в плане " + type.label() + " расчёта");
                }

                previous = stage;
                checked.add(run);
            }

            copy.put(type, List.copyOf(checked));
        }

        return Collections.unmodifiableMap(copy);
    }

    /** Построитель планов этапов по типам расчёта. */
    public static final class Builder {

        private final Map<CalculationType, List<CalculationRun>> plans =
                new EnumMap<>(CalculationType.class);

        private Builder() {
        }

        public Builder plan(CalculationType type, CalculationRun... runs) {
            if (type == null) {
                throw new IllegalArgumentException("тип расчёта не задан");
            }
            if (runs == null || runs.length == 0) {
                throw new IllegalArgumentException("план " + type.label()
                        + " расчёта пуст");
            }
            if (plans.containsKey(type)) {
                throw new IllegalArgumentException("план " + type.label()
                        + " расчёта уже задан");
            }
            for (CalculationRun run : runs) {
                if (run == null) {
                    throw new IllegalArgumentException("этап " + type.label()
                            + " расчёта не задан");
                }
            }
            plans.put(type, List.of(runs));
            return this;
        }

        public CalculationOrchestrator build() {
            return new CalculationOrchestrator(plans);
        }
    }
}