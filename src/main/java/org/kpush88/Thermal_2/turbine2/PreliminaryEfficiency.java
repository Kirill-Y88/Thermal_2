package org.kpush88.Thermal_2.turbine2;

import java.util.List;
import java.util.Locale;

/**
 * Предварительная оценка внутреннего относительного КПД ступени, отсека и турбины по компактной
 * эмпирической модели.
 *
 * <p><b>Зачем отдельный класс.</b> Геометрия проточной части появляется только после детального
 * расчёта, а оценить КПД нужно раньше — при выборе числа ступеней, диаметров и разбиении
 * теплоперепада. Модель не заменяет {@code StageSolver}: она даёт число, с которым можно начать,
 * и показывает, в какую сторону его сдвигают теплоперепад, объём, давление, влажность и расход.
 *
 * <p><b>Формула.</b>
 * <pre>
 *     η = 0.827
 *         − 0.048·(H/50 − 1)
 *         + 0.029·ln(v/0.1)
 *         − 0.635·(1 − x)
 *         + 0.099·(G/7.5 − 1)
 * </pre>
 * Здесь {@code H} — кДж/кг, {@code v} — м³/кг, {@code p} — МПа, {@code x} — степень сухости
 * (1 — перегретый пар), {@code G} — кг/с. Для ступени берутся параметры перед ней и сухость за
 * ней; для отсека — средние величины: {@code H/z}, {@code √(v_вх·v_вых)},
 * {@code √(p_вх·p_вых)}, {@code (x_вх+x_вых)/2}. Для турбины КПД — средневзвешенный по
 * {@code G·H} отсеков.
 *
 * <table>
 *   <caption>Коэффициенты модели</caption>
 *   <tr>
 *     <th>Тип ступени</th><th>η₀</th><th>H₀, кДж/кг</th><th>v₀, м³/кг</th>
 *     <th>p₀, МПа</th><th>G₀, кг/с</th><th>k_H</th><th>k_v</th><th>k_p</th>
 *     <th>k_x</th><th>k_G</th>
 *   </tr>
 *   <tr>
 *     <td>Нерегулируемая</td><td>0.827</td><td>50</td><td>0.1</td><td>1</td><td>7.5</td>
 *     <td>0.048</td><td>0.029</td><td>0</td><td>0.635</td><td>0.099</td>
 *   </tr>
 *   <tr>
 *     <td>Регулирующая одновенечная</td><td>0.80</td><td>100</td><td>0.04</td><td>1</td>
 *     <td>10</td><td>0.02</td><td>0.01</td><td>0</td><td>0.30</td><td>0.01</td>
 *   </tr>
 *   <tr>
 *     <td>Регулирующая двухвенечная</td><td>0.765</td><td>100</td><td>0.04</td><td>1</td>
 *     <td>10</td><td>0.02</td><td>0.01</td><td>0</td><td>0.30</td><td>0.01</td>
 *   </tr>
 * </table>
 *
 * <p><b>Давление.</b> В калибровке отдельная чувствительность к давлению оказалась нулевой:
 * после теплоперепада, объёма и влажности давление уже не даёт независимого вклада (в опорной
 * машине оно сильно коррелирует с объёмом). Член {@code k_p·ln(p/p₀)} в {@link StageLaw}
 * оставлен, чтобы модель можно было перенастроить на другую машину; в текущем законе
 * {@code k_p = 0}.
 *
 * <p><b>Калибровка.</b> Коэффициенты подобраны по опорной машине 10 МВт
 * ({@code org.kpush88.Thermal_2.turbine.ReferenceTurbine}): 27 ступеней и 5 отсеков, метод
 * наименьших квадратов с неотрицательными чувствительностями. Максимальная ошибка: ступени —
 * около 1.4 п.п., отсеки — около 1.6 п.п., турбина — около 0.4 п.п. Поэтому модель и
 * называется предварительной: она не воспроизводит треугольники скоростей и явные потери
 * {@code StageSolver}, а повторяет их суммарный эффект в окрестности опорной машины.
 *
 * <p><b>Почему не 0.10 по теплоперепаду.</b> Первый вариант коэффициентов брал
 * {@code k_H = 0.10}, как в приближённой оценке. На ЦНД это давало занижение на 5…7 п.п.:
 * теплоперепад там большой, и сильная линейная поправка «съедала» рост КПД от объёма. По
 * фактическим 27 ступеням чувствительность почти вдвое меньше — 0.048. Это не подгонка под
 * ответ, а разница между «на глаз» и калибровкой.
 *
 * <p><b>Регулирующие ступени.</b> Для {@link StageKind#REGULATING_SINGLE_ROW} и
 * {@link StageKind#REGULATING_DOUBLE_ROW} базовая точка 0.80 / 0.765 соответствует уровню
 * лопаточного КПД {@code η_ол}, а не внутреннего относительного {@code η_oi}. Если нужен
 * {@code η_oi} по формулам курсового проекта (включая потери с выходной скоростью, утечки и
 * влажность), используйте {@link CourseProjectEfficiency}. Это различие существенно: при
 * {@code h₀ = 175 кДж/кг} формула методички даёт для двухвенечной ступени около 0.658, тогда как
 * компактная модель — около 0.75.
 *
 * <p><b>Область применимости.</b> Формула монотонна и не имеет разрывов, но за пределами
 * калибровки (давление в сотни МПа, объём в десятки м³/кг, сухость около нуля) результат
 * ограничивается диапазоном {@code [0, 1]}. Признак ограничения виден в
 * {@link StageEstimate#clamped()}. Исключения выбрасываются только на некорректных входных
 * данных, а не на «неудобном» результате.
 *
 * <p>Класс неизменяемый и потокобезопасный: единственное состояние — ссылка на
 * {@link HsDiagram}, которая не меняется.
 */
