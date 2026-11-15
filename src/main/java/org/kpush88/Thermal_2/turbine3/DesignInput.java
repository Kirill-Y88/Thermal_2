package org.kpush88.Thermal_2.turbine3;

/**
 * Исходные данные проектировочного расчёта турбины.
 *
 * <p>Давление — МПа, температура — °C, мощность — МВт, частота — об/мин, расход — кг/с,
 * теплоперепад — кДж/кг. Для давлений в барах есть {@link #ofBar}.
 */
public record DesignInput(
        double nominalPowerMW,
        double inletPressureMPa,
        double inletTemperatureC,
        double exhaustPressureMPa,
        double rotationRpm,
        RegulatingKind regulatingKind,
        SectionHeatDrops sectionHeatDrops) {

    /** Перевод бара в МПа. */
    private static final double BAR = 0.1;

    public DesignInput {
        requirePositive(nominalPowerMW, "номинальная мощность N_э^н");
        requirePositive(inletPressureMPa, "давление свежего пара P₀");
        requirePositive(inletTemperatureC, "температура свежего пара t₀");
        requirePositive(exhaustPressureMPa, "давление в конденсаторе P_к");
        requirePositive(rotationRpm, "частота вращения n");
        if (!(exhaustPressureMPa < inletPressureMPa)) {
            throw new IllegalArgumentException("давление конденсатора должно быть ниже давления свежего пара");
        }
        if (regulatingKind == null) {
            throw new IllegalArgumentException("тип регулирующей ступени не задан");
        }
        if (sectionHeatDrops == null) {
            throw new IllegalArgumentException("перепады отсеков не заданы");
        }
    }

    /** Исходные данные из документа, где давления записаны в барах. */
    public static DesignInput ofBar(double nominalPowerMW, double inletPressureBar,
                                    double inletTemperatureC, double exhaustPressureBar,
                                    double rotationRpm, RegulatingKind regulatingKind,
                                    SectionHeatDrops sectionHeatDrops) {
        return new DesignInput(nominalPowerMW, inletPressureBar * BAR, inletTemperatureC,
                exhaustPressureBar * BAR, rotationRpm, regulatingKind, sectionHeatDrops);
    }

    /** Располагаемые теплоперепады отсеков, кДж/кг. */
    public record SectionHeatDrops(double chvdKJkg, double chsdKJkg, double chndKJkg) {

        public SectionHeatDrops {
            requirePositive(chvdKJkg, "теплоперепад ЧВД");
            requirePositive(chsdKJkg, "теплоперепад ЧСД");
            requirePositive(chndKJkg, "теплоперепад ЧНД");
        }
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен быть положительным: " + value);
        }
    }
}
