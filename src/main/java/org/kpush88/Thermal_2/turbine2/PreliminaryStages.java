package org.kpush88.Thermal_2.turbine2;

import java.util.List;
import java.util.Locale;

/**
 * Результат раздела 2.5: предварительная проточная часть по отсекам.
 *
 * <p>Расчёт даёт то, что нужно для перехода к детальным разделам 3.1–3.4: число ступеней,
 * диаметры, высоты лопаток и распределение теплоперепада. Каждая ступень хранится отдельно,
 * потому что по ней потом считаются треугольники скоростей, закрутка и прочность.
 *
 * <p><b>Единицы.</b> Диаметры и высоты — м, теплоперепад и внутренняя работа — кДж/кг,
 * давление — МПа, мощность — кВт, скорость — м/с.
 *
 * @param chvd             ЧВД
 * @param chsd             ЧСД
 * @param chnd             ЧНД
 * @param totalStages      суммарное число нерегулируемых ступеней
 * @param totalHeatDropKJkg суммарный располагаемый перепад отсеков, кДж/кг
 * @param internalWorkKJkg суммарная внутренняя работа нерегулируемых ступеней, кДж/кг
 * @param totalPowerKW     суммарная внутренняя мощность нерегулируемых ступеней, кВт
 * @param recoveryHeatKJkg суммарный возврат тепла, кДж/кг
 */
