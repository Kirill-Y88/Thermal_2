package org.kpush88.Thermal_2.turbine4;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Граф процесса расширения пара в турбине.
 *
 * <p>Класс хранит точки состояния и связи между ними, но не выполняет тепловой расчёт.
 * Фактическая цепочка строится через {@link FlowPoint#getNextActual()}, изоэнтропийные
 * вспомогательные точки — через {@link FlowPoint#getNextIsentropic()}. Изоэнтропийные точки
 * не входят в {@link #actualPath()} и используются только для построения h—s диаграммы.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProcessFlow {

    private Turbine turbine;

    private FlowPoint root;

    /** Реестр всех узлов по идентификатору. */
    private final Map<Long, FlowPoint> pointsById = new HashMap<>();

    private long nextId = 1L;

    /**
     * Зарегистрировать точку и при необходимости присвоить ей идентификатор.
     *
     * @throws IllegalArgumentException если точка не задана или её идентификатор уже занят
     */
    public FlowPoint register(FlowPoint point) {
        if (point == null) {
            throw new IllegalArgumentException("точка не задана");
        }
        Long id = point.getId();
        if (id == null) {
            id = allocateId();
            point.setId(id);
        } else if (id <= 0L) {
            throw new IllegalArgumentException("идентификатор точки должен быть положительным: " + id);
        }
        FlowPoint existing = pointsById.putIfAbsent(id, point);
        if (existing != null && existing != point) {
            throw new IllegalArgumentException("точка с идентификатором " + id + " уже зарегистрирована");
        }
        return point;
    }

    /**
     * Связать две фактические точки: {@code from → to} и обратную ссылку {@code to.parent = from}.
     */
    public FlowPoint linkActual(FlowPoint from, FlowPoint to) {
        register(from);
        register(to);
        if (from == to) {
            throw new IllegalArgumentException("точка не может ссылаться сама на себя");
        }
        if (from.getNextActual() != null && from.getNextActual() != to) {
            throw new IllegalStateException("у точки " + from.getId()
                    + " уже есть следующая фактическая точка");
        }
        if (to.getParent() != null && to.getParent() != from) {
            throw new IllegalStateException("у точки " + to.getId() + " уже есть другая предыдущая точка");
        }
        from.setNextActual(to);
        to.setParent(from);
        return to;
    }

    /** Вставить точку в фактическую цепочку сразу после {@code anchor}. */
    public FlowPoint insertActualAfter(FlowPoint anchor, FlowPoint point) {
        register(anchor);
        register(point);
        if (anchor == point) {
            throw new IllegalArgumentException("точка не может ссылаться сама на себя");
        }
        if (point.getParent() != null || point.getNextActual() != null) {
            throw new IllegalStateException("точка " + point.getId() + " уже включена в фактическую цепочку");
        }
        FlowPoint oldNext = anchor.getNextActual();
        point.setParent(anchor);
        point.setNextActual(oldNext);
        anchor.setNextActual(point);
        if (oldNext != null) {
            oldNext.setParent(point);
        }
        return point;
    }

    /** Добавить точку в конец фактической цепочки. */
    public FlowPoint appendActual(FlowPoint point) {
        register(point);
        if (root == null) {
            root = point;
            return point;
        }
        FlowPoint last = root;
        Set<FlowPoint> visited = new HashSet<>();
        while (last.getNextActual() != null) {
            if (!visited.add(last)) {
                throw new IllegalStateException("цикл в фактической цепочке у точки " + last.getId());
            }
            last = last.getNextActual();
        }
        return insertActualAfter(last, point);
    }

    /**
     * Связать точку с её изоэнтропным состоянием или следующей изоэнтропной точкой.
     */
    public FlowPoint linkIsentropic(FlowPoint from, FlowPoint to) {
        register(from);
        register(to);
        if (from == to) {
            throw new IllegalArgumentException("точка не может ссылаться сама на себя");
        }
        if (from.getNextIsentropic() != null && from.getNextIsentropic() != to) {
            throw new IllegalStateException("у точки " + from.getId()
                    + " уже есть следующая изоэнтропная точка");
        }
        from.setNextIsentropic(to);
        return to;
    }

    /** Фактическая цепочка процесса в порядке прохождения пара. */
    public List<FlowPoint> actualPath() {
        List<FlowPoint> result = new ArrayList<>();
        Set<FlowPoint> visited = new HashSet<>();
        FlowPoint current = root;
        while (current != null) {
            if (!visited.add(current)) {
                throw new IllegalStateException("цикл в фактической цепочке у точки " + current.getId());
            }
            result.add(current);
            current = current.getNextActual();
        }
        return result;
    }

    /** Все узлы графа (фактические и изоэнтропийные) в порядке обхода в глубину. */
    public List<FlowPoint> allPoints() {
        List<FlowPoint> result = new ArrayList<>();
        if (root == null) {
            return result;
        }
        Set<FlowPoint> visited = new HashSet<>();
        Deque<FlowPoint> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            FlowPoint current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            result.add(current);
            if (current.getNextActual() != null) {
                stack.push(current.getNextActual());
            }
            if (current.getNextIsentropic() != null) {
                stack.push(current.getNextIsentropic());
            }
        }
        return result;
    }

    /**
     * Найти фактическую точку на границе цилиндра/отсека.
     *
     * @throws IllegalArgumentException если точка с таким типом не найдена
     * @throws IllegalStateException    если таких точек несколько
     */
    public FlowPoint boundary(PointCylinderType type) {
        if (type == null) {
            throw new IllegalArgumentException("тип границы цилиндра не задан");
        }
        FlowPoint result = null;
        for (FlowPoint point : actualPath()) {
            FlowPointMetadata metadata = point.getFlowPointMetadata();
            if (metadata != null && metadata.getPointCylinderType() == type) {
                if (result != null) {
                    throw new IllegalStateException("граница " + type + " встречается несколько раз");
                }
                result = point;
            }
        }
        if (result == null) {
            throw new IllegalArgumentException("фактическая граница " + type + " не найдена");
        }
        return result;
    }

    /** Фактические точки указанной ступени. */
    public List<FlowPoint> pointsOf(Stage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("ступень не задана");
        }
        List<FlowPoint> result = new ArrayList<>();
        for (FlowPoint point : actualPath()) {
            FlowPointMetadata metadata = point.getFlowPointMetadata();
            if (metadata != null && Objects.equals(metadata.getStage(), stage)) {
                result.add(point);
            }
        }
        return result;
    }

    /** Фактические точки с указанным типом внутри ступени. */
    public List<FlowPoint> pointsOf(StageFlowPointType type) {
        if (type == null) {
            throw new IllegalArgumentException("тип точки ступени не задан");
        }
        List<FlowPoint> result = new ArrayList<>();
        for (FlowPoint point : actualPath()) {
            FlowPointMetadata metadata = point.getFlowPointMetadata();
            if (metadata != null && metadata.getStageFlowPointType() == type) {
                result.add(point);
            }
        }
        return result;
    }

    /**
     * Проверить целостность графа.
     *
     * <p>Проверяются регистрация всех узлов, тип точки, корректность обратных ссылок,
     * отсутствие циклов и соответствие типа цели типу связи. Пустой граф считается корректным.
     */
    public void validateGraph() {
        if (root == null) {
            if (!pointsById.isEmpty()) {
                throw new IllegalStateException("в графе есть точки, но не задан корень процесса");
            }
            return;
        }
        if (root.getParent() != null) {
            throw new IllegalStateException("корень процесса должен иметь пустого родителя");
        }
        if (pointType(root) != PointType.ACTUAL) {
            throw new IllegalStateException("корень процесса должен быть фактической точкой");
        }
        Set<FlowPoint> visiting = new HashSet<>();
        Set<FlowPoint> done = new HashSet<>();
        validateNode(root, visiting, done);
        for (FlowPoint point : pointsById.values()) {
            if (!done.contains(point)) {
                throw new IllegalStateException("точка " + point.getId() + " не связана с корнем процесса");
            }
        }
        validateUniqueBoundaries();
    }

    private void validateNode(FlowPoint node, Set<FlowPoint> visiting, Set<FlowPoint> done) {
        if (node == null || done.contains(node)) {
            return;
        }
        if (!visiting.add(node)) {
            throw new IllegalStateException("цикл в графе процесса у точки " + node.getId());
        }
        validatePoint(node);

        FlowPoint nextActual = node.getNextActual();
        if (nextActual != null) {
            if (pointType(node) == PointType.ISENTROPIC) {
                throw new IllegalStateException("изоэнтропная точка " + node.getId()
                        + " не может иметь следующую фактическую точку");
            }
            if (pointType(nextActual) != PointType.ACTUAL) {
                throw new IllegalStateException("nextActual точки " + node.getId()
                        + " должен указывать на фактическую точку");
            }
            if (nextActual.getParent() != node) {
                throw new IllegalStateException("обратная ссылка parent не соответствует nextActual "
                        + "у точки " + node.getId());
            }
        }

        FlowPoint nextIsentropic = node.getNextIsentropic();
        if (nextIsentropic != null && pointType(nextIsentropic) != PointType.ISENTROPIC) {
            throw new IllegalStateException("nextIsentropic точки " + node.getId()
                    + " должен указывать на изоэнтропную точку");
        }

        validateNode(nextActual, visiting, done);
        validateNode(nextIsentropic, visiting, done);
        visiting.remove(node);
        done.add(node);
    }

    private void validatePoint(FlowPoint point) {
        if (point.getId() == null) {
            throw new IllegalStateException("точка не зарегистрирована: " + point);
        }
        if (pointsById.get(point.getId()) != point) {
            throw new IllegalStateException("точка " + point.getId() + " отсутствует в реестре");
        }
        if (point.getPoint() == null) {
            throw new IllegalStateException("у точки " + point.getId() + " не заданы параметры состояния");
        }
        FlowPointMetadata metadata = point.getFlowPointMetadata();
        if (metadata == null || metadata.getPointType() == null) {
            throw new IllegalStateException("у точки " + point.getId() + " не задан тип");
        }
        FlowPoint parent = point.getParent();
        if (parent != null && parent.getNextActual() != point) {
            throw new IllegalStateException("parent точки " + point.getId()
                    + " не подтверждается nextActual родителя");
        }
        if (metadata.getStageFlowPointType() != null && metadata.getStage() == null) {
            throw new IllegalStateException("у точки " + point.getId()
                    + " задан тип точки ступени, но не задана ступень");
        }
        if (metadata.getRowKind() != null && metadata.getStage() == null) {
            throw new IllegalStateException("у точки " + point.getId()
                    + " задана решётка, но не задана ступень");
        }
        if (metadata.getStage() != null) {
            Stage stage = metadata.getStage();
            if (stage.getKind() == null) {
                throw new IllegalStateException("у ступени точки " + point.getId() + " не задан тип");
            }
            if (stage.getNumber() <= 0) {
                throw new IllegalStateException("у ступени точки " + point.getId()
                        + " номер должен быть положительным");
            }
        }
        Double massFlow = point.getMassFlow();
        if (massFlow != null && (!(massFlow > 0.0) || !Double.isFinite(massFlow))) {
            throw new IllegalStateException("у точки " + point.getId()
                    + " расход должен быть положительным: " + massFlow);
        }
    }

    private void validateUniqueBoundaries() {
        Map<PointCylinderType, FlowPoint> boundaries = new EnumMap<>(PointCylinderType.class);
        for (FlowPoint point : actualPath()) {
            FlowPointMetadata metadata = point.getFlowPointMetadata();
            if (metadata == null || metadata.getPointCylinderType() == null) {
                continue;
            }
            FlowPoint previous = boundaries.putIfAbsent(metadata.getPointCylinderType(), point);
            if (previous != null) {
                throw new IllegalStateException("граница " + metadata.getPointCylinderType()
                        + " встречается несколько раз");
            }
        }
    }

    private static PointType pointType(FlowPoint point) {
        if (point == null || point.getFlowPointMetadata() == null
                || point.getFlowPointMetadata().getPointType() == null) {
            throw new IllegalStateException("у точки "
                    + (point == null ? null : point.getId()) + " не задан тип");
        }
        return point.getFlowPointMetadata().getPointType();
    }

    private long allocateId() {
        if (nextId <= 0L) {
            nextId = 1L;
        }
        while (pointsById.containsKey(nextId)) {
            nextId++;
        }
        return nextId++;
    }
}

