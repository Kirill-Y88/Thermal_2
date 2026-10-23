package org.kpush88.Thermal_2.v2;

import java.util.*;

/**
 * Граф сцепок соединений тепловой схемы.
 *
 * <p>Вершины — соединения {@link Connection}: сечения трассы, в которых известно
 * состояние рабочего тела. Ребро — «среда идёт из соединения {@code from} в соединение
 * {@code to}». Сами элементы (котёл, турбина, подогреватели, трубы) в граф не входят:
 * элемент превращает свои входы в выходы внутри себя, а рёбра соединяют порты разных
 * элементов.
 *
 * <p>Ключевое свойство модели: у соединения не больше одного входящего и одного
 * исходящего ребра — разветвление и смешение потоков возможны только внутри элемента.
 * Поэтому соседи хранятся парами «вершина → сосед» ({@link #successor},
 * {@link #predecessor}), а {@link #addEdge} падает при попытке занять уже подключённый
 * порт: неявный тройник или неявное смешение собрать нельзя.
 *
 * <p>Из-за степени ≤ 1 рёбра не образуют цепочек «ребро → ребро»: путь чередуется
 * «ребро, элемент, ребро». Обходы и проверка ацикличности такому графу не нужны — они
 * вырождены; цикл тепловой схемы живёт на уровне элементов и легален
 * (котёл → турбина → конденсатор → насос → котёл).
 *
 * <p>Коллекции — {@link LinkedHashSet}/{@link LinkedHashMap}: порядок вершин и рёбер
 * совпадает с порядком добавления, поэтому отчёты и (позже) порядок строк системы
 * уравнений воспроизводимы от запуска к запуску.
 *
 * <p>Тип вершины параметризован, чтобы граф оставался переиспользуемым, но в схеме он
 * всегда {@code SchemeGraph<Connection>}.
 */
public class SchemeGraph<T> {

    /** Ребро графа: направленная пара вершин (from -> to). */
    public record Edge<V>(V from, V to) {

        @Override
        public String toString() {
            return from + " -> " + to;
        }
    }

    // все вершины, в том числе неподключённые
    private final Set<T> vertices = new LinkedHashSet<>();
    // вершина -> сосед по исходящему ребру (не больше одного)
    private final Map<T, T> outgoing = new LinkedHashMap<>();
    // вершина -> сосед по входящему ребру (не больше одного)
    private final Map<T, T> incoming = new LinkedHashMap<>();

    /** Регистрирует вершину без рёбер (например, ещё не подключённый порт элемента). */
    public void addVertex(T vertex) {
        vertices.add(vertex);
    }

    /** Регистрирует несколько вершин (порты элемента) в порядке перечисления. */
    public void addVertices(Collection<? extends T> vertices) {
        this.vertices.addAll(vertices);
    }

    /**
     * Добавляет ребро {@code from -> to}; повторное добавление того же ребра игнорируется.
     *
     * @throws IllegalArgumentException если ребро — петля ({@code from} и {@code to} — одна вершина)
     * @throws IllegalStateException    если у {@code from} уже есть исходящее ребро или у
     *                                  {@code to} — входящее: разветвление и смешение
     *                                  допускаются только внутри элемента
     */
    public void addEdge(T from, T to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            throw new IllegalArgumentException("петля: соединение не может ссылаться само на себя: " + from);
        }
        addVertex(from);
        addVertex(to);

        T currentTarget = outgoing.get(from);
        if (currentTarget != null && !currentTarget.equals(to)) {
            throw new IllegalStateException("у соединения " + from + " уже есть исходящее ребро -> "
                    + currentTarget + "; разветвление потока возможно только внутри элемента (PipeTee)");
        }
        T currentSource = incoming.get(to);
        if (currentSource != null && !currentSource.equals(from)) {
            throw new IllegalStateException("в соединение " + to + " уже входит ребро из " + currentSource
                    + "; смешение потоков возможно только внутри элемента (MixingHeater)");
        }
        outgoing.put(from, to);
        incoming.put(to, from);
    }

    /** Удаляет ребро from -> to, если оно есть. */
    public void removeEdge(T from, T to) {
        if (to.equals(outgoing.get(from))) {
            outgoing.remove(from);
        }
        if (from.equals(incoming.get(to))) {
            incoming.remove(to);
        }
    }

    /** Зарегистрирована ли вершина в графе. */
    public boolean hasVertex(T vertex) {
        return vertices.contains(vertex);
    }

    /** Есть ли ребро from -> to. */
    public boolean hasEdge(T from, T to) {
        return to.equals(outgoing.get(from));
    }

    /** Сосед по исходящему ребру: единственный, если ребро есть. */
    public Optional<T> successor(T vertex) {
        return Optional.ofNullable(outgoing.get(vertex));
    }

    /** Сосед по входящему ребру: единственный, если ребро есть. */
    public Optional<T> predecessor(T vertex) {
        return Optional.ofNullable(incoming.get(vertex));
    }

    /** Все вершины в порядке регистрации — только чтение. */
    public Set<T> getVertices() {
        return Collections.unmodifiableSet(vertices);
    }

    /** Все рёбра в порядке добавления. */
    public List<Edge<T>> edges() {
        List<Edge<T>> edges = new ArrayList<>(outgoing.size());
        for (Map.Entry<T, T> entry : outgoing.entrySet()) {
            edges.add(new Edge<>(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableList(edges);
    }

    /** Число вершин (соединений) в графе. */
    public int vertexCount() {
        return vertices.size();
    }

    /** Число рёбер (связей между соединениями). */
    public int edgeCount() {
        return outgoing.size();
    }

    /** Входящая степень вершины: 0 или 1. */
    public int inDegree(T vertex) {
        return incoming.containsKey(vertex) ? 1 : 0;
    }

    /** Исходящая степень вершины: 0 или 1. */
    public int outDegree(T vertex) {
        return outgoing.containsKey(vertex) ? 1 : 0;
    }

    /** Полная степень вершины: 0 (висячая) или 1 (подключённая с одной стороны). */
    public int degree(T vertex) {
        return inDegree(vertex) + outDegree(vertex);
    }

}