public final class PreliminaryEfficiency {

    /** Базовый КПД калибровки при {@code H = 50}, {@code v = 0.1}, {@code p = 1}, {@code x = 1}, {@code G = 7.5}. */
    private static final double BASE_EFFICIENCY = 0.827;

    /** Опорный теплоперепад ступени, кДж/кг. */
    private static final double REFERENCE_HEAT_DROP = 50.0;

    /** Опорный удельный объём, м³/кг. */
    private static final double REFERENCE_VOLUME = 0.1;

    /** Опорное давление, МПа. */
    private static final double REFERENCE_PRESSURE = 1.0;

    /** Опорный расход, кг/с. */
    private static final double REFERENCE_FLOW = 7.5;

    /** Чувствительность к теплоперепаду, 1/(кДж/кг). */
    private static final double HEAT_DROP_SENSITIVITY = 0.048;

    /** Чувствительность к удельному объёму. */
    private static final double VOLUME_SENSITIVITY = 0.029;

    /** Чувствительность к давлению: в калибровке независимого вклада нет. */
    private static final double PRESSURE_SENSITIVITY = 0.0;

    /** Чувствительность к влажности. */
    private static final double WETNESS_SENSITIVITY = 0.635;

    /** Чувствительность к расходу. */
    private static final double FLOW_SENSITIVITY = 0.099;

    private final HsDiagram hs;

    /** Модель со своей hs-диаграммой. */
    public PreliminaryEfficiency() {
        this(new HsDiagram());
    }

    /**
     * Модель с общей hs-диаграммой.
     *
     * @param hs диаграмма, по которой считаются перепады, объёмы и сухости
     */
    public PreliminaryEfficiency(HsDiagram hs) {
        if (hs == null) {
            throw new IllegalArgumentException("hs-диаграмма не задана");
        }
        this.hs = hs;
    }

    /** Тип ступени: нерегулируемая или регулирующая одно-/двухвенечная. */
    public enum StageKind {
        NON_REGULATING("нерегулируемая"),
        REGULATING_SINGLE_ROW("регулирующая одновенечная"),
        REGULATING_DOUBLE_ROW("регулирующая двухвенечная");

        private final String label;

        StageKind(String label) {
            this.label = label;
        }

        /** Название для отчёта. */
        public String label() {
            return label;
        }

        /** Регулирующая ступень. */
        public boolean isRegulating() {
            return this != NON_REGULATING;
        }
    }

