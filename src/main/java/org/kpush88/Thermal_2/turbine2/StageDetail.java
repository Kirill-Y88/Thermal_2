package org.kpush88.Thermal_2.turbine2;

import java.util.Locale;

/**
 * Детальный расчёт нерегулируемой одновенечной ступени — разделы 3.2 и 3.3 методички
 * {@code university/Курс турб.docx}.
 *
 * <p>Хранит треугольники скоростей, давления и объёмы за решётками, геометрию, потери, КПД и
 * мощность. Одна запись используется для первой ступени ЧВД (3.2) и последних ступеней ЧНД
 * (3.3); различие — в исходных параметрах и учёте влажности.
 *
 * <p>{@link #inletWetness()} и {@link #outletWetness()} — доли жидкой фазы {@code y = 1 − x}.
 * Для перегретого пара они равны нулю.
 *
 * <p>{@code h₀* = h₀ + χ₀·C₀²/2000} — располагаемый перепад от параметров торможения.
 * Для последующих ступеней отсека {@code C₀} берётся из выходной скорости предыдущей
 * ступени, поэтому {@code h₀*} учитывает кинетическую энергию, перенесённую в ступень;
 * первая ступень отсека стартует с {@code C₀ = 0}.
 *
 * <p>{@code E₀ = h₀* − χ·Δh_вс}: для всех ступеней, кроме последней, {@code χ = 1}. При этом
 * использованный теплоперепад равен {@code h₀*} минус все потери.
 *
 * <p>Единицы: скорость — м/с, угол — градусы, давление — МПа, объём — м³/кг, площадь — м²,
 * длина — м, теплоперепад и потери — кДж/кг, мощность — кВт.
 *
 * @param heatDropKJkg             статический располагаемый перепад {@code h₀}, кДж/кг
 * @param stagnationHeatDropKJkg   располагаемый перепад от параметров торможения {@code h₀*},
 *                                 кДж/кг
 * @param inletVelocityMS          скорость на входе {@code C₀}, м/с
 */
