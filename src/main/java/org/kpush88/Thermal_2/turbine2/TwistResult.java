package org.kpush88.Thermal_2.turbine2;

import java.util.List;
import java.util.Locale;

/**
 * Расчёт закрутки последней ступени по трём радиальным участкам — раздел 3.4 методички
 * {@code university/Курс турб.docx}.
 *
 * <p>Принят закон постоянства угла выхода из сопловой решётки: сопловые лопатки не
 * закручиваются, а рабочая лопатка профилируется по высоте. Каждый участок рассчитывается как
 * короткая ступень со своими окружной скоростью, степенью реактивности и треугольниками.
 *
 * <p>Радиусы отсчитываются от оси турбины. Границы участков берутся через половину высоты
 * рабочей лопатки: {@code r₁ = r_ср − l₂/2}, {@code r₃ = r_ср + l₂/2}.
 *
 * <p>Единицы: радиус — м, скорость — м/с, угол — градусы, давление — МПа, объём — м³/кг,
 * теплоперепад и потери — кДж/кг, расход — кг/с, мощность — кВт.
 */
public record TwistResult(
        int stageNumber,
        double heatDropKJkg,
        double meanDiameterM,
        double bladeHeightM,
        double outletAngleDeg,
        double totalFlowKgS,
        double designFlowKgS,
        double totalPowerKW,
        double profileEfficiency,
        List<Section> sections) {

    public TwistResult {
        if (stageNumber < 1) {
            throw new IllegalArgumentException("номер ступени должен быть положительным");
        }
        if (sections == null || sections.size() != 3) {
            throw new IllegalArgumentException("закрутка считается по трём радиальным участкам");
        }
        sections = List.copyOf(sections);
        requirePositive(heatDropKJkg, "перепад ступени");
        requirePositive(meanDiameterM, "средний диаметр");
        requirePositive(bladeHeightM, "высота лопатки");
        requirePositive(outletAngleDeg, "угол α₁эф");
        requirePositive(totalFlowKgS, "суммарный расход");
        requirePositive(designFlowKgS, "расчётный расход");
        requirePositive(totalPowerKW, "суммарная мощность");
        requireInterval(profileEfficiency, "лопаточный КПД");
    }

    /** Лопаточный КПД закрутки; краткая форма для отчётов. */
    public double efficiency() {
        return profileEfficiency;
    }

    /** Относительное отклонение суммарного расхода от расчётного. */
    public double flowDeviation() {
        return totalFlowKgS / designFlowKgS - 1.0;
    }

    /** Сводка раздела 3.4 для протокола. */
    public String describe() {
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT,
                "3.4: ступень №%d, d = %.3f м, l₂ = %.4f м, α₁эф = %.2f°, "
                        + "G = %.4f кг/с, N_ол = %.2f кВт, η_ол = %.4f",
                stageNumber, meanDiameterM, bladeHeightM, outletAngleDeg,
                totalFlowKgS, totalPowerKW, profileEfficiency));
        for (Section section : sections) {
            text.append(System.lineSeparator()).append("  ").append(section.describe());
        }
        return text.toString();
    }

    /**
     * Один радиальный участок.
     *
     * @param index                      номер участка 1…3
     * @param radiusM                    средний радиус участка
     * @param reaction                   степень реактивности
     * @param nozzleHeatDropKJkg         перепад сопловой решётки
     * @param rotorHeatDropKJkg          перепад рабочей решётки
     * @param nozzleTheoreticalVelocityMS {@code C₁t}
     * @param nozzleActualVelocityMS     {@code C₁}
     * @param nozzleMach                 {@code M₁t}
     * @param nozzlePressureMPa          давление за соплом
     * @param nozzleVolumeM3kg           объём за соплом
     * @param flowKgS                    расход участка
     * @param circumferentialVelocityMS  {@code U_i}
     * @param nozzleExitAngleDeg         {@code α₁i}
     * @param rotorInletVelocityMS       {@code W₁i}
     * @param rotorInletAngleDeg         {@code β₁i}
     * @param rotorTheoreticalVelocityMS {@code W₂ti}
     * @param rotorActualVelocityMS      {@code W₂i}
     * @param rotorExitAngleDeg          {@code β₂i}
     * @param exitVelocityMS             {@code C₂i}
     * @param exitAngleDeg               {@code α₂i}
     * @param nozzleLossKJkg             потеря в сопле
     * @param rotorLossKJkg              потеря в рабочей решётке
     * @param exitLossKJkg               потеря с выходной скоростью
     * @param profileEfficiency          КПД участка
     * @param powerKW                    мощность участка
     */
    public record Section(
            int index,
            double radiusM,
            double reaction,
            double nozzleHeatDropKJkg,
            double rotorHeatDropKJkg,
            double nozzleTheoreticalVelocityMS,
            double nozzleActualVelocityMS,
            double nozzleMach,
            double nozzlePressureMPa,
            double nozzleVolumeM3kg,
            double flowKgS,
            double circumferentialVelocityMS,
            double nozzleExitAngleDeg,
            double rotorInletVelocityMS,
            double rotorInletAngleDeg,
            double rotorTheoreticalVelocityMS,
            double rotorActualVelocityMS,
            double rotorExitAngleDeg,
            double exitVelocityMS,
            double exitAngleDeg,
            double nozzleLossKJkg,
            double rotorLossKJkg,
            double exitLossKJkg,
            double profileEfficiency,
            double powerKW) {

        public Section {
            if (index < 1) {
                throw new IllegalArgumentException("номер участка должен быть положительным");
            }
            requirePositive(radiusM, "радиус участка");
            requirePositive(nozzleHeatDropKJkg, "перепад сопла");
            requirePositive(rotorHeatDropKJkg, "перепад рабочей решётки");
            requirePositive(nozzleTheoreticalVelocityMS, "C₁t");
            requirePositive(nozzleActualVelocityMS, "C₁");
            requirePositive(nozzlePressureMPa, "давление за соплом");
            requirePositive(nozzleVolumeM3kg, "объём за соплом");
            requirePositive(flowKgS, "расход участка");
            requirePositive(circumferentialVelocityMS, "окружная скорость");
            requirePositive(rotorInletVelocityMS, "W₁");
            requirePositive(rotorTheoreticalVelocityMS, "W₂t");
            requirePositive(rotorActualVelocityMS, "W₂");
            requirePositive(exitVelocityMS, "C₂");
            requireInterval(profileEfficiency, "КПД участка");
            if (reaction < 0.0 || reaction >= 1.0) {
                throw new IllegalArgumentException("реактивность участка вне [0, 1): " + reaction);
            }
        }

        /** Строка участка для протокола. */
        public String describe() {
            return String.format(Locale.ROOT,
                    "r = %.3f м, ρ = %.3f, ΔG = %.4f кг/с, C₁ = %.2f м/с, "
                            + "W₂ = %.2f м/с, C₂ = %.2f м/с, η_ол = %.4f, N = %.2f кВт",
                    radiusM, reaction, flowKgS, nozzleActualVelocityMS,
                    rotorActualVelocityMS, exitVelocityMS, profileEfficiency, powerKW);
        }
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен быть положительным: " + value);
        }
    }

    private static void requireInterval(double value, String name) {
        if (!(value > 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " вне (0, 1): " + value);
        }
    }
}
