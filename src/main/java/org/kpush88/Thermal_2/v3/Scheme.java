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
 * <p>Схема — единственное место, где создаются связи. Связь ({@link Connection}) сразу
 * знает оба конца, поэтому ей не нужен отдельный граф: список связей и есть схема, а
 * «порты» элемента — это его связи, зарегистрированные в его же списках.
 *
 * <p>Сборка идёт в два шага, как это будет делать загрузчик схемы: сначала элементы
 * ({@link #add}), затем связи «выход → вход» ({@link #link}). Элемент добавляется до своих
 * связей, поэтому {@link #add} отвергает элемент, у которого связи уже есть: иначе схема
 * получила бы связи, созданные в обход неё.
 *
 * <pre>{@code
 * Scheme scheme = new Scheme().add(boiler).add(liveSteamPipe).add(turbine);
 * scheme.link(boiler, HEATING_MEDIUM, liveSteamPipe, HEATING_MEDIUM, "свежий пар");
 * scheme.link(liveSteamPipe, HEATING_MEDIUM, turbine, HEATING_MEDIUM, "свежий пар");
 * }</pre>
 *
 * <p>Роли среды на концах связи могут различаться — это норма, а не ошибка: роль
 * определяется элементом, внутри которого находится среда.
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
     * @param out              элемент-источник, уже добавленный в схему
     * @param transferTypeOut  роль среды внутри элемента-источника
     * @param in               элемент-приёмник, уже добавленный в схему
     * @param transferTypeIn   роль среды внутри элемента-приёмника
     * @param uname            подпись связи: среда, начинающая участок
     * @return созданная связь — в неё же записывается состояние потока
     * @throws IllegalArgumentException если элемент не зарегистрирован в схеме, роли не
     *                                  заданы или связь замкнута на одном элементе
     */
    public Connection link(Element out, HeatTransferType transferTypeOut,
                           Element in, HeatTransferType transferTypeIn, String uname) {
        requireElement(out);
        requireElement(in);
        Objects.requireNonNull(transferTypeOut, "transferTypeOut");
        Objects.requireNonNull(transferTypeIn, "transferTypeIn");
        return register(new Connection(uname, out, transferTypeOut, in, transferTypeIn));
    }

    /**
     * Создаёт связь, у которой известен только источник: выход элемента за границей схемы
     * (вход в схему ещё не смоделирован). Диагностируется {@link #danglingConnections()},
     * но ошибкой не считается.
     */
    public Connection openOut(Element out, HeatTransferType transferTypeOut, String uname) {
        requireElement(out);
        Objects.requireNonNull(transferTypeOut, "transferTypeOut");
        return register(new Connection(uname, out, transferTypeOut, null, null));
    }

    /**
     * Создаёт связь, у которой известен только приёмник: вход элемента из-за границы схемы.
     * Диагностируется {@link #danglingConnections()}, но ошибкой не считается.
     */
    public Connection openIn(Element in, HeatTransferType transferTypeIn, String uname) {
        requireElement(in);
        Objects.requireNonNull(transferTypeIn, "transferTypeIn");
        return register(new Connection(uname, null, null, in, transferTypeIn));
    }

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
     * конца принадлежат этой схеме; связь не замкнута на одном элементе; связь не соединяет
     * два аппарата напрямую — между ними должен стоять участок трассы ({@link Line}: труба
     * или тройник); у концов заданы роли среды; списки связей элементов не разошлись с
     * самими связями; давление и расход не отрицательны.
     *
     * <p>Связь с открытым концом и элемент без связей ошибкой не считаются — это граница
     * схемы, её отдаёт {@link #danglingConnections()}. Совпадение ролей на концах связи не
     * требуется: роль определяется элементом, внутри которого находится среда.
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        Set<Connection> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Connection connection : connections) {
            String name = "связь «" + connection.getName() + "» ["
                    + connection.getElementsUnames() + "]";
            if (!seen.add(connection)) {
                problems.add(name + " зарегистрирована в схеме дважды");
            }
            Element out = connection.getElementOut();
            Element in = connection.getElementIn();
            if (out == null && in == null) {
                problems.add(name + " не подключена ни к одному элементу");
            }
            if (out != null && !elements.contains(out)) {
                problems.add(name + " выходит из элемента «" + out.name()
                        + "», не зарегистрированного в схеме");
            }
            if (in != null && !elements.contains(in)) {
                problems.add(name + " входит в элемент «" + in.name()
                        + "», не зарегистрированный в схеме");
            }
            if (out != null && out == in) {
                problems.add(name + " замкнута на элементе «" + out.name() + "»");
            }
            if (out != null && in != null && !(out instanceof Line) && !(in instanceof Line)) {
                problems.add(name + " соединяет два аппарата напрямую: между элементами «"
                        + out.name() + "» и «" + in.name()
                        + "» должен стоять трубопровод (Pipe/PipeTee)");
            }
            if (out != null && connection.getTransferTypeOut() == null) {
                problems.add(name + " не задаёт роль среды на выходе элемента «"
                        + out.name() + "»");
            }
            if (in != null && connection.getTransferTypeIn() == null) {
                problems.add(name + " не задаёт роль среды на входе элемента «"
                        + in.name() + "»");
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
            for (Connection connection : element.getInPutConnections()) {
                if (connection.getElementIn() != element) {
                    problems.add("связь «" + connection.getName() + "» числится входом элемента «"
                            + element.name() + "», но ведёт в «"
                            + Element.describe(connection.getElementIn()) + "»");
                }
                if (!connections.contains(connection)) {
                    problems.add("входная связь «" + connection.getName() + "» элемента «"
                            + element.name() + "» не зарегистрирована в схеме");
                }
            }
            for (Connection connection : element.getOutPutConnections()) {
                if (connection.getElementOut() != element) {
                    problems.add("связь «" + connection.getName() + "» числится выходом элемента «"
                            + element.name() + "», но выходит из «"
                            + Element.describe(connection.getElementOut()) + "»");
                }
                if (!connections.contains(connection)) {
                    problems.add("выходная связь «" + connection.getName() + "» элемента «"
                            + element.name() + "» не зарегистрирована в схеме");
                }
            }
        }

        return problems;
    }

    private Connection register(Connection connection) {
        connections.add(connection);
        if (connection.getElementOut() != null) {
            connection.getElementOut().registerOutput(connection);
        }
        if (connection.getElementIn() != null) {
            connection.getElementIn().registerInput(connection);
        }
        return connection;
    }

    private void requireElement(Element element) {
        Objects.requireNonNull(element, "element");
        if (!elements.contains(element)) {
            throw new IllegalArgumentException("элемент «" + element.name()
                    + "» не зарегистрирован в схеме: сначала вызовите add(...)");
        }
    }
}
