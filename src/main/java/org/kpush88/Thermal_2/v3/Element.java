package org.kpush88.Thermal_2.v3;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Элемент тепловой схемы: котёл, турбина, подогреватель, насос, участок трассы.
 *
 * <p>Элемент превращает среду на своих входах в среду на своих выходах. Связь
 * ({@link Connection}) — самостоятельный объект, помнящий оба конца, поэтому «патрубки»
 * элемента — это его связи: входные перечислены в {@link #getInPutConnections()}, выходные —
 * в {@link #getOutPutConnections()}, а роль среды на каждом конце хранится в самой связи
 * ({@link Connection#getTransferTypeIn()} / {@link Connection#getTransferTypeOut()}).
 *
 * <p>Следствие модели: число связей на стороне элемента не ограничено. В v2 «второй
 * патрубок» и «вторая связь того же патрубка» различались, здесь патрубков нет — у турбины
 * четыре выхода, у деаэратора три входа, и это просто четыре и три связи. Ограничение
 * степени (как в v2) появится вместе с отдельным объектом патрубка, если он понадобится.
 *
 * <p>Порядок связей значим и сохраняется: у турбины отборы перечисляются по ходу
 * расширения, у деаэратора — в порядке поступления потоков; он же задаёт порядок строк в
 * отчётах.
 *
 * <p>Регистрирует связи только {@link Scheme} (методы {@code registerInput} /
 * {@code registerOutput} доступны внутри пакета): так связь не может оказаться в списке
 * одного элемента, указывая при этом на другой.
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

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final List<Connection> inPutConnections = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final List<Connection> outPutConnections = new ArrayList<>();

    protected Element() {
    }

    protected Element(String uname) {
        this.uname = uname;
    }

    protected Element(Long id, String uname) {
        this.id = id;
        this.uname = uname;
    }

    /** Входные связи с указанной ролью среды — в порядке добавления (только чтение). */
    public List<Connection> getInPutConnections(HeatTransferType heatTransferType) {
        return filter(inPutConnections, heatTransferType, true);
    }

    /** Выходные связи с указанной ролью среды — в порядке добавления (только чтение). */
    public List<Connection> getOutPutConnections(HeatTransferType heatTransferType) {
        return filter(outPutConnections, heatTransferType, false);
    }

    /** Все входные связи элемента в порядке добавления (только чтение). */
    public List<Connection> getInPutConnections() {
        return Collections.unmodifiableList(inPutConnections);
    }

    /** Все выходные связи элемента в порядке добавления (только чтение). */
    public List<Connection> getOutPutConnections() {
        return Collections.unmodifiableList(outPutConnections);
    }

    /** Все связи элемента: сначала входные, затем выходные (только чтение). */
    public List<Connection> connections() {
        List<Connection> all = new ArrayList<>(inPutConnections);
        all.addAll(outPutConnections);
        return Collections.unmodifiableList(all);
    }

    /** Имя элемента для отчётов и сообщений об ошибках. */
    public String name() {
        return uname != null ? uname : getClass().getSimpleName();
    }

    /** Регистрирует входную связь; вызывается схемой при её создании. */
    void registerInput(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        if (connection.getElementIn() != this) {
            throw new IllegalArgumentException("связь «" + connection.getName()
                    + "» входит в элемент " + describe(connection.getElementIn())
                    + ", а не в " + name());
        }
        if (!inPutConnections.contains(connection)) {
            inPutConnections.add(connection);
        }
    }

    /** Регистрирует выходную связь; вызывается схемой при её создании. */
    void registerOutput(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        if (connection.getElementOut() != this) {
            throw new IllegalArgumentException("связь «" + connection.getName()
                    + "» выходит из элемента " + describe(connection.getElementOut())
                    + ", а не из " + name());
        }
        if (!outPutConnections.contains(connection)) {
            outPutConnections.add(connection);
        }
    }

    /** Имя элемента для сообщений об ошибках; неизвестный элемент обозначается «?». */
    static String describe(Element element) {
        return element != null ? element.name() : "?";
    }

    private static List<Connection> filter(List<Connection> source, HeatTransferType heatTransferType,
                                           boolean input) {
        Objects.requireNonNull(heatTransferType, "heatTransferType");
        List<Connection> result = new ArrayList<>();
        for (Connection connection : source) {
            HeatTransferType actual = input
                    ? connection.getTransferTypeIn()
                    : connection.getTransferTypeOut();
            if (actual == heatTransferType) {
                result.add(connection);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public String toString() {
        return name() + " [" + getClass().getSimpleName() + "]: входов "
                + inPutConnections.size() + ", выходов " + outPutConnections.size();
    }
}
