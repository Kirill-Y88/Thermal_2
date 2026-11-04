package org.kpush88.Thermal_2.turbine2;

import java.util.Locale;

/**
 * Детальный расчёт регулирующей ступени — раздел 3.1 методички
 * {@code university/Курс турб.docx}.
 *
 * <p>Содержит сопловую решётку, первый рабочий венец, поворотную решётку и второй рабочий
 * венец: треугольники скоростей, числа Маха, площади, высоты, хорды, шаги, числа лопаток и
 * потери. По ним находятся лопаточный КПД, использованный теплоперепад и мощность.
 *
 * <p>{@link #profileEfficiency()} учитывает потери в решётках и с выходной скоростью;
 * {@link #internalEfficiency()} дополнительно учитывает трение диска, парциальность и утечки.
 *
 * <p><b>Опечатка в методичке.</b> Для поворотной решётки напечатано
 * {@code C₁t = √(2·3.5·10³) = 240.9} м/с, хотя {@code √7000 = 83.67} м/с. Значение
 * {@code 240.9} соответствует перепаду {@code 29} кДж/кг. Здесь считается формула.
 *
 * <p>Единицы: скорость — м/с, угол — градусы, давление — МПа, объём — м³/кг, площадь — м²,
 * длина — м, теплоперепад и потери — кДж/кг, мощность — кВт.
 */
