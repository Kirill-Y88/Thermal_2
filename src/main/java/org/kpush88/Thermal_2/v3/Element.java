package org.kpush88.Thermal_2.v3;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Элемент тепловой схемы: котёл, турбина, подогреватель, насос, участок трассы.
 *
 * <p>Элемент объявляет <b>порты</b> ({@link Port}) — именованные места, к которым
 * подключаются связи, — и превращает среду на входах в среду на выходах. Порт несёт имя,
 * направление, контур и кратность, поэтому «два дренажных входа в деаэратор» различимы по
 * имени ({@code drainIn} — коллектор), а «второй патрубок на том же конце» случайно не
 * появится: кратность объявлена классом и проверяется при подключении.
 *
 * <p>Связь ({@link Connection}) — самостоятельный объект, помнящий оба порта, поэтому
 * состояние на стыке существует в единственном экземпляре: элемент, записавший его в
 * связь, тем самым задаёт вход следующего элемента. Списки связей по портам элемент ведёт
 * сам, но заполняет их только схема ({@link Scheme#link}), поэтому связь не может
 * оказаться в списке одного элемента, указывая при этом на другой.
 *
 * <p>Объявление портов — дело класса: в конструкторе вызывается {@link #declarePort} (или
 * {@link #inPort} / {@link #outPort}), и порт сразу получает кратность. Порты и связи по
 * портам сохраняют порядок объявления и подключения: у турбины отборы перечисляются по
 * ходу расширения, у деаэратора — в порядке поступления потоков; он же задаёт порядок
 * строк в отчётах.
 */
public abstract class Element {

    /** Идентификатор для хранения схемы (файл/БД); в расчёте не участвует. */
    @Getter
    @Setter
    private Long id;

    /** Человекочитаемое имя элемента: «Котёл», «ПВД-1». */
    @Getter
    @Setter
    private String uname;

    /** Порты в порядке объявления: имя → порт. */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Map<String, Port> ports = new LinkedHashMap<>();

    /** Связи, подключённые к каждому порту, в порядке подключения. */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Map<Port, List<Connection>> byPort = new LinkedHashMap<>();

    protected Element() {
    }

    protected Element(String uname) {
        this.uname = uname;
    }

    protected Element(Long id, String uname) {
        this.id = id;
        this.uname = uname;
    }

    // ---------- объявление портов ----------

    /**
     * Объявляет порт элемента. Вызывается один раз из конструктора класса элемента.
     *
     * @param name      имя порта внутри элемента («waterIn», «extractionOut»)
     * @param direction вход или выход
     * @param space     контур аппарата
     * @param min       минимальное число связей
     * @param max       максимальное число связей ({@link Integer#MAX_VALUE} — без ограничения)
     * @return созданный порт — его класс элемента отдаёт своим аксессором
     */
    protected final Port declarePort(String name, PortDirection direction, SpaceType space,
                                     int min, int max) {
        Objects.requireNonNull(name, "name");
        if (ports.containsKey(name)) {
            throw new IllegalStateException("у элемента «" + name() + "» уже объявлен порт «"
                    + name + "»");
        }
        Port port = new Port(this, name, direction, space, min, max);
        ports.put(name, port);
        byPort.put(port, new ArrayList<>());
        return port;
    }

    /** Объявляет обязательный вход: ровно одна связь. */
    protected final Port inPort(String name, SpaceType space) {
        return declarePort(name, PortDirection.IN, space, 1, 1);
    }

    /** Объявляет вход с кратностью от {@code min} до {@code max}. */
    protected final Port inPort(String name, SpaceType space, int min, int max) {
        return declarePort(name, PortDirection.IN, space, min, max);
    }

    /** Объявляет обязательный выход: ровно одна связь. */
    protected final Port outPort(String name, SpaceType space) {
        return declarePort(name, PortDirection.OUT, space, 1, 1);
    }

    /** Объявляет выход с кратностью от {@code min} до {@code max}. */
    protected final Port outPort(String name, SpaceType space, int min, int max) {
        return declarePort(name, PortDirection.OUT, space, min, max);
    }

    // ---------- порты ----------

    /** Все порты элемента в порядке объявления (только чтение). */
    public List<Port> getPorts() {
        return List.copyOf(ports.values());
    }

    /** Порты заданного направления в порядке объявления (только чтение). */
    public List<Port> getPorts(PortDirection direction) {
        Objects.requireNonNull(direction, "direction");
        List<Port> result = new ArrayList<>();
        for (Port port : ports.values()) {
            if (port.direction() == direction) {
                result.add(port);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Порты заданного контура в порядке объявления (только чтение). */
    public List<Port> getPorts(SpaceType space) {
        Objects.requireNonNull(space, "space");
        List<Port> result = new ArrayList<>();
        for (Port port : ports.values()) {
            if (port.space() == space) {
                result.add(port);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Порты контура и направления в порядке объявления (только чтение). */
    public List<Port> ports(SpaceType space, PortDirection direction) {
        Objects.requireNonNull(space, "space");
        Objects.requireNonNull(direction, "direction");
        List<Port> result = new ArrayList<>();
        for (Port port : ports.values()) {
            if (port.space() == space && port.direction() == direction) {
                result.add(port);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Порт по имени. */
    public Optional<Port> findPort(String name) {
        return Optional.ofNullable(ports.get(name));
    }

    /**
     * Порт по имени; отсутствие порта — ошибка, а не {@code null}: имя порта в коде схемы
     * всегда известно заранее.
     */
    public Port port(String name) {
        Port port = ports.get(name);
        if (port == null) {
            throw new IllegalArgumentException("у элемента «" + name() + "» нет порта «" + name
                    + "»; объявлены: " + ports.keySet());
        }
        return port;
    }

    /** Порт принадлежит этому элементу. */
    public boolean owns(Port port) {
        return port != null && port.element() == this;
    }

    // ---------- связи по портам ----------

    /** Все входные связи элемента в порядке объявления портов и подключения (только чтение). */
    public List<Connection> getInPutConnections() {
        return connectionsOf(PortDirection.IN, null);
    }

    /** Все выходные связи элемента в порядке объявления портов и подключения (только чтение). */
    public List<Connection> getOutPutConnections() {
        return connectionsOf(PortDirection.OUT, null);
    }

    /** Все связи элемента: сначала входные, затем выходные (только чтение). */
    public List<Connection> connections() {
        List<Connection> all = new ArrayList<>(getInPutConnections());
        all.addAll(getOutPutConnections());
        return Collections.unmodifiableList(all);
    }

    /** Входные связи одного порта в порядке подключения (только чтение). */
    public List<Connection> getInPutConnections(Port port) {
        return connectionsOf(requirePort(port, PortDirection.IN));
    }

    /** Выходные связи одного порта в порядке подключения (только чтение). */
    public List<Connection> getOutPutConnections(Port port) {
        return connectionsOf(requirePort(port, PortDirection.OUT));
    }

    /** Входные связи заданного контура — в порядке объявления портов (только чтение). */
    public List<Connection> getInPutConnections(SpaceType space) {
        return connectionsOf(PortDirection.IN, Objects.requireNonNull(space, "space"));
    }

    /** Выходные связи заданного контура — в порядке объявления портов (только чтение). */
    public List<Connection> getOutPutConnections(SpaceType space) {
        return connectionsOf(PortDirection.OUT, Objects.requireNonNull(space, "space"));
    }

    /** Число связей на порту: 0 — порт свободен, 2 — коллектор принял два потока. */
    public int portSize(Port port) {
        Objects.requireNonNull(port, "port");
        return connectionsOf(requirePort(port, port.direction())).size();
    }

    /**
     * Единственная связь на порту. Для портов кратности 1…1 это и есть «вход элемента»;
     * попытка взять единственную связь с коллектора — ошибка конфигурации схемы.
     *
     * @throws IllegalStateException если на порту не ровно одна связь
     */
    public Connection single(Port port) {
        Objects.requireNonNull(port, "port");
        List<Connection> connections = connectionsOf(requirePort(port, port.direction()));
        if (connections.size() != 1) {
            throw new IllegalStateException("на порту " + port.label() + " " + connections.size()
                    + " связей, а ожидалась одна");
        }
        return connections.get(0);
    }

    /** Регистрирует выходную связь; вызывается схемой при её создании. */
    void registerOutput(Connection connection) {
        register(connection, PortDirection.OUT);
    }

    /** Регистрирует входную связь; вызывается схемой при её создании. */
    void registerInput(Connection connection) {
        register(connection, PortDirection.IN);
    }

    private void register(Connection connection, PortDirection direction) {
        Objects.requireNonNull(connection, "connection");
        Port port = direction == PortDirection.OUT ? connection.getPortOut() : connection.getPortIn();
        if (port == null || port.element() != this) {
            throw new IllegalArgumentException("связь «" + connection.getName()
                    + "» не подключена к «" + direction.title() + "» элемента «" + name() + "»");
        }
        List<Connection> connections = byPort.get(port);
        if (!connections.contains(connection)) {
            connections.add(connection);
        }
    }

    private List<Connection> connectionsOf(PortDirection direction, SpaceType space) {
        List<Connection> result = new ArrayList<>();
        for (Port port : ports.values()) {
            if (port.direction() != direction) {
                continue;
            }
            if (space != null && port.space() != space) {
                continue;
            }
            result.addAll(byPort.get(port));
        }
        return Collections.unmodifiableList(result);
    }

    private List<Connection> connectionsOf(Port port) {
        return Collections.unmodifiableList(byPort.get(port));
    }

    private Port requirePort(Port port, PortDirection direction) {
        Objects.requireNonNull(port, "port");
        if (port.element() != this) {
            throw new IllegalArgumentException("порт " + port.label() + " принадлежит элементу «"
                    + port.element().name() + "», а не «" + name() + "»");
        }
        if (port.direction() != direction) {
            throw new IllegalArgumentException("порт " + port.label() + " — "
                    + port.direction().title() + ", а запрошены связи со стороны «"
                    + direction.title() + "»");
        }
        return port;
    }

    // ---------- каналы потока ----------

    /**
     * Каналы потока элемента — независимые протоки рабочего тела.
     *
     * <p>По умолчанию канал = группа портов одного контура: у поверхностного подогревателя
     * это паровая сторона ({@code steamIn}, {@code drainIn} → {@code drainOut}) и водяная
     * ({@code waterIn} → {@code waterOut}). Такой вывод верен для всех элементов, внутри
     * которых среда не меняет контур: труба, тройник, насос, турбина, поверхностный
     * подогреватель.
     *
     * <p>Элементы, в которых среда переходит из контура в контур, переопределяют метод:
     * котёл (вода → пар), конденсатор (выхлоп и дренажи → конденсат), деаэратор и
     * смешивающий подогреватель (пар и дренажи смешиваются с водой). Валидатор требует,
     * чтобы каждый порт входил ровно в один канал, — забытый при переопределении порт
     * сразу виден.
     */
    public List<FlowChannel> flowChannels() {
        List<FlowChannel> channels = new ArrayList<>();
        for (SpaceType space : SpaceType.values()) {
            List<Port> in = ports(space, PortDirection.IN);
            List<Port> out = ports(space, PortDirection.OUT);
            if (in.isEmpty() && out.isEmpty()) {
                continue;
            }
            channels.add(new FlowChannel(space, in, out));
        }
        return Collections.unmodifiableList(channels);
    }

    // ---------- тип и проверки ----------

    /** Тип элемента для загрузчика схемы и отчётов. */
    public abstract ElementType elementType();

    /**
     * Структурные ошибки элемента: кратность портов (сколько объявлено и сколько подключено)
     * и разбиение портов по каналам потока. Проверки конкретного класса — в
     * {@link #validateElement(List)}.
     *
     * @return список сообщений; пустой список — элемент собран корректно
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        for (Port port : ports.values()) {
            int size = byPort.get(port).size();
            if (size < port.min()) {
                problems.add("порт " + port.label() + ": подключено " + size
                        + " связей, а объявлено не менее " + port.min());
            }
            if (size > port.max()) {
                problems.add("порт " + port.label() + ": подключено " + size
                        + " связей, а объявлено не более " + port.max());
            }
        }

        Map<Port, Integer> channelOfPort = new LinkedHashMap<>();
        for (FlowChannel channel : flowChannels()) {
            if (channel.in().isEmpty()) {
                problems.add("у элемента «" + name() + "» канал (" + channel.space().title()
                        + ") " + channel.describe() + " не имеет входов");
            }
            if (channel.out().isEmpty()) {
                problems.add("у элемента «" + name() + "» канал (" + channel.space().title()
                        + ") " + channel.describe() + " не имеет выходов");
            }
            for (Port port : channel.in()) {
                channelOfPort.merge(port, 1, Integer::sum);
            }
            for (Port port : channel.out()) {
                channelOfPort.merge(port, 1, Integer::sum);
            }
        }
        for (Port port : ports.values()) {
            int count = channelOfPort.getOrDefault(port, 0);
            if (count == 0) {
                problems.add("порт " + port.label() + " не входит ни в один канал потока");
            } else if (count > 1) {
                problems.add("порт " + port.label() + " входит в " + count
                        + " каналов потока: поток должен принадлежать одному каналу");
            }
        }

        validateElement(problems);
        return problems;
    }

    /** Проверки конкретного класса элемента; по умолчанию дополнительных правил нет. */
    protected void validateElement(List<String> problems) {
    }

    /** Имя элемента для отчётов и сообщений об ошибках. */
    public String name() {
        return uname != null ? uname : getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return name() + " [" + elementType() + "]: портов " + ports.size()
                + ", связей " + connections().size();
    }
}
