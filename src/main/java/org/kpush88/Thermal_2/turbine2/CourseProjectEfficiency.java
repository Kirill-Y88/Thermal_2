package org.kpush88.Thermal_2.turbine2;

import java.util.List;
import java.util.Locale;

/**
 * КПД отсеков и регулирующей ступени по формулам курсового проекта
 * ({@code university/Курс турб.docx}, раздел 2.3).
 *
 * <p><b>Что это за формулы.</b> Методичка оценивает внутренний относительный КПД
 * {@code η_oi} цилиндров через расход, средний удельный объём, теплоперепад отсека и влажность.
 * В отличие от {@link PreliminaryEfficiency}, это не модель лопаточной решётки, а эмпирические
 * формулы для предварительного построения процесса расширения. Они включают потери, которые
 * компактная модель регулирующих ступеней не учитывает: например, для двухвенечной ступени при
 * {@code h₀ = 175 кДж/кг} методичка даёт {@code η_oi ≈ 0.658}.
 *
 * <pre>
 *     G′ = N_эк / (H₀′·η_оэ),                 η_оэ = 0.81
 *     V_ср = √(V_вх·V_вых)
 *     k_вл = 1 − 0.4·(y₁ + y₂)·h₀^вл / h₀^отсека
 *     ξ_вс = Δh_вс / h₀^отсека,               Δh_вс = 16 кДж/кг
 *     η_oi^ЧВД = (0.925 − 0.5/(G′·V_ср))·(1 − (h₀^ЧВД − 600)/20000)
 *     η_oi^ЧСД = та же формула · k_вл
 *     η_oi^ЧНД = 0.87·(1 − (h₀^ЧНД − 400)/10000)·k_вл − ξ_вс
 *     η_oi^2рс = 0.8 − (2·10⁻⁴/G′)·√(P₀′·10⁶ / V₀′)
 * </pre>
 *
 * <p><b>Единицы.</b> Давление — МПа (в формуле регулирующей ступени оно переводится в Па
 * множителем {@code 10⁶}), теплоперепад — кДж/кг, расход — кг/с, удельный объём — м³/кг,
 * мощность — кВт. Влажность {@code y = 1 − x} (0 — сухой пар), а не степень сухости.
 *
 * <p><b>Что такое {@code H₀′}.</b> Это располагаемый перепад всей турбины от состояния перед
 * стопорными клапанами до конденсатора. Его <b>нельзя</b> получить сложением перепадов отсеков:
 * каждый следующий отсек начинается после потерь предыдущего, поэтому сумма перепадов отсеков
 * больше {@code H₀′}. В примере методички регулирующая ступень и отсеки дают
 * {@code 175 + 339.19 + 375.21 + 347.35 = 1236.75} кДж/кг, а {@code H₀′ = 1161.6} кДж/кг;
 * при этом {@code h_i = 956.67} кДж/кг и
 * {@code η_oi = 956.67/1161.6 = 0.824}. Поэтому {@link #turbine(double, double,
 * RegulatingEstimate, List)} принимает {@code H₀′} отдельным аргументом.
 *
 * <p><b>Опечатки в источнике.</b> В документе при подстановке в формулу регулирующей ступени
 * стоит {@code G′ = 6.08}, хотя выше получено {@code G′ = 6.8}; при {@code 6.08} результат был
 * бы 0.641, а не 0.658. В формуле ЧСД подставлено {@code k_вл = 0.98894}, хотя по приведённым
 * {@code y₁ = 0}, {@code y₂ = 0.03261}, {@code h₀^вл = 101.91} и {@code h₀^ЧСД = 375.21}
 * получается {@code 0.99646}; результат 0.8776 соответствует именно {@code 0.99646}. Класс
 * считает по формулам, а не по напечатанным числам.
 *
 * <p><b>Область применимости.</b> Формула ЧВД/ЧСД содержит {@code 0.5/(G′·V_ср)}. При высоком
 * давлении и малом объёме (например, ЦВД опорной машины 14 МПа с {@code V_ср ≈ 0.03 м³/кг})
 * это слагаемое превышает 0.925, и формула даёт отрицательный КПД. Класс не подставляет
 * «правдоподобное» число и не обрезает результат: он выбрасывает {@link IllegalArgumentException}
 * с указанием слагаемого и входных данных. Так же поступает любая формула, вышедшая из
 * {@code (0, 1)}.
 *
 * <p>Класс не хранит состояния: все методы статические.
 */