public record PreliminaryStages(
        SectionLayout chvd,
        SectionLayout chsd,
        SectionLayout chnd,
        int totalStages,
        double totalHeatDropKJkg,
        double internalWorkKJkg,
        double totalPowerKW,
        double recoveryHeatKJkg) {

    public PreliminaryStages {
        if (chvd == null || chsd == null || chnd == null) {
            throw new IllegalArgumentException("разложение по отсекам задано не полностью");
        }
        if (totalStages < 1) {
            throw new IllegalArgumentException("суммарное число ступеней должно быть "
                    + "положительным: " + totalStages);
        }
        requirePositive(totalHeatDropKJkg, "суммарный располагаемый перепад");
        requirePositive(internalWorkKJkg, "суммарная внутренняя работа");
        requirePositive(totalPowerKW, "суммарная внутренняя мощность");
        requireNonNegative(recoveryHeatKJkg, "суммарный возврат тепла");
        if (chvd.stageCount() + chsd.stageCount() + chnd.stageCount() != totalStages) {
            throw new IllegalArgumentException("суммарное число ступеней " + totalStages
                    + " не совпадает с суммой по отсекам");
        }
    }

    /**
     * Отсеки в порядке прохождения пара.
     *
     * @return ЧВД, ЧСД, ЧНД
     */
    public List<SectionLayout> sections() {
        return List.of(chvd, chsd, chnd);
    }

    /**
     * Таблица распределения теплоперепада и сводка по отсекам.
     *
     * @return многострочный текст для протокола
     */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append("2.5: предварительная проточная часть")
                .append(System.lineSeparator());
        for (SectionLayout section : sections()) {
            text.append(section.describe()).append(System.lineSeparator());
        }
        text.append(String.format(Locale.ROOT,
                "2.5 итог: ступеней = %d, ΣH₀ = %.2f кДж/кг, h_i = %.2f кДж/кг, "
                        + "N_i = %.1f кВт, возврат тепла = %.2f кДж/кг",
                totalStages, totalHeatDropKJkg, internalWorkKJkg, totalPowerKW,
                recoveryHeatKJkg));
        return text.toString();
    }

    /**
     * Одна ступень предварительной проточной части.
     *
     * @param number              номер ступени внутри отсека
     * @param name                обозначение, например {@code ЧВД-1}
     * @param regulating          является ли ступень регулирующей
     * @param meanDiameterM       средний диаметр, м
     * @param rootDiameterM       корневой диаметр, м
     * @param tipDiameterM        периферийный диаметр, м
     * @param nozzleHeightM       высота сопловой лопатки, м
     * @param bladeHeightM        высота рабочей лопатки, м
     * @param heatDropKJkg        располагаемый теплоперепад ступени с возвратом тепла, кДж/кг
     * @param reaction            степень реактивности
     * @param circumferentialSpeedMS окружная скорость, м/с
     * @param speedRatio          {@code U/Ca}
     * @param admission           парциальность
     * @param outletPressureMPa   давление за ступенью, МПа
     */
    public record StageLayout(
            int number,
            String name,
            boolean regulating,
            double meanDiameterM,
            double rootDiameterM,
            double tipDiameterM,
            double nozzleHeightM,
            double bladeHeightM,
            double heatDropKJkg,
            double reaction,
            double circumferentialSpeedMS,
            double speedRatio,
            double admission,
            double outletPressureMPa) {

        public StageLayout {
            if (number < 1) {
                throw new IllegalArgumentException("номер ступени должен быть положительным: "
                        + number);
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("обозначение ступени не задано");
            }
            requirePositive(meanDiameterM, "средний диаметр");
            requirePositive(rootDiameterM, "корневой диаметр");
            requirePositive(tipDiameterM, "периферийный диаметр");
            requirePositive(nozzleHeightM, "высота сопловой лопатки");
            requirePositive(bladeHeightM, "высота рабочей лопатки");
            requirePositive(heatDropKJkg, "теплоперепад ступени");
            if (!(reaction >= 0.0 && reaction < 1.0) || !Double.isFinite(reaction)) {
                throw new IllegalArgumentException("степень реактивности вне [0, 1): "
                        + reaction);
            }
            requirePositive(circumferentialSpeedMS, "окружная скорость");
            requirePositive(speedRatio, "U/Ca");
            if (!(admission > 0.0 && admission <= 1.0) || !Double.isFinite(admission)) {
                throw new IllegalArgumentException("парциальность вне (0, 1]: " + admission);
            }
            requirePositive(outletPressureMPa, "давление за ступенью");
            if (!(tipDiameterM > rootDiameterM)) {
                throw new IllegalArgumentException("периферийный диаметр " + tipDiameterM
                        + " м должен быть больше корневого " + rootDiameterM + " м");
            }
        }


        /**
         * Строка ступени для протокола.
         *
         * @return текст одной строки
         */
        public String describe() {
            return String.format(Locale.ROOT,
                    "  %-7s d = %.3f м, d_к = %.3f м, l₂ = %.4f м, h₀ = %.2f кДж/кг, "
                            + "ρ = %.3f, U = %.1f м/с, U/Ca = %.3f, e = %.2f, p₂ = %.3f МПа",
                    name, meanDiameterM, rootDiameterM, bladeHeightM, heatDropKJkg,
                    reaction, circumferentialSpeedMS, speedRatio, admission,
                    outletPressureMPa);
        }
    }


    /**
     * Предварительная проточная часть одного отсека.
     *
     * @param name              обозначение отсека
     * @param stageCount        число ступеней
     * @param totalHeatDropKJkg суммарный перепад ступеней с возвратом тепла, кДж/кг
     * @param meanHeatDropKJkg  средний перепад на ступень, кДж/кг
     * @param recoveryHeatKJkg  возврат тепла, кДж/кг
     * @param recoveryCoefficient коэффициент возврата тепла
     * @param firstMeanDiameterM средний диаметр первой ступени, м
     * @param lastMeanDiameterM средний диаметр последней ступени, м
     * @param firstBladeHeightM высота рабочей лопатки первой ступени, м
     * @param lastBladeHeightM  высота рабочей лопатки последней ступени, м
     * @param lastRootDiameterM корневой диаметр последней ступени, м
     * @param inletPressureMPa  давление на входе, МПа
     * @param outletPressureMPa давление на выходе, МПа
     * @param inletEnthalpyKJkg энтальпия на входе, кДж/кг
     * @param outletEnthalpyKJkg энтальпия на выходе, кДж/кг
     * @param internalWorkKJkg  внутренняя работа отсека, кДж/кг
     * @param efficiency        внутренний КПД отсека
     * @param stages            ступени отсека
     */
    public record SectionLayout(
            String name,
            int stageCount,
            double totalHeatDropKJkg,
            double meanHeatDropKJkg,
            double recoveryHeatKJkg,
            double recoveryCoefficient,
            double firstMeanDiameterM,
            double lastMeanDiameterM,
            double firstBladeHeightM,
            double lastBladeHeightM,
            double lastRootDiameterM,
            double inletPressureMPa,
            double outletPressureMPa,
            double inletEnthalpyKJkg,
            double outletEnthalpyKJkg,
            double internalWorkKJkg,
            double efficiency,
            List<StageLayout> stages) {

        public SectionLayout {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("обозначение отсека не задано");
            }
            if (stageCount < 1) {
                throw new IllegalArgumentException("число ступеней отсека должно быть "
                        + "положительным: " + stageCount);
            }
            if (stages == null || stages.size() != stageCount) {
                throw new IllegalArgumentException("список ступеней отсека " + name
                        + " не соответствует числу ступеней " + stageCount);
            }
            requirePositive(totalHeatDropKJkg, "суммарный перепад отсека");
            requirePositive(meanHeatDropKJkg, "средний перепад ступени");
            requireNonNegative(recoveryHeatKJkg, "возврат тепла");
            requireNonNegative(recoveryCoefficient, "коэффициент возврата тепла");
            requirePositive(firstMeanDiameterM, "диаметр первой ступени");
            requirePositive(lastMeanDiameterM, "диаметр последней ступени");
            requirePositive(firstBladeHeightM, "высота лопатки первой ступени");
            requirePositive(lastBladeHeightM, "высота лопатки последней ступени");
            requirePositive(lastRootDiameterM, "корневой диаметр последней ступени");
            requirePositive(inletPressureMPa, "давление на входе отсека");
            requirePositive(outletPressureMPa, "давление на выходе отсека");
            requirePositive(inletEnthalpyKJkg, "энтальпия на входе отсека");
            requirePositive(outletEnthalpyKJkg, "энтальпия на выходе отсека");
            requirePositive(internalWorkKJkg, "внутренняя работа отсека");
            if (!(efficiency > 0.0 && efficiency < 1.0) || !Double.isFinite(efficiency)) {
                throw new IllegalArgumentException("КПД отсека вне (0, 1): " + efficiency);
            }
            stages = List.copyOf(stages);
        }


        /**
         * Сводка отсека и таблица его ступеней.
         *
         * @return многострочный текст
         */
        public String describe() {
            StringBuilder text = new StringBuilder();
            text.append(String.format(Locale.ROOT,
                    "%s: z = %d, d_ср1 = %.3f м, d_срz = %.3f м, l₂z = %.4f м, "
                            + "Σh₀ = %.2f кДж/кг, q = %.2f кДж/кг, η = %.4f",
                    name, stageCount, firstMeanDiameterM, lastMeanDiameterM,
                    lastBladeHeightM, totalHeatDropKJkg, recoveryHeatKJkg, efficiency))
                    .append(System.lineSeparator());
            for (StageLayout stage : stages) {
                text.append(stage.describe()).append(System.lineSeparator());
            }
            return text.toString().stripTrailing();
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
}
