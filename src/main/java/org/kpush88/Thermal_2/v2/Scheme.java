package org.kpush88.Thermal_2.v2;

import java.util.*;

/**
 * Тепловая схема: элементы, их соединения и рёбра между соединениями.
 *
 * <p>Схема — единственное место, где известно, какому элементу принадлежит соединение и
 * является ли оно входом или выходом. Поэтому именно она, а не сам граф, умеет
 * связывать «выход с входом» и проверять, что схема собрана корректно.
 *
 * <p>Аппараты соединяются только через участки трассы ({@link Line}): ребро графа —
 * сварной стык двух патрубков, поэтому линия «котёл → турбина» существует как элемент
 * {@link Pipe}. Ребро «аппарат → аппарат» — ошибка сборки, её сообщает {@link #validate()}.
 *
 * <p>Сборка идёт в два шага, как это будет делать загрузчик схемы: сначала элементы
 * со своими портами ({@link #add}), затем связи портов ({@link #link}). Порты должны
 * быть созданы до добавления элемента — иначе схема о них не узнает, и
 * {@link #validate()} об этом сообщит.
 *
 * <pre>{@code
 * Scheme scheme = new Scheme().add(boiler).add(liveSteamPipe).add(turbine);
 * scheme.link(boilerLiveSteam, liveSteamPipeIn);
 * scheme.link(liveSteamPipeOut, turbineLiveSteam);
 * }</pre>
 */
public class Scheme {

    private final SchemeGraph<Connection> graph = new SchemeGraph<>();
    private final List<Element> elements = new ArrayList<>();
    // порт -> элемент-владелец
    private final Map<Connection, Element> owners = new LinkedHashMap<>();
    private final Set<Connection> inputs = new LinkedHashSet<>();
    private final Set<Connection> outputs = new LinkedHashSet<>();

    /** Регистрирует элемент и все его порты как вершины графа. */
    public Scheme add(Element element) {
        Objects.requireNonNull(element, "element");
        if (elements.contains(element)) {
            throw new IllegalArgumentException("элемент уже добавлен в схему: " + element);
        }
        for (Connection port : element.connections()) {
            Element owner = owners.get(port);
            if (owner != null) {
                throw new IllegalStateException("соединение «" + port.getName() + "» уже принадлежит элементу "
                        + owner.name() + ": один порт не может обслуживать два элемента");
            }
            owners.put(port, element);
        }
        inputs.addAll(element.getInputConnections());
        outputs.addAll(element.getOutputConnections());
        elements.add(element);
        graph.addVertices(element.connections());
        return this;
    }

    /**
     * Связывает выход одного элемента со входом другого (ребро {@code from -> to}).
     *
     * @throws IllegalArgumentException если порт не принадлежит схеме или направление
     *                                  связи не «выход → вход»
     * @throws IllegalStateException    если порт уже подключён (см. {@link SchemeGraph#addEdge})
     */
    public Scheme link(Connection from, Connection to) {
        requireRegistered(from);
        requireRegistered(to);
        if (!outputs.contains(from)) {
            throw new IllegalArgumentException("ребро должно выходить из выхода элемента, но «"
                    + from.getName() + "» — вход элемента " + owners.get(from).name());
        }
        if (!inputs.contains(to)) {
            throw new IllegalArgumentException("ребро должно входить во вход элемента, но «"
                    + to.getName() + "» — выход элемента " + owners.get(to).name());
        }
        graph.addEdge(from, to);
        return this;
    }

    /** Элемент, которому принадлежит порт. */
    public Optional<Element> ownerOf(Connection port) {
        return Optional.ofNullable(owners.get(port));
    }

    /** Является ли порт входом своего элемента. */
    public boolean isInput(Connection port) {
        return inputs.contains(port);
    }

    /** Является ли порт выходом своего элемента. */
    public boolean isOutput(Connection port) {
        return outputs.contains(port);
    }

    /** Все порты схемы в порядке регистрации — только чтение. */
    public Set<Connection> getPorts() {
        return Collections.unmodifiableSet(owners.keySet());
    }

    /** Элементы схемы в порядке добавления — только чтение. */
    public List<Element> getElements() {
        return Collections.unmodifiableList(elements);
    }

    public SchemeGraph<Connection> getGraph() {
        return graph;
    }

    /**
     * Порты без рёбер: схема не замкнута. Это не ошибка, а диагностика — элемент,
     * который должен закрыть порт, может быть ещё не добавлен в схему.
     */
    public List<Connection> danglingPorts() {
        List<Connection> dangling = new ArrayList<>();
        for (Connection port : owners.keySet()) {
            if (graph.degree(port) == 0) {
                dangling.add(port);
            }
        }
        return Collections.unmodifiableList(dangling);
    }

    /**
     * Структурные ошибки схемы; пустой список — схема собрана корректно.
     *
     * <p>Проверяется: у каждой вершины графа есть элемент-владелец; все порты элементов
     * зарегистрированы; порт не является одновременно входом и выходом; каждое ребро
     * соединяет выход с входом, не замыкается на том же элементе и не соединяет два
     * аппарата напрямую — между ними должен стоять участок трассы ({@link Line}: труба
     * или тройник). Висячие порты ошибкой не считаются — их отдаёт
     * {@link #danglingPorts()}.
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();

        for (Connection vertex : graph.getVertices()) {
            if (!owners.containsKey(vertex)) {
                problems.add("вершина «" + vertex.getName() + "» не принадлежит ни одному элементу схемы");
            }
        }

        for (Element element : elements) {
            for (Connection port : element.connections()) {
                if (owners.get(port) != element) {
                    problems.add("порт «" + port.getName() + "» элемента " + element.name()
                            + " не зарегистрирован в схеме: порт создан после добавления элемента");
                }
            }
        }

        for (Connection port : inputs) {
            if (outputs.contains(port)) {
                problems.add("соединение «" + port.getName() + "» одновременно вход и выход элемента "
                        + owners.get(port).name());
            }
        }

        for (SchemeGraph.Edge<Connection> edge : graph.edges()) {
            String name = "«" + edge.from().getName() + " -> " + edge.to().getName() + "»";
            if (!outputs.contains(edge.from())) {
                problems.add("ребро " + name + " выходит не из выхода элемента");
            }
            if (!inputs.contains(edge.to())) {
                problems.add("ребро " + name + " входит не во вход элемента");
            }
            Element from = owners.get(edge.from());
            Element to = owners.get(edge.to());
            if (from != null && from == to) {
                problems.add("ребро " + name + " замкнуто на элементе " + from.name());
            }
            if (from != null && to != null && from != to
                    && !(from instanceof Line) && !(to instanceof Line)) {
                problems.add("ребро " + name + " соединяет два аппарата напрямую: между элементами «"
                        + from.name() + "» и «" + to.name()
                        + "» должен стоять трубопровод (Pipe/PipeTee)");
            }
        }

        return problems;
    }

    private void requireRegistered(Connection port) {
        if (port == null) {
            throw new IllegalArgumentException("соединение не задано");
        }
        if (!owners.containsKey(port)) {
            throw new IllegalArgumentException("соединение «" + port.getName()
                    + "» не принадлежит ни одному элементу схемы");
        }
    }
}