    /**
     * Набор коэффициентов модели для одного типа ступени.
     *
     * @param kind                  тип ступени
     * @param baseEfficiency        КПД в опорной точке
     * @param referenceHeatDrop     опорный теплоперепад, кДж/кг
     * @param referenceVolume       опорный удельный объём, м³/кг
     * @param referencePressure     опорное давление, МПа
     * @param referenceFlow         опорный расход, кг/с
     * @param heatDropSensitivity   чувствительность к теплоперепаду
     * @param volumeSensitivity     чувствительность к объёму
     * @param pressureSensitivity   чувствительность к давлению
     * @param wetnessSensitivity    чувствительность к влажности
     * @param flowSensitivity       чувствительность к расходу
     */
    public record StageLaw(
            StageKind kind,
            double baseEfficiency,
            double referenceHeatDrop,
            double referenceVolume,
            double referencePressure,
            double referenceFlow,
            double heatDropSensitivity,
            double volumeSensitivity,
            double pressureSensitivity,
            double wetnessSensitivity,
            double flowSensitivity) {

        /** Проверки, без которых формула потеряла бы смысл. */
        public StageLaw {
            if (kind == null) {
                throw new IllegalArgumentException("тип ступени не задан");
            }
            requireFraction(baseEfficiency, "базовый КПД");
            requirePositive(referenceHeatDrop, "опорный теплоперепад");
            requirePositive(referenceVolume, "опорный удельный объём");
            requirePositive(referencePressure, "опорное давление");
            requirePositive(referenceFlow, "опорный расход");
            requireNonNegative(heatDropSensitivity, "чувствительность к теплоперепаду");
            requireNonNegative(volumeSensitivity, "чувствительность к объёму");
            requireNonNegative(pressureSensitivity, "чувствительность к давлению");
            requireNonNegative(wetnessSensitivity, "чувствительность к влажности");
            requireNonNegative(flowSensitivity, "чувствительность к расходу");
        }

        /**
         * КПД по формуле без ограничения диапазоном {@code [0, 1]}.
         *
         * <p>Именно это значение сравнивается с калибровкой; ограничение делает
         * {@link PreliminaryEfficiency#stage(StageKind, StageState)}.
         */
        public double efficiency(double heatDrop, double volume, double pressure,
                                 double quality, double flow) {
            requirePositive(heatDrop, "теплоперепад ступени");
            requirePositive(volume, "удельный объём");
            requirePositive(pressure, "давление");
            requireQuality(quality);
            requirePositive(flow, "расход ступени");
            return baseEfficiency
                    - heatDropSensitivity * (heatDrop / referenceHeatDrop - 1.0)
                    + volumeSensitivity * Math.log(volume / referenceVolume)
                    + pressureSensitivity * Math.log(pressure / referencePressure)
                    - wetnessSensitivity * (1.0 - quality)
                    + flowSensitivity * (flow / referenceFlow - 1.0);
        }
    }

    private static final StageLaw NON_REGULATING_LAW = new StageLaw(
            StageKind.NON_REGULATING, BASE_EFFICIENCY,
            REFERENCE_HEAT_DROP, REFERENCE_VOLUME, REFERENCE_PRESSURE, REFERENCE_FLOW,
            HEAT_DROP_SENSITIVITY, VOLUME_SENSITIVITY, PRESSURE_SENSITIVITY,
            WETNESS_SENSITIVITY, FLOW_SENSITIVITY);

    private static final StageLaw REGULATING_SINGLE_ROW_LAW = new StageLaw(
            StageKind.REGULATING_SINGLE_ROW, 0.80,
            100.0, 0.04, 1.0, 10.0,
            0.02, 0.01, 0.0, 0.30, 0.01);

    private static final StageLaw REGULATING_DOUBLE_ROW_LAW = new StageLaw(
            StageKind.REGULATING_DOUBLE_ROW, 0.765,
            100.0, 0.04, 1.0, 10.0,
            0.02, 0.01, 0.0, 0.30, 0.01);

