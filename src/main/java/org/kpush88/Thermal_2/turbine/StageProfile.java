package org.kpush88.Thermal_2.turbine;

import java.util.Locale;

/**
 * Профиль ступени: углы решёток, реакция, скоростные коэффициенты и коэффициенты потерь.
 *
 * <p>Профиль — то, что в методичке задаётся таблицей для типа ступени, а не для каждой ступени
 * отдельно: перегретый пар в ЦВД, влажный в ЦНД, короткие лопатки первых ступеней. Поэтому
 * профиль живёт отдельным объектом и может повторяться у десятка ступеней, а геометрия
 * ({@link StageGeometry}) отвечает только за размеры: диаметр, высоту, парциальность.
 *
 * <p><b>Углы.</b> {@code nozzleAngle} — угол выхода потока из сопловой решётки α₁, отсчитанный
 * от окружного направления; типично 11…16° (малые углы дают большую окружную составляющую
 * скорости, но растягивают лопатку). {@code rotorAngle} — конструктивный угол выхода рабочей
 * решётки β₂, отсчитанный от направления, <b>обратного</b> окружной скорости: пар выходит из
 * каналов колеса против вращения и именно поэтому отдаёт энергию валу. Соглашение об отсчёте
 * зафиксировано здесь, потому что от него зависит знак окружной составляющей в треугольнике
 * скоростей.
 *
 * <p><b>Потери.</b> Профильные потери уже сидят в скоростных коэффициентах φ и ψ: они
 * уменьшают скорости {@code c₁ = φ·c₁s} и {@code w₂ = ψ·w₂s}, поэтому отдельного слагаемого
 * «потери профиля» нет. Остальные три вида потерь считаются долями от располагаемого перепада
 * ступени и вводятся коэффициентами:
 * <pre>
 *     Δh_вт  = ζ_вт  · (b/l) · H,      вторичные, растут с укорочением лопатки;
 *     Δh_упл = ζ_упл · (δ/l) · H,      утечки через радиальный зазор, растут так же;
 *     Δh_вл  = ζ_вл  · (1 − x̄) · H,    влажностные, растут с влажностью.
 * </pre>
 * Все три — инженерные оценки, а не точные формулы: в литературе коэффициенты гуляют в разы
 * (для влажностных потерь встречается и 0.3, и 1.0). Поэтому они вынесены в профиль и доступны
 * калибровке, а не спрятаны в код расчёта. Значения по умолчанию подобраны так, чтобы ступень
 * средней длины (l ≈ 0.2 м, b/l ≈ 0.3) отдавала 88…91 % располагаемого перепада — это
 * соответствует η_oi отсека 0.85 при пяти-шести ступенях с возвратом тепла.
 *
 * <p>Класс неизменяемый.
 */
public final class StageProfile {

    private final double nozzleAngle;
    private final double rotorAngle;
    private final double reaction;
    private final double nozzleVelocity;
    private final double rotorVelocity;
    private final double discharge;
    private final double chord;
    private final double clearance;
    private final double secondaryLoss;
    private final double leakageLoss;
    private final double wetnessLoss;

    private StageProfile(Builder builder) {
        this.nozzleAngle = builder.nozzleAngle;
        this.rotorAngle = builder.rotorAngle;
        this.reaction = builder.reaction;
        this.nozzleVelocity = builder.nozzleVelocity;
        this.rotorVelocity = builder.rotorVelocity;
        this.discharge = builder.discharge;
        this.chord = builder.chord;
        this.clearance = builder.clearance;
        this.secondaryLoss = builder.secondaryLoss;
        this.leakageLoss = builder.leakageLoss;
        this.wetnessLoss = builder.wetnessLoss;
    }

    /** Профиль по умолчанию: активная ступень средней длины с малой реакцией. */
    public static StageProfile defaults() {
        return builder().build();
    }

    /** Сборщик профиля: начинает со значений по умолчанию, меняется только заданное. */
    public static Builder builder() {
        return new Builder();
    }

    /** Угол выхода из сопловой решётки α₁, град. */
    public double nozzleAngle() {
        return nozzleAngle;
    }

    /** Конструктивный угол выхода рабочей решётки β₂ (от обратного направления u), град. */
    public double rotorAngle() {
        return rotorAngle;
    }

    /** Степень реакции ρ. */
    public double reaction() {
        return reaction;
    }

    /** Скоростной коэффициент сопловой решётки φ. */
    public double nozzleVelocity() {
        return nozzleVelocity;
    }

    /** Скоростной коэффициент рабочей решётки ψ. */
    public double rotorVelocity() {
        return rotorVelocity;
    }

    /** Коэффициент расхода μ для уравнения пропускной способности. */
    public double discharge() {
        return discharge;
    }

    /** Хорда профиля b, м. */
    public double chord() {
        return chord;
    }

    /** Радиальный зазор δ, м. */
    public double clearance() {
        return clearance;
    }

    /**
     * Копия профиля с другим радиальным зазором δ.
     *
     * <p>Единственная изменяемая величина — зазор: он растёт от износа уплотнений, и именно его
     * меняет диагностический поиск причины падения мощности. Остальные коэффициенты переносятся
     * без изменений; диапазон δ проверяется в {@link Builder#build()}.
     *
     * @param clearance радиальный зазор δ, м (неотрицательный)
     */
    public StageProfile withClearance(double clearance) {
        return builder()
                .nozzleAngle(nozzleAngle)
                .rotorAngle(rotorAngle)
                .reaction(reaction)
                .nozzleVelocity(nozzleVelocity)
                .rotorVelocity(rotorVelocity)
                .discharge(discharge)
                .chord(chord)
                .clearance(clearance)
                .secondaryLoss(secondaryLoss)
                .leakageLoss(leakageLoss)
                .wetnessLoss(wetnessLoss)
                .build();
    }

