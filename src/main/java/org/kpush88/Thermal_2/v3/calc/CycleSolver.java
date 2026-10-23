package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Boiler;
import org.kpush88.Thermal_2.v3.Condenser;
import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.FlowChannel;
import org.kpush88.Thermal_2.v3.Pump;
import org.kpush88.Thermal_2.v3.Scheme;
import org.kpush88.Thermal_2.v3.Turbine;
import org.kpush88.Thermal_2.v3.thermo.WaterSteam;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Расчёт цикла: подбор расхода свежего пара под заданную электрическую мощность.
 *
 * <p>Схема и её состояния не зависят от расходов, а расходы зависят от одного числа — расхода
 * свежего пара. Поэтому расчёт распадается на две задачи, и внешняя итерация их связывает:
 * <ol>
 *   <li><b>состояния</b> — {@link StateResolver} до этапа {@link Stage#RESOLVED}: давления, энтальпии,
 *       недогревы, работа насосов;</li>
 *   <li><b>расходы</b> — {@link FlowSystem} с граничным условием {@code G_свежего = D₀}: балансы
 *       массы и энергии аппаратов;</li>
 *   <li><b>проверка мощности</b> — {@code N_э,расч = η_эм·ΣD_i·H_i} по найденным расходам и
 *       перепадам отсеков. Если она не совпала с заданием, расход свежего пара пересчитывается.</li>
 * </ol>
 *
 * <p>Откуда берётся первое приближение и как идёт поправка (та же схема, что в
 * {@code theory/04}, п. 2, и в прототипе v1):
 * <pre>
 *   D₀ = k_рег · N_э / (H₀ · ηoi · η_эм)      H₀ — располагаемый теплоперепад до конденсатора
 *   N_э,расч = η_эм · N_i                     N_i — внутренняя мощность по расходам
 *   σ = (N_э,расч − N_э) / N_э
 *   k_рег ← k_рег · N_э / N_э,расч            пока |σ| > допуска
 * </pre>
 *
 * <p><b>Почему сходится за две итерации.</b> Энтальпии от расходов не зависят (давления заданы
 * режимом, ηoi — параметр аппарата), поэтому мощность пропорциональна расходу: σ после первой
 * итерации — это ровно ошибка исходного коэффициента регенерации. Поправка по отношению мощностей
 * её и снимает, а вторая итерация нужна, чтобы это подтвердить числом, а не рассуждением.
 *
 * <p><b>Что такое k_рег.</b> Это не отношение расходов, а <b>надбавка на регенерацию</b>: во
 * сколько раз пара через турбину нужно пропустить больше, чем в цикле без отборов
 * ({@code D_без = N_э/(H₀·ηoi·η_эм)}, весь пар расширяется до давления конденсатора). Отборы
 * забирают пар, который уже не работает в последних отсеках, поэтому для той же мощности через
 * турбину идёт больше пара: в реперном расчёте k_рег ≈ 1.17. Коэффициент не выводится из схемы —
 * он и есть то, что подбирается внешней итерацией, а {@link #withRegenerationCoefficient} задаёт
 * начальное приближение.
 *
 * <p><b>Каждая итерация считается заново.</b> Журнал хранит этапы по порядку, и пересчитать
 * {@code SOLVED} в нём нельзя; поэтому итерация начинается со свежего журнала, а связи
 * возвращаются к исходным данным ({@link InputSnapshot}) — иначе посчитанные прошлой итерацией
 * расходы стали бы ГУ для следующей.
 *
 * <p><b>Схема после расчёта остаётся в исходном виде.</b> Журнал пишет вычисленные величины и в
 * связи, поэтому по завершении они возвращаются к входным: посчитанное живёт в журнале результата
 * ({@link CycleResult#journal()}). Иначе повторный расчёт прочитал бы расходы прошлого как ГУ и
 * упёрся бы в «два разных расхода на одном сечении», а сама схема перестала бы быть исходными
 * данными.
 *
 * <p>Расчёт ведётся по одной турбине: мощность и КПД — её параметры, и складывать их из нескольких
 * турбин в одну цифру было бы уже другим расчётом. Схема без турбины или с двумя — ошибка с
 * указанием, сколько их найдено.
 */
public final class CycleSolver {

    /** Допуск сходимости по мощности: 0.5 %, как в методике расчёта. */
    public static final double DEFAULT_TOLERANCE = 0.005;

    /** Предел внешних итераций: двух достаточно, запас — на случай изменённой модели. */
    public static final int DEFAULT_MAX_ITERATIONS = 10;

    /** Коэффициент регенерации по умолчанию: расход свежего пара равен расходу питательной воды. */
    public static final double DEFAULT_REGENERATION = 1.0;

    private final Scheme scheme;
    private final double regeneration;
    private final double tolerance;
    private final int maxIterations;

    private final WaterSteam steam = new WaterSteam();

    private CycleSolver(Scheme scheme, double regeneration, double tolerance, int maxIterations) {
        this.scheme = scheme;
        this.regeneration = regeneration;
        this.tolerance = tolerance;
        this.maxIterations = maxIterations;
    }

    /** Расчёт цикла по схеме с настройками по умолчанию. */
    public static CycleSolver of(Scheme scheme) {
        return new CycleSolver(Objects.requireNonNull(scheme, "scheme"), DEFAULT_REGENERATION,
                DEFAULT_TOLERANCE, DEFAULT_MAX_ITERATIONS);
    }

    /**
     * Начальный коэффициент регенерации.
     *
     * @throws IllegalArgumentException если коэффициент не положителен
     */
    public CycleSolver withRegenerationCoefficient(double coefficient) {
        if (!(coefficient > 0)) {
            throw new IllegalArgumentException("коэффициент регенерации должен быть положительным: "
                    + coefficient);
        }
        return new CycleSolver(scheme, coefficient, tolerance, maxIterations);
    }

    /**
     * Допуск сходимости по мощности, в долях единицы.
     *
     * @throws IllegalArgumentException если допуск не попадает в (0, 1)
     */
    public CycleSolver withTolerance(double relative) {
        if (!(relative > 0 && relative < 1)) {
            throw new IllegalArgumentException("допуск сходимости должен быть в (0, 1): " + relative);
        }
        return new CycleSolver(scheme, regeneration, relative, maxIterations);
    }

    /**
     * Предел числа внешних итераций.
     *
     * @throws IllegalArgumentException если предел меньше единицы
     */
    public CycleSolver withMaxIterations(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("предел итераций должен быть не меньше 1: " + limit);
        }
        return new CycleSolver(scheme, regeneration, tolerance, limit);
    }

    /**
     * Считает цикл и возвращает его показатели.
     *
     * @throws IllegalStateException если у турбины не заданы мощность или КПД, состояния не
     *                               разрешились, балансы не замкнулись или внешняя итерация не
     *                               сошлась
     */
    public CycleResult calculate() {
        Turbine turbine = singleTurbine();
        double power = required(turbine.getElectricPower(), "электрическая мощность N_э",
                turbine, "withElectricPower(...)");
        double electricEfficiency = required(turbine.getElectricEfficiency(),
                "КПД турбоагрегата по мощности η_эм", turbine, "withElectricEfficiency(...)");
        double internalEfficiency = required(turbine.getInternalEfficiency(),
                "внутренний относительный КПД ηoi", turbine, "withInternalEfficiency(...)");
        Connection live = RuleSupport.connection(turbine, turbine.steamIn());
        if (live == null) {
            throw new IllegalStateException("турбина «" + turbine.name()
                    + "» не подключена к свежему пару: порт steamIn без связи");
        }

        InputSnapshot inputs = InputSnapshot.of(scheme);
        try {
            return iterate(inputs, turbine, live, power, electricEfficiency, internalEfficiency);
        } finally {
            inputs.restore();
        }
    }

    /**
     * Внешняя итерация: от начального коэффициента регенерации до совпадения мощности.
     *
     * <p>Каждый шаг начинается с возврата схемы к исходным данным ({@link InputSnapshot}) —
     * иначе расходы, посчитанные предыдущим шагом, стали бы входными данными для следующего.
     */
    private CycleResult iterate(InputSnapshot inputs, Turbine turbine, Connection live, double power,
                                double electricEfficiency, double internalEfficiency) {
        double coefficient = regeneration;
        List<CycleResult.Iteration> history = new ArrayList<>();
        CalculationJournal journal;
        double flow;
        double internalPower;
        double calculated;
        double deviation;
        while (true) {
            inputs.restore();
            journal = CalculationJournal.of(scheme);
            StateResolver.of(journal).resolveTo(Stage.RESOLVED);

            double available = availableHeatDrop(journal, turbine, live);
            flow = coefficient * power / (available * internalEfficiency * electricEfficiency);
            FlowSystem.of(journal).withFlow(live, flow).solve();
            journal.capture(Stage.SOLVED);

            internalPower = internalPower(journal, turbine);
            calculated = electricEfficiency * internalPower;
            deviation = (calculated - power) / power;
            history.add(new CycleResult.Iteration(history.size() + 1, coefficient, flow,
                    internalPower, calculated, deviation));
            if (Math.abs(deviation) <= tolerance) {
                break;
            }
            if (!(calculated > 0)) {
                throw new IllegalStateException("расчётная электрическая мощность не положительна ("
                        + calculated + " кВт): поправлять коэффициент регенерации не от чего:"
                        + System.lineSeparator() + describe(history));
            }
            if (history.size() >= maxIterations) {
                throw new IllegalStateException("внешняя итерация не сошлась за " + maxIterations
                        + " итераций (допуск " + percent(tolerance) + "):" + System.lineSeparator()
                        + describe(history));
            }
            coefficient = coefficient * power / calculated;
        }

        List<SchemeState.Missing> missing = journal.missing();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("состояние схемы осталось неполным: не задано "
                    + missing.size() + " величин, поэтому показатели цикла не определены");
        }
        double boilerDuty = heatInput(journal);
        if (!(boilerDuty > 0)) {
            throw new IllegalStateException("теплота, подведённая в котле, не положительна: "
                    + boilerDuty + " кВт — проверьте параметры свежего пара и питательной воды");
        }
        double condenserDuty = heatRejected(journal);
        double pumpPower = pumpPower(journal);
        journal.capture(Stage.RESULT);

        return new CycleResult(power, calculated, internalPower, pumpPower, boilerDuty,
                condenserDuty, internalPower / boilerDuty, (internalPower - pumpPower) / boilerDuty,
                flow, coefficient, deviation, history.size(), history, journal);
    }

    /**
     * Располагаемый теплоперепад H₀: от свежего пара до изоэнтропной энтальпии при давлении
     * выхлопа. Это теплоперепад цикла без отборов — именно он превращает заданную мощность в
     * начальное приближение расхода.
     */
    private double availableHeatDrop(CalculationJournal journal, Turbine turbine, Connection live) {
        Double pressure = journal.value(live, Quantity.PRESSURE);
        Double enthalpy = journal.value(live, Quantity.ENTHALPY);
        Connection exhaust = RuleSupport.connection(turbine, turbine.exhaustOut());
        Double exhaustPressure = exhaust == null ? null : journal.value(exhaust, Quantity.PRESSURE);
        if (pressure == null || enthalpy == null || exhaustPressure == null) {
            throw new IllegalStateException("располагаемый теплоперепад H₀ не из чего считать: "
                    + "нужны давление и энтальпия перед турбиной и давление выхлопа (p = "
                    + pressure + ", h = " + enthalpy + ", p_выхлопа = " + exhaustPressure + ")");
        }
        return enthalpy - steam.enthalpyPS(exhaustPressure, steam.entropyPH(pressure, enthalpy));
    }

    /** Внутренняя мощность турбины: сколько энергии пар отдал в её каналах, кВт. */
    private static double internalPower(CalculationJournal journal, Turbine turbine) {
        return -energy(journal, turbine);
    }

    /** Теплота, подведённая в котлах, кВт. */
    private double heatInput(CalculationJournal journal) {
        double sum = 0;
        for (Element element : scheme.getElements()) {
            if (element instanceof Boiler) {
                sum += energy(journal, element);
            }
        }
        return sum;
    }

    /** Теплота, отведённая в конденсаторах, кВт (положительная величина). */
    private double heatRejected(CalculationJournal journal) {
        double sum = 0;
        for (Element element : scheme.getElements()) {
            if (element instanceof Condenser) {
                sum += energy(journal, element);
            }
        }
        return -sum;
    }

    /** Мощность насосов: работа, которую они передали воде, кВт. */
    private double pumpPower(CalculationJournal journal) {
        double sum = 0;
        for (Element element : scheme.getElements()) {
            if (element instanceof Pump) {
                sum += energy(journal, element);
            }
        }
        return sum;
    }

    /**
     * Энергия, которую элемент отдаёт наружу: {@code Σвых G·h − Σвх G·h} по всем каналам.
     *
     * <p>Для турбины это работа (с минусом — она отдаёт её как разность энтальпий), для котла —
     * подведённая теплота, для конденсатора — отведённая, для насоса — затраченная работа. Знак
     * задаёт не элемент, а направление: энергия выхода минус энергия входа.
     */
    private static double energy(CalculationJournal journal, Element element) {
        double sum = 0;
        for (FlowChannel channel : element.flowChannels()) {
            List<Connection> in = RuleSupport.connections(element, channel.in());
            List<Connection> out = RuleSupport.connections(element, channel.out());
            if (in == null || out == null || in.isEmpty() || out.isEmpty()) {
                continue;
            }
            for (Connection connection : in) {
                sum -= energy(journal, element, connection);
            }
            for (Connection connection : out) {
                sum += energy(journal, element, connection);
            }
        }
        return sum;
    }

    private static double energy(CalculationJournal journal, Element element,
                                 Connection connection) {
        Double flow = journal.value(connection, Quantity.FLOW);
        Double enthalpy = journal.value(connection, Quantity.ENTHALPY);
        if (flow == null || enthalpy == null) {
            throw new IllegalStateException("«" + element.name() + "»: состояние связи «"
                    + connection.getName() + "» неполно (G = " + flow + ", h = " + enthalpy
                    + ") — энергетический показатель не посчитать");
        }
        return flow * enthalpy;
    }

    /** Единственная турбина схемы: с несколькими расчёт цикла не определён. */
    private Turbine singleTurbine() {
        List<Turbine> turbines = new ArrayList<>();
        for (Element element : scheme.getElements()) {
            if (element instanceof Turbine found) {
                turbines.add(found);
            }
        }
        if (turbines.size() != 1) {
            throw new IllegalStateException("расчёт цикла ведётся по одной турбине, а в схеме их "
                    + turbines.size() + ": мощность и КПД — её параметры");
        }
        return turbines.get(0);
    }

    private static double required(Double value, String what, Turbine turbine, String setter) {
        if (value == null) {
            throw new IllegalStateException("у турбины «" + turbine.name() + "» не задана " + what
                    + ": расчёт цикла ведётся от неё — задайте " + setter);
        }
        return value;
    }

    private static String describe(List<CycleResult.Iteration> history) {
        StringBuilder text = new StringBuilder();
        for (CycleResult.Iteration iteration : history) {
            text.append("  ").append(iteration.describe()).append(System.lineSeparator());
        }
        return text.toString();
    }

    private static String percent(double fraction) {
        return Math.round(fraction * 10000.0) / 100.0 + " %";
    }
}
