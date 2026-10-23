package org.kpush88.Thermal_2.v3;

import java.util.ArrayList;
import java.util.List;

/**
 * Турбина: свежий пар расширяется, отбирается в регенеративные подогреватели и уходит в
 * конденсатор.
 *
 * <p>Порты: вход {@code steamIn}, по порту на каждый отбор ({@code extraction1Out},
 * {@code extraction2Out}, …) и выхлоп {@code exhaustOut}. У каждого отбора <b>свой порт</b>,
 * потому что у каждого отбора свои параметры: давление и расход — состояние связи, а связь
 * подключается к одному порту. Общий порт-коллектор означал бы один узел с одними
 * параметрами на все отборы и заставил бы гадать, чьё давление записывать в связь; поэтому
 * число отборов — свойство конструкции турбины (параметр конструктора), а не следствие того,
 * сколько труб успели подключить.
 *
 * <p>Порты объявлены в порядке хода расширения: первый отбор — самый горячий, выхлоп —
 * последним. Порядок задан объявлением, поэтому не зависит от порядка вызовов
 * {@link Scheme#link}; {@link Element#getOutPutConnections()} перечисляет отборы в том же
 * порядке.
 *
 * <p>Каждый отбор необязателен (кратность 0…1): отбор может быть не подключён — тогда расход
 * через него нулевой, а «второй патрубок» на том же узле невозможен. У конденсационной
 * турбины отборов нет вовсе ({@code extractionCount = 0}).
 *
 * <p>Доли расходов по отборам — предмет расчёта, а не конструкции. А вот <b>внутренний
 * относительный КПД</b> ηoi — параметр аппарата: он определяет, насколько реальное расширение
 * в отсеке хуже изоэнтропного, и без него модель расчётного слоя ({@code TurbineModel})
 * не берётся считать энтальпии отборов.
 * Давления отборов приходят из связей (их задаёт режим или подогреватели), а не из турбины:
 * турбина расширяет пар <b>до</b> давления, которое уже определено схемой.
 *
 * <p>Из того же семейства — <b>электрическая мощность</b> и <b>КПД турбоагрегата по мощности</b>
 * η_м·η_г. Мощность задаёт режим, а не конструкцию, но относится к турбине, а не к трассе, поэтому
 * хранится здесь: именно она — цель расчёта цикла, к которой подбирается расход свежего пара.
 * Энтальпий эти два параметра не меняют: они связывают уже найденную внутреннюю мощность с
 * электрической, поэтому их читает расчёт цикла, а не модель состояния
 * ({@code TurbineModel}).
 */
public class Turbine extends Device {

    private final Port steamIn;

    /** Отборы в порядке хода расширения; номер отбора — индекс + 1. */
    private final List<Port> extractionOuts;

    private final Port exhaustOut;

    /** Внутренний относительный КПД ηoi; {@code null} — не задан. */
    private Double internalEfficiency;

    /** Электрическая мощность на клеммах генератора, кВт; {@code null} — не задана. */
    private Double electricPower;

    /** КПД турбоагрегата по мощности (η_м·η_г); {@code null} — не задан. */
    private Double electricEfficiency;

    /**
     * @param uname           имя турбины для отчётов
     * @param extractionCount число отборов: у каждого отбора будет свой порт
     */
    public Turbine(String uname, int extractionCount) {
        this(null, uname, extractionCount);
    }

    public Turbine(Long id, String uname, int extractionCount) {
        super(id, uname);
        if (extractionCount < 0) {
            throw new IllegalArgumentException("у турбины «" + name() + "» не может быть отборов: "
                    + extractionCount);
        }
        steamIn = inPort("steamIn", SpaceType.STEAM);
        List<Port> declared = new ArrayList<>(extractionCount);
        for (int number = 1; number <= extractionCount; number++) {
            declared.add(outPort("extraction" + number + "Out", SpaceType.STEAM, 0, 1));
        }
        extractionOuts = List.copyOf(declared);
        exhaustOut = outPort("exhaustOut", SpaceType.STEAM);
    }

    /** Свежий пар. */
    public Port steamIn() {
        return steamIn;
    }

    /** Отборы в порядке хода расширения (только чтение, может быть пустым). */
    public List<Port> extractionOuts() {
        return extractionOuts;
    }

