package org.kpush88.Thermal_2.turbine3;

import java.util.Locale;

/**
 * КПД отсеков и регулирующей ступени по формулам раздела 2.3 методички. Набор статических формул
 * без состояния: расход, средний объём, влажностная поправка, потеря с выходной скоростью.
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

        public String label() {
            return label;
        }
    }

    /** Оценка отсека: КПД, перепад, полезно использованный перепад и средний объём. */
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
                throw new IllegalArgumentException("полезно использованный перепад вне (0, h₀): "
                        + internalWork);
            }
            requirePositive(flow, "расход отсека");
            requirePositive(meanVolume, "средний удельный объём отсека");
            requireWetnessFactor(wetnessFactor);
            requireExitLoss(exitLoss);
        }

        public String describe() {
            return String.format(Locale.ROOT,
                    "%s: η_oi = %.4f, h₀ = %.2f кДж/кг, h_i = %.2f кДж/кг, V_ср = %.4f м³/кг",
                    section.label(), efficiency, heatDrop, internalWork, meanVolume);
        }
    }

    /** Оценка регулирующей ступени по формуле методички. */
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
                throw new IllegalArgumentException("полезно использованный перепад вне (0, h₀): "
                        + internalWork);
            }
            requirePositive(flow, "расход через регулирующую ступень");
            requirePositive(pressureMPa, "давление перед регулирующей ступенью");
            requirePositive(volume, "удельный объём перед регулирующей ступенью");
        }

        public double power() {
            return flow * internalWork;
        }

        public String describe() {
            return String.format(Locale.ROOT,
                    "регулирующая ступень (%s): η_oi = %.4f, h_i = %.2f кДж/кг, N_i = %.1f кВт",
                    kind.label(), efficiency, internalWork, power());
        }
    }

    /** Предварительный расход: {@code G′ = N_эк/(H₀′·η_оэ)}. */
    public static double flow(double power, double heatDrop, double electricalEfficiency) {
        requirePositive(power, "экономическая мощность");
        requirePositive(heatDrop, "располагаемый теплоперепад");
        requireEfficiency(electricalEfficiency, "относительный электрический КПД");
        return power / (heatDrop * electricalEfficiency);
    }

    /** Средний удельный объём участка: {@code V_ср = √(V_вх·V_вых)}. */
    public static double meanVolume(double inletVolume, double outletVolume) {
        requirePositive(inletVolume, "удельный объём на входе");
        requirePositive(outletVolume, "удельный объём на выходе");
        return Math.sqrt(inletVolume * outletVolume);
    }

    /** Влажностная поправка: {@code k_вл = 1 − 0.4·(y₁+y₂)·h₀^вл/h₀}. */
    public static double wetnessFactor(double y1, double y2, double wetHeatDrop,
                                       double sectionHeatDrop) {
        requireWetness(y1, "начальная влажность y₁");
        requireWetness(y2, "конечная влажность y₂");
        requireNonNegative(wetHeatDrop, "теплоперепад влажной зоны");
        requirePositive(sectionHeatDrop, "теплоперепад отсека");
        if (wetHeatDrop > sectionHeatDrop) {
            throw new IllegalArgumentException("теплоперепад влажной зоны не может превышать "
                    + "теплоперепад отсека");
        }
        double factor = 1.0 - 0.4 * (y1 + y2) * wetHeatDrop / sectionHeatDrop;
        requireWetnessFactor(factor);
        return factor;
    }

    /** Относительная потеря с выходной скоростью: {@code ξ_вс = Δh_вс/h₀}. */
    public static double exitVelocityLoss(double exitHeatDrop, double sectionHeatDrop) {
        requireNonNegative(exitHeatDrop, "потеря с выходной скоростью");
        requirePositive(sectionHeatDrop, "теплоперепад отсека");
        double loss = exitHeatDrop / sectionHeatDrop;
        requireExitLoss(loss);
        return loss;
    }

    /** КПД отсека по среднему объёму, влажностной поправке и потере с выходной скоростью. */
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
        double efficiency = switch (section) {
            case CHVD -> highPressureEfficiency(flow, heatDrop, meanVolume);
            case CHSD -> highPressureEfficiency(flow, heatDrop, meanVolume) * wetnessFactor;
            case CHND -> lowPressureEfficiency(heatDrop) * wetnessFactor - exitLoss;
        };
        requireEfficiency(efficiency, "КПД отсека " + section.label());
        return new SectionEstimate(section, efficiency, heatDrop, efficiency * heatDrop,
                flow, meanVolume, wetnessFactor, exitLoss);
    }

    /** КПД отсека без влажностной поправки и потери с выходной скоростью (ЧВД). */
    public static SectionEstimate section(Section section, double flow, double heatDrop,
                                          double meanVolume) {
        return section(section, flow, heatDrop, meanVolume, 1.0, 0.0);
    }

    /** КПД двухвенечной регулирующей ступени. */
    public static RegulatingEstimate regulatingDoubleRow(double flow, double pressureMPa,
                                                         double volume, double heatDrop) {
        return regulating(RegulatingKind.TWO_ROW, flow, pressureMPa, volume, heatDrop);
    }

    /** КПД одновенечной регулирующей ступени (оценка, база 0.85). */
    public static RegulatingEstimate regulatingSingleRow(double flow, double pressureMPa,
                                                         double volume, double heatDrop) {
        return regulating(RegulatingKind.ONE_ROW, flow, pressureMPa, volume, heatDrop);
    }

    /** КПД регулирующей ступени: {@code base − (2·10⁻⁴/G′)·√(P₀′·10⁶/V₀′)}. */
    public static double regulatingEfficiency(RegulatingKind kind, double flow,
                                              double pressureMPa, double volume) {
        if (kind == null) {
            throw new IllegalArgumentException("тип регулирующей ступени не задан");
        }
        requirePositive(flow, "расход через регулирующую ступень");
        requirePositive(pressureMPa, "давление перед регулирующей ступенью");
        requirePositive(volume, "удельный объём перед регулирующей ступенью");
        double base = kind == RegulatingKind.TWO_ROW ? REGULATING_BASE : SINGLE_ROW_BASE;
        double efficiency = base - REGULATING_COEFFICIENT / flow
                * Math.sqrt(pressureMPa * MPA / volume);
        requireEfficiency(efficiency, "КПД регулирующей ступени");
        return efficiency;
    }

    private static RegulatingEstimate regulating(RegulatingKind kind, double flow,
                                                 double pressureMPa, double volume,
                                                 double heatDrop) {
        requirePositive(heatDrop, "теплоперепад регулирующей ступени");
        double efficiency = regulatingEfficiency(kind, flow, pressureMPa, volume);
        return new RegulatingEstimate(kind, efficiency, heatDrop, efficiency * heatDrop,
                flow, pressureMPa, volume);
    }

    /** КПД турбины по внутренней работе и располагаемому перепаду. */
    public static double turbineEfficiency(double internalWork, double availableDrop) {
        requirePositive(internalWork, "внутренняя работа турбины");
        requirePositive(availableDrop, "располагаемый теплоперепад турбины");
        double efficiency = internalWork / availableDrop;
        requireEfficiency(efficiency, "КПД турбины");
        return efficiency;
    }

    /** ЧВД/ЧСД: {@code (0.925 − 0.5/(G′·V_ср))·(1 − (h₀ − 600)/20000)}. */
    private static double highPressureEfficiency(double flow, double heatDrop,
                                                 double meanVolume) {
        double volumeTerm = HIGH_PRESSURE_FLOW_TERM / (flow * meanVolume);
        if (!(volumeTerm < HIGH_PRESSURE_BASE) || !Double.isFinite(volumeTerm)) {
            throw new IllegalArgumentException("формула ЧВД/ЧСД вне области применимости: "
                    + "0.5/(G′·V_ср) = " + volumeTerm);
        }
        return (HIGH_PRESSURE_BASE - volumeTerm)
                * (1.0 - (heatDrop - HIGH_PRESSURE_REFERENCE_DROP)
                / HIGH_PRESSURE_DROP_SCALE);
    }

    /** ЧНД: {@code 0.87·(1 − (h₀ − 400)/10000)}. */
    private static double lowPressureEfficiency(double heatDrop) {
        return LOW_PRESSURE_BASE
                * (1.0 - (heatDrop - LOW_PRESSURE_REFERENCE_DROP)
                / LOW_PRESSURE_DROP_SCALE);
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
            throw new IllegalArgumentException("влажностная поправка должна лежать в (0, 1]: "
                    + value);
        }
    }

    private static void requireExitLoss(double value) {
        if (!(value >= 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("относительная потеря с выходной скоростью "
                    + "должна лежать в [0, 1): " + value);
        }
    }
}