    /** Коэффициент вторичных потерь ζ_вт (доля от (b/l)·H). */
    public double secondaryLoss() {
        return secondaryLoss;
    }

    /** Коэффициент утечек ζ_упл (доля от (δ/l)·H). */
    public double leakageLoss() {
        return leakageLoss;
    }

    /** Коэффициент влажностных потерь ζ_вл (доля от (1 − x̄)·H). */
    public double wetnessLoss() {
        return wetnessLoss;
    }

    /** Строка профиля для протокола. */
    public String describe() {
        return String.format(Locale.ROOT,
                "α₁=%.1f°, β₂=%.1f°, ρ=%.3f, φ=%.3f, ψ=%.3f, μ=%.3f, b=%.3f м, δ=%.4f м",
                nozzleAngle, rotorAngle, reaction, nozzleVelocity, rotorVelocity, discharge,
                chord, clearance);
    }

    /** Сборщик профиля с проверками при {@link #build()}. */
    public static final class Builder {

        private double nozzleAngle = 12.0;
        private double rotorAngle = 24.0;
        private double reaction = 0.08;
        private double nozzleVelocity = 0.96;
        private double rotorVelocity = 0.94;
        private double discharge = 0.96;
        private double chord = 0.06;
        private double clearance = 0.0006;
        private double secondaryLoss = 0.03;
        private double leakageLoss = 1.0;
        private double wetnessLoss = 0.40;

        private Builder() {
        }

        /** Угол выхода из сопловой решётки α₁, град (5…40). */
        public Builder nozzleAngle(double degrees) {
            this.nozzleAngle = degrees;
            return this;
        }

        /** Конструктивный угол выхода рабочей решётки β₂, град (5…45). */
        public Builder rotorAngle(double degrees) {
            this.rotorAngle = degrees;
            return this;
        }

        /** Степень реакции ρ (0…0.6). */
        public Builder reaction(double value) {
            this.reaction = value;
            return this;
        }

        /** Скоростной коэффициент сопловой решётки φ (0.7…1). */
        public Builder nozzleVelocity(double value) {
            this.nozzleVelocity = value;
            return this;
        }

        /** Скоростной коэффициент рабочей решётки ψ (0.7…1). */
        public Builder rotorVelocity(double value) {
            this.rotorVelocity = value;
            return this;
        }

        /** Коэффициент расхода μ (0…1]. */
        public Builder discharge(double value) {
            this.discharge = value;
            return this;
        }

        /** Хорда профиля b, м. */
        public Builder chord(double metres) {
            this.chord = metres;
            return this;
        }

        /** Радиальный зазор δ, м. */
        public Builder clearance(double metres) {
            this.clearance = metres;
            return this;
        }

        /** Коэффициент вторичных потерь ζ_вт. */
        public Builder secondaryLoss(double value) {
            this.secondaryLoss = value;
            return this;
        }

        /** Коэффициент утечек ζ_упл. */
        public Builder leakageLoss(double value) {
            this.leakageLoss = value;
            return this;
        }

        /** Коэффициент влажностных потерь ζ_вл. */
        public Builder wetnessLoss(double value) {
            this.wetnessLoss = value;
            return this;
        }

        /**
         * Собирает профиль и проверяет диапазоны.
         *
         * <p>Проверки не формальность: угол 90° даёт нулевую окружную составляющую и нулевую
         * работу ступени, φ > 1 нарушает второе начало, μ > 1 означает расход больше
         * теоретического. Такая ступень не «плохо считается», а не имеет смысла, поэтому
         * ошибка называется сразу, с величиной и допустимым диапазоном.
         */
        public StageProfile build() {
            requireRange(nozzleAngle, 5.0, 40.0, "угол выхода из сопловой решётки α₁, град");
            requireRange(rotorAngle, 5.0, 45.0, "угол выхода рабочей решётки β₂, град");
            requireRange(reaction, 0.0, 0.6, "степень реакции ρ");
            requireRange(nozzleVelocity, 0.7, 1.0, "скоростной коэффициент φ");
            requireRange(rotorVelocity, 0.7, 1.0, "скоростной коэффициент ψ");
            requireRange(discharge, 0.0, 1.0, "коэффициент расхода μ");
            requirePositive(chord, "хорда профиля b, м");
            if (!(clearance >= 0)) {
                throw new IllegalArgumentException("радиальный зазор δ, м не может быть "
                        + "отрицательным: " + clearance);
            }
            requirePositiveOrZero(secondaryLoss, "коэффициент вторичных потерь ζ_вт");
            requirePositiveOrZero(leakageLoss, "коэффициент утечек ζ_упл");
            requirePositiveOrZero(wetnessLoss, "коэффициент влажностных потерь ζ_вл");
            return new StageProfile(this);
        }

        private static void requireRange(double value, double min, double max, String what) {
            if (!(value >= min && value <= max)) {
                throw new IllegalArgumentException(what + " = " + value + " вне диапазона ["
                        + min + ", " + max + "]");
            }
        }

        private static void requirePositive(double value, String what) {
            if (!(value > 0)) {
                throw new IllegalArgumentException(what + " должно быть положительным: " + value);
            }
        }

        private static void requirePositiveOrZero(double value, String what) {
            if (!(value >= 0)) {
                throw new IllegalArgumentException(what + " не может быть отрицательным: "
                        + value);
            }
        }
    }
}
