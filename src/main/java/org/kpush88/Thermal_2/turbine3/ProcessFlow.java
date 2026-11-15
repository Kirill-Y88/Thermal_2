package org.kpush88.Thermal_2.turbine3;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class ProcessFlow {

    private Turbine turbine;

    private FlowPoint root;

    /** Реестр всех узлов по идентификатору. */
    private final Map<Long, FlowPoint> pointsById = new HashMap<>();

    private long nextId = 1L;

    /** Зарегистрировать точку и присвоить ей идентификатор. */
    public FlowPoint register(FlowPoint point) {
        if (point == null) {
            throw new IllegalArgumentException("точка не задана");
        }
        if (point.getId() == null) {
            point.setId(nextId++);
        }
        pointsById.putIfAbsent(point.getId(), point);
        return point;
    }

    /** Связать две реальные точки: {@code from → to} и обратная ссылка {@code to.parent = from}. */
    public FlowPoint linkActual(FlowPoint from, FlowPoint to) {
        register(from);
        register(to);
        from.setNextActual(to);
        to.setParent(from);
        return to;
    }

    /** Вставить точку в реальную цепочку сразу после {@code anchor}. */
    public FlowPoint insertActualAfter(FlowPoint anchor, FlowPoint point) {
        register(anchor);
        register(point);
        FlowPoint oldNext = anchor.getNextActual();
        point.setParent(anchor);
        point.setNextActual(oldNext);
        anchor.setNextActual(point);
        if (oldNext != null) {
            oldNext.setParent(point);
        }
        return point;
    }

    /** Добавить точку в конец реальной цепочки. */
    public FlowPoint appendActual(FlowPoint point) {
        register(point);
        if (root == null) {
            root = point;
            return point;
        }
        FlowPoint last = root;
        while (last.getNextActual() != null) {
            last = last.getNextActual();
        }
        return insertActualAfter(last, point);
    }

    /** Связать точку с её изоэнтропным состоянием или следующей изоэнтропной точкой. */
    public FlowPoint linkIsentropic(FlowPoint from, FlowPoint to) {
        register(from);
        register(to);
        from.setNextIsentropic(to);
        return to;
    }

    /** Реальная цепочка процесса в порядке прохождения пара. */
    public List<FlowPoint> actualPath() {
        List<FlowPoint> result = new ArrayList<>();
        FlowPoint current = root;
        while (current != null) {
            result.add(current);
            current = current.getNextActual();
        }
        return result;
    }

    /** Все узлы графа (реальные и изоэнтропные) в порядке обхода в глубину. */
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

    /** Проверка целостности графа: ацикличность, обратные ссылки и регистрация точек. */
    public void validateGraph() {
        if (root == null) {
            return;
        }
        if (root.getParent() != null) {
            throw new IllegalStateException("корень процесса должен иметь пустого родителя");
        }
        validateNode(root, new HashSet<>(), new HashSet<>());
    }

    private void validateNode(FlowPoint node, Set<FlowPoint> visiting, Set<FlowPoint> done) {
        if (node == null || done.contains(node)) {
            return;
        }
        if (visiting.contains(node)) {
            throw new IllegalStateException("цикл в графе процесса у точки " + node.getId());
        }
        if (node.getPointType() == null) {
            throw new IllegalStateException("у точки " + node.getId() + " не задан тип");
        }
        if (node.getId() == null) {
            throw new IllegalStateException("точка не зарегистрирована: " + node);
        }
        if (node.getNextActual() != null && node.getNextActual().getParent() != node) {
            throw new IllegalStateException("обратная ссылка parent не соответствует nextActual "
                    + "у точки " + node.getId());
        }
        visiting.add(node);
        validateNode(node.getNextActual(), visiting, done);
        validateNode(node.getNextIsentropic(), visiting, done);
        visiting.remove(node);
        done.add(node);
    }
}
