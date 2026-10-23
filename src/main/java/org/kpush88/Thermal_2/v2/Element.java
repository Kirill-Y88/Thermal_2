package org.kpush88.Thermal_2.v2;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Элемент тепловой схемы: котёл, турбина, подогреватель, трубопровод.
 *
 * <p>Элемент — это «гиперребро» графа схемы: он превращает рабочее тело на своих
 * входах в рабочее тело на своих выходах. Входы и выходы — собственные соединения
 * элемента ({@link Connection}); связь с соседними элементами задаётся рёбрами
 * {@link SchemeGraph} между этими соединениями. Внутри элемента среда может менять
 * сторону и агрегатное состояние (котёл: вода → пар, конденсатор: пар → конденсат),
 * поэтому соединения на входе и выходе — всегда разные объекты.
 *
 * <p>Порядок добавления портов значим и сохраняется ({@link LinkedHashSet}):
 * у турбины отборы перечисляются по ходу расширения, у смешивающего подогревателя —
 * в порядке поступления потоков. Он же задаёт порядок строк в отчётах.
 */
@Getter
@Setter
public abstract class Element {

    /** Идентификатор для хранения схемы (файл/БД); в расчёте не участвует. */
    private Long id;

    /** Человекочитаемое имя элемента: «Котёл», «ПВД-1». */
    private String uname;

    @Getter(AccessLevel.NONE)
    private final Set<Connection> inputConnections = new LinkedHashSet<>();

    @Getter(AccessLevel.NONE)
    private final Set<Connection> outputConnections = new LinkedHashSet<>();

    protected Element() {
    }

    protected Element(String uname) {
        this.uname = uname;
    }

    protected Element(Long id, String uname) {
        this.id = id;
        this.uname = uname;
    }

    /** Регистрирует входное соединение элемента и возвращает его же. */
    public Connection addInput(Connection connection) {
        inputConnections.add(connection);
        return connection;
    }

    /** Регистрирует выходное соединение элемента и возвращает его же. */
    public Connection addOutput(Connection connection) {
        outputConnections.add(connection);
        return connection;
    }

    /** Создаёт и регистрирует входное соединение. */
    public Connection addInput(String uname, HeatTransferType transferType) {
        return addInput(new Connection(uname, transferType));
    }

    /** Создаёт и регистрирует выходное соединение. */
    public Connection addOutput(String uname, HeatTransferType transferType) {
        return addOutput(new Connection(uname, transferType));
    }

    /** Все порты элемента: сначала входы, затем выходы (только чтение). */
    public Set<Connection> connections() {
        Set<Connection> all = new LinkedHashSet<>(inputConnections);
        all.addAll(outputConnections);
        return Collections.unmodifiableSet(all);
    }

    /** Входные соединения элемента в порядке добавления (только чтение). */
    public Set<Connection> getInputConnections() {
        return Collections.unmodifiableSet(inputConnections);
    }

    /** Выходные соединения элемента в порядке добавления (только чтение). */
    public Set<Connection> getOutputConnections() {
        return Collections.unmodifiableSet(outputConnections);
    }

    /** Имя элемента для отчётов и сообщений об ошибках. */
    public String name() {
        return uname != null ? uname : getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return name() + " [" + getClass().getSimpleName() + "]: входов "
                + inputConnections.size() + ", выходов " + outputConnections.size();
    }
}