public final class CourseProjectEfficiency {

    /** Относительный электрический КПД по умолчанию (табл. 1 методички). */
    public static final double DEFAULT_ELECTRICAL_EFFICIENCY = 0.81;

    /** Потеря с выходной скоростью для турбин малой мощности, кДж/кг. */
    public static final double DEFAULT_EXIT_HEAT_DROP = 16.0;

    private static final double HIGH_PRESSURE_BASE = 0.925;
    private static final double HIGH_PRESSURE_FLOW_TERM = 0.5;
    private static final double HIGH_PRESSURE_REFERENCE_DROP = 600.0;
    private static final double HIGH_PRESSURE_DROP_SCALE = 20000.0;
    private static final double LOW_PRESSURE_BASE = 0.87;
    private static final double LOW_PRESSURE_REFERENCE_DROP = 400.0;
    private static final double LOW_PRESSURE_DROP_SCALE = 10000.0;
    private static final double REGULATING_BASE = 0.8;
    private static final double SINGLE_ROW_BASE = 0.85;
    private static final double REGULATING_COEFFICIENT = 2.0e-4;
    private static final double MPA = 1.0e6;

    private CourseProjectEfficiency() {
    }

    /** Отсек турбины по методичке. */
    public enum Section {
        CHVD("ЧВД"),
        CHSD("ЧСД"),
        CHND("ЧНД");

        private final String label;

        Section(String label) {
            this.label = label;
        }

        /** Название для отчёта. */
        public String label() {
            return label;
        }
    }

    /** Тип регулирующей ступени. */
    public enum RegulatingKind {
        SINGLE_ROW("одновенечная"),
        DOUBLE_ROW("двухвенечная");

        private final String label;

        RegulatingKind(String label) {
            this.label = label;
        }

        /** Название для отчёта. */
        public String label() {
            return label;
        }
    }

    /**
     * Оценка отсека по методичке.
     *
     * @param section       отсек
     * @param efficiency    {@code η_oi} отсека, 0…1
     * @param heatDrop      располагаемый теплоперепад отсека, кДж/кг
     * @param internalWork  полезно использованный перепад {@code η_oi·h₀}, кДж/кг
     * @param flow          расход {@code G′}, кг/с
     * @param meanVolume    средний удельный объём {@code V_ср}, м³/кг
     * @param wetnessFactor {@code k_вл} (для ЧВД — 1)
     * @param exitLoss      {@code ξ_вс} (для ЧВД и ЧСД — 0)
     */
    public record SectionEstimate(
            Section section,
            double efficiency,
            double heatDrop,
            double internalWork,
            double flow,
            double meanVolume,
            double wetnessFactor,
            double exitLoss) {

        public SectionEstimate {
            if (section == null) {
                throw new IllegalArgumentException("отсек не задан");
            }
            requireEfficiency(efficiency, "КПД отсека " + section.label());
            requirePositive(heatDrop, "теплоперепад отсека");
            if (!(internalWork > 0.0 && internalWork < heatDrop)) {
                throw new IllegalArgumentException("полезно использованный перепад " + internalWork
                        + " кДж/кг вне диапазона (0, " + heatDrop + ")");
            }
            requirePositive(flow, "расход отсека");
            requirePositive(meanVolume, "средний удельный объём отсека");
            requireWetnessFactor(wetnessFactor);
            requireExitLoss(exitLoss);
            if (section == Section.CHVD) {
                if (Math.abs(wetnessFactor - 1.0) > 1e-12) {
                    throw new IllegalArgumentException("для ЧВД влажностная поправка не "
                            + "применяется: k_вл = " + wetnessFactor);
                }
                if (Math.abs(exitLoss) > 1e-12) {
                    throw new IllegalArgumentException("для ЧВД потеря с выходной скоростью "
                            + "не входит в формулу: ξ_вс = " + exitLoss);
                }
            }
            if (section == Section.CHSD && Math.abs(exitLoss) > 1e-12) {
                throw new IllegalArgumentException("для ЧСД потеря с выходной скоростью "
                        + "не входит в формулу: ξ_вс = " + exitLoss);
            }
        }

        /** Внутренняя мощность отсека, кВт. */
        public double power() {
            return flow * internalWork;
        }

        /** Строка для отчёта. */
        public String describe() {
            return String.format(Locale.ROOT,
                    "%s: η_oi = %.4f, h₀ = %.2f кДж/кг, h_i = %.2f кДж/кг, "
                            + "G′ = %.3f кг/с, V_ср = %.4f м³/кг, k_вл = %.5f, ξ_вс = %.5f",
                    section.label(), efficiency, heatDrop, internalWork, flow,
                    meanVolume, wetnessFactor, exitLoss);
        }
    }

