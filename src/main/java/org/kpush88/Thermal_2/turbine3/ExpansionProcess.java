package org.kpush88.Thermal_2.turbine3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Предварительный процесс расширения пара в виде графа {@link FlowPoint}.
 *
 * <p>Фактические узлы (вход, за регулирующей ступенью, за ЧВД, за ЧСД, выхлоп) имеют тип
 * {@link FlowPointType#UNRESOLVED}: их окончательное положение в ступенях определится на
 * следующих этапах расчёта. Изоэнтропные узлы имеют тип {@link FlowPointType#ISENTROPIC}
 * и не входят в карты ступеней.
 *
 * <p>Роль узла задаётся структурой графа и именованными ссылками этого класса, а не типом
 * {@code FlowPointType}: отдельные типы под цилиндры и отсеки не создаются.
 */
public final class ExpansionProcess {

    private static final double ENTROPY_TOLERANCE = 1.0e-5;
    private static final double MASS_FLOW_TOLERANCE = 1.0e-9;

    private final ProcessFlow flow;
    private final Nodes nodes;
    private final Map<FlowPoint, String> roles = new LinkedHashMap<>();

    public ExpansionProcess(ProcessFlow flow, Nodes nodes) {
        if (flow == null || nodes == null) {
            throw new IllegalArgumentException("процесс расширения не задан");
        }
        this.flow = flow;
        this.nodes = nodes;
        fillRoles();
        validate();
    }

    public ProcessFlow flow() {
        return flow;
    }

    public Nodes nodes() {
        return nodes;
    }

    public FlowPoint inlet() {
        return nodes.inlet();
    }

    public FlowPoint regulatingStageOutlet() {
        return nodes.regulatingStageOutlet();
    }

    public FlowPoint chvdOutlet() {
        return nodes.chvdOutlet();
    }

    public FlowPoint chsdOutlet() {
        return nodes.chsdOutlet();
    }

    public FlowPoint exhaust() {
        return nodes.exhaust();
    }

    public FlowPoint regulatingStageIsentropic() {
        return nodes.regulatingStageIsentropic();
    }

    public FlowPoint chvdIsentropic() {
        return nodes.chvdIsentropic();
    }

    public FlowPoint chsdIsentropic() {
        return nodes.chsdIsentropic();
    }

    public FlowPoint chndIsentropic() {
        return nodes.chndIsentropic();
    }

    public FlowPoint exhaustIsentropic() {
        return nodes.exhaustIsentropic();
    }

    /** Фактическая цепочка узлов от входа до выхлопа. */
    public List<FlowPoint> actualPath() {
        return flow.actualPath();
    }

    /** Изоэнтропные узлы процесса в порядке расширения. */
    public List<FlowPoint> isentropicPoints() {
        return List.of(nodes.regulatingStageIsentropic(), nodes.chvdIsentropic(),
                nodes.chsdIsentropic(), nodes.chndIsentropic(), nodes.exhaustIsentropic());
    }

    /** Фактический узел на входе отсека. */
    public FlowPoint sectionInlet(CourseProjectEfficiency.Section section) {
        if (section == null) {
            throw new IllegalArgumentException("отсек не задан");
        }
        return switch (section) {
            case CHVD -> nodes.regulatingStageOutlet();
            case CHSD -> nodes.chvdOutlet();
            case CHND -> nodes.chsdOutlet();
        };
    }

    /** Фактический узел на выходе отсека. */
    public FlowPoint sectionOutlet(CourseProjectEfficiency.Section section) {
        if (section == null) {
            throw new IllegalArgumentException("отсек не задан");
        }
        return switch (section) {
            case CHVD -> nodes.chvdOutlet();
            case CHSD -> nodes.chsdOutlet();
            case CHND -> nodes.exhaust();
        };
    }

    /** Проверка структуры и термодинамической согласованности процесса. */
    public void validate() {
        if (flow.getRoot() != nodes.inlet()) {
            throw new IllegalStateException("корень процесса должен совпадать с входом");
        }
        flow.validateGraph();
        List<FlowPoint> path = flow.actualPath();
        if (path.isEmpty() || path.get(0) != nodes.inlet()
                || path.get(path.size() - 1) != nodes.exhaust()) {
            throw new IllegalStateException("фактическая цепочка должна начинаться входом "
                    + "и заканчиваться выхлопом");
        }
        int previous = -1;
        for (FlowPoint point : List.of(nodes.inlet(), nodes.regulatingStageOutlet(),
                nodes.chvdOutlet(), nodes.chsdOutlet(), nodes.exhaust())) {
            int index = path.indexOf(point);
            if (index <= previous) {
                throw new IllegalStateException("нарушен порядок границ отсеков "
                        + "в фактической цепочке");
            }
            previous = index;
            if (point.getPointType() == null
                    || point.getPointType() == FlowPointType.ISENTROPIC) {
                throw new IllegalStateException("фактическая точка имеет недопустимый тип: "
                        + point.getPointType());
            }
        }
        for (FlowPoint point : isentropicPoints()) {
            if (point.getPointType() != FlowPointType.ISENTROPIC) {
                throw new IllegalStateException("изоэнтропная точка имеет тип "
                        + point.getPointType());
            }
        }
        requireMonotonic(path);
        requireIsentropic(nodes.inlet(), nodes.regulatingStageIsentropic());
        requireIsentropic(nodes.regulatingStageOutlet(), nodes.chvdIsentropic());
        requireIsentropic(nodes.chvdOutlet(), nodes.chsdIsentropic());
        requireIsentropic(nodes.chsdOutlet(), nodes.chndIsentropic());
        requireIsentropic(nodes.regulatingStageIsentropic(), nodes.exhaustIsentropic());
        List<FlowPoint> all = new ArrayList<>(path);
        all.addAll(isentropicPoints());
        requireMassFlow(all);
    }

    private static void requireMonotonic(List<FlowPoint> path) {
        for (int i = 1; i < path.size(); i++) {
            Point previous = path.get(i - 1).getPoint();
            Point current = path.get(i).getPoint();
            if (!(current.pressureMPa() < previous.pressureMPa())) {
                throw new IllegalStateException("давление должно убывать вдоль процесса");
            }
            if (!(current.enthalpyKJkg() < previous.enthalpyKJkg())) {
                throw new IllegalStateException("энтальпия должна убывать вдоль процесса");
            }
        }
    }

    private static void requireIsentropic(FlowPoint from, FlowPoint isentropic) {
        if (from.getNextIsentropic() != isentropic) {
            throw new IllegalStateException("изоэнтропная точка не связана с фактической");
        }
        Point start = from.getPoint();
        Point end = isentropic.getPoint();
        if (Math.abs(start.entropyKJkgK() - end.entropyKJkgK()) > ENTROPY_TOLERANCE) {
            throw new IllegalStateException("энтропия изоэнтропной точки не совпадает "
                    + "с исходной: " + start.entropyKJkgK() + " и " + end.entropyKJkgK());
        }
        if (!(end.enthalpyKJkg() < start.enthalpyKJkg())) {
            throw new IllegalStateException("энтальпия изоэнтропной точки должна быть "
                    + "ниже исходной");
        }
        if (!(end.pressureMPa() <= start.pressureMPa())) {
            throw new IllegalStateException("давление изоэнтропной точки должно быть "
                    + "не выше исходного");
        }
    }

    private static void requireMassFlow(List<FlowPoint> points) {
        Double reference = points.get(0).getMassFlow();
        if (reference == null || !(reference > 0.0) || !Double.isFinite(reference)) {
            throw new IllegalStateException("расход пара не задан на входе");
        }
        for (FlowPoint point : points) {
            Double massFlow = point.getMassFlow();
            if (massFlow == null
                    || Math.abs(massFlow - reference) > MASS_FLOW_TOLERANCE) {
                throw new IllegalStateException("расход пара должен быть одинаковым "
                        + "во всех точках процесса");
            }
        }
    }

    private void fillRoles() {
        roles.put(nodes.inlet(), "вход");
        roles.put(nodes.regulatingStageOutlet(), "за регулирующей ступенью");
        roles.put(nodes.chvdOutlet(), "за ЧВД");
        roles.put(nodes.chsdOutlet(), "за ЧСД");
        roles.put(nodes.exhaust(), "выхлоп");
        roles.put(nodes.regulatingStageIsentropic(), "изоэнтропа РС");
        roles.put(nodes.chvdIsentropic(), "изоэнтропа ЧВД");
        roles.put(nodes.chsdIsentropic(), "изоэнтропа ЧСД");
        roles.put(nodes.chndIsentropic(), "изоэнтропа ЧНД");
        roles.put(nodes.exhaustIsentropic(), "изоэнтропа выхлопа");
    }

    /** Построчный протокол узлов с ролями. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<FlowPoint, String> entry : roles.entrySet()) {
            text.append(entry.getValue()).append(": ")
                    .append(entry.getKey().getPoint().describe())
                    .append(System.lineSeparator());
        }
        return text.toString().stripTrailing();
    }

    /** Именованные узлы предварительного процесса расширения. */
    public record Nodes(
            FlowPoint inlet,
            FlowPoint regulatingStageOutlet,
            FlowPoint chvdOutlet,
            FlowPoint chsdOutlet,
            FlowPoint exhaust,
            FlowPoint regulatingStageIsentropic,
            FlowPoint chvdIsentropic,
            FlowPoint chsdIsentropic,
            FlowPoint chndIsentropic,
            FlowPoint exhaustIsentropic) {

        public Nodes {
            FlowPoint[] all = {inlet, regulatingStageOutlet, chvdOutlet, chsdOutlet, exhaust,
                    regulatingStageIsentropic, chvdIsentropic, chsdIsentropic,
                    chndIsentropic, exhaustIsentropic};
            for (FlowPoint point : all) {
                if (point == null) {
                    throw new IllegalArgumentException("узел процесса не задан");
                }
            }
        }
    }
}
