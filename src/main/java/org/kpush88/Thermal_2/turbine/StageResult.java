package org.kpush88.Thermal_2.turbine;

/**
 * Результат расчёта одной ступени при заданном расходе.
 *
 * <p>Запись — не «отчёт», а рабочее состояние ступени: по ней марш переходит к следующей ступени
 * ({@code outletPressure}, {@code outletEnthalpy}) и по ней же видно, что со ступенью не так
 * ({@code choked}, {@code velocityRatio}, доли потерь).
 *
 * <p><b>Что означает {@code internalWork}.</b> Это работа на окружности колеса, отданная валу,
 * за вычетом эмпирических потерь (вторичных, утечек, влажностных). Энтальпия за ступенью
 * получается из баланса энергии:
 * <pre>
 *     h₂ = h₁ − W − c₂²/2,
 * </pre>
 * то есть кинетическая энергия выхлопа из ступени <b>не возвращается</b> в работу: она остаётся
 * в потоке и уходит либо в следующую ступень (где расчёт её не подхватывает — упрощение, о
 * котором сказано в {@link StageSolver}), либо в конденсатор. Поэтому
 * {@code isentropicDrop − internalWork} — это и есть все потери ступени, включая выходную
 * скорость, и отдельного слагаемого «потери с выходной скоростью» нет.
 *
 * @param inletPressure        давление перед ступенью, МПа
 * @param inletEnthalpy        энтальпия перед ступенью, кДж/кг
 * @param outletPressure       давление за ступенью, МПа
 * @param outletEnthalpy       энтальпия за ступенью, кДж/кг
 * @param outletQuality        степень сухости за ступенью (1 — перегретый пар)
 * @param isentropicDrop       располагаемый перепад {@code h₁ − h(p₂, s₁)}, кДж/кг
 * @param internalWork         внутренняя работа ступени, кДж/кг
 * @param efficiency           η_oi ступени {@code internalWork / isentropicDrop}
 * @param velocity             скорость {@code c₁} на выходе из сопловой решётки, м/с
 * @param exitVelocity         скорость {@code c₂} на выходе из ступени, м/с
 * @param circumferentialSpeed окружная скорость {@code u}, м/с
 * @param velocityRatio        {@code u/c₁}
 * @param secondaryLoss        вторичные потери, кДж/кг
 * @param leakageLoss          потери на утечки через радиальный зазор, кДж/кг
 * @param wetnessLoss          влажностные потери, кДж/кг
 * @param choked               заперта ли сопловая решётка (β ниже критического)
 */
public record StageResult(
        double inletPressure,
        double inletEnthalpy,
        double outletPressure,
        double outletEnthalpy,
        double outletQuality,
        double isentropicDrop,
        double internalWork,
        double efficiency,
        double velocity,
        double exitVelocity,
        double circumferentialSpeed,
        double velocityRatio,
        double secondaryLoss,
        double leakageLoss,
        double wetnessLoss,
        boolean choked) {

    /**
     * Проверки, которые обязаны выполняться для любой ступени.
     *
     * <p>Это не защита от пользователя (параметры пришли из расчёта), а защита от ошибки в
     * самом расчёте: если работа больше располагаемого перепада или давление за ступенью не
     * упало, то треугольники скоростей и уравнение расхода разошлись, и молча возвращать такое
     * число нельзя.
     */
    public StageResult {
        if (!(inletPressure > outletPressure) || !(outletPressure > 0)) {
            throw new IllegalArgumentException("давление за ступенью " + outletPressure
                    + " МПа должно быть положительным и ниже давления перед ней "
                    + inletPressure + " МПа");
        }
        if (!(isentropicDrop > 0)) {
            throw new IllegalArgumentException("располагаемый перепад ступени должен быть "
                    + "положительным: " + isentropicDrop + " кДж/кг");
        }
        if (!(internalWork > 0 && internalWork <= isentropicDrop)) {
            throw new IllegalArgumentException("внутренняя работа " + internalWork
                    + " кДж/кг вне диапазона (0, " + isentropicDrop
                    + "]: треугольники скоростей не сходятся с располагаемым перепадом");
        }
    }

    /** Сумма учтённых потерь ступени, кДж/кг. */
    public double totalLoss() {
        return isentropicDrop - internalWork;
    }

    /** Учтённые эмпирические потери (без выходной скорости), кДж/кг. */
    public double empiricalLoss() {
        return secondaryLoss + leakageLoss + wetnessLoss;
    }

    /** Мощность ступени при расходе {@code flow}, кВт. */
    public double power(double flow) {
        return internalWork * flow;
    }
}