    /**
     * Оценка регулирующей ступени.
     *
     * @param kind        тип ступени
     * @param efficiency  {@code η_oi}, 0…1
     * @param heatDrop    располагаемый теплоперепад, кДж/кг
     * @param internalWork полезно использованный перепад, кДж/кг
     * @param flow        расход {@code G′}, кг/с
     * @param pressureMPa давление {@code P₀′}, МПа
     * @param volume      удельный объём {@code V₀′}, м³/кг
     */
    public record RegulatingEstimate(
            RegulatingKind kind,
            double efficiency,
            double heatDrop,
            double internalWork,
            double flow,
            double pressureMPa,
            double volume) {

        public RegulatingEstimate {
            if (kind == null) {
                throw new IllegalArgumentException("тип регулирующей ступени не задан");
            }
            requireEfficiency(efficiency, "КПД регулирующей ступени");
            requirePositive(heatDrop, "теплоперепад регулирующей ступени");
            if (!(internalWork > 0.0 && internalWork < heatDrop)) {
                throw new IllegalArgumentException("полезно использованный перепад " + internalWork
                        + " кДж/кг вне диапазона (0, " + heatDrop + ")");
            }
            requirePositive(flow, "расход через регулирующую ступень");
            requirePositive(pressureMPa, "давление перед регулирующей ступенью");
            requirePositive(volume, "удельный объём перед регулирующей ступенью");
        }

        /** Внутренняя мощность ступени, кВт. */
        public double power() {
            return flow * internalWork;
        }

        /** Строка для отчёта. */
        public String describe() {
            return String.format(Locale.ROOT,
                    "регулирующая ступень (%s): η_oi = %.4f, h₀ = %.2f кДж/кг, "
                            + "h_i = %.2f кДж/кг, G′ = %.3f кг/с, P₀′ = %.3f МПа, "
                            + "V₀′ = %.5f м³/кг",
                    kind.label(), efficiency, heatDrop, internalWork, flow,
                    pressureMPa, volume);
        }
    }

    /**
     * Оценка турбины по методичке.
     *
     * <p>{@code h_i} — сумма полезно использованных перепадов регулирующей ступени и отсеков.
     * {@code H₀′} задаётся отдельно: это изоэнтропный перепад всей турбины, и он не равен сумме
     * перепадов отсеков (см. описание класса).
     *
     * @param efficiency    {@code η_oi = h_i/H₀′}, 0…1
     * @param internalWork  {@code h_i}, кДж/кг
     * @param availableDrop {@code H₀′}, кДж/кг
     * @param flow          расход {@code G′}, кг/с
     * @param internalPower внутренняя мощность {@code G′·h_i}, кВт
     * @param sections      отсеки в порядке расширения
     * @param regulating    регулирующая ступень
     */
    public record TurbineEstimate(
            double efficiency,
            double internalWork,
            double availableDrop,
            double flow,
            double internalPower,
            List<SectionEstimate> sections,
            RegulatingEstimate regulating) {

        public TurbineEstimate {
            requireEfficiency(efficiency, "КПД турбины");
            requirePositive(internalWork, "внутренняя работа турбины");
            requirePositive(availableDrop, "располагаемый теплоперепад турбины");
            if (!(internalWork < availableDrop)) {
                throw new IllegalArgumentException("внутренняя работа " + internalWork
                        + " кДж/кг должна быть меньше располагаемого перепада "
                        + availableDrop + " кДж/кг");
            }
            requirePositive(flow, "расход через турбину");
            requirePositive(internalPower, "внутренняя мощность турбины");
            if (sections == null || sections.isEmpty()) {
                throw new IllegalArgumentException("у турбины должен быть хотя бы один отсек");
            }
            sections = List.copyOf(sections);
            if (regulating == null) {
                throw new IllegalArgumentException("регулирующая ступень не задана");
            }
        }

        /** Строка для отчёта: регулирующая ступень, отсеки и итог. */
        public String describe() {
            StringBuilder text = new StringBuilder();
            text.append(regulating.describe()).append(System.lineSeparator());
            for (SectionEstimate section : sections) {
                text.append(section.describe()).append(System.lineSeparator());
            }
            text.append(String.format(Locale.ROOT,
                    "турбина: η_oi = %.4f, h_i = %.2f кДж/кг, H₀′ = %.2f кДж/кг, "
                            + "G′ = %.3f кг/с, N_i = %.0f кВт",
                    efficiency, internalWork, availableDrop, flow, internalPower));
            return text.toString();
        }
    }

