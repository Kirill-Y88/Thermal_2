package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Deaerator;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.FlowChannel;
import org.kpush88.Thermal_2.v3.SurfaceHeater;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Расходы схемы: система балансов массы и энергии, собранная из элементов.
 *
 * <p>Неизвестные — расходы тех связей, у которых он ещё не задан. Уравнения дают сами элементы,
 * и это не список частных случаев, а два общих правила:
 * <ol>
 *   <li><b>Баланс массы канала</b> — для каждого канала каждого элемента
 *       ({@link Element#flowChannels()}). Канал уже говорит, какие порты питают какие, поэтому
 *       правило одно для котла, турбины, насоса, тройника и любой трубы.</li>
 *   <li><b>Баланс энергии аппарата</b> — для поверхностного подогревателя и деаэратора:
 *       {@code Σ G_вх·h_вх = Σ G_вых·h_вых} по всем каналам сразу. Аппарат теплообмена наружу
 *       тепла не отдаёт: сколько пар отдал, столько вода взяла, поэтому баланс пишется по
 *       аппарату целиком, а не по сторонам, и не требует новых неизвестных (тепловой поток
 *       сокращается). Именно это уравнение и определяет расход отбора — замысел тот же, что в
 *       {@code theory/04}: сначала состояния (энтальпии), потом расходы.</li>
 * </ol>
 *
 * <p><b>Чего в системе нет и почему.</b> Турбина работу отдаёт, котёл теплоту подводит, конденсатор
 * отводит — у них баланс энергии содержал бы новое неизвестное (мощность, теплоту), а эти
 * величины считаются по уже найденным расходам, а не наоборот. Смешивающий подогреватель
 * ({@code MixingHeater}) тоже пропускается: его энтальпия выхода и есть этот баланс
 * ({@link MixerModel}), поэтому уравнение было бы её же определением и сделало бы систему
 * вырожденной. У деаэратора энтальпию выхода задаёт рабочее давление
 * ({@link DeaeratorModel}), поэтому его баланс энергии — настоящее уравнение.
 *
 * <p><b>Граничный расход.</b> В замкнутом цикле балансы массы линейно зависимы: если все расходы
 * умножить на одно число, балансы останутся верными, и решение определено с точностью до
 * масштаба. Масштаб задаёт {@link #withFlow} — расход свежего пара (в расчёте цикла его даёт
 * внешняя итерация). Без граничного условия {@link #solve} не подставляет «удобное» число, а
 * сообщает ранг системы и перечисляет расходы, которые балансы не определяют.
 *
 * <p>Энтальпии к моменту сборки должны быть известны: система линейна по расходам только тогда.
 * Если хоть одна энтальпия аппарата не разрешена, сообщение называет её и элемент — иначе баланс
 * энергии молча получил бы нулевой коэффициент.
 */
public final class FlowSystem {

    /** Владелец расходов в журнале: по нему видно, что величина пришла из решения системы. */
    public static final String OWNER = "СЛАУ балансов";

    private final CalculationJournal journal;
    private final List<Connection> unknowns;
    private final Map<Connection, Integer> columns = new IdentityHashMap<>();
    private final LinearSystem system;
    private final List<String> boundaries = new ArrayList<>();

    private FlowSystem(CalculationJournal journal, List<Connection> unknowns) {
        this.journal = journal;
        this.unknowns = List.copyOf(unknowns);
        List<String> labels = new ArrayList<>();
        for (int column = 0; column < this.unknowns.size(); column++) {
            Connection connection = this.unknowns.get(column);
            columns.put(connection, column);
            labels.add("G «" + connection.getName() + "»");
        }
        this.system = new LinearSystem(labels);
        assemble();
    }

    /**
     * Собирает систему по журналу, в котором состояния уже разрешены.
     *
     * @throws IllegalStateException если журнал ждёт не тот этап: расходы решаются на
     *                               {@link Stage#SOLVED}, то есть ровно после {@code RESOLVED}
     */
    public static FlowSystem of(CalculationJournal journal) {
        requireStage(journal);
        List<Connection> unknownFlows = new ArrayList<>();
        for (Connection connection : journal.scheme().getConnections()) {
            if (journal.value(connection, Quantity.FLOW) == null) {
                unknownFlows.add(connection);
            }
        }
        return new FlowSystem(journal, unknownFlows);
    }

    /** Неизвестные расходы в порядке схемы. */
    public List<Connection> unknowns() {
        return unknowns;
    }

    /**
     * Фиксирует расход связи — граничное условие системы.
     *
     * <p>Если расход уже задан (ГУ), значение проверяется: два разных расхода на одном сечении —
     * противоречие исходных данных, а не повод выбрать одно из них.
     *
     * @return эта система — для сборки одной цепочкой
     * @throws IllegalStateException если заданный расход расходится с граничным
     */
    public FlowSystem withFlow(Connection connection, double flow) {
        Objects.requireNonNull(connection, "connection");
        if (!Double.isFinite(flow)) {
            throw new IllegalArgumentException("граничный расход должен быть конечным: " + flow);
        }
        Integer column = columns.get(connection);
        if (column == null) {
            Double known = journal.value(connection, Quantity.FLOW);
            if (known != null && !RuleSupport.same(known, flow)) {
                throw new IllegalStateException("расход «" + connection.getName()
                        + "» уже задан " + known + " кг/с, а граничное условие требует " + flow
                        + " кг/с: два разных расхода на одном сечении");
            }
            return this;
        }
        int row = system.row("граничное условие: G «" + connection.getName() + "»");
        system.term(row, column, 1.0);
        system.constant(row, flow);
        boundaries.add(describe(connection) + " = " + flow + " кг/с");
        return this;
    }

    /**
     * Решает систему, записывает расходы в журнал на этапе {@link Stage#SOLVED} и возвращает их
     * вместе с диагностикой.
     *
     * @throws IllegalStateException если балансы не определяют расходы, противоречат друг другу
     *                               или дают отрицательный расход
     */
    public FlowSolution solve() {
        LinearSystem.Solution solution = system.solve("балансы расходов");
        for (int column = 0; column < unknowns.size(); column++) {
            Connection connection = unknowns.get(column);
            double flow = solution.value(column);
            if (flow < 0) {
                if (RuleSupport.balanced(flow, 0)) {
                    flow = 0;
                } else {
                    throw new IllegalStateException("балансы дают отрицательный расход «"
                            + connection.getName() + "» = " + flow
                            + " кг/с: поток направлен против портов, а модель течения"
                            + " односторонняя");
                }
            }
            journal.write(Stage.SOLVED, OWNER, connection, Quantity.FLOW, flow);
        }
        Map<Connection, Double> flows = new LinkedHashMap<>();
        for (Connection connection : journal.scheme().getConnections()) {
            flows.put(connection, journal.value(connection, Quantity.FLOW));
        }
        return new FlowSolution(solution.rank(), solution.unknowns(), solution.equations(),
                solution.residual(), unknowns, flows, List.copyOf(boundaries));
    }

    private void assemble() {
        for (Element element : journal.scheme().getElements()) {
            for (FlowChannel channel : element.flowChannels()) {
                mass(element, channel);
            }
            energy(element);
        }
    }

    /** Баланс массы канала: сумма входов равна сумме выходов. */
    private void mass(Element element, FlowChannel channel) {
        List<Connection> in = RuleSupport.connections(element, channel.in());
        List<Connection> out = RuleSupport.connections(element, channel.out());
        if (in == null || out == null || in.isEmpty() || out.isEmpty()) {
            return;
        }
        int row = system.row(element.name() + ": масса (" + channel.describe() + ")");
        for (Connection connection : in) {
            contribute(row, connection, 1.0);
        }
        for (Connection connection : out) {
            contribute(row, connection, -1.0);
        }
    }

    /** Баланс энергии аппарата без внешнего теплообмена: по всем каналам сразу. */
    private void energy(Element element) {
        if (!(element instanceof SurfaceHeater) && !(element instanceof Deaerator)) {
            return;
        }
        int row = system.row(element.name() + ": энергия");
        int terms = 0;
        for (FlowChannel channel : element.flowChannels()) {
            List<Connection> in = RuleSupport.connections(element, channel.in());
            List<Connection> out = RuleSupport.connections(element, channel.out());
            if (in == null || out == null || in.isEmpty() || out.isEmpty()) {
                continue;
            }
            for (Connection connection : in) {
                contribute(row, connection, enthalpy(element, connection));
                terms++;
            }
            for (Connection connection : out) {
                contribute(row, connection, -enthalpy(element, connection));
                terms++;
            }
        }
        if (terms == 0) {
            throw new IllegalStateException("«" + element.name() + "»: аппарат не подключён — "
                    + "баланс энергии записывать не из чего");
        }
    }

    /** Слагаемое {@code coefficient·G}: известный расход уходит в правую часть. */
    private void contribute(int row, Connection connection, double coefficient) {
        Integer column = columns.get(connection);
        if (column != null) {
            system.term(row, column, coefficient);
            return;
        }
        Double flow = journal.value(connection, Quantity.FLOW);
        if (flow == null) {
            throw new IllegalStateException("расход «" + connection.getName()
                    + "» не попал в неизвестные: состояние схемы изменилось после сборки системы");
        }
        system.constant(row, -coefficient * flow);
    }

    private double enthalpy(Element element, Connection connection) {
        Double enthalpy = journal.value(connection, Quantity.ENTHALPY);
        if (enthalpy == null) {
            throw new IllegalStateException("«" + element.name() + "»: энтальпия связи «"
                    + connection.getName() + "» (" + connection.getPortsUnames() + ") не разрешена — "
                    + "баланс энергии аппарата записать нельзя: система линейна по расходам только "
                    + "при известных энтальпиях");
        }
        return enthalpy;
    }

    private static void requireStage(CalculationJournal journal) {
        Objects.requireNonNull(journal, "journal");
        if (!journal.isOpen()) {
            throw new IllegalStateException("журнал завершён: расходы писать некуда");
        }
        Stage pending = journal.pendingStage();
        if (pending != Stage.SOLVED) {
            throw new IllegalStateException("балансы расходов решаются на этапе "
                    + Stage.SOLVED.name() + " (" + Stage.SOLVED.title() + "), а журнал ждёт "
                    + pending.name() + " (" + pending.title() + "): сначала разрешите состояния");
        }
    }

    private static String describe(Connection connection) {
        return "«" + connection.getName() + "» [" + connection.getPortsUnames() + "]";
    }

    /** Итог решения: расходы всех сечений и диагностика системы. */
    public record FlowSolution(int rank, int unknowns, int equations, double residual,
                               List<Connection> unknownConnections,
                               Map<Connection, Double> flows,
                               List<String> boundaries) {

        public FlowSolution {
            unknownConnections = List.copyOf(unknownConnections);
            flows = Collections.unmodifiableMap(new LinkedHashMap<>(flows));
            boundaries = List.copyOf(boundaries);
        }

        /** Расход сечения; {@code null} — расхода нет (сечение не входит в расчёт). */
        public Double flow(Connection connection) {
            if (!flows.containsKey(connection)) {
                throw new IllegalArgumentException("связь «" + connection.getName()
                        + "» не принадлежит схеме расчёта");
            }
            return flows.get(connection);
        }

        /** Подпись «балансы расходов: 28 уравнений, 27 неизвестных, ранг 27, невязка 4.2E-13». */
        public String describe() {
            StringBuilder text = new StringBuilder("балансы расходов: ")
                    .append(equations).append(" уравнений, ").append(unknowns)
                    .append(" неизвестных, ранг ").append(rank)
                    .append(", невязка ").append(residual);
            for (String boundary : boundaries) {
                text.append(System.lineSeparator()).append("  граничное условие: ").append(boundary);
            }
            return text.toString();
        }
    }
}