public record RegulatingStageDetail(
        double heatDropKJkg,
        double meanDiameterM,
        double circumferentialVelocityMS,
        double admission,
        double nozzleHeatDropKJkg,
        double firstRotorHeatDropKJkg,
        double turningHeatDropKJkg,
        double secondRotorHeatDropKJkg,
        double exitVelocityMS,
        double exitLossKJkg,
        double availableEnergyKJkg,
        double profileEfficiency,
        double frictionLossKJkg,
        double partialLossKJkg,
        double sealLeakLossKJkg,
        double usedHeatDropKJkg,
        double internalEfficiency,
        double internalPowerKW,
        NozzleRow nozzle,
        RotorRow firstRotor,
        TurningRow turning,
        RotorRow secondRotor) {

    public RegulatingStageDetail {
        if (nozzle == null || firstRotor == null || turning == null || secondRotor == null) {
            throw new IllegalArgumentException("детальный расчёт регулирующей ступени неполон");
        }
        requirePositive(heatDropKJkg, "перепад h₀^рс");
        requirePositive(meanDiameterM, "средний диаметр");
        requirePositive(circumferentialVelocityMS, "окружная скорость");
        requirePositive(admission, "степень парциальности");
        requirePositive(nozzleHeatDropKJkg, "перепад сопловой решётки");
        requirePositive(firstRotorHeatDropKJkg, "перепад первого венца");
        requirePositive(turningHeatDropKJkg, "перепад поворотной решётки");
        requirePositive(secondRotorHeatDropKJkg, "перепад второго венца");
        requireNonNegative(exitVelocityMS, "выходная скорость");
        requireNonNegative(exitLossKJkg, "потеря с выходной скоростью");
        requirePositive(availableEnergyKJkg, "располагаемая энергия E₀");
        requireInterval(profileEfficiency, "лопаточный КПД η_ол");
        requireInterval(internalEfficiency, "внутренний КПД η_oi");
        requireNonNegative(frictionLossKJkg, "потеря на трение");
        requireNonNegative(partialLossKJkg, "потеря от парциальности");
        requireNonNegative(sealLeakLossKJkg, "потеря от утечек");
        requirePositive(usedHeatDropKJkg, "использованный теплоперепад");
        requirePositive(internalPowerKW, "внутренняя мощность");
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

    /** Сводка раздела 3.1 для протокола. */
    public String describe() {
        return String.format(Locale.ROOT,
                "3.1: d = %.3f м, U = %.2f м/с, e = %.3f, C₁ = %.2f м/с, "
                        + "C₂ = %.2f м/с, E₀ = %.2f кДж/кг, η_ол = %.4f, "
                        + "h_i = %.2f кДж/кг, η_oi = %.4f, N_i = %.1f кВт",
                meanDiameterM, circumferentialVelocityMS, admission,
                nozzle.actualVelocityMS(), secondRotor.exitAbsoluteVelocityMS(),
                availableEnergyKJkg, profileEfficiency, usedHeatDropKJkg,
                internalEfficiency, internalPowerKW);
    }

    /**
     * Сопловая решётка.
     *
     * @param theoreticalVelocityMS {@code C₁t}, м/с
     * @param actualVelocityMS      {@code C₁}, м/с
     * @param exitAngleDeg          {@code α₁}, °
     * @param mach                  {@code M₁t}
     * @param pressureMPa           {@code P₁t}, МПа
     * @param volumeM3kg            {@code V₁t}, м³/кг
     * @param areaM2                {@code F₁}, м²
     * @param heightM               {@code l₁}, м
     * @param chordM                хорда, м
     * @param pitchM                шаг, м
     * @param bladeCount            число лопаток
     * @param lossKJkg              потеря, кДж/кг
     * @param flowCoefficient       {@code μ₁}
     * @param speedCoefficient      {@code φ}
     */
    public record NozzleRow(
            double theoreticalVelocityMS,
            double actualVelocityMS,
            double exitAngleDeg,
            double mach,
            double pressureMPa,
            double volumeM3kg,
            double areaM2,
            double heightM,
            double chordM,
            double pitchM,
            int bladeCount,
            double lossKJkg,
            double flowCoefficient,
            double speedCoefficient) {

        public NozzleRow {
            requirePositive(theoreticalVelocityMS, "теоретическая скорость сопла");
            requirePositive(actualVelocityMS, "действительная скорость сопла");
            requirePositive(mach, "число Маха сопла");
            requirePositive(pressureMPa, "давление за соплом");
            requirePositive(volumeM3kg, "объём за соплом");
            requirePositive(areaM2, "площадь сопла");
            requirePositive(heightM, "высота сопла");
            requirePositive(chordM, "хорда сопла");
            requirePositive(pitchM, "шаг сопла");
            if (bladeCount < 1) {
                throw new IllegalArgumentException("число сопловых лопаток должно быть "
                        + "положительным: " + bladeCount);
            }
            requireNonNegative(lossKJkg, "потеря в сопле");
        }
    }

    /**
     * Рабочая решётка первого или второго венца.
     *
     * @param heatDropKJkg           перепад решётки, кДж/кг
     * @param inletVelocityMS        {@code W₁}, м/с
     * @param inletAngleDeg          {@code β₁}, °
     * @param theoreticalVelocityMS  {@code W₂t}, м/с
     * @param actualVelocityMS       {@code W₂}, м/с
     * @param exitAngleDeg           {@code β₂}, °
     * @param exitAbsoluteVelocityMS {@code C₂}, м/с
     * @param exitAbsoluteAngleDeg   {@code α₂}, °
     * @param mach                   {@code M₂t}
     * @param pressureMPa            давление за решёткой, МПа
     * @param volumeM3kg             объём за решёткой, м³/кг
     * @param areaM2                 выходная площадь, м²
     * @param heightM                высота лопатки, м
     * @param chordM                 хорда, м
     * @param pitchM                 шаг, м
     * @param bladeCount             число лопаток
     * @param lossKJkg               потеря, кДж/кг
     * @param flowCoefficient        {@code μ₂}
     * @param speedCoefficient       {@code ψ}
     */
    public record RotorRow(
            double heatDropKJkg,
            double inletVelocityMS,
            double inletAngleDeg,
            double theoreticalVelocityMS,
            double actualVelocityMS,
            double exitAngleDeg,
            double exitAbsoluteVelocityMS,
            double exitAbsoluteAngleDeg,
            double mach,
            double pressureMPa,
            double volumeM3kg,
            double areaM2,
            double heightM,
            double chordM,
            double pitchM,
            int bladeCount,
            double lossKJkg,
            double flowCoefficient,
            double speedCoefficient) {

        public RotorRow {
            requirePositive(heatDropKJkg, "перепад рабочей решётки");
            requirePositive(inletVelocityMS, "относительная скорость входа");
            requirePositive(theoreticalVelocityMS, "теоретическая относительная скорость");
            requirePositive(actualVelocityMS, "действительная относительная скорость");
            requirePositive(exitAbsoluteVelocityMS, "абсолютная скорость выхода");
            requirePositive(mach, "число Маха рабочей решётки");
            requirePositive(pressureMPa, "давление за рабочей решёткой");
            requirePositive(volumeM3kg, "объём за рабочей решёткой");
            requirePositive(areaM2, "площадь рабочей решётки");
            requirePositive(heightM, "высота рабочей лопатки");
            requirePositive(chordM, "хорда рабочей решётки");
            requirePositive(pitchM, "шаг рабочей решётки");
            if (bladeCount < 1) {
                throw new IllegalArgumentException("число рабочих лопаток должно быть "
                        + "положительным: " + bladeCount);
            }
            requireNonNegative(lossKJkg, "потеря в рабочей решётке");
        }
    }

    /**
     * Поворотная решётка. Геометрия и треугольники те же, что у рабочей решётки, но
     * теплоперепад идёт на поворот потока, а не на рабочее колесо.
     *
     * @param heatDropKJkg          перепад, кДж/кг
     * @param inletVelocityMS       {@code W₁}, м/с
     * @param inletAngleDeg         {@code β₁}, °
     * @param theoreticalVelocityMS {@code C₁t}, м/с
     * @param actualVelocityMS      {@code C₁}, м/с
     * @param exitAngleDeg          {@code α₁}, °
     * @param mach                  {@code M₁t}
     * @param pressureMPa           давление за решёткой, МПа
     * @param volumeM3kg            объём за решёткой, м³/кг
     * @param areaM2                выходная площадь, м²
     * @param heightM               высота, м
     * @param chordM                хорда, м
     * @param pitchM                шаг, м
     * @param bladeCount            число лопаток
     * @param lossKJkg              потеря, кДж/кг
     * @param flowCoefficient       {@code μ₁}
     * @param speedCoefficient      {@code φ}
     */
    public record TurningRow(
            double heatDropKJkg,
            double inletVelocityMS,
            double inletAngleDeg,
            double theoreticalVelocityMS,
            double actualVelocityMS,
            double exitAngleDeg,
            double mach,
            double pressureMPa,
            double volumeM3kg,
            double areaM2,
            double heightM,
            double chordM,
            double pitchM,
            int bladeCount,
            double lossKJkg,
            double flowCoefficient,
            double speedCoefficient) {

        public TurningRow {
            requirePositive(heatDropKJkg, "перепад поворотной решётки");
            requirePositive(inletVelocityMS, "относительная скорость входа");
            requirePositive(theoreticalVelocityMS, "теоретическая скорость поворотной решётки");
            requirePositive(actualVelocityMS, "действительная скорость поворотной решётки");
            requirePositive(mach, "число Маха поворотной решётки");
            requirePositive(pressureMPa, "давление за поворотной решёткой");
            requirePositive(volumeM3kg, "объём за поворотной решёткой");
            requirePositive(areaM2, "площадь поворотной решётки");
            requirePositive(heightM, "высота поворотной решётки");
            requirePositive(chordM, "хорда поворотной решётки");
            requirePositive(pitchM, "шаг поворотной решётки");
            if (bladeCount < 1) {
                throw new IllegalArgumentException("число поворотных лопаток должно быть "
                        + "положительным: " + bladeCount);
            }
            requireNonNegative(lossKJkg, "потеря в поворотной решётке");
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

    private static void requireInterval(double value, String name) {
        if (!(value > 0.0 && value < 1.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " вне (0, 1): " + value);
        }
    }
}
