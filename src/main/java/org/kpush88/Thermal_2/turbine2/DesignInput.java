package org.kpush88.Thermal_2.turbine2;

/**
 * Исходные данные проектировочного расчёта турбины.
 *
 * <p>Запись отделяет <b>требования проекта</b> от эмпирических коэффициентов
 * ({@link DesignAssumptions}). Здесь только то, что задаёт вариант: мощность, параметры свежего
 * пара и конденсатора, частота вращения, тип регулирующей ступени и располагаемые теплоперепады
 * отсеков.
 *
 * <p><b>Почему перепады отсеков — исходные данные.</b> В методичке
 * ({@code university/Курс турб.docx}, раздел 2.3) величины {@code h₀^ЧВД = 339.19},
 * {@code h₀^ЧСД = 375.21}, {@code h₀^ЧНД = 347.35} кДж/кг читаются с предварительного процесса
 * расширения и зависят от прототипа. В разделах 2.1–2.2 они не вычисляются, поэтому класс не
 * может получить их «сам»: если не задать явно, он начнёт угадывать. Позже их будет давать
 * модель прототипа, но и тогда они останутся входом расчёта.
 *
 * <p>Давления — МПа, температура — °C, мощность — МВт, расход — кг/с, теплоперепад — кДж/кг.
 * Методичка местами пишет давления в барах; для таких данных есть фабрика
 * {@link #ofBar(double, double, double, double, double, CourseProjectEfficiency.RegulatingKind,
 * SectionHeatDrops)}, чтобы не ошибиться с переводом.
 *
 * @param nominalPowerMW       номинальная электрическая мощность {@code N_э^н}, МВт
 * @param inletPressureMPa     давление свежего пара перед стопорными клапанами {@code P₀}, МПа
 * @param inletTemperatureC    температура свежего пара {@code t₀}, °C
 * @param exhaustPressureMPa   давление в конденсаторе {@code P_к}, МПа
 * @param rotationRpm          частота вращения {@code n}, об/мин
 * @param regulatingKind       тип регулирующей ступени
 * @param sectionHeatDrops     располагаемые перепады отсеков, кДж/кг
 */
public record DesignInput(
        double nominalPowerMW,
        double inletPressureMPa,
        double inletTemperatureC,
        double exhaustPressureMPa,
        double rotationRpm,
        CourseProjectEfficiency.RegulatingKind regulatingKind,
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
            throw new IllegalArgumentException("давление в конденсаторе P_к = "
                    + exhaustPressureMPa + " МПа должно быть ниже давления свежего пара P₀ = "
                    + inletPressureMPa + " МПа");
        }
        if (regulatingKind == null) {
            throw new IllegalArgumentException("тип регулирующей ступени не задан");
        }
        if (sectionHeatDrops == null) {
            throw new IllegalArgumentException("располагаемые перепады отсеков не заданы");
        }
    }

    /**
     * Исходные данные из документа, где давления записаны в барах.
     *
     * <p>Например, опорный вариант методички: {@code N_э^н = 8 МВт}, {@code P₀ = 28 бар},
     * {@code t₀ = 430 °C}, {@code P_к = 0.04 бар}, {@code n = 3000 об/мин}.
     *
     * @param nominalPowerMW     номинальная мощность, МВт
     * @param inletPressureBar   давление свежего пара, бар
     * @param inletTemperatureC  температура свежего пара, °C
     * @param exhaustPressureBar давление в конденсаторе, бар
     * @param rotationRpm        частота вращения, об/мин
     * @param regulatingKind     тип регулирующей ступени
     * @param sectionHeatDrops   располагаемые перепады отсеков, кДж/кг
     * @return исходные данные в единицах расчёта
     */
    public static DesignInput ofBar(double nominalPowerMW, double inletPressureBar,
                                    double inletTemperatureC, double exhaustPressureBar,
                                    double rotationRpm,
                                    CourseProjectEfficiency.RegulatingKind regulatingKind,
                                    SectionHeatDrops sectionHeatDrops) {
        return new DesignInput(nominalPowerMW, inletPressureBar * BAR, inletTemperatureC,
                exhaustPressureBar * BAR, rotationRpm, regulatingKind, sectionHeatDrops);
    }

    /**
     * Располагаемые теплоперепады отсеков, кДж/кг.
     *
     * <p>Для ЧНД величина в методичке получается из процесса до давления конденсатора. Класс
     * всё равно считает её по {@code P_к′} через IF97 и использует заданное значение только как
     * контроль: расхождение больше {@code 1 кДж/кг} попадает в примечания результата. Так
     * исключается ситуация, когда расчёт молча смешивает несовместимые данные.
     *
     * @param chvdKJkg располагаемый перепад ЧВД, кДж/кг
     * @param chsdKJkg располагаемый перепад ЧСД, кДж/кг
     * @param chndKJkg контрольный перепад ЧНД, кДж/кг
     */
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
