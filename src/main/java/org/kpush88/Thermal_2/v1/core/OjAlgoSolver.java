package org.kpush88.Thermal_2.v1.core;

import org.ojalgo.matrix.decomposition.LU;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.R064Store;

/**
 * Решатель СЛАУ на базе <b>ojAlgo</b> (LU-разложение с частичным выбором главного элемента).
 *
 * <p>Промышленный решатель вместо учебного метода Гаусса: даёт устойчивое решение,
 * масштабируется на большие разреженные схемы и служит основой для последующего
 * перехода к разреженным хранилищам.
 *
 * <p>Замечание о версии: в ojAlgo 54+ классы переименованы
 * ({@code Primitive64Store → R064Store}, {@code Primitive64Matrix → MatrixR064}).
 * Здесь используется текущий API (проверено на ojAlgo 57.4.0).
 */
public final class OjAlgoSolver implements LinearSolver {

    @Override
    public double[] solve(double[][] a, double[] b) {
        int n = b.length;
        if (a.length != n) {
            throw new IllegalArgumentException("Матрица должна быть квадратной: " + a.length + "×" + n);
        }

        R064Store matrix = R064Store.FACTORY.make(n, n);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                matrix.set(r, c, a[r][c]);
            }
        }

        R064Store rhs = R064Store.FACTORY.make(n, 1);
        for (int r = 0; r < n; r++) {
            rhs.set(r, 0, b[r]);
        }

        LU<Double> lu = LU.R064.make(matrix);
        if (!lu.decompose(matrix)) {
            throw new IllegalStateException("СЛАУ вырождена (нулевой определитель)");
        }
        MatrixStore<Double> solution = lu.solve(rhs)
                .orElseThrow(() -> new IllegalStateException("СЛАУ не имеет решения"));
        return solution.toRawCopy1D();
    }
}
