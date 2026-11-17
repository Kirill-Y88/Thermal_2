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

    /** Этап 2.1: узлы входа и конца изоэнтропы, давления, перепад и расход. */
    public record EnergyEstimate(
            FlowPoint inlet,
            FlowPoint isentropicExhaust,
            double economicPowerKW,
            double inletPressureAtValvesMPa,
            double exhaustPressureMPa,
            double exhaustPressureWithLossMPa) {

        public EnergyEstimate {
            if (inlet == null || isentropicExhaust == null) {
                throw new IllegalArgumentException("точки этапа 2.1 не заданы");
            }
            if (inlet.getPointType() != FlowPointType.UNRESOLVED) {
                throw new IllegalArgumentException("входная точка должна иметь тип UNRESOLVED");
            }
            if (isentropicExhaust.getPointType() != FlowPointType.ISENTROPIC) {
                throw new IllegalArgumentException("конец изоэнтропы должен иметь тип ISENTROPIC");
            }
            if (inlet.getMassFlow() == null || !(inlet.getMassFlow() > 0.0)) {
                throw new IllegalArgumentException("расход G′ не задан на входной точке");
            }
            requirePositive(economicPowerKW, "N_эк");
            requirePositive(inletPressureAtValvesMPa, "P₀");
            requirePositive(exhaustPressureMPa, "P_к");
            requirePositive(exhaustPressureWithLossMPa, "P_к′");
            if (!(exhaustPressureWithLossMPa < inlet.getPoint().pressureMPa())) {
                throw new IllegalArgumentException("P_к′ должно быть ниже P₀′");
            }
            if (!(inlet.getPoint().enthalpyKJkg()
                    - isentropicExhaust.getPoint().enthalpyKJkg() > 0.0)) {
                throw new IllegalArgumentException("располагаемый перепад H₀′ не положителен");
            }
        }

        /** Давление за клапанами P₀′, МПа. */
        public double inletPressureAtNozzlesMPa() {
            return inlet.getPoint().pressureMPa();
        }

        /** Температура свежего пара t₀, °C. */
        public double inletTemperatureC() {
            return inlet.getPoint().temperatureC();
        }

        /** Энтальпия h₀′, кДж/кг. */
        public double inletEnthalpyKJkg() {
            return inlet.getPoint().enthalpyKJkg();
        }

        /** Энтропия s₀′, кДж/(кг·K). */
        public double inletEntropyKJkgK() {
            return inlet.getPoint().entropyKJkgK();
        }

        /** Удельный объём V₀′, м³/кг. */
        public double inletVolumeM3kg() {
            return inlet.getPoint().volumeM3kg();
        }

        /** Энтальпия конца изоэнтропы, кДж/кг. */
        public double isentropicExhaustEnthalpyKJkg() {
            return isentropicExhaust.getPoint().enthalpyKJkg();
        }

        /** Располагаемый теплоперепад H₀′, кДж/кг. */
        public double availableHeatDropKJkg() {
            return inletEnthalpyKJkg() - isentropicExhaustEnthalpyKJkg();
        }

        /** Предварительный расход G′, кг/с. */
        public double estimatedFlowKgS() {
            return inlet.getMassFlow();
        }

        public String describe() {
            return String.format(Locale.ROOT,
                    "2.1: N_эк = %.0f кВт, P₀′ = %.4f МПа, P_к′ = %.5f МПа, "
                            + "h₀′ = %.2f кДж/кг, H₀′ = %.2f кДж/кг, G′ = %.3f кг/с",
                    economicPowerKW, inletPressureAtNozzlesMPa(), exhaustPressureWithLossMPa(),
                    inletEnthalpyKJkg(), availableHeatDropKJkg(), estimatedFlowKgS());
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

    /** Этап 2.3: процесс расширения в узлах, КПД элементов и уточнённый расход. */
    public record Expansion(
            ExpansionProcess process,
            CourseProjectEfficiency.RegulatingEstimate regulating,
            CourseProjectEfficiency.SectionEstimate chvd,
            CourseProjectEfficiency.SectionEstimate chsd,
            CourseProjectEfficiency.SectionEstimate chnd,
            double internalWorkKJkg,
            double refinedFlowKgS,
            double turbineEfficiency) {

        public Expansion {
            if (process == null || regulating == null || chvd == null || chsd == null
                    || chnd == null) {
                throw new IllegalArgumentException("оценка элементов турбины неполна");
            }
            requirePositive(internalWorkKJkg, "h_i");
            requirePositive(refinedFlowKgS, "G");
            requireEfficiency(turbineEfficiency, "η_oi");
        }

        /** Давление P₂^рс, МПа. */
        public double regulatingStageOutletPressureMPa() {
            return process.regulatingStageOutlet().getPoint().pressureMPa();
        }

        /** Энтальпия h₂^рс, кДж/кг. */
        public double regulatingStageOutletEnthalpyKJkg() {
            return process.regulatingStageOutlet().getPoint().enthalpyKJkg();
        }

        /** Удельный объём V₂^рс, м³/кг. */
        public double regulatingStageOutletVolumeM3kg() {
            return process.regulatingStageOutlet().getPoint().volumeM3kg();
        }

        /** Давление за ЧВД, МПа. */
        public double chvdOutletPressureMPa() {
            return process.chvdOutlet().getPoint().pressureMPa();
        }

        /** Удельный объём за ЧВД, м³/кг. */
        public double chvdOutletVolumeM3kg() {
            return process.chvdOutlet().getPoint().volumeM3kg();
        }

        /** Давление за ЧСД, МПа. */
        public double chsdOutletPressureMPa() {
            return process.chsdOutlet().getPoint().pressureMPa();
        }

        /** Удельный объём за ЧСД, м³/кг. */
        public double chsdOutletVolumeM3kg() {
            return process.chsdOutlet().getPoint().volumeM3kg();
        }

        /** Объём в конце изоэнтропы ЧНД, м³/кг. */
        public double exhaustVolumeM3kg() {
            return process.chndIsentropic().getPoint().volumeM3kg();
        }

        /** Фактический объём на выхлопе, м³/кг. */
        public double exhaustActualVolumeM3kg() {
            return process.exhaust().getPoint().volumeM3kg();
        }

        public String describe() {
            String summary = String.format(Locale.ROOT,
                    "2.3: η_рс = %.4f, η_ЧВД = %.4f, η_ЧСД = %.4f, η_ЧНД = %.4f, "
                            + "h_i = %.2f кДж/кг, G = %.3f кг/с, η_oi = %.4f",
                    regulating.efficiency(), chvd.efficiency(), chsd.efficiency(),
                    chnd.efficiency(), internalWorkKJkg, refinedFlowKgS, turbineEfficiency);
            return summary + System.lineSeparator() + process.describe();
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