    /**
     * Предварительный расход пара:
     * {@code G′ = N_эк / (H₀′·η_оэ)}.
     *
     * @param power                экономическая мощность, кВт
     * @param heatDrop             располагаемый теплоперепад {@code H₀′}, кДж/кг
     * @param electricalEfficiency относительный электрический КПД {@code η_оэ}
     * @return расход, кг/с
     */
    public static double flow(double power, double heatDrop, double electricalEfficiency) {
        requirePositive(power, "экономическая мощность");
        requirePositive(heatDrop, "располагаемый теплоперепад");
        requireEfficiency(electricalEfficiency, "относительный электрический КПД");
        return power / (heatDrop * electricalEfficiency);
    }

    /**
     * Средний удельный объём участка: {@code V_ср = √(V_вх·V_вых)}.
     *
     * @param inletVolume  удельный объём на входе, м³/кг
     * @param outletVolume удельный объём на выходе, м³/кг
     * @return средний объём, м³/кг
     */
    public static double meanVolume(double inletVolume, double outletVolume) {
        requirePositive(inletVolume, "удельный объём на входе");
        requirePositive(outletVolume, "удельный объём на выходе");
        return Math.sqrt(inletVolume * outletVolume);
    }

    /**
     * Влажностная поправка:
     * {@code k_вл = 1 − 0.4·(y₁ + y₂)·h₀^вл/h₀^отсека}.
     *
     * <p>{@code y} — степень влажности {@code 1 − x}, а не сухость. Формула учитывает только
     * часть перепада, лежащую во влажном паре; поэтому {@code wetHeatDrop} может быть меньше
     * {@code sectionHeatDrop}, но не больше него.
     *
     * @param y1              влажность в начале участка, 0…1
     * @param y2              влажность в конце участка, 0…1
     * @param wetHeatDrop     располагаемый перепад во влажной зоне, кДж/кг
     * @param sectionHeatDrop располагаемый перепад отсека, кДж/кг
     * @return {@code k_вл} в {@code (0, 1]}
     */
    public static double wetnessFactor(double y1, double y2, double wetHeatDrop,
                                       double sectionHeatDrop) {
        requireWetness(y1, "начальная влажность y₁");
        requireWetness(y2, "конечная влажность y₂");
        requireNonNegative(wetHeatDrop, "теплоперепад влажной зоны");
        requirePositive(sectionHeatDrop, "теплоперепад отсека");
        if (wetHeatDrop > sectionHeatDrop) {
            throw new IllegalArgumentException("теплоперепад влажной зоны " + wetHeatDrop
                    + " кДж/кг не может превышать теплоперепад отсека "
                    + sectionHeatDrop + " кДж/кг");
        }
        double factor = 1.0 - 0.4 * (y1 + y2) * wetHeatDrop / sectionHeatDrop;
        requireWetnessFactor(factor);
        return factor;
    }

