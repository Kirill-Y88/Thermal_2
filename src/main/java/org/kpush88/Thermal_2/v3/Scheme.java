package org.kpush88.Thermal_2.v3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Тепловая схема: реестр элементов и связей между ними.
 *
 * <p>Схема — единственное место, где создаются связи и, значит, единственное место, где
 * конструкция схемы может измениться. Связь ({@link Connection}) сразу знает оба
 * <b>порта</b>, поэтому отдельный граф для уравнений не нужен и не хранится: граф потоков
 * ({@link #flowGraph()}) выводится из портов и каналов элементов одним проходом при каждом
 * обращении.
 *
 * <p>Сборка идёт в два шага, как это будет делать загрузчик схемы: сначала элементы
 * ({@link #add}), затем связи «выход → вход» ({@link #link}). Элемент добавляется до своих
 * связей, поэтому {@link #add} отвергает элемент, у которого связи уже есть: иначе схема
 * получила бы связи, созданные в обход неё.
 *
 * <pre>{@code
 * Scheme scheme = new Scheme().add(boiler).add(liveSteamPipe).add(turbine);
 * scheme.link(boiler.steamOut(), liveSteamPipe.in(), "свежий пар");
 * scheme.link(liveSteamPipe.out(), turbine.steamIn(), "свежий пар");
 * }</pre>
 *
 * <p>Проверки при подключении: порт принадлежит зарегистрированному элементу, направление
 * «выход → вход», на порту есть свободная кратность, контуры концов совпадают. Последние
 * два правила — то, ради чего порты и введены: «второй патрубок» и «пар в водяной порт»
 * отвергаются в момент сборки, а не всплывают в расчёте.
 */
public class Scheme {

    private final List<Element> elements = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();

    /** Регистрирует элемент. Связи элемента создаются позже вызовами {@link #link}. */
    public Scheme add(Element element) {
        Objects.requireNonNull(element, "element");
        if (elements.contains(element)) {
            throw new IllegalArgumentException("элемент «" + element.name()
                    + "» уже добавлен в схему");
        }
        if (!element.connections().isEmpty()) {
            throw new IllegalStateException("элемент «" + element.name()
                    + "» добавлен в схему с готовыми связями: связи создаёт сама схема, "
                    + "вызовом link(...)");
        }
        elements.add(element);
        return this;
    }

    /**
     * Создаёт и регистрирует связь «выход элемента {@code out} → вход элемента {@code in}».
     *
     * @param out   выходной порт источника, элемент которого уже добавлен в схему
     * @param in    входной порт приёмника, элемент которого уже добавлен в схему
     * @param uname подпись связи: среда, начинающая участок (может быть {@code null})
     * @return созданная связь — в неё же записывается состояние потока
     * @throws IllegalArgumentException если порт не зарегистрирован в схеме, направление не
     *                                  «выход → вход», контуры не совпадают или связь
     *                                  замкнута на одном элементе
     * @throws IllegalStateException    если кратность порта исчерпана
     */
    public Connection link(Port out, Port in, String uname) {
        requirePort(out);
        requirePort(in);
        requireDirection(out, PortDirection.OUT);
        requireDirection(in, PortDirection.IN);
        requireCapacity(out);
        requireCapacity(in);
        return register(new Connection(uname, out, in));
    }

    /** Связь без подписи: имя для отчётов собирается из имён элементов. */
    public Connection link(Port out, Port in) {
        return link(out, in, null);
    }

    /**
     * Создаёт связь, у которой известен только источник: выход элемента за границей схемы
     * (приток в схему ещё не смоделирован). Диагностируется {@link #danglingConnections()},
     * но ошибкой не считается.
     */
    public Connection openOut(Port out, String uname) {
        requirePort(out);
        requireDirection(out, PortDirection.OUT);
        requireCapacity(out);
        return register(new Connection(uname, out, null));
    }

    /**
     * Создаёт связь, у которой известен только приёмник: вход элемента из-за границы схемы
     * (например, питательная вода от внешнего источника). Диагностируется
     * {@link #danglingConnections()}, но ошибкой не считается.
     */
    public Connection openIn(Port in, String uname) {
        requirePort(in);
        requireDirection(in, PortDirection.IN);
        requireCapacity(in);
        return register(new Connection(uname, null, in));
    }

    // ---------- содержимое схемы ----------

    /** Элементы схемы в порядке добавления (только чтение). */
    public List<Element> getElements() {
        return Collections.unmodifiableList(elements);
    }

    /** Связи схемы в порядке создания (только чтение). */
    public List<Connection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    /** Связи без второго конца: начало или конец схемы. Это диагностика, а не ошибка. */
    public List<Connection> danglingConnections() {
        List<Connection> dangling = new ArrayList<>();
        for (Connection connection : connections) {
            if (connection.isOpen()) {
                dangling.add(connection);
            }
        }
        return Collections.unmodifiableList(dangling);
    }

    /**
     * Структурные ошибки схемы; пустой список — схема собрана корректно.
     *
     * <p>Проверяется: связь зарегистрирована один раз и хотя бы к одному элементу; оба её
     * конца принадлежат этой схеме; связь не соединяет два аппарата напрямую — между ними
     * должен стоять участок трассы ({@link Line}: труба или тройник); давление и расход не
     * отрицательны; у каждого элемента сошлись списки связей и кратность портов, и каждый
     * порт входит ровно в один канал потока ({@link Element#validate()}).
     *
     * <p>Связь с открытым концом и элемент без связей ошибкой не считаются — это граница
     * схемы, её отдаёт {@link #danglingConnections()}. Направление портов, «петля» на одном
     * элементе и совпадение контуров проверяются при создании связи ({@link #link}) и здесь
     * уже невозможны.
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        Set<Connection> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Connection connection : connections) {
            String name = "связь «" + connection.getName() + "» ["
                    + connection.getPortsUnames() + "]";
            if (!seen.add(connection)) {
                problems.add(name + " зарегистрирована в схеме дважды");
            }
            Element out = connection.getElementOut();
            Element in = connection.getElementIn();
            if (out != null && !elements.contains(out)) {
                problems.add(name + " выходит из элемента «" + out.name()
                        + "», не зарегистрированного в схеме");
            }
            if (in != null && !elements.contains(in)) {
                problems.add(name + " входит в элемент «" + in.name()
                        + "», не зарегистрированного в схеме");
            }
            if (out != null && in != null && !(out instanceof Line) && !(in instanceof Line)) {
                problems.add(name + " соединяет два аппарата напрямую: между элементами «"
                        + out.name() + "» и «" + in.name()
                        + "» должен стоять трубопровод (Pipe/PipeTee)");
            }
            if (connection.getPressure() != null && connection.getPressure() < 0) {
                problems.add(name + " имеет отрицательное давление: "
                        + connection.getPressure() + " МПа");
            }
            if (connection.getFlow() != null && connection.getFlow() < 0) {
                problems.add(name + " имеет отрицательный расход: "
                        + connection.getFlow() + " кг/с");
            }
        }

        for (Element element : elements) {
            for (Connection connection : element.connections()) {
                if (!connections.contains(connection)) {
                    problems.add("связь «" + connection.getName() + "» числится у элемента «"
                            + element.name() + "», но не зарегистрирована в схеме");
                }
            }
            problems.addAll(element.validate());
        }

        return problems;
    }

    private Connection register(Connection connection) {
        connections.add(connection);
        if (connection.getPortOut() != null) {
            connection.getPortOut().element().registerOutput(connection);
        }
        if (connection.getPortIn() != null) {
            connection.getPortIn().element().registerInput(connection);
        }
        return connection;
    }

    // ---------- проверки при подключении ----------

    private void requirePort(Port port) {
        Objects.requireNonNull(port, "port");
        Element element = port.element();
        if (!elements.contains(element)) {
            throw new IllegalArgumentException("порт " + port.label()
                    + " принадлежит элементу «" + element.name()
                    + "», не зарегистрированному в схеме: сначала вызовите add(...)");
        }
        if (!element.owns(port)) {
            throw new IllegalArgumentException("порт " + port.label()
                    + " не объявлен элементом «" + element.name() + "»");
        }
    }

    private void requireDirection(Port port, PortDirection direction) {
        if (port.direction() != direction) {
            throw new IllegalArgumentException("порт " + port.label() + " — "
                    + port.direction().title() + ", а связь подключается со стороны «"
                    + direction.title() + "»");
        }
    }

    /**
     * Кратность порта: связь сверх объявленного максимума — ошибка сборки.
     *
     * <p>Проверка здесь, а не только в {@link #validate()}, потому что место ошибки известно:
     * видно, какой порт переполнен и сколько связей он уже принял. Минимум кратности —
     * наоборот, проверяется валидатором: незанятый обязательный порт означает неполную
     * схему, а не ошибку конкретного вызова.
     */
    private void requireCapacity(Port port) {
        int size = port.element().portSize(port);
        if (size >= port.max()) {
            throw new IllegalStateException("порт " + port.label() + " уже принимает " + size
                    + " связей, а объявлено не более " + port.max()
                    + ": для нескольких потоков нужен коллектор с большей кратностью");
        }
    }

    // ---------- граф потоков ----------

    /**
     * Граф потоков: вершины — связи, рёбра — «входная связь → выходная связь» внутри канала
     * элемента ({@link FlowChannel}).
     *
     * <p>Граф <b>не хранится</b>: он строится заново за один проход по элементам, поэтому не
     * может разойтись с конструкцией. Число рёбер на канал = произведению числа входных
     * связей на число выходных: у турбины (1 вход × 4 выхода) четыре ребра, у деаэратора
     * (3 входа × 1 выход) три, у трубы одно.
     *
     * <p>Рёбра — только массовые: тепло между каналами внутри аппарата не ребро. Поэтому
     * «пар греет воду» не превращается в ложный путь, а баланс каждого канала считается по
     * его портам, без обхода графа.
     *
     * <p>Порядок вершин — порядок создания связей, порядок рёбер — порядок элементов,
     * каналов и связей на портах, поэтому результат воспроизводим и годится для отчётов и
     * нумерации неизвестных.
     */
    public SchemeGraph<Connection> flowGraph() {
        SchemeGraph<Connection> graph = new SchemeGraph<>();
        for (Connection connection : connections) {
            graph.addVertex(connection);
        }
        for (Element element : elements) {
            for (FlowChannel channel : element.flowChannels()) {
                for (Port in : channel.in()) {
                    List<Connection> sources = element.getInPutConnections(in);
                    for (Port out : channel.out()) {
                        List<Connection> targets = element.getOutPutConnections(out);
                        for (Connection source : sources) {
                            for (Connection target : targets) {
                                graph.addEdge(source, target);
                            }
                        }
                    }
                }
            }
        }
        return graph;
    }

    /**
     * Граф потоков одного контура: те же рёбра, у которых оба конца в этом контуре.
     *
     * <p>Переход контура внутри элемента ({@link FlowChannel#crossesSpaces()}) в такой граф
     * не попадает: питательная вода кончается на входе котла, а пар начинается на его
     * выходе. Так паровая и водяная части схемы обходятся раздельно, а место превращения
     * остаётся в уравнении элемента, где ему и место.
     */
    public SchemeGraph<Connection> flowGraph(SpaceType space) {
        Objects.requireNonNull(space, "space");
        SchemeGraph<Connection> filtered = new SchemeGraph<>();
        for (Connection connection : connections) {
            if (connection.getSpace() == space) {
                filtered.addVertex(connection);
            }
        }
        for (Element element : elements) {
            for (FlowChannel channel : element.flowChannels()) {
                for (Port in : channel.in()) {
                    if (in.space() != space) {
                        continue;
                    }
                    for (Port out : channel.out()) {
                        if (out.space() != space) {
                            continue;
                        }
                        for (Connection source : element.getInPutConnections(in)) {
                            for (Connection target : element.getOutPutConnections(out)) {
                                filtered.addEdge(source, target);
                            }
                        }
                    }
                }
            }
        }
        return filtered;
    }
}
