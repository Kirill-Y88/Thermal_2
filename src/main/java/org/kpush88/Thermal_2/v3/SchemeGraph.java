package org.kpush88.Thermal_2.v3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Направленный граф потоков: вершины — связи ({@link Connection}), ребро — «среда идёт из
 * связи {@code from} в связь {@code to} через элемент».
 *
 * <p>В отличие от {@code SchemeGraph} из v2, где граф был источником истины и запрещал
 * второй конец на порту, здесь граф <b>производный</b>: его строит
 * {@link Scheme#flowGraph()} одним проходом по каналам элементов ({@link FlowChannel}), и он
 * не хранится в схеме. Поэтому он не может разойтись с конструкцией: связи и порты
 * добавляются только через {@code Scheme}, а рёбра пересчитываются заново при каждом
 * вызове. Степень вершины здесь не ограничена — у турбины один вход и четыре выхода, у
 * деаэратора три входа; ограничение кратности перенесено на порты, где ему и место.
 *
 * <p>Рёбра — только <b>массовые</b>. Тепло передаётся между каналами внутри аппарата
 * (греющий пар → питательная вода) и ребром не является: иначе в графе появились бы
 * ложные циклы и «путь пара в воду», которого нет. Поэтому граф отвечает на вопросы
 * «куда идёт среда» и «сколько потоков смешивается», но не «что кого греет» — второе
 * задаётся уравнениями элемента.
 *
 * <p>Коллекции — {@link LinkedHashMap}/{@link LinkedHashSet}: порядок вершин и рёбер
 * совпадает с порядком сборки схемы, поэтому отчёты, порядок строк системы уравнений и
 * результаты обхода воспроизводимы от запуска к запуску.
 *
 * <p>Тип вершины параметризован, чтобы граф оставался переиспользуемым; в схеме он всегда
 * {@code SchemeGraph<Connection>}.
 */
public class SchemeGraph<T> {

    /** Ребро графа: направленная пара вершин (from → to). */
    public record Edge<V>(V from, V to) {

        @Override
        public String toString() {
            return from + " -> " + to;
        }
    }

    // все вершины, в том числе неподключённые
    private final Set<T> vertices = new LinkedHashSet<>();
    // вершина -> соседи по исходящим рёбрам в порядке добавления
    private final Map<T, Set<T>> outgoing = new LinkedHashMap<>();
    // вершина -> соседи по входящим рёбрам в порядке добавления
    private final Map<T, Set<T>> incoming = new LinkedHashMap<>();

    /** Регистрирует вершину без рёбер. */
    public void addVertex(T vertex) {
        Objects.requireNonNull(vertex, "vertex");
        vertices.add(vertex);
    }

    /** Регистрирует несколько вершин в порядке перечисления. */
    public void addVertices(Iterable<? extends T> vertices) {
        Objects.requireNonNull(vertices, "vertices");
        for (T vertex : vertices) {
            addVertex(vertex);
        }
    }

    /**
     * Добавляет ребро {@code from → to}; повторное добавление того же ребра игнорируется.
     *
     * @throws IllegalArgumentException если ребро — петля ({@code from} и {@code to} — одна
     *                                  вершина): поток не может питать сам себя
     */
    public void addEdge(T from, T to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            throw new IllegalArgumentException("петля: связь не может питать сама себя: " + from);
        }
        addVertex(from);
        addVertex(to);
        outgoing.computeIfAbsent(from, key -> new LinkedHashSet<>()).add(to);
        incoming.computeIfAbsent(to, key -> new LinkedHashSet<>()).add(from);
    }

    // ---------- степени и доступ ----------

    /** Удаляет ребро from → to, если оно есть. */
    public void removeEdge(T from, T to) {
        Set<T> targets = outgoing.get(from);
        if (targets != null) {
            targets.remove(to);
            if (targets.isEmpty()) {
                outgoing.remove(from);
            }
        }
        Set<T> sources = incoming.get(to);
        if (sources != null) {
            sources.remove(from);
            if (sources.isEmpty()) {
                incoming.remove(to);
            }
        }
    }

    /** Зарегистрирована ли вершина в графе. */
    public boolean hasVertex(T vertex) {
        return vertices.contains(vertex);
    }

    /** Есть ли ребро from → to. */
    public boolean hasEdge(T from, T to) {
        Set<T> targets = outgoing.get(from);
        return targets != null && targets.contains(to);
    }

    /** Все вершины в порядке регистрации — только чтение. */
    public Set<T> getVertices() {
        return Collections.unmodifiableSet(vertices);
    }

    /** Соседи по исходящим рёбрам в порядке добавления. */
    public List<T> successors(T vertex) {
        Set<T> targets = outgoing.get(vertex);
        return targets == null ? List.of() : List.copyOf(targets);
    }

    /** Соседи по входящим рёбрам в порядке добавления. */
    public List<T> predecessors(T vertex) {
        Set<T> sources = incoming.get(vertex);
        return sources == null ? List.of() : List.copyOf(sources);
    }

    /** Все рёбра в порядке добавления. */
    public List<Edge<T>> edges() {
        List<Edge<T>> edges = new ArrayList<>();
        for (Map.Entry<T, Set<T>> entry : outgoing.entrySet()) {
            for (T target : entry.getValue()) {
                edges.add(new Edge<>(entry.getKey(), target));
            }
        }
        return Collections.unmodifiableList(edges);
    }

    /** Число вершин. */
    public int vertexCount() {
        return vertices.size();
    }

    /** Число рёбер. */
    public int edgeCount() {
        int count = 0;
        for (Set<T> targets : outgoing.values()) {
            count += targets.size();
        }
        return count;
    }

    /** Входящая степень вершины. */
    public int inDegree(T vertex) {
        Set<T> sources = incoming.get(vertex);
        return sources == null ? 0 : sources.size();
    }

    /** Исходящая степень вершины. */
    public int outDegree(T vertex) {
        Set<T> targets = outgoing.get(vertex);
        return targets == null ? 0 : targets.size();
    }

    /** Полная степень вершины. */
    public int degree(T vertex) {
        return inDegree(vertex) + outDegree(vertex);
    }

    // ---------- обходы ----------

    /**
     * Все вершины, достижимые из {@code start} по направлению потока, включая саму
     * {@code start}. Порядок — обход в ширину от ближайших к дальним.
     *
     * <p>Это и есть ответ на вопрос «куда уходит среда из этого сечения»: например, из
     * свежего пара достижимы все отборы, выхлоп и весь водяной тракт, потому что цикл
     * замкнут.
     */
    public Set<T> reachableFrom(T start) {
        Objects.requireNonNull(start, "start");
        Set<T> visited = new LinkedHashSet<>();
        if (!vertices.contains(start)) {
            return Collections.unmodifiableSet(visited);
        }
        Deque<T> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            for (T next : successors(queue.poll())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return Collections.unmodifiableSet(visited);
    }

    /**
     * Топологический порядок вершин — порядок, в котором состояние можно считать за один
     * проход по графу (каждая вершина после всех своих предшественников). При равных
     * условиях выбирается вершина, добавленная раньше, поэтому порядок воспроизводим.
     *
     * @throws IllegalStateException если граф содержит цикл: в замкнутой тепловой схеме
     *                               контур воды цикличен, и считать его нужно блоками
     *                               ({@link #stronglyConnectedComponents()})
     */
    public List<T> topologicalOrder() {
        List<T> order = sortTopologically();
        if (order.size() != vertices.size()) {
            Set<T> left = new LinkedHashSet<>(vertices);
            left.removeAll(order);
            throw new IllegalStateException("граф содержит цикл: топологический порядок "
                    + "невозможен, не упорядочено вершин — " + left.size()
                    + "; циклы находит stronglyConnectedComponents(): " + left);
        }
        return Collections.unmodifiableList(order);
    }

    /** В графе есть цикл (замкнутый контур, по которому среда возвращается в себя). */
    public boolean hasCycle() {
        return sortTopologically().size() != vertices.size();
    }

    private List<T> sortTopologically() {
        Map<T, Integer> remaining = new LinkedHashMap<>();
        for (T vertex : vertices) {
            remaining.put(vertex, inDegree(vertex));
        }
        Deque<T> ready = new ArrayDeque<>();
        for (T vertex : vertices) {
            if (remaining.get(vertex) == 0) {
                ready.add(vertex);
            }
        }
        List<T> order = new ArrayList<>(vertices.size());
        while (!ready.isEmpty()) {
            T vertex = ready.poll();
            order.add(vertex);
            for (T next : successors(vertex)) {
                int left = remaining.merge(next, -1, Integer::sum);
                if (left == 0) {
                    ready.add(next);
                }
            }
        }
        return order;
    }

    // ---------- компоненты сильной связности ----------

    /**
     * Компоненты сильной связности (Косараю): каждая — максимальная группа вершин, из
     * любой в любую есть путь по направлению потока.
     *
     * <p>Компонента размера 1 без петли — участок, который считается за один проход;
     * компонента больше 1 — замкнутый контур, который нужно решать совместно. В
     * регенеративной схеме контур воды (деаэратор → насос → ПВД → котёл → турбина →
     * конденсатор → ПНД → деаэратор) образует одну большую компоненту, а цепочка дренажей
     * ПВД — ациклические компоненты по одной вершине. Это и есть блочно-треугольная
     * структура системы уравнений: блоки можно решать по порядку компонент, а внутри
     * большой компоненты — итерационно.
     *
     * <p>Порядок: компоненты выдаются в порядке, обратном порядку завершения обхода, то
     * есть истоки конденсации идут первыми. Внутри компоненты вершины — в порядке обхода.
     */
    public List<Set<T>> stronglyConnectedComponents() {
        List<T> finish = finishOrder();
        Set<T> assigned = new LinkedHashSet<>();
        List<Set<T>> components = new ArrayList<>();
        for (int i = finish.size() - 1; i >= 0; i--) {
            T start = finish.get(i);
            if (!assigned.add(start)) {
                continue;
            }
            Set<T> component = new LinkedHashSet<>();
            Deque<T> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                T vertex = stack.pop();
                component.add(vertex);
                for (T previous : predecessors(vertex)) {
                    if (assigned.add(previous)) {
                        stack.push(previous);
                    }
                }
            }
            components.add(Collections.unmodifiableSet(component));
        }
        return Collections.unmodifiableList(components);
    }

    /** Порядок завершения обхода в глубину, итеративно — без риска переполнить стек. */
    private List<T> finishOrder() {
        Set<T> visited = new LinkedHashSet<>();
        List<T> order = new ArrayList<>(vertices.size());
        for (T start : vertices) {
            if (!visited.add(start)) {
                continue;
            }
            Deque<T> stack = new ArrayDeque<>();
            Deque<Iterator<T>> iterators = new ArrayDeque<>();
            stack.push(start);
            iterators.push(successors(start).iterator());
            while (!stack.isEmpty()) {
                Iterator<T> iterator = iterators.peek();
                if (iterator.hasNext()) {
                    T next = iterator.next();
                    if (visited.add(next)) {
                        stack.push(next);
                        iterators.push(successors(next).iterator());
                    }
                } else {
                    iterators.pop();
                    order.add(stack.pop());
                }
            }
        }
        return order;
    }
}