    /**
     * Порт отбора номер {@code number} (нумерация с 1: первый отбор — самый горячий).
     *
     * @throws IllegalArgumentException если отбора с таким номером у турбины нет
     */
    public Port extractionOut(int number) {
        if (number < 1 || number > extractionOuts.size()) {
            throw new IllegalArgumentException("у турбины «" + name() + "» нет отбора №" + number
                    + ": объявлено отборов — " + extractionOuts.size());
        }
        return extractionOuts.get(number - 1);
    }

    /** Выхлоп в конденсатор. */
    public Port exhaustOut() {
        return exhaustOut;
    }

    /**
     * Задаёт внутренний относительный КПД ηoi.
     *
     * <p>КПД относится ко всей турбине, а не к отсеку: отсеки различаются давлениями отборов,
     * и разные КПД по отсекам — следующий шаг (тогда параметр переедет на отсек). Пока ηoi
     * один, потому что турбина в схеме одна и её проточная часть считается целиком.
     *
     * @param efficiency ηoi в долях единицы, {@code 0 < η ≤ 1}
     * @return эта турбина — для сборки схемы одной цепочкой
     * @throws IllegalArgumentException если КПД не попадает в (0, 1]
     */
    public Turbine withInternalEfficiency(double efficiency) {
        if (!(efficiency > 0 && efficiency <= 1)) {
            throw new IllegalArgumentException("у турбины «" + name()
                    + "» внутренний относительный КПД должен быть в (0, 1]: " + efficiency);
        }
        this.internalEfficiency = efficiency;
        return this;
    }

    /** Внутренний относительный КПД ηoi; {@code null} — параметр не задан. */
    public Double getInternalEfficiency() {
        return internalEfficiency;
    }

    /**
     * Задаёт электрическую мощность турбоагрегата — на клеммах генератора.
     *
     * <p>Мощность — это <b>задание расчёта</b>, а не состояние проточной части: из неё считается
     * расход свежего пара ({@code D₀ = N_э / (H₀·ηoi·η_м·η_г)}), а энтальпии отборов от неё не
     * зависят. Поэтому модель состояния ({@code TurbineModel}) мощность не читает: она расширяет
     * пар до заданных давлений, а мощность проверяется отдельно — сравнением с
     * {@code ΣD_i·H_i·η_м·η_г}. Расчёт цикла читает этот параметр как цель, к которой подбирается
     * коэффициент регенерации.
     *
     * <p>Единица — кВт, потому что расходы в схеме кг/с, а энтальпии кДж/кг: произведение даёт
     * сразу кВт, и пересчёт в МВт не нужен ни в одной формуле.
     *
     * @param kilowatts электрическая мощность, кВт
     * @return эта турбина — для сборки схемы одной цепочкой
     * @throws IllegalArgumentException если мощность не положительна
     */
    public Turbine withElectricPower(double kilowatts) {
        if (!(kilowatts > 0)) {
            throw new IllegalArgumentException("у турбины «" + name()
                    + "» электрическая мощность должна быть положительной: " + kilowatts + " кВт");
        }
        this.electricPower = kilowatts;
        return this;
    }

    /** Электрическая мощность на клеммах генератора, кВт; {@code null} — параметр не задан. */
    public Double getElectricPower() {
        return electricPower;
    }

    /**
     * Задаёт КПД турбоагрегата по мощности — долю внутренней мощности, доходящую до клемм
     * генератора: {@code η_эм = η_м·η_г} (механический КПД на КПД генератора).
     *
     * <p>Это не «КПД турбины» из методички ({@code η_э = ηoi·η_м·η_г}, 0.76…0.90): ηoi уже учтён в
     * энтальпиях отборов, и второй раз его применять нельзя. Здесь остаются только потери между
     * внутренней мощностью и электрической, поэтому значение близко к единице (0.96…0.99).
     *
     * @param efficiency η_эм в долях единицы, {@code 0 < η ≤ 1}
     * @return эта турбина — для сборки схемы одной цепочкой
     * @throws IllegalArgumentException если КПД не попадает в (0, 1]
     */
    public Turbine withElectricEfficiency(double efficiency) {
        if (!(efficiency > 0 && efficiency <= 1)) {
            throw new IllegalArgumentException("у турбины «" + name()
                    + "» КПД турбоагрегата по мощности должен быть в (0, 1]: " + efficiency);
        }
        this.electricEfficiency = efficiency;
        return this;
    }

    /** КПД турбоагрегата по мощности η_м·η_г; {@code null} — параметр не задан. */
    public Double getElectricEfficiency() {
        return electricEfficiency;
    }

    @Override
    public ElementType elementType() {
        return ElementType.TURBINE;
    }
}

