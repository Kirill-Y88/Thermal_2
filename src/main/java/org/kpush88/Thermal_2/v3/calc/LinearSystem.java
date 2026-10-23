package org.kpush88.Thermal_2.v3.calc;

import org.ojalgo.matrix.decomposition.QR;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.R064Store;

import java.util.ArrayList;
import java.util.List;

/**
 * Система линейных уравнений {@code A·x = b} с диагностикой вместо падения.
 *
 * <p>Матрица собирается по строкам: {@link #row} заводит уравнение, {@link #term} кладёт
 * коэффициент при неизвестном (повторные вклады складываются — так элемент может внести
 * слагаемое, не зная, вносил ли его кто-то ещё), {@link #constant} переносит известное в правую
 * часть. Список неизвестных задаётся заранее: в балансах схемы он и так известен до сборки, а
 * «столбец появляется при первом упоминании» сделало бы порядок неизвестных зависимым от порядка
 * элементов.
 *
 * <p>Система балансов <b>не обязана быть квадратной</b>. В замкнутом цикле балансов массы на одно
 * уравнение больше, чем независимых связей между расходами (сумма всех балансов тождественно
 * равна нулю), а заданный расход свежего пара — ещё одно уравнение, не добавляющее неизвестного.
 * Поэтому решается задача наименьших квадратов ({@link QR} из ojAlgo): у совместной системы
 * невязка нулевая и решение точное, а переопределённость сама по себе не мешает.
 *
 * <p>Что вместо падения. Ранг считается всегда, и по нему видно, чем система плоха:
 * <ul>
 *   <li><b>ранг меньше числа неизвестных</b> — балансы не определяют часть расходов (замкнутый
 *       цикл без заданного расхода: решение определено с точностью до масштаба). Сообщение
 *       перечисляет неопределяемые неизвестные;</li>
 *   <li><b>ранг полный, но невязка не нулевая</b> — уравнения противоречат друг другу (например,
 *       баланс энергии аппарата не сходится с заданными энтальпиями). Сообщение называет строку с
 *       наибольшей невязкой.</li>
 * </ul>
 * Точное («машинное») совпадение здесь не требуется: коэффициенты — энтальпии в кДж/кг, и в
 * пределах допуска невязка означает округление, а не ошибку.
 */
final class LinearSystem {

    /** Порог нуля при определении ранга: коэффициенты — единицы и энтальпии, решения — кг/с. */
    private static final double TOLERANCE = 1e-9;

    /** Допуск невязки: энтальпии задаются округлёнными, но баланс обязан сходиться. */
    private static final double RESIDUAL = 1e-6;

    private final List<String> unknowns;
    private final List<Row> rows = new ArrayList<>();

    LinearSystem(List<String> unknowns) {
        this.unknowns = List.copyOf(unknowns);
    }

    /** Заводит строку с подписью для отчётов и возвращает её номер. */
    int row(String label) {
        rows.add(new Row(label, unknowns.size()));
        return rows.size() - 1;
    }

    /** Добавляет коэффициент при неизвестном {@code column}; нулевые вклады пропускаются. */
    void term(int row, int column, double coefficient) {
        if (coefficient == 0.0) {
            return;
        }
        Row target = rows.get(row);
        target.coefficients[column] += coefficient;
    }

    /** Добавляет слагаемое в правую часть уравнения. */
    void constant(int row, double value) {
        Row target = rows.get(row);
        target.rhs += value;
    }

    int equationCount() {
        return rows.size();
    }

    int unknownCount() {
        return unknowns.size();
    }

    /**
     * Решает систему и возвращает значения неизвестных вместе с диагностикой.
     *
     * @param what что именно решается — попадает в сообщение («балансы расходов»)
     * @throws IllegalStateException если балансы не определяют неизвестные или противоречат друг
     *                               другу
     */
    Solution solve(String what) {
        int equations = rows.size();
        int count = unknowns.size();
        if (count == 0) {
            return new Solution(new double[0], 0, 0, equations, 0.0);
        }

        R064Store matrix = R064Store.FACTORY.make(equations, count);
        R064Store rhs = R064Store.FACTORY.make(equations, 1);
        for (int r = 0; r < equations; r++) {
            Row row = rows.get(r);
            for (int c = 0; c < count; c++) {
                matrix.set(r, c, row.coefficients[c]);
            }
            rhs.set(r, 0, row.rhs);
        }

        QR<Double> qr = QR.R064.make(matrix);
        qr.decompose(matrix);
        int rank = qr.getRank();
        if (rank < count) {
            throw new IllegalStateException(what + ": балансы не определяют " + (count - rank)
                    + " из " + count + " неизвестных (ранг " + rank + "): "
                    + undetermined(qr, count));
        }

        double[] values = qr.solve(rhs)
                .orElseThrow(() -> new IllegalStateException(what + ": система не имеет решения ("
                        + equations + " уравнений, " + count + " неизвестных)"))
                .toRawCopy1D();

        double worst = 0;
        int worstRow = -1;
        for (int r = 0; r < equations; r++) {
            double residual = Math.abs(residual(rows.get(r), values));
            if (residual > worst) {
                worst = residual;
                worstRow = r;
            }
        }
        if (worst > RESIDUAL) {
            throw new IllegalStateException(what + ": балансы противоречат друг другу — уравнение «"
                    + rows.get(worstRow).label + "» не выполняется, невязка " + worst);
        }
        return new Solution(values, rank, count, equations, worst);
    }

    /** Неизвестные, которые балансы не определяют: нулевые диагонали треугольного множителя. */
    private String undetermined(QR<Double> qr, int count) {
        MatrixStore<Double> upper = qr.getR();
        List<String> free = new ArrayList<>();
        for (int c = 0; c < count; c++) {
            if (Math.abs(upper.get(c, c)) <= TOLERANCE) {
                free.add(unknowns.get(c));
            }
        }
        if (free.isEmpty()) {
            return "невязка зависимых строк не устранена";
        }
        return String.join(", ", free);
    }

    private static double residual(Row row, double[] values) {
        double sum = 0;
        for (int c = 0; c < values.length; c++) {
            sum += row.coefficients[c] * values[c];
        }
        return sum - row.rhs;
    }

    /** Итог решения: значения неизвестных и диагностика системы. */
    record Solution(double[] values, int rank, int unknowns, int equations, double residual) {

        double value(int column) {
            return values[column];
        }

        /** Подпись «27 уравнений, 27 неизвестных, ранг 27, невязка 1.2E-13». */
        String describe() {
            return equations + " уравнений, " + unknowns + " неизвестных, ранг " + rank
                    + ", невязка " + residual;
        }
    }

    /** Уравнение: коэффициенты при неизвестных и правая часть. */
    private static final class Row {

        private final String label;
        private final double[] coefficients;
        private double rhs;

        private Row(String label, int count) {
            this.label = label;
            this.coefficients = new double[count];
        }
    }
}