    /**
     * Закон для типа ступени.
     *
     * @param kind тип ступени
     * @return коэффициенты модели
     */
    public static StageLaw law(StageKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("тип ступени не задан");
        }
        return switch (kind) {
            case NON_REGULATING -> NON_REGULATING_LAW;
            case REGULATING_SINGLE_ROW -> REGULATING_SINGLE_ROW_LAW;
            case REGULATING_DOUBLE_ROW -> REGULATING_DOUBLE_ROW_LAW;
        };
    }

    /**
     * Состояние ступени для компактной модели.
     *
     * @param flow     расход, кг/с
     * @param heatDrop располагаемый теплоперепад, кДж/кг
     * @param pressure давление перед ступенью, МПа
     * @param volume   удельный объём перед ступенью, м³/кг
     * @param quality  степень сухости за ступенью, 0…1
     */
    public record StageState(double flow, double heatDrop, double pressure,
                             double volume, double quality) {

        public StageState {
            requirePositive(flow, "расход ступени");
            requirePositive(heatDrop, "теплоперепад ступени");
            requirePositive(pressure, "давление перед ступенью");
            requirePositive(volume, "удельный объём перед ступенью");
            requireQuality(quality);
        }
    }

    /**
     * Состояние отсека: границы, средние величины и число ступеней.
     *
     * @param flow           расход, кг/с
     * @param heatDrop       располагаемый теплоперепад отсека, кДж/кг
     * @param stageCount     число ступеней
     * @param inletPressure  давление на входе, МПа
     * @param inletVolume    удельный объём на входе, м³/кг
     * @param outletPressure давление на выходе, МПа
     * @param outletVolume   удельный объём на выходе, м³/кг
     * @param inletQuality   степень сухости на входе, 0…1
     * @param outletQuality  степень сухости на выходе, 0…1
     */
    public record SectionState(
            double flow,
            double heatDrop,
            int stageCount,
            double inletPressure,
            double inletVolume,
            double outletPressure,
            double outletVolume,
            double inletQuality,
            double outletQuality) {

        public SectionState {
            requirePositive(flow, "расход отсека");
            requirePositive(heatDrop, "теплоперепад отсека");
            if (stageCount < 1) {
                throw new IllegalArgumentException("число ступеней отсека должно быть "
                        + "положительным: " + stageCount);
            }
            requireExpansion(inletPressure, outletPressure);
            requirePositive(inletVolume, "удельный объём на входе отсека");
            requirePositive(outletVolume, "удельный объём на выходе отсека");
            requireQuality(inletQuality);
            requireQuality(outletQuality);
        }
    }

    /**
     * Оценка одной ступени: КПД, работа и разложение по поправкам.
     *
     * <p>Поправки записаны со знаком, с которым они входят в сумму. Их сумма плюс
     * {@code baseEfficiency} даёт неограниченное значение; {@code efficiency} — уже ограниченное
     * диапазоном {@code [0, 1]}. Если они различаются, {@link #clamped()} возвращает {@code true}.
     *
     * @param kind               тип ступени
     * @param efficiency         КПД после ограничения, 0…1
     * @param heatDrop           располагаемый теплоперепад, кДж/кг
     * @param internalWork       внутренняя работа {@code η·H}, кДж/кг
     * @param flow               расход, кг/с
     * @param pressure           давление перед ступенью, МПа
     * @param volume             удельный объём перед ступенью, м³/кг
     * @param quality            степень сухости за ступенью, 0…1
     * @param baseEfficiency     КПД в опорной точке
     * @param heatDropCorrection поправка на теплоперепад
     * @param volumeCorrection   поправка на объём
     * @param pressureCorrection поправка на давление
     * @param wetnessCorrection  поправка на влажность
     * @param flowCorrection     поправка на расход
     */
    public record StageEstimate(
            StageKind kind,
            double efficiency,
            double heatDrop,
            double internalWork,
            double flow,
            double pressure,
            double volume,
            double quality,
            double baseEfficiency,
            double heatDropCorrection,
            double volumeCorrection,
            double pressureCorrection,
            double wetnessCorrection,
            double flowCorrection) {

        public StageEstimate {
            if (kind == null) {
                throw new IllegalArgumentException("тип ступени не задан");
            }
            requireClosedFraction(efficiency, "КПД ступени");
            requirePositive(heatDrop, "теплоперепад ступени");
            if (!(internalWork >= 0.0 && internalWork <= heatDrop)) {
                throw new IllegalArgumentException("внутренняя работа ступени " + internalWork
                        + " кДж/кг вне диапазона [0, " + heatDrop + "]");
            }
            requirePositive(flow, "расход ступени");
            requirePositive(pressure, "давление перед ступенью");
            requirePositive(volume, "удельный объём перед ступенью");
            requireQuality(quality);
            requireFraction(baseEfficiency, "базовый КПД");
            requireFinite(heatDropCorrection, "поправка на теплоперепад");
            requireFinite(volumeCorrection, "поправка на объём");
            requireFinite(pressureCorrection, "поправка на давление");
            requireFinite(wetnessCorrection, "поправка на влажность");
            requireFinite(flowCorrection, "поправка на расход");
        }

        /** Сумма всех поправок к базовому КПД. */
        public double totalCorrection() {
            return heatDropCorrection + volumeCorrection + pressureCorrection
                    + wetnessCorrection + flowCorrection;
        }

        /** КПД до ограничения диапазоном {@code [0, 1]}. */
        public double rawEfficiency() {
            return baseEfficiency + totalCorrection();
        }

        /** Результат пришлось ограничить диапазоном {@code [0, 1]}. */
        public boolean clamped() {
            return Math.abs(rawEfficiency() - efficiency) > 1e-12;
        }

        /** Строка для отчёта. */
        public String describe() {
            return String.format(Locale.ROOT,
                    "ступень (%s): η = %.4f (база %.3f; поправки: H %+.4f, v %+.4f, "
                            + "p %+.4f, x %+.4f, G %+.4f)%s, H = %.2f кДж/кг, "
                            + "p = %.3f МПа, v = %.5f м³/кг, x = %.3f, G = %.4f кг/с",
                    kind.label(), efficiency, baseEfficiency, heatDropCorrection,
                    volumeCorrection, pressureCorrection, wetnessCorrection,
                    flowCorrection, clamped() ? ", ограничено [0, 1]" : "",
                    heatDrop, pressure, volume, quality, flow);
        }
    }

    /**
     * Оценка отсека: средний КПД ступени, работа и мощность.
     *
     * @param kind         тип ступеней отсека
     * @param efficiency   КПД отсека (средний по ступеням), 0…1
     * @param heatDrop     располагаемый теплоперепад отсека, кДж/кг
     * @param internalWork внутренняя работа {@code η·H}, кДж/кг
     * @param flow         расход, кг/с
     * @param stageCount   число ступеней
     * @param meanStage    оценка средней ступени, из которой получен КПД
     */
    public record SectionEstimate(
            StageKind kind,
            double efficiency,
            double heatDrop,
            double internalWork,
            double flow,
            int stageCount,
            StageEstimate meanStage) {

        public SectionEstimate {
            if (kind == null) {
                throw new IllegalArgumentException("тип ступени отсека не задан");
            }
            requireClosedFraction(efficiency, "КПД отсека");
            requirePositive(heatDrop, "теплоперепад отсека");
            if (!(internalWork >= 0.0 && internalWork <= heatDrop)) {
                throw new IllegalArgumentException("внутренняя работа отсека " + internalWork
                        + " кДж/кг вне диапазона [0, " + heatDrop + "]");
            }
            requirePositive(flow, "расход отсека");
            if (stageCount < 1) {
                throw new IllegalArgumentException("число ступеней отсека должно быть "
                        + "положительным: " + stageCount);
            }
            if (meanStage == null) {
                throw new IllegalArgumentException("оценка средней ступени не задана");
            }
            if (meanStage.kind() != kind) {
                throw new IllegalArgumentException("средняя ступень относится к типу "
                        + meanStage.kind() + ", а отсек — к " + kind);
            }
        }

        /** Располагаемая мощность отсека, кВт. */
        public double availablePower() {
            return flow * heatDrop;
        }

        /** Внутренняя мощность отсека, кВт. */
        public double internalPower() {
            return flow * internalWork;
        }

        /** Строка для отчёта. */
        public String describe() {
            return String.format(Locale.ROOT,
                    "отсек (%s, z = %d): η = %.4f, H = %.2f кДж/кг, W = %.2f кДж/кг, "
                            + "G = %.4f кг/с, N_расп = %.0f кВт, N_вн = %.0f кВт",
                    kind.label(), stageCount, efficiency, heatDrop, internalWork,
                    flow, availablePower(), internalPower());
        }
    }

    /**
     * Оценка турбины: КПД, взвешенный по {@code G·H} отсеков, и суммарные мощности.
     *
     * @param efficiency     КПД турбины, 0…1
     * @param availablePower располагаемая мощность {@code ΣG·H}, кВт
     * @param internalPower  внутренняя мощность {@code ΣG·η·H}, кВт
     * @param flow           расход через первый отсек (вход турбины), кг/с
     * @param sections       отсеки в порядке расширения
     */
    public record TurbineEstimate(
            double efficiency,
            double availablePower,
            double internalPower,
            double flow,
            List<SectionEstimate> sections) {

        public TurbineEstimate {
            requireClosedFraction(efficiency, "КПД турбины");
            requirePositive(availablePower, "располагаемая мощность турбины");
            if (!(internalPower >= 0.0 && internalPower <= availablePower)) {
                throw new IllegalArgumentException("внутренняя мощность турбины " + internalPower
                        + " кВт вне диапазона [0, " + availablePower + "]");
            }
            requirePositive(flow, "расход через турбину");
            if (sections == null || sections.isEmpty()) {
                throw new IllegalArgumentException("у турбины должен быть хотя бы один отсек");
            }
            sections = List.copyOf(sections);
        }

        /** Строка для отчёта: отсеки и итог. */
        public String describe() {
            StringBuilder text = new StringBuilder();
            for (SectionEstimate section : sections) {
                text.append(section.describe()).append(System.lineSeparator());
            }
            text.append(String.format(Locale.ROOT,
                    "турбина: η = %.4f, N_расп = %.0f кВт, N_вн = %.0f кВт, G = %.4f кг/с",
                    efficiency, availablePower, internalPower, flow));
            return text.toString();
        }
    }

    /**
     * Оценка ступени по её состоянию.
     *
     * <p>Поправки считаются отдельно, чтобы их можно было показать в отчёте. Итог ограничивается
     * диапазоном {@code [0, 1]}; исходное значение доступно через
     * {@link StageEstimate#rawEfficiency()}.
     *
     * @param kind  тип ступени
     * @param state расход, теплоперепад, давление, объём и сухость
     * @return оценка ступени
     */
    public StageEstimate stage(StageKind kind, StageState state) {
        if (state == null) {
            throw new IllegalArgumentException("состояние ступени не задано");
        }
        StageLaw law = law(kind);
        double heatDropCorrection = -law.heatDropSensitivity()
                * (state.heatDrop() / law.referenceHeatDrop() - 1.0);
        double volumeCorrection = law.volumeSensitivity()
                * Math.log(state.volume() / law.referenceVolume());
        double pressureCorrection = law.pressureSensitivity()
                * Math.log(state.pressure() / law.referencePressure());
        double wetnessCorrection = -law.wetnessSensitivity() * (1.0 - state.quality());
        double flowCorrection = law.flowSensitivity()
                * (state.flow() / law.referenceFlow() - 1.0);
        double raw = law.baseEfficiency() + heatDropCorrection + volumeCorrection
                + pressureCorrection + wetnessCorrection + flowCorrection;
        double efficiency = clamp(raw);
        return new StageEstimate(kind, efficiency, state.heatDrop(),
                efficiency * state.heatDrop(), state.flow(), state.pressure(),
                state.volume(), state.quality(), law.baseEfficiency(),
                heatDropCorrection, volumeCorrection, pressureCorrection,
                wetnessCorrection, flowCorrection);
    }

    /**
     * Оценка ступени по параметрам на входе и давлению за ней.
     *
     * <p>Состояние за ступенью неизвестно, поэтому сухость берётся в конце изоэнтропы. Это
     * нижняя оценка {@code x} и, следовательно, осторожная (заниженная) оценка КПД. Если
     * действительная энтальпия за ступенью известна, используйте перегрузку с {@code hOut}.
     *
     * @param kind  тип ступени
     * @param flow  расход, кг/с
     * @param pIn   давление перед ступенью, МПа
     * @param hIn   энтальпия перед ступенью, кДж/кг
     * @param pOut  давление за ступенью, МПа
     * @return оценка ступени
     */
    public StageEstimate stage(StageKind kind, double flow, double pIn, double hIn, double pOut) {
        requirePositive(flow, "расход ступени");
        requireExpansion(pIn, pOut);
        double hOut = hs.isentropicEnthalpy(pIn, hIn, pOut);
        return stage(kind, new StageState(flow, hs.isentropicDrop(pIn, hIn, pOut), pIn,
                hs.volumePH(pIn, hIn), hs.vapourFractionPH(pOut, hOut)));
    }

    /**
     * Оценка ступени по действительному состоянию за ней.
     *
     * <p>Располагаемый перепад всё равно считается по изоэнтропе
     * {@code H = h₁ − h(p₂, s₁)}, а {@code hOut} используется только для объёма и сухости за
     * ступенью. Так оценка КПД остаётся сравнимой с {@code StageResult.efficiency()}.
     *
     * @param kind  тип ступени
     * @param flow  расход, кг/с
     * @param pIn   давление перед ступенью, МПа
     * @param hIn   энтальпия перед ступенью, кДж/кг
     * @param pOut  давление за ступенью, МПа
     * @param hOut  энтальпия за ступенью, кДж/кг
     * @return оценка ступени
     */
    public StageEstimate stage(StageKind kind, double flow, double pIn, double hIn,
                               double pOut, double hOut) {
        requirePositive(flow, "расход ступени");
        requireExpansion(pIn, pOut);
        return stage(kind, new StageState(flow, hs.isentropicDrop(pIn, hIn, pOut), pIn,
                hs.volumePH(pIn, hIn), hs.vapourFractionPH(pOut, hOut)));
    }

    /**
     * Оценка отсека по средним величинам.
     *
     * <p>Средняя ступень: {@code H/z}, {@code √(v_вх·v_вых)}, {@code √(p_вх·p_вых)},
     * {@code (x_вх+x_вых)/2}. КПД отсека принимается равным КПД этой средней ступени. Для
     * отсека с сильным изменением объёма это приближение: оно не разбивает отсек на ступени, а
     * сжимает его в одну осреднённую ступень.
     *
     * @param kind  тип ступеней отсека
     * @param state границы, средние величины и число ступеней
     * @return оценка отсека
     */
    public SectionEstimate section(StageKind kind, SectionState state) {
        if (state == null) {
            throw new IllegalArgumentException("состояние отсека не задано");
        }
        double meanHeatDrop = state.heatDrop() / state.stageCount();
        double meanVolume = Math.sqrt(state.inletVolume() * state.outletVolume());
        double meanPressure = Math.sqrt(state.inletPressure() * state.outletPressure());
        double meanQuality = 0.5 * (state.inletQuality() + state.outletQuality());
        StageEstimate meanStage = stage(kind, new StageState(state.flow(), meanHeatDrop,
                meanPressure, meanVolume, meanQuality));
        double efficiency = meanStage.efficiency();
        return new SectionEstimate(kind, efficiency, state.heatDrop(),
                efficiency * state.heatDrop(), state.flow(), state.stageCount(), meanStage);
    }

    /**
     * Оценка отсека по параметрам на входе и давлению за ним.
     *
     * <p>Состояние за отсеком неизвестно: объём и сухость берутся в конце изоэнтропы. Это
     * осторожная оценка, как и у одноимённой перегрузки {@link #stage(StageKind, double,
     * double, double, double)}.
     *
     * @param kind       тип ступеней отсека
     * @param flow       расход, кг/с
     * @param pIn        давление на входе, МПа
     * @param hIn        энтальпия на входе, кДж/кг
     * @param pOut       давление на выходе, МПа
     * @param stageCount число ступеней
     * @return оценка отсека
     */
    public SectionEstimate section(StageKind kind, double flow, double pIn, double hIn,
                                   double pOut, int stageCount) {
        requirePositive(flow, "расход отсека");
        requireExpansion(pIn, pOut);
        double hOut = hs.isentropicEnthalpy(pIn, hIn, pOut);
        return section(kind, flow, pIn, hIn, pOut, hOut, stageCount);
    }

    /**
     * Оценка отсека по действительному состоянию за ним.
     *
     * <p>Располагаемый перепад отсека — изоэнтропный
     * {@code H = h_вх − h(p_вых, s_вх)}; {@code hOut} задаёт только объём и сухость на выходе.
     * Поэтому результат сравним с {@code TurbineResult.SectionResult.efficiency()}.
     *
     * @param kind       тип ступеней отсека
     * @param flow       расход, кг/с
     * @param pIn        давление на входе, МПа
     * @param hIn        энтальпия на входе, кДж/кг
     * @param pOut       давление на выходе, МПа
     * @param hOut       энтальпия на выходе, кДж/кг
     * @param stageCount число ступеней
     * @return оценка отсека
     */
    public SectionEstimate section(StageKind kind, double flow, double pIn, double hIn,
                                   double pOut, double hOut, int stageCount) {
        requirePositive(flow, "расход отсека");
        requireExpansion(pIn, pOut);
        SectionState state = new SectionState(flow, hs.isentropicDrop(pIn, hIn, pOut),
                stageCount, pIn, hs.volumePH(pIn, hIn), pOut, hs.volumePH(pOut, hOut),
                hs.vapourFractionPH(pIn, hIn), hs.vapourFractionPH(pOut, hOut));
        return section(kind, state);
    }

    /**
     * Оценка турбины по отсекам.
     *
     * <p>КПД — отношение внутренней мощности к располагаемой:
     * {@code η = ΣG_i·η_i·H_i / ΣG_i·H_i}. Расход через первый отсек считается расходом на входе
     * турбины; отсеки должны идти в порядке расширения. Отборы пара между отсеками учитываются
     * тем, что у каждого отсека свой расход.
     *
     * @param sections отсеки в порядке расширения
     * @return оценка турбины
     */
    public TurbineEstimate turbine(List<SectionEstimate> sections) {
        if (sections == null || sections.isEmpty()) {
            throw new IllegalArgumentException("для оценки турбины нужен хотя бы один отсек");
        }
        double availablePower = 0.0;
        double internalPower = 0.0;
        for (SectionEstimate section : sections) {
            availablePower += section.availablePower();
            internalPower += section.internalPower();
        }
        if (!(availablePower > 0.0)) {
            throw new IllegalArgumentException("располагаемая мощность турбины должна быть "
                    + "положительной: " + availablePower + " кВт");
        }
        double efficiency = clamp(internalPower / availablePower);
        return new TurbineEstimate(efficiency, availablePower, internalPower,
                sections.get(0).flow(), sections);
    }

    /**
     * Смешивает КПД регулирующей и нерегулируемых ступеней по числу ступеней.
     *
     * <p>Предполагается, что теплоперепад на ступень одинаков; тогда средний КПД — это
     * {@code (η_рс·z_рс + η_н·z_н)/(z_рс + z_н)}. Для разных теплоперепадов используйте
     * {@link #turbine(List)}: там весовые коэффициенты — {@code G·H}.
     *
     * @param regulatingEfficiency КПД регулирующей ступени
     * @param regulatingStages     число регулирующих ступеней
     * @param stageEfficiency      КПД нерегулируемой ступени
     * @param stageCount           число нерегулируемых ступеней
     * @return средний КПД
     */
    public static double mix(double regulatingEfficiency, int regulatingStages,
                             double stageEfficiency, int stageCount) {
        requireFraction(regulatingEfficiency, "КПД регулирующей ступени");
        requireFraction(stageEfficiency, "КПД нерегулируемой ступени");
        if (regulatingStages < 0) {
            throw new IllegalArgumentException("число регулирующих ступеней не может быть "
                    + "отрицательным: " + regulatingStages);
        }
        if (stageCount < 0) {
            throw new IllegalArgumentException("число нерегулируемых ступеней не может быть "
                    + "отрицательным: " + stageCount);
        }
        int total = regulatingStages + stageCount;
        if (total < 1) {
            throw new IllegalArgumentException("для смешивания нужна хотя бы одна ступень");
        }
        return (regulatingEfficiency * regulatingStages
                + stageEfficiency * stageCount) / total;
    }

    // --- Служебное ---

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен быть положительным: " + value);
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!(value >= 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " не может быть отрицательным: " + value);
        }
    }

    private static void requireFraction(double value, String name) {
        if (!(value > 0.0 && value <= 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен лежать в (0, 1]: " + value);
        }
    }

    private static void requireClosedFraction(double value, String name) {
        if (!(value >= 0.0 && value <= 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен лежать в [0, 1]: " + value);
        }
    }

    private static void requireQuality(double quality) {
        requireFraction(quality, "степень сухости");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " не является конечным числом: " + value);
        }
    }

    private static void requireExpansion(double pIn, double pOut) {
        if (!(pIn > pOut) || !(pOut > 0.0) || !Double.isFinite(pIn) || !Double.isFinite(pOut)) {
            throw new IllegalArgumentException("давление за ступенью " + pOut
                    + " МПа должно быть положительным и ниже давления перед ней " + pIn + " МПа");
        }
    }
}
