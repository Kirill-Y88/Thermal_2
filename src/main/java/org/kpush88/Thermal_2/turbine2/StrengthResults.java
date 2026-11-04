package org.kpush88.Thermal_2.turbine2;

import java.util.Locale;

/**
 * Прочностные расчёты — разделы 4.1–4.3 методички {@code university/Курс турб.docx}.
 *
 * <p><b>4.1.</b> Осевое усилие считается для первой нерегулируемой ступени и умножается на
 * число ступеней: {@code R_aI} — усилие на рабочие лопатки, {@code R_aII} — на диск с учётом
 * разгрузочных отверстий и давления в корневом зазоре.
 *
 * <p><b>4.2.</b> Для последней ступени проверяются растяжение от центробежных сил и изгиб от
 * окружного и осевого усилий потока. Суммарное напряжение сравнивается с пределом текучести.
 *
 * <p><b>4.3.</b> Для диафрагмы первой нерегулируемой ступени считаются максимальное напряжение
 * и прогиб от перепада давления.
 *
 * <p>Единицы: сила — Н, напряжение — МПа, давление — МПа, прогиб — м.
 *
 * @param axialForce    расчёт 4.1
 * @param blade         расчёт 4.2
 * @param diaphragm     расчёт 4.3
 * @param stageCount    число нерегулируемых ступеней
 */
