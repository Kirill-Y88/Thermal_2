package org.kpush88.Thermal_2.v3;

/**
 * Насос: повышает давление воды, а его работа уходит в энтальпию.
 *
 * <p>Один вход и один выход, один водяной канал — вывод по контуру портов работает без
 * переопределения. Насос стоит на границе трактов: конденсатный — в конденсатном,
 * питательный — в питательном, и оба не меняют контур, поэтому портов пара у насоса нет.
 *
 * <p>Параметры аппарата — <b>приращение давления</b> (напор) и <b>КПД</b>. Напор — это то, что
 * насос умеет: он не выводится из состояния, поэтому задаётся числом в МПа. КПД превращает
 * идеальную работу {@code v′·Δp} в реальную: вода несжимаема, поэтому энтальпия на напоре
 * равна {@code h_вс + v′·Δp/η}. Если КПД не задан, работа считается идеальной (η = 1) — это
 * нижняя оценка, и она честно записана в модели расчётного слоя ({@code PumpModel}), а не
 * спрятана.
 *
 * <p>Без заданного напора насос остаётся «нулевым»: состояние не меняется, давление напора
 * определяется трактом (соседними сечениями). Так схема собирается и до того, как параметры
 * насосов известны, — незаданное просто остаётся в отчёте {@code missing()}.
 */
public class Pump extends Device {

    private final Port waterIn = inPort("waterIn", SpaceType.WATER);
    private final Port waterOut = outPort("waterOut", SpaceType.WATER);

    /** Приращение давления, МПа; {@code null} — напор не задан. */
    private Double pressureRise;

    /** КПД насоса в долях единицы; {@code null} — идеальный процесс (η = 1). */
    private Double efficiency;

    public Pump(String uname) {
        super(uname);
    }

    public Pump(Long id, String uname) {
        super(id, uname);
    }

    /** Вода на входе (всасывание). */
    public Port waterIn() {
        return waterIn;
    }

    /** Вода на выходе (напор). */
    public Port waterOut() {
        return waterOut;
    }

    /**
     * Задаёт приращение давления (напор) насоса.
     *
     * <p>Именно приращение, а не абсолютное давление напора: давление на всасывании задаётся
     * трактом (деаэратор, конденсатор, соседний насос), и насос к нему прибавляет своё. Так
     * параметр остаётся свойством аппарата и не дублирует ГУ схемы.
     *
     * @param mpa приращение давления, МПа
     * @return этот насос — для сборки схемы одной цепочкой
     * @throws IllegalArgumentException если приращение не положительно
     */
    public Pump withPressureRise(double mpa) {
        if (!(mpa > 0)) {
            throw new IllegalArgumentException("у насоса «" + name()
                    + "» приращение давления должно быть положительным: " + mpa + " МПа");
        }
        this.pressureRise = mpa;
        return this;
    }

    /**
     * Задаёт КПД насоса.
     *
     * @param efficiency КПД в долях единицы, {@code 0 < η ≤ 1}
     * @return этот насос — для сборки схемы одной цепочкой
     * @throws IllegalArgumentException если КПД не попадает в (0, 1]
     */
    public Pump withEfficiency(double efficiency) {
        if (!(efficiency > 0 && efficiency <= 1)) {
            throw new IllegalArgumentException("у насоса «" + name()
                    + "» КПД должен быть в (0, 1]: " + efficiency);
        }
        this.efficiency = efficiency;
        return this;
    }

    /** Приращение давления, МПа; {@code null} — напор не задан. */
    public Double getPressureRise() {
        return pressureRise;
    }

    /** КПД насоса; {@code null} — идеальный процесс (η = 1). */
    public Double getEfficiency() {
        return efficiency;
    }

    @Override
    public ElementType elementType() {
        return ElementType.PUMP;
    }
}
