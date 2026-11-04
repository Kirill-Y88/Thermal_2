package org.kpush88.Thermal_2.turbine2;

import java.util.List;
import java.util.Locale;

/**
 * Результат предварительного расчёта турбины по разделам 2.1–2.4 методички.
 *
 * <p>Запись хранит не только итог, но и промежуточные величины каждого этапа. Это нужно для
 * проверки: если итоговый расход разошёлся с ожидаемым, по {@link EnergyEstimate} видно, на
 * каком перепаде это произошло, а по {@link Expansion} — какой отсек дал неверный КПД.
 *
 * <p><b>Единицы.</b> Мощность — кВт (кроме {@link PowerLimit#limitPowerMW()}, где она в МВт,
 * как в формуле 2.4), давление — МПа, энтальпия и теплоперепад — кДж/кг, энтропия —
 * кДж/(кг·K), объём — м³/кг, расход — кг/с, скорость — м/с, диаметр и высота — м, площадь —
 * м², угол — градусы.
 *
 * <p><b>Примечания.</b> {@link #notes()} — не ошибки, а расхождения, которые расчёт сумел
 * обнаружить и объяснить: например, контрольный перепад ЧНД из {@link DesignInput} отличается
 * от перепада, посчитанного по давлению конденсатора. Молча смешивать такие данные нельзя,
 * поэтому они выводятся в отчёт.
 *
 * @param energy          этап 2.1: экономическая мощность, давления, энтальпии и расход
 * @param regulatingStage этап 2.2: геометрия регулирующей ступени
 * @param expansion       этап 2.3: КПД элементов и уточнённый расход
 * @param powerLimit      этап 2.4: предельная мощность и число выхлопов
 * @param notes           предупреждения о расхождениях
 */