public record StageDetail(
        int globalNumber,
        int sectionNumber,
        String name,
        double meanDiameterM,
        double rootDiameterM,
        double bladeHeightM,
        double nozzleHeightM,
        double heatDropKJkg,
        double stagnationHeatDropKJkg,
        double inletVelocityMS,
        double reaction,
        double admission,
        double speedRatio,
        double circumferentialVelocityMS,
        double fictiveVelocityMS,
        double inletPressureMPa,
        double inletEnthalpyKJkg,
        double inletVolumeM3kg,
        double inletWetness,
        double outletWetness,
        double nozzleHeatDropKJkg,
        double rotorHeatDropKJkg,
        double nozzleTheoreticalVelocityMS,
        double nozzleActualVelocityMS,
        double nozzleExitAngleDeg,
        double nozzleMach,
        double nozzleFlowCoefficient,
        double nozzleSpeedCoefficient,
        double nozzlePressureMPa,
        double nozzleVolumeM3kg,
        double nozzleAreaM2,
        double nozzleLossKJkg,
        double nozzleChordM,
        double nozzlePitchM,
        double nozzlePitchRatio,
        int nozzleBladeCount,
        double rotorTheoreticalVelocityMS,
        double rotorActualVelocityMS,
        double rotorInletVelocityMS,
        double rotorInletAngleDeg,
        double rotorExitAngleDeg,
        double rotorExitAbsoluteVelocityMS,
        double rotorExitAbsoluteAngleDeg,
        double rotorMach,
        double rotorFlowCoefficient,
        double rotorSpeedCoefficient,
        double rotorPressureMPa,
        double rotorVolumeM3kg,
        double rotorAreaM2,
        double rotorLossKJkg,
        double rotorChordM,
        double rotorPitchM,
        double rotorPitchRatio,
        int rotorBladeCount,
        double exitVelocityMS,
        double exitLossKJkg,
        double availableEnergyKJkg,
        double profileEfficiency,
        double frictionLossKJkg,
        double partialLossKJkg,
        double bladeLeakLossKJkg,
        double diaphragmLeakLossKJkg,
        double wetnessLossKJkg,
        double totalLossKJkg,
        double usedHeatDropKJkg,
        double internalEfficiency,
        double internalPowerKW) {

    public StageDetail {
        if (globalNumber < 1 || sectionNumber < 1) {
            throw new IllegalArgumentException("номера ступени должны быть положительными");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("обозначение ступени не задано");
        }
        requirePositive(meanDiameterM, "средний диаметр");
        requirePositive(rootDiameterM, "корневой диаметр");
        requirePositive(bladeHeightM, "высота рабочей лопатки");
        requirePositive(nozzleHeightM, "высота сопловой лопатки");
        requirePositive(heatDropKJkg, "располагаемый перепад ступени");
        requirePositive(stagnationHeatDropKJkg, "располагаемый перепад от параметров торможения");
        requireNonNegative(inletVelocityMS, "скорость входа C₀");
        if (stagnationHeatDropKJkg < heatDropKJkg) {
            throw new IllegalArgumentException("перепад h₀* не может быть меньше статического h₀: "
                    + stagnationHeatDropKJkg + " < " + heatDropKJkg);
        }
        requirePositive(circumferentialVelocityMS, "окружная скорость");
        requirePositive(nozzleTheoreticalVelocityMS, "теоретическая скорость сопла");
        requirePositive(nozzleActualVelocityMS, "действительная скорость сопла");
        requirePositive(nozzlePressureMPa, "давление за соплом");
        requirePositive(nozzleAreaM2, "площадь сопла");
        requirePositive(rotorTheoreticalVelocityMS, "теоретическая относительная скорость");
        requirePositive(rotorActualVelocityMS, "действительная относительная скорость");
        requirePositive(rotorInletVelocityMS, "относительная скорость входа");
        requirePositive(rotorExitAbsoluteVelocityMS, "абсолютная скорость выхода");
        requirePositive(rotorPressureMPa, "давление за рабочей решёткой");
        requirePositive(rotorAreaM2, "площадь рабочей решётки");
        requirePositive(availableEnergyKJkg, "располагаемая энергия E₀");
        requireInterval(profileEfficiency, "лопаточный КПД");
        requirePositive(usedHeatDropKJkg, "использованный теплоперепад");
        requireInterval(internalEfficiency, "внутренний КПД");
        requirePositive(internalPowerKW, "внутренняя мощность");
        if (reaction < 0.0 || reaction >= 1.0
                || admission <= 0.0 || admission > 1.0 || speedRatio <= 0.0) {
            throw new IllegalArgumentException("реактивность, парциальность или U/Ca вне "
                    + "допустимого диапазона");
        }
        if (inletWetness < 0.0 || inletWetness >= 1.0
                || outletWetness < 0.0 || outletWetness >= 1.0) {
            throw new IllegalArgumentException("влажность вне [0, 1)");
        }
        if (nozzleBladeCount < 1 || rotorBladeCount < 1) {
            throw new IllegalArgumentException("числа лопаток должны быть положительными");
        }
    }

    /** Внутренний относительный КПД {@code η_oi}; краткая форма для отчётов. */
    public double efficiency() {
        return internalEfficiency;
    }

    /** Внутренняя мощность; краткая форма для отчётов. */
    public double powerKW() {
        return internalPowerKW;
    }

    /** Использованный теплоперепад {@code h_i}; краткая форма для отчётов. */
    public double internalWorkKJkg() {
        return usedHeatDropKJkg;
    }

    /** Строка раздела 3.2/3.3 для протокола. */
    public String describe() {
        return String.format(Locale.ROOT,
                "3.x %s (№%d): d = %.3f м, l₂ = %.4f м, h₀ = %.2f кДж/кг, "
                        + "h₀* = %.2f кДж/кг, C₀ = %.2f м/с, ρ = %.3f, "
                        + "C₁ = %.2f м/с, W₂ = %.2f м/с, C₂ = %.2f м/с, "
                        + "E₀ = %.2f кДж/кг, η_ол = %.4f, h_i = %.2f кДж/кг, "
                        + "η_oi = %.4f, N_i = %.1f кВт",
                name, globalNumber, meanDiameterM, bladeHeightM, heatDropKJkg,
                stagnationHeatDropKJkg, inletVelocityMS, reaction,
                nozzleActualVelocityMS, rotorActualVelocityMS,
                rotorExitAbsoluteVelocityMS, availableEnergyKJkg, profileEfficiency,
                usedHeatDropKJkg, internalEfficiency, internalPowerKW);
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " должен быть положительным: " + value);
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!(value >= 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " не может быть отрицательной: " + value);
        }
    }

    private static void requireInterval(double value, String name) {
        if (!(value > 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " вне (0, 1): " + value);
        }
    }
}
