package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Scheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Журнал расчёта: единственное место, где меняются величины в сечениях, и единственное, где
 * известно их происхождение.
 *
 * <p>Журнал ведёт расчёт по этапам ({@link Stage}). Пока этап открыт, в него можно писать;
 * {@link #capture} закрывает этап и кладёт в журнал неизменяемый снапшот
 * ({@link SchemeState}). Следующий этап пишет поверх состояния, но снапшот предыдущего уже
 * зафиксирован — поэтому всегда видно, что было известно до этапа и что он добавил
 * ({@link SchemeState#diff}).
 *
 * <p>Три правила, ради которых журнал и существует:
 * <ul>
 *   <li><b>Порядок этапов.</b> Первым фиксируется {@link Stage#INPUT}, дальше — только вперёд;
 *       писать можно лишь в ещё не зафиксированный этап. Так расчёт не может «вернуться» и
 *       молча переписать входные данные.</li>
 *   <li><b>ГУ не перезаписываются.</b> Если величина уже записана на более раннем этапе
 *       (например, давление отбора задано на {@code INPUT}), запись другого значения на
 *       следующем этапе — ошибка с указанием обоих происхождений. Флаг «рассчитано» это
 *       правило не выражает.</li>
 *   <li><b>Идемпотентность.</b> Повторная запись того же числа (тот же владелец на следующем
 *       проходе или другая модель, получившая то же значение) ничего не меняет и не считается
 *       конфликтом — это важно для многопроходного разрешения состояний.</li>
 * </ul>
 *
 * <p>Всё, что записано в связи до создания журнала, считается входными данными
 * ({@link Stage#INPUT}, владелец {@value #SCHEME_INPUT_OWNER}): так загрузчик схемы может
 * заполнить ГУ обычными {@link Connection#withState}, а расчёт начать с журнала.
 *
 * <p>Журнал не решает систему уравнений: расходы появляются только там, где массовый баланс
 * сводится к одному неизвестному ({@link StateResolver} и {@link MassBalance}). Остальные
 * величины остаются в {@link #missing()} — по этому отчёту {@link FlowSystem} и определяет размер
 * системы. Показатели цикла ({@link Stage#RESULT}) в журнал не пишутся вовсе: это не состояние
 * сечения, а свёртка состояний, и её место — в {@link CycleResult}.
 */
public final class CalculationJournal {

    /** Владелец величин, занесённых в связи до начала расчёта. */
    public static final String SCHEME_INPUT_OWNER = "исходные данные схемы";

    private final Scheme scheme;

    /** Сечения схемы: проверка принадлежности по идентичности, как и везде в схеме. */
    private final Set<Connection> known = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Текущее состояние всех сечений; снапшоты — его копии. */
    private final Map<Connection, ConnectionState> working = new LinkedHashMap<>();

    /** Зафиксированные этапы в порядке расчёта. */
    private final List<SchemeState> states = new ArrayList<>();

    /** Последний зафиксированный этап; {@code null} — журнал ещё пуст. */
    private Stage captured;

    private CalculationJournal(Scheme scheme) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        Origin input = new Origin(Stage.INPUT, SCHEME_INPUT_OWNER);
        for (Connection connection : scheme.getConnections()) {
            known.add(connection);
            ConnectionState state = ConnectionState.current(connection, input);
            if (!state.readings().isEmpty()) {
                working.put(connection, state);
            }
        }
    }

    /**
     * Открывает журнал для схемы. Значения, уже записанные в связи, становятся входными
     * данными — их происхождение {@link Stage#INPUT} и {@value #SCHEME_INPUT_OWNER}.
     */
    public static CalculationJournal of(Scheme scheme) {
        return new CalculationJournal(scheme);
    }

    /** Схема, для которой ведётся журнал. */
    public Scheme scheme() {
        return scheme;
    }

    /**
     * Этап, в который сейчас можно писать: {@link Stage#INPUT} до первого снимка, затем
     * следующий за последним зафиксированным.
     *
     * @throws IllegalStateException если зафиксирован последний этап: расчёт завершён
     */
    public Stage pendingStage() {
        if (captured == null) {
            return Stage.INPUT;
        }
        Stage next = captured.next();
        if (next == null) {
            throw new IllegalStateException("журнал завершён: этап " + captured
                    + " зафиксирован, записывать больше некуда");
        }
        return next;
    }

    /** Этап ещё не зафиксирован: в него можно писать. */
    public boolean isOpen() {
        if (captured == null) {
            return true;
        }
        return captured.next() != null;
    }

    /**
     * Фиксирует этап: кладёт в журнал неизменяемый снапшот текущих значений и закрывает
     * запись в него.
     *
     * <p>Первым фиксируется {@link Stage#INPUT}, дальше — только более поздние этапы. Этапы
     * можно пропускать ({@code INPUT → RESOLVED}), если промежуточный ничего не добавляет, но
     * вернуться назад или зафиксировать этап дважды нельзя: снапшот одного этапа должен
     * существовать в единственном экземпляре, иначе «разница этапов» станет неоднозначной.
     *
     * @param stage фиксируемый этап
     * @return снапшот этого этапа
     * @throws IllegalStateException если нарушен порядок этапов
     */
    public SchemeState capture(Stage stage) {
        Objects.requireNonNull(stage, "stage");
        if (captured == null && stage != Stage.INPUT) {
            throw new IllegalStateException("журнал начинается с этапа " + Stage.INPUT.name()
                    + " (" + Stage.INPUT.title() + "): передано " + stage.name()
                    + ". Входные данные фиксируются первыми");
        }
        if (captured != null && !stage.isAfter(captured)) {
            throw new IllegalStateException("этап " + stage.name() + " не позже уже "
                    + "зафиксированного " + captured.name() + ": снапшоты фиксируются по "
                    + "порядку этапов; зафиксированы: " + capturedStages());
        }
        SchemeState state = new SchemeState(scheme, stage, working);
        states.add(state);
        captured = stage;
        return state;
    }

    /** Фиксирует текущий этап ({@link #pendingStage()}) и возвращает его снапшот. */
    public SchemeState capture() {
        return capture(pendingStage());
    }

    /** Зафиксированные снапшоты в порядке этапов (только чтение). */
    public List<SchemeState> states() {
        return Collections.unmodifiableList(states);
    }

    /** Снапшот указанного этапа. */
    public SchemeState state(Stage stage) {
        Objects.requireNonNull(stage, "stage");
        for (SchemeState state : states) {
            if (state.stage() == stage) {
                return state;
            }
        }
        throw new IllegalArgumentException("этап " + stage.name()
                + " ещё не зафиксирован; зафиксированы: " + capturedStages());
    }

    /** Последний зафиксированный снапшот. */
    public SchemeState last() {
        if (states.isEmpty()) {
            throw new IllegalStateException("журнал пуст: сначала вызовите capture(...)");
        }
        return states.get(states.size() - 1);
    }

    /**
     * Записывает величину в сечение на указанном этапе.
     *
     * <p>Запись проходит, только если этап — текущий ({@link #pendingStage()}), связь
     * принадлежит схеме, а величина в этом сечении ещё не задана. Повторная запись того же
     * значения — не ошибка и ничего не меняет; другое значение — конфликт с указанием обоих
     * происхождений.
     *
     * @param stage      текущий этап расчёта
     * @param owner      владелец значения: имя элемента или модели
     * @param connection сечение схемы
     * @param quantity   записываемая величина
     * @param value      значение величины
     * @return {@code true}, если величина появилась; {@code false}, если она уже была
     * записана тем же значением и запись ничего не изменила — по этому признаку резолвер
     * понимает, что проход ещё добавляет величины
     * @throws IllegalStateException    если этап не текущий или величина уже задана другим
     *                                  значением
     * @throws IllegalArgumentException если связь не из схемы или значение не конечное
     */
    public boolean write(Stage stage, String owner, Connection connection, Quantity quantity,
                         double value) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(quantity, "quantity");
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("у записываемой величины должен быть владелец");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("значение величины должно быть конечным: "
                    + value + " (" + quantity.title() + ", связь «" + connection.getName() + "»)");
        }

        Stage expected = pendingStage();
        if (stage != expected) {
            throw new IllegalStateException("писать можно только в текущий этап: ожидается "
                    + expected.name() + " (" + expected.title() + "), передано " + stage.name());
        }
        requireConnection(connection);

        ConnectionState state = working.getOrDefault(connection, ConnectionState.empty());
        ConnectionState.Reading existing = state.reading(quantity);
        if (existing != null) {
            if (Double.compare(existing.value(), value) == 0) {
                return false;
            }
            throw new IllegalStateException("величина уже задана: связь «"
                    + connection.getName() + "» [" + connection.getPortsUnames() + "], "
                    + quantity.describe() + " = " + existing.value() + " ("
                    + existing.origin().describe() + "); попытка записать " + value
                    + " на этапе " + new Origin(stage, owner).describe());
        }

        Origin origin = new Origin(stage, owner);
        working.put(connection, state.with(quantity, value, origin));
        quantity.write(connection, value);
        return true;
    }

    /**
     * Текущее значение величины — из журнала, а не из поля связи. Пока этап не зафиксирован,
     * журнал и связь совпадают; читать через журнал нужно затем, чтобы расчёт видел ровно то,
     * что записано с происхождением, и не мог опереться на прямое присваивание в обход него.
     *
     * @return значение или {@code null}, если величина ещё не задана
     * @throws IllegalArgumentException если связь не принадлежит схеме журнала
     */
    public Double value(Connection connection, Quantity quantity) {
        Objects.requireNonNull(quantity, "quantity");
        requireConnection(connection);
        ConnectionState state = working.get(connection);
        return state == null ? null : state.value(quantity);
    }

    /**
     * Происхождение величины в сечении.
     *
     * @return этап и владелец или {@code null}, если величина ещё не задана
     * @throws IllegalArgumentException если связь не принадлежит схеме журнала
     */
    public Origin origin(Connection connection, Quantity quantity) {
        Objects.requireNonNull(quantity, "quantity");
        requireConnection(connection);
        ConnectionState state = working.get(connection);
        ConnectionState.Reading reading = state == null ? null : state.reading(quantity);
        return reading == null ? null : reading.origin();
    }

    private void requireConnection(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        if (!known.contains(connection)) {
            throw new IllegalArgumentException("связь «" + connection.getName()
                    + "» не принадлежит схеме журнала");
        }
    }

    /** Записывает давление и энтальпию сечения — обычная пара «состояние известно». */
    public void writeState(Stage stage, String owner, Connection connection, double pressure,
                           double enthalpy) {
        write(stage, owner, connection, Quantity.PRESSURE, pressure);
        write(stage, owner, connection, Quantity.ENTHALPY, enthalpy);
    }

    /** Записывает расход сечения. */
    public void writeFlow(Stage stage, String owner, Connection connection, double flow) {
        write(stage, owner, connection, Quantity.FLOW, flow);
    }

    /**
     * Незаданные величины по последнему снапшоту (если снимков ещё нет — по текущему
     * состоянию). Отчёт перечисляет то, чего не хватает следующему этапу: связи по порядку
     * схемы, величины по порядку объявления.
     */
    public List<SchemeState.Missing> missing() {
        SchemeState state = states.isEmpty()
                ? new SchemeState(scheme, Stage.INPUT, working)
                : last();
        return state.missing();
    }

    /** Многострочный отчёт: зафиксированные этапы и число заполненных величин на каждом. */
    public String describe() {
        StringBuilder text = new StringBuilder("Журнал расчёта: зафиксировано ")
                .append(states.size()).append(" этапов");
        for (SchemeState state : states) {
            text.append(System.lineSeparator()).append("  ").append(state.stage().name())
                    .append(" (").append(state.stage().title()).append("): ")
                    .append(state.filled()).append(" из ").append(state.total())
                    .append(" величин");
        }
        if (isOpen()) {
            Stage pending = pendingStage();
            text.append(System.lineSeparator()).append("  текущий этап: ").append(pending.name())
                    .append(" (").append(pending.title()).append(")");
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return describe();
    }

    private String capturedStages() {
        List<String> names = new ArrayList<>();
        for (SchemeState state : states) {
            names.add(state.stage().name());
        }
        return names.toString();
    }
}
