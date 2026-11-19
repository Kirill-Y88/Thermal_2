package org.kpush88.Thermal_2.turbine4;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculationOrchestratorTest {

    @Test
    @DisplayName("этапы выполняются в порядке плана")
    void runsStagesInOrder() {
        List<CalculationStage> log = new ArrayList<>();
        CalculationOrchestrator orchestrator = CalculationOrchestrator.builder()
                .plan(CalculationType.DESIGN,
                        run(CalculationStage.PRELIMINARY, log),
                        run(CalculationStage.DETAILED, log))
                .build();

        CalculationResult result = orchestrator.run(CalculationType.DESIGN,
                new TestInput(CalculationType.DESIGN));

        assertEquals(List.of(CalculationStage.PRELIMINARY, CalculationStage.DETAILED), log);
        assertEquals(List.of(CalculationStage.PRELIMINARY, CalculationStage.DETAILED),
                orchestrator.stages(CalculationType.DESIGN));
        assertEquals(CalculationStage.DETAILED, result.resultFlow().getCalculationStage());
        assertEquals(2, result.flows().size());
    }

    @Test
    @DisplayName("для разных типов расчёта используются разные планы")
    void usesPlanOfRequestedType() {
        List<CalculationStage> designLog = new ArrayList<>();
        List<CalculationStage> verificationLog = new ArrayList<>();
        CalculationOrchestrator orchestrator = CalculationOrchestrator.builder()
                .plan(CalculationType.DESIGN,
                        run(CalculationStage.PRELIMINARY, designLog),
                        run(CalculationStage.DETAILED, designLog))
                .plan(CalculationType.VERIFICATION,
                        run(CalculationStage.DETAILED, verificationLog))
                .build();

        orchestrator.run(CalculationType.DESIGN, new TestInput(CalculationType.DESIGN));
        orchestrator.run(CalculationType.VERIFICATION,
                new TestInput(CalculationType.VERIFICATION));

        assertEquals(List.of(CalculationStage.PRELIMINARY, CalculationStage.DETAILED), designLog);
        assertEquals(List.of(CalculationStage.DETAILED), verificationLog);
    }

    @Test
    @DisplayName("исходные данные чужого типа отклоняются")
    void inputOfAnotherTypeRejected() {
        CalculationOrchestrator orchestrator = CalculationOrchestrator.builder()
                .plan(CalculationType.DESIGN, run(CalculationStage.PRELIMINARY, new ArrayList<>()))
                .plan(CalculationType.VERIFICATION, run(CalculationStage.DETAILED, new ArrayList<>()))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.run(CalculationType.DESIGN,
                        new TestInput(CalculationType.VERIFICATION)));
    }

    @Test
    @DisplayName("отсутствие плана для типа расчёта отклоняется")
    void missingPlanRejected() {
        CalculationOrchestrator orchestrator = CalculationOrchestrator.builder()
                .plan(CalculationType.DESIGN, run(CalculationStage.PRELIMINARY, new ArrayList<>()))
                .build();

        assertThrows(IllegalStateException.class,
                () -> orchestrator.run(CalculationType.VERIFICATION,
                        new TestInput(CalculationType.VERIFICATION)));
    }

    @Test
    @DisplayName("null тип расчёта и null исходные данные отклоняются")
    void nullArgumentsRejected() {
        CalculationOrchestrator orchestrator = CalculationOrchestrator.builder()
                .plan(CalculationType.DESIGN, run(CalculationStage.PRELIMINARY, new ArrayList<>()))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.run(null, new TestInput(CalculationType.DESIGN)));
        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.run(CalculationType.DESIGN, null));
    }

    @Test
    @DisplayName("повторное объявление плана отклоняется")
    void duplicatePlanRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CalculationOrchestrator.builder()
                        .plan(CalculationType.DESIGN,
                                run(CalculationStage.PRELIMINARY, new ArrayList<>()))
                        .plan(CalculationType.DESIGN,
                                run(CalculationStage.DETAILED, new ArrayList<>())));
    }

    @Test
    @DisplayName("повтор стадии в плане отклоняется")
    void duplicateStageRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CalculationOrchestrator.builder()
                        .plan(CalculationType.DESIGN,
                                run(CalculationStage.PRELIMINARY, new ArrayList<>()),
                                run(CalculationStage.PRELIMINARY, new ArrayList<>()))
                        .build());
    }

    @Test
    @DisplayName("этап не по порядку отклоняется")
    void stageOutOfOrderRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CalculationOrchestrator.builder()
                        .plan(CalculationType.DESIGN,
                                run(CalculationStage.DETAILED, new ArrayList<>()),
                                run(CalculationStage.PRELIMINARY, new ArrayList<>()))
                        .build());
    }

    @Test
    @DisplayName("этап без стадии отклоняется")
    void nullStageRejected() {
        CalculationRun run = new CalculationRun() {
            @Override
            public CalculationStage getCalculationStage() {
                return null;
            }

            @Override
            public ProcessFlow run(CalculationContext context) {
                return filledFlow(CalculationStage.PRELIMINARY);
            }
        };

        assertThrows(IllegalArgumentException.class,
                () -> CalculationOrchestrator.builder()
                        .plan(CalculationType.DESIGN, run)
                        .build());
    }

    @Test
    @DisplayName("этап, вернувший null, отклоняется")
    void nullFlowRejected() {
        CalculationRun run = new CalculationRun() {
            @Override
            public CalculationStage getCalculationStage() {
                return CalculationStage.PRELIMINARY;
            }

            @Override
            public ProcessFlow run(CalculationContext context) {
                return null;
            }
        };

        CalculationOrchestrator orchestrator = CalculationOrchestrator.builder()
                .plan(CalculationType.DESIGN, run)
                .build();

        assertThrows(IllegalStateException.class,
                () -> orchestrator.run(CalculationType.DESIGN,
                        new TestInput(CalculationType.DESIGN)));
    }

    @Test
    @DisplayName("граф чужой стадии отклоняется")
    void flowOfAnotherStageRejected() {
        CalculationRun run = new CalculationRun() {
            @Override
            public CalculationStage getCalculationStage() {
                return CalculationStage.PRELIMINARY;
            }

            @Override
            public ProcessFlow run(CalculationContext context) {
                return filledFlow(CalculationStage.DETAILED);
            }
        };

        CalculationOrchestrator orchestrator = CalculationOrchestrator.builder()
                .plan(CalculationType.DESIGN, run)
                .build();

        assertThrows(IllegalStateException.class,
                () -> orchestrator.run(CalculationType.DESIGN,
                        new TestInput(CalculationType.DESIGN)));
    }

    @Test
    @DisplayName("пустой граф отклоняется")
    void emptyFlowRejected() {
        CalculationRun run = new CalculationRun() {
            @Override
            public CalculationStage getCalculationStage() {
                return CalculationStage.PRELIMINARY;
            }

            @Override
            public ProcessFlow run(CalculationContext context) {
                return new ProcessFlow(CalculationStage.PRELIMINARY);
            }
        };

        CalculationOrchestrator orchestrator = CalculationOrchestrator.builder()
                .plan(CalculationType.DESIGN, run)
                .build();

        assertThrows(IllegalStateException.class,
                () -> orchestrator.run(CalculationType.DESIGN,
                        new TestInput(CalculationType.DESIGN)));
    }

    @Test
    @DisplayName("некорректный граф отклоняется")
    void invalidFlowRejected() {
        CalculationRun run = new CalculationRun() {
            @Override
            public CalculationStage getCalculationStage() {
                return CalculationStage.PRELIMINARY;
            }

            @Override
            public ProcessFlow run(CalculationContext context) {
                ProcessFlow flow = new ProcessFlow(CalculationStage.PRELIMINARY);
                FlowPoint point = actualPoint();
                flow.appendActual(point);
                point.setNextActual(point);
                return flow;
            }
        };

        CalculationOrchestrator orchestrator = CalculationOrchestrator.builder()
                .plan(CalculationType.DESIGN, run)
                .build();

        assertThrows(IllegalStateException.class,
                () -> orchestrator.run(CalculationType.DESIGN,
                        new TestInput(CalculationType.DESIGN)));
    }

    @Test
    @DisplayName("исключение этапа дополняется названием стадии")
    void stageExceptionWrapped() {
        CalculationRun run = new CalculationRun() {
            @Override
            public CalculationStage getCalculationStage() {
                return CalculationStage.PRELIMINARY;
            }

            @Override
            public ProcessFlow run(CalculationContext context) {
                throw new IllegalStateException("тестовая ошибка");
            }
        };

        CalculationOrchestrator orchestrator = CalculationOrchestrator.builder()
                .plan(CalculationType.DESIGN, run)
                .build();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> orchestrator.run(CalculationType.DESIGN,
                        new TestInput(CalculationType.DESIGN)));

        assertTrue(error.getMessage().contains(CalculationStage.PRELIMINARY.label()));
        assertTrue(error.getCause() instanceof IllegalStateException);
    }

    @Test
    @DisplayName("примечания этапов попадают в результат")
    void notesAreCollected() {
        CalculationRun run = new CalculationRun() {
            @Override
            public CalculationStage getCalculationStage() {
                return CalculationStage.PRELIMINARY;
            }

            @Override
            public ProcessFlow run(CalculationContext context) {
                context.note("проверено");
                return filledFlow(CalculationStage.PRELIMINARY);
            }
        };

        CalculationOrchestrator orchestrator = CalculationOrchestrator.builder()
                .plan(CalculationType.DESIGN, run)
                .build();

        CalculationResult result = orchestrator.run(CalculationType.DESIGN,
                new TestInput(CalculationType.DESIGN));

        assertEquals(List.of("проверено"), result.notes());
    }

    @Test
    @DisplayName("контекст требует исходные данные нужного типа")
    void contextRequiresTypedInput() {
        CalculationContext context = new CalculationContext(CalculationType.DESIGN,
                new TestInput(CalculationType.DESIGN));

        assertThrows(IllegalStateException.class,
                () -> context.requireInput(OtherTestInput.class));
    }

    private static CalculationRun run(CalculationStage stage, List<CalculationStage> log) {
        return new CalculationRun() {
            @Override
            public CalculationStage getCalculationStage() {
                return stage;
            }

            @Override
            public ProcessFlow run(CalculationContext context) {
                log.add(stage);
                return filledFlow(stage);
            }
        };
    }

    private static ProcessFlow filledFlow(CalculationStage stage) {
        ProcessFlow flow = new ProcessFlow(stage);
        flow.appendActual(actualPoint());
        return flow;
    }

    private static FlowPoint actualPoint() {
        FlowPoint point = FlowPoint.actual(
                new Point(1.0, 300.0, 3000.0, 6.8, 0.18, 1.0));
        point.setMassFlow(10.0);
        return point;
    }

    private record TestInput(CalculationType calculationType) implements CalculationInput {
    }

    private record OtherTestInput(CalculationType calculationType) implements CalculationInput {
    }
}