public record PreliminaryResult(
        EnergyEstimate energy,
        RegulatingStage regulatingStage,
        Expansion expansion,
        PowerLimit powerLimit,
        List<String> notes) {

    public PreliminaryResult {
        if (energy == null || regulatingStage == null || expansion == null
                || powerLimit == null) {
            throw new IllegalArgumentException("результат предварительного расчёта неполон");
        }
        if (notes == null) {
            throw new IllegalArgumentException("примечания к расчёту не заданы");
        }
        notes = List.copyOf(notes);
    }

    /**
     * Сводка для протокола: этапы 2.1–2.4 и примечания.
     *
     * @return многострочный текст
     */
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

    /**
     * Этап 2.1: экономическая мощность, давления, располагаемый перепад и предварительный
     * расход.
     *
     * @param economicPowerKW            {@code N_эк}, кВт
     * @param inletPressureAtValvesMPa   {@code P₀} перед стопорными клапанами, МПа
     * @param inletPressureAtNozzlesMPa  {@code P₀′} после клапанов, МПа
     * @param exhaustPressureMPa         {@code P_к} в конденсаторе, МПа
     * @param exhaustPressureWithLossMPa {@code P_к′} с потерей с выходной скоростью, МПа
     * @param inletEnthalpyKJkg          {@code h₀′}, кДж/кг
     * @param inletEntropyKJkgK          {@code s₀′}, кДж/(кг·K)
     * @param inletVolumeM3kg            {@code V₀′}, м³/кг
     * @param isentropicExhaustEnthalpyKJkg энтальпия в конце изоэнтропы, кДж/кг
     * @param availableHeatDropKJkg      {@code H₀′}, кДж/кг
     * @param estimatedFlowKgS           {@code G′}, кг/с
     */
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
            requirePositive(economicPowerKW, "экономическая мощность N_эк");
            requirePositive(inletPressureAtValvesMPa, "давление P₀");
            requirePositive(inletPressureAtNozzlesMPa, "давление P₀′");
            requirePositive(exhaustPressureMPa, "давление P_к");
            requirePositive(exhaustPressureWithLossMPa, "давление P_к′");
            if (!(exhaustPressureWithLossMPa < inletPressureAtNozzlesMPa)) {
                throw new IllegalArgumentException("давление P_к′ = " + exhaustPressureWithLossMPa
                        + " МПа должно быть ниже P₀′ = " + inletPressureAtNozzlesMPa + " МПа");
            }
            requirePositive(inletEnthalpyKJkg, "энтальпия h₀′");
            requirePositive(inletEntropyKJkgK, "энтропия s₀′");
            requirePositive(inletVolumeM3kg, "удельный объём V₀′");
            requirePositive(isentropicExhaustEnthalpyKJkg, "энтальпия конца изоэнтропы");
            if (!(availableHeatDropKJkg > 0.0)) {
                throw new IllegalArgumentException("располагаемый перепад H₀′ должен быть "
                        + "положительным: " + availableHeatDropKJkg + " кДж/кг");
            }
            requirePositive(estimatedFlowKgS, "предварительный расход G′");
        }

        /** Строка этапа 2.1 для протокола. */
        public String describe() {
            return String.format(Locale.ROOT,
                    "2.1: N_эк = %.0f кВт, P₀′ = %.4f МПа, P_к′ = %.5f МПа, "
                            + "h₀′ = %.2f кДж/кг, H₀′ = %.2f кДж/кг, G′ = %.3f кг/с",
                    economicPowerKW, inletPressureAtNozzlesMPa, exhaustPressureWithLossMPa,
                    inletEnthalpyKJkg, availableHeatDropKJkg, estimatedFlowKgS);
        }
    }

    /**
     * Этап 2.2: регулирующая ступень.
     *
     * @param kind                      тип ступени
     * @param heatDropKJkg              {@code h₀^рс}, кДж/кг
     * @param fictiveVelocityMS         {@code Ca}, м/с
     * @param circumferentialVelocityMS {@code U}, м/с
     * @param meanDiameterM             {@code d^рс}, м
     * @param reaction                  {@code ρ_т}
     * @param nozzleHeatDropKJkg        {@code h_ос}, кДж/кг
     * @param nozzleTheoreticalVelocityMS {@code c₁t}, м/с
     * @param nozzlePressureMPa         {@code P₁t}, МПа
     * @param nozzleVolumeM3kg          {@code V₁t}, м³/кг
     * @param nozzleAreaM2              {@code F₁}, м²
     * @param admission                 {@code e} (предварительное)
     * @param nozzleHeightM             {@code l₁}, м (предварительная)
     * @param nozzleExitAngleDeg        {@code α₁эф}, °
     */
    public record RegulatingStage(
            CourseProjectEfficiency.RegulatingKind kind,
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
            requirePositive(heatDropKJkg, "теплоперепад регулирующей ступени");
            requirePositive(fictiveVelocityMS, "фиктивная скорость Ca");
            requirePositive(circumferentialVelocityMS, "окружная скорость U");
            requirePositive(meanDiameterM, "средний диаметр регулирующей ступени");
            if (!(reaction >= 0.0 && reaction < 1.0)) {
                throw new IllegalArgumentException("степень реактивности вне [0, 1): " + reaction);
            }
            requirePositive(nozzleHeatDropKJkg, "теплоперепад сопловой решётки");
            requirePositive(nozzleTheoreticalVelocityMS, "теоретическая скорость c₁t");
            requirePositive(nozzlePressureMPa, "давление P₁t");
            requirePositive(nozzleVolumeM3kg, "удельный объём V₁t");
            requirePositive(nozzleAreaM2, "площадь F₁");
            if (!(admission > 0.0 && admission <= 1.0)) {
                throw new IllegalArgumentException("предварительная парциальность e должна "
                        + "лежать в (0, 1]: " + admission);
            }
            requirePositive(nozzleHeightM, "высота сопловой решётки l₁");
            if (!(nozzleExitAngleDeg > 0.0 && nozzleExitAngleDeg < 90.0)) {
                throw new IllegalArgumentException("угол α₁эф вне (0, 90)°: "
                        + nozzleExitAngleDeg);
            }
        }

        /** Строка этапа 2.2 для протокола. */
        public String describe() {
            return String.format(Locale.ROOT,
                    "2.2: %s ступень, h₀^рс = %.1f кДж/кг, Ca = %.2f м/с, U = %.2f м/с, "
                            + "d = %.3f м, P₁t = %.3f МПа, V₁t = %.4f м³/кг, "
                            + "e = %.4f, l₁ = %.4f м",
                    kind.label(), heatDropKJkg, fictiveVelocityMS, circumferentialVelocityMS,
                    meanDiameterM, nozzlePressureMPa, nozzleVolumeM3kg, admission,
                    nozzleHeightM);
        }
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен быть положительным: " + value);
        }
    }

    /**
     * Этап 2.3: КПД регулирующей ступени и отсеков, внутренняя работа и уточнённый расход.
     *
     * @param regulating                     оценка регулирующей ступени
     * @param chvd                           оценка ЧВД
     * @param chsd                           оценка ЧСД
     * @param chnd                           оценка ЧНД
     * @param regulatingStageOutletPressureMPa давление за регулирующей ступенью, МПа
     * @param regulatingStageOutletEnthalpyKJkg энтальпия за регулирующей ступенью, кДж/кг
     * @param regulatingStageOutletVolumeM3kg удельный объём за регулирующей ступенью, м³/кг
     * @param chvdOutletPressureMPa          давление за ЧВД, МПа
     * @param chvdOutletVolumeM3kg           удельный объём за ЧВД (изоэнтропа), м³/кг
     * @param chsdOutletPressureMPa          давление за ЧСД, МПа
     * @param chsdOutletVolumeM3kg           удельный объём за ЧСД (изоэнтропа), м³/кг
     * @param exhaustVolumeM3kg              удельный объём на выхлопе по изоэнтропе, м³/кг
     * @param exhaustActualVolumeM3kg        действительный удельный объём на выхлопе, м³/кг
     * @param internalWorkKJkg               суммарная внутренняя работа {@code h_i}, кДж/кг
     * @param refinedFlowKgS                 уточнённый расход {@code G}, кг/с
     * @param turbineEfficiency              {@code η_oi = h_i/H₀′}
     */
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
                throw new IllegalArgumentException("оценка одного из элементов турбины не задана");
            }
            requirePositive(regulatingStageOutletPressureMPa,
                    "давление за регулирующей ступенью");
            requirePositive(regulatingStageOutletEnthalpyKJkg,
                    "энтальпия за регулирующей ступенью");
            requirePositive(regulatingStageOutletVolumeM3kg,
                    "удельный объём за регулирующей ступенью");
            requirePositive(chvdOutletPressureMPa, "давление за ЧВД");
            requirePositive(chvdOutletVolumeM3kg, "удельный объём за ЧВД");
            requirePositive(chsdOutletPressureMPa, "давление за ЧСД");
            requirePositive(chsdOutletVolumeM3kg, "удельный объём за ЧСД");
            requirePositive(exhaustVolumeM3kg, "удельный объём на выхлопе");
            requirePositive(exhaustActualVolumeM3kg, "действительный объём на выхлопе");
            requirePositive(internalWorkKJkg, "внутренняя работа h_i");
            requirePositive(refinedFlowKgS, "уточнённый расход G");
            if (!(turbineEfficiency > 0.0 && turbineEfficiency < 1.0)) {
                throw new IllegalArgumentException("КПД турбины вне (0, 1): "
                        + turbineEfficiency);
            }
        }

        /** Строка этапа 2.3 для протокола. */
        public String describe() {
            return String.format(Locale.ROOT,
                    "2.3: η_рс = %.4f, η_ЧВД = %.4f, η_ЧСД = %.4f, η_ЧНД = %.4f, "
                            + "h_i = %.2f кДж/кг, G = %.3f кг/с, η_oi = %.4f",
                    regulating.efficiency(), chvd.efficiency(), chsd.efficiency(),
                    chnd.efficiency(), internalWorkKJkg, refinedFlowKgS, turbineEfficiency);
        }
    }

    /**
     * Этап 2.4: предельная мощность одной выхлопной камеры.
     *
     * @param exitVelocityMS     {@code C₂z}, м/с
     * @param lastStageVolumeM3kg {@code V₂z}, м³/кг
     * @param limitPowerMW       {@code N_пр}, МВт
     * @param exhaustCount       число выхлопов
     */
    public record PowerLimit(
            double exitVelocityMS,
            double lastStageVolumeM3kg,
            double limitPowerMW,
            int exhaustCount) {

        public PowerLimit {
            requirePositive(exitVelocityMS, "выходная скорость C₂z");
            requirePositive(lastStageVolumeM3kg, "удельный объём на выхлопе V₂z");
            requirePositive(limitPowerMW, "предельная мощность N_пр");
            if (exhaustCount < 1) {
                throw new IllegalArgumentException("число выхлопов не может быть меньше 1: "
                        + exhaustCount);
            }
        }

        /** Строка этапа 2.4 для протокола. */
        public String describe() {
            return String.format(Locale.ROOT,
                    "2.4: C₂z = %.2f м/с, V₂z = %.3f м³/кг, N_пр = %.2f МВт, выхлопов = %d",
                    exitVelocityMS, lastStageVolumeM3kg, limitPowerMW, exhaustCount);
        }
    }

    }
