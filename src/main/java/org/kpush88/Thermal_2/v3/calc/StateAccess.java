package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;

import java.util.Objects;

/**
 * Доступ модели к состоянию схемы: чтение известного и запись вычисленного.
 *
 * <p>Модель не работает с полями связи напрямую — она видит только {@link CalculationJournal}.
 * Это не формальность: благодаря единственному каналу записи у каждой величины есть этап и
 * владелец, а конфликт (попытка записать другое значение поверх ГУ) обнаруживается сразу и с
 * указанием обоих происхождений. Прямое присваивание в {@link Connection} прошло бы мимо
 * журнала и обесценило бы и снапшоты, и отчёт {@code missing()}.
 *
 * <p>Владелец и этап задаются при создании доступа: резолвер создаёт его на каждый элемент,
 * поэтому записанное всегда подписано именем элемента, а не модели вообще.
 */
public final class StateAccess {

    private final CalculationJournal journal;
    private final Stage stage;
    private final String owner;

    StateAccess(CalculationJournal journal, Stage stage, String owner) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.stage = Objects.requireNonNull(stage, "stage");
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("у доступа к состоянию должен быть владелец");
        }
        this.owner = owner;
    }

    /** Этап, в который пишет модель. */
    public Stage stage() {
        return stage;
    }

    /** Владелец записей — имя элемента, для которого создан доступ. */
    public String owner() {
        return owner;
    }

    /** Значение величины; {@code null} — неизвестно. */
    public Double read(Connection connection, Quantity quantity) {
        Objects.requireNonNull(quantity, "quantity");
        return journal.value(connection, quantity);
    }

    /** Величина уже известна. */
    public boolean known(Connection connection, Quantity quantity) {
        return read(connection, quantity) != null;
    }

    /** Происхождение известной величины; {@code null} — величина не задана. */
    public Origin origin(Connection connection, Quantity quantity) {
        Objects.requireNonNull(quantity, "quantity");
        return journal.origin(connection, quantity);
    }

    /**
     * Записывает величину в сечение от имени владельца доступа.
     *
     * @return {@code true}, если величина появилась; {@code false}, если она уже была записана
     * тем же значением
     * @throws IllegalStateException если величина уже задана другим значением (конфликт с ГУ
     *                               или с другой моделью)
     */
    public boolean write(Connection connection, Quantity quantity, double value) {
        Objects.requireNonNull(quantity, "quantity");
        return journal.write(stage, owner, connection, quantity, value);
    }

    /** Подпись значения для сообщений об ошибках: связь, величина, значение и происхождение. */
    public String describe(Connection connection, Quantity quantity) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(quantity, "quantity");
        String where = "связь «" + connection.getName() + "» [" + connection.getPortsUnames() + "]";
        Double value = read(connection, quantity);
        if (value == null) {
            return where + ": " + quantity.describe() + " не задано";
        }
        Origin origin = origin(connection, quantity);
        return where + ": " + quantity.describe() + " = " + value
                + (origin != null ? " (" + origin.describe() + ")" : "");
    }

    @Override
    public String toString() {
        return "доступ к состоянию [" + stage.name() + " · " + owner + "]";
    }
}