    /**
     * Относительная потеря с выходной скоростью:
     * {@code ξ_вс = Δh_вс/h₀^отсека}.
     *
     * @param exitHeatDrop    потеря с выходной скоростью {@code Δh_вс}, кДж/кг
     * @param sectionHeatDrop теплоперепад отсека, кДж/кг
     * @return {@code ξ_вс} в {@code [0, 1)}
     */
    public static double exitVelocityLoss(double exitHeatDrop, double sectionHeatDrop) {
        requireNonNegative(exitHeatDrop, "потеря с выходной скоростью");
        requirePositive(sectionHeatDrop, "теплоперепад отсека");
        double loss = exitHeatDrop / sectionHeatDrop;
        requireExitLoss(loss);
        return loss;
    }

    /**
     * КПД отсека по методичке.
     *
     * <p>Для ЧВД влажностная поправка не применяется ({@code k_вл = 1}), для ЧВД и ЧСД потеря с
     * выходной скоростью не входит в формулу ({@code ξ_вс = 0}). Если передать другие значения,
     * будет выброшено исключение: молча игнорировать аргумент нельзя — результат перестал бы
     * соответствовать выбранной формуле.
     *
     * @param section       отсек
     * @param flow          расход {@code G′}, кг/с
     * @param heatDrop      располагаемый перепад отсека {@code h₀}, кДж/кг
     * @param meanVolume    средний удельный объём {@code V_ср}, м³/кг
     * @param wetnessFactor {@code k_вл} (для ЧВД — 1)
     * @param exitLoss      {@code ξ_вс} (для ЧВД и ЧСД — 0)
     * @return оценка отсека
     */
    public static SectionEstimate section(Section section, double flow, double heatDrop,
                                          double meanVolume, double wetnessFactor,
                                          double exitLoss) {
        if (section == null) {
            throw new IllegalArgumentException("отсек не задан");
        }
        requirePositive(flow, "расход отсека");
        requirePositive(heatDrop, "теплоперепад отсека");
        requirePositive(meanVolume, "средний удельный объём");
        requireWetnessFactor(wetnessFactor);
        requireExitLoss(exitLoss);
        if (section == Section.CHVD && Math.abs(wetnessFactor - 1.0) > 1e-12) {
            throw new IllegalArgumentException("для ЧВД влажностная поправка не применяется: "
                    + "k_вл = " + wetnessFactor);
        }
        if (section != Section.CHND && Math.abs(exitLoss) > 1e-12) {
            throw new IllegalArgumentException("для " + section.label() + " потеря с выходной "
                    + "скоростью не входит в формулу: ξ_вс = " + exitLoss);
        }
        double efficiency = switch (section) {
            case CHVD -> highPressureEfficiency(flow, heatDrop, meanVolume);
            case CHSD -> highPressureEfficiency(flow, heatDrop, meanVolume) * wetnessFactor;
            case CHND -> lowPressureEfficiency(heatDrop) * wetnessFactor - exitLoss;
        };
        requireEfficiency(efficiency, "КПД отсека " + section.label());
        return new SectionEstimate(section, efficiency, heatDrop, efficiency * heatDrop,
                flow, meanVolume, wetnessFactor, exitLoss);
    }

    /**
     * КПД отсека по входному и выходному объёмам; {@code V_ср} считается геометрическим средним.
     *
     * @param section       отсек
     * @param flow          расход {@code G′}, кг/с
     * @param heatDrop      располагаемый перепад отсека, кДж/кг
     * @param inletVolume   удельный объём на входе, м³/кг
     * @param outletVolume  удельный объём на выходе, м³/кг
     * @param wetnessFactor {@code k_вл} (для ЧВД — 1)
     * @param exitLoss      {@code ξ_вс} (для ЧВД и ЧСД — 0)
     * @return оценка отсека
     */
    public static SectionEstimate section(Section section, double flow, double heatDrop,
                                          double inletVolume, double outletVolume,
                                          double wetnessFactor, double exitLoss) {
        return section(section, flow, heatDrop, meanVolume(inletVolume, outletVolume),
                wetnessFactor, exitLoss);
    }

    /**
     * КПД отсека без влажностной поправки и без потери с выходной скоростью: удобно для ЧВД.
     *
     * @param section    отсек
     * @param flow       расход {@code G′}, кг/с
     * @param heatDrop   располагаемый перепад отсека, кДж/кг
     * @param meanVolume средний удельный объём {@code V_ср}, м³/кг
     * @return оценка отсека
     */
    public static SectionEstimate section(Section section, double flow, double heatDrop,
                                          double meanVolume) {
        return section(section, flow, heatDrop, meanVolume, 1.0, 0.0);
    }

