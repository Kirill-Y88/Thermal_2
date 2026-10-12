package org.kpush88.Thermal_2.v1.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Глобальная система балансовых уравнений — результат <b>сборки</b> локальных вкладов
 * элементов (паттерн Matrix Assembly, как сборка глобальной матрицы в МКЭ).
 *
 * <p>Каждый элемент вызывает {@link #equationFor(Object, String)},
 * {@link #term(int, Variable, double)} и {@link #constant(int, double)}. Одинаковые
 * ячейки (строка, столбец) суммируются. Число строк определяется числом объявленных
 * уравнений, число столбцов — реестром неизвестных, поэтому при добавлении элемента
 * размер системы растёт автоматически.
 *
 * <p>Соглашение о знаках: уравнение приводится к виду {@code Σ coeff·x = rhs}, где
 * {@code term(...)} добавляет коэффициент в <b>левую</b> часть, а
 * {@code constant(...)} — в <b>правую</b>.
 */
public final class EquationSystem {

    private final UnknownRegistry registry;

    private final Map<Object, Map<String, Integer>> rowsByOwner = new IdentityHashMap<>();
    private final List<String> rowLabels = new ArrayList<>();
    private final Map<Long, Double> coefficients = new HashMap<>();
    private final List<Double> rhs = new ArrayList<>();

    public EquationSystem(UnknownRegistry registry) {
        this.registry = registry;
    }

    /**
     * Возвращает (создавая при первом обращении) номер строки уравнения, принадлежащего
     * элементу {@code owner} и помеченного строковым {@code kind}. Позволяет одному
     * элементу вносить несколько разных уравнений (например, «баланс массы» и
     * «тепловой баланс»). Сопоставление по идентичности владельца — детерминированный
     * адрес строки.
     */
    public int equationFor(Object owner, String kind) {
        Map<String, Integer> byKind = rowsByOwner.computeIfAbsent(owner, o -> new LinkedHashMap<>());
        return byKind.computeIfAbsent(kind, k -> {
            rowLabels.add(label(owner, k));
            rhs.add(0.0);
            return rowLabels.size() - 1;
        });
    }

    /** Добавляет коэффициент при переменной в левую часть уравнения. */
    public void term(int row, Variable variable, double coefficient) {
        if (coefficient == 0.0) {
            return;
        }
        int col = registry.indexOf(variable);
        coefficients.merge(key(row, col), coefficient, Double::sum);
    }

    /** Добавляет слагаемое в правую часть уравнения. */
    public void constant(int row, double value) {
        rhs.set(row, rhs.get(row) + value);
    }

    public int equationCount() {
        return rowLabels.size();
    }

    public int variableCount() {
        return registry.size();
    }

    public List<String> equationLabels() {
        return List.copyOf(rowLabels);
    }

    /** Собирает матрицу {@code A} и вектор {@code b}, решает СЛАУ указанным решателем. */
    public double[] solve(LinearSolver solver) {
        int n = equationCount();
        if (n != variableCount()) {
            throw new IllegalStateException(
                    "Система не квадратная: " + n + " уравнений, " + variableCount() + " неизвестных");
        }
        double[][] a = new double[n][n];
        double[] b = new double[n];
        for (Map.Entry<Long, Double> e : coefficients.entrySet()) {
            int r = (int) (e.getKey() >> 32);
            int c = (int) (e.getKey() & 0xffffffffL);
            a[r][c] = e.getValue();
        }
        for (int r = 0; r < n; r++) {
            b[r] = rhs.get(r);
        }
        return solver.solve(a, b);
    }

    private static String label(Object owner, String kind) {
        String ownerName = (owner instanceof Element element) ? element.name() : String.valueOf(owner);
        return ownerName + " / " + kind;
    }

    private static long key(int row, int col) {
        return ((long) row << 32) | (col & 0xffffffffL);
    }
}