public record StrengthResults(
        AxialForce axialForce,
        BladeStrength blade,
        DiaphragmStrength diaphragm,
        int stageCount) {

    public StrengthResults {
        if (axialForce == null || blade == null || diaphragm == null) {
            throw new IllegalArgumentException("прочностные расчёты неполны");
        }
        if (stageCount < 1) {
            throw new IllegalArgumentException("число ступеней должно быть положительным: "
                    + stageCount);
        }
    }

    /** Сводка разделов 4.1–4.3 для протокола. */
    public String describe() {
        return String.format(Locale.ROOT,
                "4.1: R_aI = %.1f Н, R_aII = %.1f Н, R_a = %.1f Н, "
                        + "R_aΣ = %.2f кН (%d ступеней)%n"
                        + "4.2: σ_раст = %.2f МПа, σ_изг = %.2f МПа, σ = %.2f МПа, "
                        + "K = %.3f%n"
                        + "4.3: ΔP = %.3f МПа, σ = %.2f МПа, Δ = %.6f м, K = %.2f",
                axialForce.bladeForceN(), axialForce.discForceN(),
                axialForce.totalForceN(), axialForce.totalRotorForceN() / 1000.0,
                stageCount, blade.tensileStressMPa(), blade.bendingStressMPa(),
                blade.totalStressMPa(), blade.safetyFactor(),
                diaphragm.pressureDifferenceMPa(), diaphragm.maximumStressMPa(),
                diaphragm.deflectionM(), diaphragm.safetyFactor());
    }

    /**
     * Расчёт 4.1 — осевое усилие.
     *
     * @param bladeForceN          {@code R_aI}, Н
     * @param discForceN           {@code R_aII}, Н
     * @param totalForceN          {@code R_a = R_aI + R_aII}, Н
     * @param totalRotorForceN     {@code R_aΣ = R_a·z}, Н
     * @param pressureDifferenceMPa {@code P₁ − P₂}, МПа
     * @param reliefAreaM2         площадь разгрузочных отверстий, м²
     * @param clearanceAreaM2      площадь корневого зазора, м²
     * @param pressureRatioAlpha   {@code α = μ_рF_р/(μ_кF_к)}
     * @param pressureRatioBeta    {@code β}
     * @param pressureFactor       коэффициент {@code k}
     */
    public record AxialForce(
            double bladeForceN,
            double discForceN,
            double totalForceN,
            double totalRotorForceN,
            double pressureDifferenceMPa,
            double reliefAreaM2,
            double clearanceAreaM2,
            double pressureRatioAlpha,
            double pressureRatioBeta,
            double pressureFactor) {

        public AxialForce {
            requirePositive(bladeForceN, "осевое усилие на лопатки");
            requirePositive(discForceN, "осевое усилие на диск");
            requirePositive(totalForceN, "суммарное осевое усилие ступени");
            requirePositive(totalRotorForceN, "осевое усилие ротора");
            requirePositive(pressureDifferenceMPa, "перепад давлений");
            requirePositive(reliefAreaM2, "площадь разгрузочных отверстий");
            requirePositive(clearanceAreaM2, "площадь корневого зазора");
            requireNonNegative(pressureRatioAlpha, "коэффициент α");
            requireNonNegative(pressureRatioBeta, "коэффициент β");
            requirePositive(pressureFactor, "коэффициент k");
        }
    }

    /**
     * Расчёт 4.2 — прочность рабочей лопатки последней ступени.
     *
     * @param tensileStressMPa   напряжение растяжения
     * @param circumferentialForceN окружное усилие {@code R_u}, Н
     * @param axialForceN        осевое усилие {@code R_z}, Н
     * @param bendingForceN      равнодействующая {@code R_изг}, Н
     * @param bendingStressMPa   напряжение изгиба
     * @param totalStressMPa     суммарное напряжение
     * @param yieldStrengthMPa   предел текучести
     * @param safetyFactor       коэффициент запаса
     * @param bladeCount         число рабочих лопаток
     * @param chordM             хорда
     * @param pitchM             шаг
     */
    public record BladeStrength(
            double tensileStressMPa,
            double circumferentialForceN,
            double axialForceN,
            double bendingForceN,
            double bendingStressMPa,
            double totalStressMPa,
            double yieldStrengthMPa,
            double safetyFactor,
            int bladeCount,
            double chordM,
            double pitchM) {

        public BladeStrength {
            requirePositive(tensileStressMPa, "напряжение растяжения");
            requirePositive(circumferentialForceN, "окружное усилие");
            requireNonNegative(axialForceN, "осевое усилие лопатки");
            requirePositive(bendingForceN, "изгибающее усилие");
            requirePositive(bendingStressMPa, "напряжение изгиба");
            requirePositive(totalStressMPa, "суммарное напряжение");
            requirePositive(yieldStrengthMPa, "предел текучести");
            requirePositive(safetyFactor, "коэффициент запаса");
            if (bladeCount < 1) {
                throw new IllegalArgumentException("число лопаток должно быть положительным");
            }
            requirePositive(chordM, "хорда лопатки");
            requirePositive(pitchM, "шаг лопатки");
        }
    }

    /**
     * Расчёт 4.3 — прочность диафрагмы первой нерегулируемой ступени.
     *
     * @param pressureDifferenceMPa {@code ΔP = P₀ − P₁}
     * @param maximumStressMPa      максимальное напряжение
     * @param deflectionM           прогиб
     * @param yieldStrengthMPa      предел текучести
     * @param safetyFactor          коэффициент запаса
     * @param outerDiameterM        внешний диаметр
     * @param thicknessM            толщина
     */
    public record DiaphragmStrength(
            double pressureDifferenceMPa,
            double maximumStressMPa,
            double deflectionM,
            double yieldStrengthMPa,
            double safetyFactor,
            double outerDiameterM,
            double thicknessM) {

        public DiaphragmStrength {
            requirePositive(pressureDifferenceMPa, "перепад давления на диафрагме");
            requirePositive(maximumStressMPa, "напряжение диафрагмы");
            requirePositive(deflectionM, "прогиб диафрагмы");
            requirePositive(yieldStrengthMPa, "предел текучести диафрагмы");
            requirePositive(safetyFactor, "коэффициент запаса диафрагмы");
            requirePositive(outerDiameterM, "внешний диаметр диафрагмы");
            requirePositive(thicknessM, "толщина диафрагмы");
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