    /**
     * КПД двухвенечной регулирующей ступени:
     * {@code η_oi = 0.8 − (2·10⁻⁴/G′)·√(P₀′·10⁶/V₀′)}.
     *
     * @param flow        расход {@code G′}, кг/с
     * @param pressureMPa давление {@code P₀′}, МПа
     * @param volume      удельный объём {@code V₀′}, м³/кг
     * @param heatDrop    располагаемый теплоперепад ступени, кДж/кг
     * @return оценка регулирующей ступени
     */
    public static RegulatingEstimate regulatingDoubleRow(double flow, double pressureMPa,
                                                         double volume, double heatDrop) {
        return regulating(RegulatingKind.DOUBLE_ROW, flow, pressureMPa, volume, heatDrop);
    }

    /**
     * КПД одновенечной регулирующей ступени.
     *
     * <p><b>Экстраполяция.</b> В методичке приведена только формула двухвенечной ступени
     * ({@code 0.8 − ...}). Для одновенечной принята та же зависимость от
     * {@code √(P₀′/V₀′)}, но с базой 0.85: одновенечная ступень имеет более высокий КПД и
     * перерабатывает меньший теплоперепад. Это не формула из документа, а оценка; при
     * {@code G′ = 6.8}, {@code P₀′ = 2.716 МПа}, {@code V₀′ = 0.1157 м³/кг} она даёт 0.7075.
     * Если нужна только документированная формула, используйте
     * {@link #regulatingDoubleRow(double, double, double, double)}.
     *
     * @param flow        расход {@code G′}, кг/с
     * @param pressureMPa давление {@code P₀′}, МПа
     * @param volume      удельный объём {@code V₀′}, м³/кг
     * @param heatDrop    располагаемый теплоперепад ступени, кДж/кг
     * @return оценка регулирующей ступени
     */
    public static RegulatingEstimate regulatingSingleRow(double flow, double pressureMPa,
                                                         double volume, double heatDrop) {
        return regulating(RegulatingKind.SINGLE_ROW, flow, pressureMPa, volume, heatDrop);
    }

    private static RegulatingEstimate regulating(RegulatingKind kind, double flow,
                                                 double pressureMPa, double volume,
                                                 double heatDrop) {
        if (kind == null) {
            throw new IllegalArgumentException("тип регулирующей ступени не задан");
        }
        requirePositive(flow, "расход через регулирующую ступень");
        requirePositive(pressureMPa, "давление перед регулирующей ступенью");
        requirePositive(volume, "удельный объём перед регулирующей ступенью");
        requirePositive(heatDrop, "теплоперепад регулирующей ступени");
        double base = kind == RegulatingKind.DOUBLE_ROW ? REGULATING_BASE : SINGLE_ROW_BASE;
        double efficiency = base - REGULATING_COEFFICIENT / flow
                * Math.sqrt(pressureMPa * MPA / volume);
        requireEfficiency(efficiency, "КПД регулирующей ступени " + kind.label());
        return new RegulatingEstimate(kind, efficiency, heatDrop, efficiency * heatDrop,
                flow, pressureMPa, volume);
    }

    /**
     * КПД турбины по внутренней работе и располагаемому перепаду:
     * {@code η_oi = h_i/H₀′}.
     *
     * @param internalWork  {@code h_i}, кДж/кг
     * @param availableDrop {@code H₀′}, кДж/кг
     * @return {@code η_oi} в {@code (0, 1)}
     */
    public static double turbineEfficiency(double internalWork, double availableDrop) {
        requirePositive(internalWork, "внутренняя работа турбины");
        requirePositive(availableDrop, "располагаемый теплоперепад турбины");
        double efficiency = internalWork / availableDrop;
        requireEfficiency(efficiency, "КПД турбины");
        return efficiency;
    }

