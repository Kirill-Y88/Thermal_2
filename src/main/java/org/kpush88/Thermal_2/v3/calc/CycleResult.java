package org.kpush88.Thermal_2.v3.calc;

import java.util.List;

/**
 * Показатели цикла: мощности, теплоты и КПД по найденным расходам и энтальпиям.
 *
 * <p>Ни одна из этих величин не является состоянием сечения, поэтому в журнал они не пишутся:
 * журнал хранит давление, энтальпию и расход, а показатели из них выводятся. Зато в отчёте
 * остаются и заданная мощность, и полученная: расчёт цикла — это подбор расхода свежего пара под
 * заданную мощность, и совпадение этих двух чисел и есть его результат
 * ({@link #deviation()} — относительное расхождение).
 *
 * <p><b>КПД — по внутренней мощности</b>, как в прототипе {@code v1.cycle.CycleResult}:
 * {@code η_брутто = N_i/Q_котла}, {@code η_нетто = (N_i − N_н)/Q_котла}. Электрическая мощность
 * получается из внутренней умножением на КПД турбоагрегата, поэтому «электрический КПД» отличается
 * от брутто ровно на этот множитель; смешивать их в одной таблице без подписи нельзя, и здесь
 * выбран тот же смысл, что в v1, чтобы числа были сравнимы с реперными.
 *
 * <p><b>Тождество баланса.</b> {@link #balanceResidual()} = {@code Q_котла + N_н − N_i − Q_к}
 * обязано быть нулём: теплота, подведённая в котле, и работа насосов уходят в работу турбины и
 * теплоту конденсатора. Ненулевая невязка означает ошибку в модели, а не в данных, — поэтому она
 * не «допуск», а проверка.
 */
public record CycleResult(double electricPower,
                          double calculatedElectricPower,
                          double internalPower,
                          double pumpPower,
                          double boilerDuty,
                          double condenserDuty,
                          double grossEfficiency,
                          double netEfficiency,
                          double liveSteamFlow,
                          double regenerationCoefficient,
                          double deviation,
                          int iterations,
                          List<Iteration> history,
                          CalculationJournal journal) {

    public CycleResult {
        history = List.copyOf(history);
    }

    /** Невязка энергетического баланса цикла: {@code Q_котла + N_н − N_i − Q_к}. */
    public double balanceResidual() {
        return boilerDuty + pumpPower - internalPower - condenserDuty;
    }

    /** Удельный расход теплоты на выработку электроэнергии, кДж/(кВт·ч). */
    public double heatRate() {
        return 3600.0 * boilerDuty / calculatedElectricPower;
    }

    /** Многострочный отчёт: итерации, мощности, теплоты, КПД и проверка баланса. */
    public String describe() {
        StringBuilder text = new StringBuilder("Расчёт цикла: ")
                .append(iterations).append(" внешних итераций, k_рег = ")
                .append(regenerationCoefficient).append(", σ = ")
                .append(percent(deviation));
        for (Iteration iteration : history) {
            text.append(System.lineSeparator()).append("  ").append(iteration.describe());
        }
        text.append(System.lineSeparator())
                .append("  расход свежего пара D₀ = ").append(liveSteamFlow).append(" кг/с")
                .append(System.lineSeparator())
                .append("  внутренняя мощность N_i = ").append(internalPower).append(" кВт")
                .append(System.lineSeparator())
                .append("  электрическая мощность N_э = ").append(calculatedElectricPower)
                .append(" кВт (задано ").append(electricPower).append(" кВт)")
                .append(System.lineSeparator())
                .append("  мощность насосов N_н = ").append(pumpPower).append(" кВт")
                .append(System.lineSeparator())
                .append("  теплота котла Q_котла = ").append(boilerDuty).append(" кВт")
                .append(System.lineSeparator())
                .append("  теплота конденсатора Q_к = ").append(condenserDuty).append(" кВт")
                .append(System.lineSeparator())
                .append("  КПД брутто = ").append(percent(grossEfficiency))
                .append(", КПД нетто = ").append(percent(netEfficiency))
                .append(System.lineSeparator())
                .append("  невязка баланса Q_котла + N_н − N_i − Q_к = ").append(balanceResidual())
                .append(" кВт");
        return text.toString();
    }

    @Override
    public String toString() {
        return describe();
    }

    private static String percent(double fraction) {
        return Math.round(fraction * 10000.0) / 100.0 + " %";
    }

    /**
     * Одна внешняя итерация: коэффициент регенерации, получившийся расход, мощность и
     * расхождение с заданием.
     */
    public record Iteration(int number, double regenerationCoefficient, double liveSteamFlow,
                            double internalPower, double electricPower, double deviation) {

        /** Подпись «итерация 1: k_рег = 1.00000, D₀ = 1.00000 кг/с, N_э = 1005.3 кВт, σ = −3.4 %». */
        public String describe() {
            return "итерация " + number + ": k_рег = " + regenerationCoefficient
                    + ", D₀ = " + liveSteamFlow + " кг/с, N_э = " + electricPower + " кВт, σ = "
                    + Math.round(deviation * 10000.0) / 100.0 + " %";
        }
    }
}
