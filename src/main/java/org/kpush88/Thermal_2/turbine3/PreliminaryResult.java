package org.kpush88.Thermal_2.turbine3;

import java.util.List;
import java.util.Locale;

/**
 * Результат предварительного расчёта турбины (разделы 2.1–2.4 методички).
 */
public record PreliminaryResult(
        EnergyEstimate energy,
        RegulatingStage regulatingStage,
        Expansion expansion,
        PowerLimit powerLimit,
        List<String> notes) {

    public PreliminaryResult {
        if (energy == null || regulatingStage == null || expansion == null || powerLimit == null) {
            throw new IllegalArgumentException("результат предварительного расчёта неполон");
        }
        if (notes == null) {
            throw new IllegalArgumentException("примечания к расчёту не заданы");
        }
        notes = List.copyOf(notes);
    }

    /** Сводка этапов 2.1–2.4 и примечания. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append(energy.describe()).append(System.lineSeparator());
        text.append(regulatingStage.describe()).append(System.lineSeparator());
        text.append(expansion.describe()).append(System.lineSeparator());
        text.append(powerLimit.describe());
        for (String note : notes) {
            text.append(System.lineSeparator()).append("примечание: ").append(note);
        }
        return text.toString();
    }

    /** Этап 2.1: экономическая мощность, давления, располагаемый перепад и расход. */
    public record EnergyEstimate(
            double economicPowerKW,
            double inletPressureAtValvesMPa,
            double inletPressureAtNozzlesMPa,
            double exhaustPressureMPa,
            double exhaustPressureWithLossMPa,
            double inletEnthalpyKJkg,
            double inletEntropyKJkgK,
            double inletVolumeM3kg,
            double isentropicExhaustEnthalpyKJkg,
            double availableHeatDropKJkg,
            double estimatedFlowKgS) {

        public EnergyEstimate {
            requirePositive(economicPowerKW, "N_эк");
            requirePositive(inletPressureAtValvesMPa, "P₀");
            requirePositive(inletPressureAtNozzlesMPa, "P₀′");
            requirePositive(exhaustPressureMPa, "P_к");
            requirePositive(exhaustPressureWithLossMPa, "P_к′");
            if (!(exhaustPressureWithLossMPa < inletPressureAtNozzlesMPa)) {
                throw new IllegalArgumentException("P_к′ должно быть ниже P₀′");
            }
            requirePositive(inletEnthalpyKJkg, "h₀′");
            requirePositive(inletEntropyKJkgK, "s₀′");
            requirePositive(inletVolumeM3kg, "V₀′");
            requirePositive(isentropicExhaustEnthalpyKJkg, "энтальпия конца изоэнтропы");
            requirePositive(availableHeatDropKJkg, "H₀′");
            requirePositive(estimatedFlowKgS, "G′");
        }

        public String describe() {
            return String.format(Locale.ROOT,
                    "2.1: N_эк = %.0f кВт, P₀′ = %.4f МПа, P_к′ = %.5f МПа, "
                            + "h₀′ = %.2f кДж/кг, H₀′ = %.2f кДж/кг, G′ = %.3f кг/с",
                    economicPowerKW, inletPressureAtNozzlesMPa, exhaustPressureWithLossMPa,
                    inletEnthalpyKJkg, availableHeatDropKJkg, estimatedFlowKgS);
        }
    }

    /** Этап 2.2: регулирующая ступень. */
    public record RegulatingStage(
            RegulatingKind kind,
            double heatDropKJkg,
            double fictiveVelocityMS,
            double circumferentialVelocityMS,
            double meanDiameterM,
            double reaction,
            double nozzleHeatDropKJkg,
            double nozzleTheoreticalVelocityMS,
            double nozzlePressureMPa,
            double nozzleVolumeM3kg,
            double nozzleAreaM2,
            double admission,
            double nozzleHeightM,
            double nozzleExitAngleDeg) {

        public RegulatingStage {
            if (kind == null) {
                throw new IllegalArgumentException("тип регулирующей ступени не задан");
            }
            requirePositive(heatDropKJkg, "h₀^рс");
            requirePositive(fictiveVelocityMS, "Ca");
            requirePositive(circumferentialVelocityMS, "U");
            requirePositive(meanDiameterM, "d^рс");
            requireReaction(reaction);
            requirePositive(nozzleHeatDropKJkg, "h_ос");
            requirePositive(nozzleTheoreticalVelocityMS, "C₁t");
            requirePositive(nozzlePressureMPa, "P₁t");
            requirePositive(nozzleVolumeM3kg, "V₁t");
            requirePositive(nozzleAreaM2, "F₁");
            requireAdmission(admission);
            requirePositive(nozzleHeightM, "l₁");
            requireAngle(nozzleExitAngleDeg);
        }

        public String describe() {
            return String.format(Locale.ROOT,
                    "2.2: %s ступень, h₀^рс = %.1f кДж/кг, Ca = %.2f м/с, U = %.2f м/с, "
                            + "d = %.3f м, P₁t = %.3f МПа, V₁t = %.4f м³/кг, e = %.4f, l₁ = %.4f м",
                    kind.label(), heatDropKJkg, fictiveVelocityMS, circumferentialVelocityMS,
                    meanDiameterM, nozzlePressureMPa, nozzleVolumeM3kg, admission, nozzleHeightM);
        }
    }

    /** Этап 2.3: КПД элементов, внутренняя работа и уточнённый расход. */
    public record Expansion(
            CourseProjectEfficiency.RegulatingEstimate regulating,
            CourseProjectEfficiency.SectionEstimate chvd,
            CourseProjectEfficiency.SectionEstimate chsd,
            CourseProjectEfficiency.SectionEstimate chnd,
            double regulatingStageOutletPressureMPa,
            double regulatingStageOutletEnthalpyKJkg,
            double regulatingStageOutletVolumeM3kg,
            double chvdOutletPressureMPa,
            double chvdOutletVolumeM3kg,
            double chsdOutletPressureMPa,
            double chsdOutletVolumeM3kg,
            double exhaustVolumeM3kg,
            double exhaustActualVolumeM3kg,
            double internalWorkKJkg,
            double refinedFlowKgS,
            double turbineEfficiency) {

        public Expansion {
            if (regulating == null || chvd == null || chsd == null || chnd == null) {
                throw new IllegalArgumentException("оценка элементов турбины неполна");
            }
            requirePositive(regulatingStageOutletPressureMPa, "P₂^рс");
            requirePositive(regulatingStageOutletEnthalpyKJkg, "h₂^рс");
            requirePositive(regulatingStageOutletVolumeM3kg, "V₂^рс");
            requirePositive(chvdOutletPressureMPa, "P_ЧВД");
            requirePositive(chvdOutletVolumeM3kg, "V_ЧВД");
            requirePositive(chsdOutletPressureMPa, "P_ЧСД");
            requirePositive(chsdOutletVolumeM3kg, "V_ЧСД");
            requirePositive(exhaustVolumeM3kg, "V_выхл");
            requirePositive(exhaustActualVolumeM3kg, "V_выхл действ.");
            requirePositive(internalWorkKJkg, "h_i");
            requirePositive(refinedFlowKgS, "G");
            requireEfficiency(turbineEfficiency, "η_oi");
        }

        public String describe() {
            return String.format(Locale.ROOT,
                    "2.3: η_рс = %.4f, η_ЧВД = %.4f, η_ЧСД = %.4f, η_ЧНД = %.4f, "
                            + "h_i = %.2f кДж/кг, G = %.3f кг/с, η_oi = %.4f",
                    regulating.efficiency(), chvd.efficiency(), chsd.efficiency(),
                    chnd.efficiency(), internalWorkKJkg, refinedFlowKgS, turbineEfficiency);
        }
    }

    /** Этап 2.4: предельная мощность и число выхлопов. */
    public record PowerLimit(
            double exitVelocityMS,
            double lastStageVolumeM3kg,
            double limitPowerMW,
            int exhaustCount) {

        public PowerLimit {
            requirePositive(exitVelocityMS, "C₂z");
            requirePositive(lastStageVolumeM3kg, "V₂z");
            requirePositive(limitPowerMW, "N_пр");
            if (exhaustCount < 1) {
                throw new IllegalArgumentException("число выхлопов не может быть меньше 1");
            }
        }

        public String describe() {
            return String.format(Locale.ROOT,
                    "2.4: C₂z = %.2f м/с, V₂z = %.3f м³/кг, N_пр = %.2f МВт, выхлопов = %d",
                    exitVelocityMS, lastStageVolumeM3kg, limitPowerMW, exhaustCount);
        }
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен быть положительным: " + value);
        }
    }

    private static void requireEfficiency(double value, String name) {
        if (!(value > 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " вне области (0, 1): " + value);
        }
    }

    private static void requireReaction(double value) {
        if (!(value >= 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("степень реактивности вне [0, 1): " + value);
        }
    }

    private static void requireAdmission(double value) {
        if (!(value > 0.0 && value <= 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("парциальность вне (0, 1]: " + value);
        }
    }

    private static void requireAngle(double value) {
        if (!(value > 0.0 && value < 90.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("угол вне (0, 90)°: " + value);
        }
    }
}