    /**
     * Собирает турбину из регулирующей ступени и отсеков.
     *
     * <p>Расход через все элементы должен быть одинаков: методичка оценивает его без учёта
     * регенеративных и регулируемых отборов. Если расходы различаются, это не та модель, и
     * метод выбрасывает исключение, а не смешивает разные режимы.
     *
     * @param flow          расход {@code G′}, кг/с
     * @param availableDrop {@code H₀′}, кДж/кг
     * @param regulating    регулирующая ступень
     * @param sections      отсеки в порядке расширения
     * @return оценка турбины
     */
    public static TurbineEstimate turbine(double flow, double availableDrop,
                                          RegulatingEstimate regulating,
                                          List<SectionEstimate> sections) {
        requirePositive(flow, "расход через турбину");
        requirePositive(availableDrop, "располагаемый теплоперепад турбины");
        if (regulating == null) {
            throw new IllegalArgumentException("регулирующая ступень не задана");
        }
        if (sections == null || sections.isEmpty()) {
            throw new IllegalArgumentException("у турбины должен быть хотя бы один отсек");
        }
        requireSameFlow(flow, regulating.flow(), "регулирующая ступень");
        double internalWork = regulating.internalWork();
        for (SectionEstimate section : sections) {
            requireSameFlow(flow, section.flow(), "отсек " + section.section().label());
            internalWork += section.internalWork();
        }
        double efficiency = turbineEfficiency(internalWork, availableDrop);
        return new TurbineEstimate(efficiency, internalWork, availableDrop, flow,
                flow * internalWork, sections, regulating);
    }

    // --- Формулы методички ---

    /**
     * ЧВД/ЧСД: {@code (0.925 − 0.5/(G′·V_ср))·(1 − (h₀ − 600)/20000)}.
     *
     * <p>Проверка {@code 0.5/(G′·V_ср) < 0.925} стоит до вычисления: именно это слагаемое
     * делает формулу непригодной при высоком давлении.
     */
    private static double highPressureEfficiency(double flow, double heatDrop,
                                                 double meanVolume) {
        double volumeTerm = HIGH_PRESSURE_FLOW_TERM / (flow * meanVolume);
        if (!(volumeTerm < HIGH_PRESSURE_BASE) || !Double.isFinite(volumeTerm)) {
            throw new IllegalArgumentException("формула методички вне области применимости: "
                    + "0,5/(G′·V_ср) = " + volumeTerm + " ≥ " + HIGH_PRESSURE_BASE
                    + " (G′ = " + flow + " кг/с, V_ср = " + meanVolume + " м³/кг). "
                    + "Формула ЧВД/ЧСД рассчитана на умеренное давление; для ЦВД высокого "
                    + "давления используйте детальный расчёт ступеней");
        }
        return (HIGH_PRESSURE_BASE - volumeTerm)
                * (1.0 - (heatDrop - HIGH_PRESSURE_REFERENCE_DROP)
                / HIGH_PRESSURE_DROP_SCALE);
    }

    /** ЧНД: {@code 0.87·(1 − (h₀ − 400)/10000)}; влажность и выходная скорость — снаружи. */
    private static double lowPressureEfficiency(double heatDrop) {
        return LOW_PRESSURE_BASE
                * (1.0 - (heatDrop - LOW_PRESSURE_REFERENCE_DROP)
                / LOW_PRESSURE_DROP_SCALE);
    }

    // --- Служебное ---

    private static void requireSameFlow(double expected, double actual, String element) {
        double tolerance = Math.max(1e-9, Math.abs(expected) * 1e-9);
        if (Math.abs(expected - actual) > tolerance) {
            throw new IllegalArgumentException("в предварительном расчёте расход через все "
                    + "элементы одинаков: G′ = " + expected + " кг/с, а у элемента «"
                    + element + "» — " + actual + " кг/с");
        }
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

    private static void requireEfficiency(double value, String name) {
        if (!(value > 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " вне области (0, 1): " + value);
        }
    }

    private static void requireWetness(double value, String name) {
        if (!(value >= 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должна лежать в [0, 1): " + value);
        }
    }

    private static void requireWetnessFactor(double value) {
        if (!(value > 0.0 && value <= 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("влажностная поправка k_вл должна лежать в "
                    + "(0, 1]: " + value);
        }
    }

    private static void requireExitLoss(double value) {
        if (!(value >= 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("относительная потеря с выходной скоростью "
                    + "ξ_вс должна лежать в [0, 1): " + value);
        }
    }
}