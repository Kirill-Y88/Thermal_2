package org.kpush88.Thermal_2.v1.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Тепловая схема как набор элементов (граф).
 *
 * <p>Собирает реестр неизвестных и систему балансовых уравнений, решает СЛАУ выбранной
 * стратегией {@link LinearSolver}. Добавление элемента — это просто {@link #add(Element)}:
 * при следующем {@link #solve(LinearSolver)} система пересобирается, число неизвестных
 * растёт, а расходы во <b>всех</b> элементах пересчитываются автоматически.
 */
public final class Scheme {

    private final List<Element> elements = new ArrayList<>();

    public Scheme add(Element element) {
        elements.add(element);
        return this;
    }

    public List<Element> elements() {
        return List.copyOf(elements);
    }

    public Solution solve(LinearSolver solver) {
        UnknownRegistry registry = new UnknownRegistry();
        for (Element element : elements) {
            element.declareUnknowns(registry);
        }
        EquationSystem system = new EquationSystem(registry);
        for (Element element : elements) {
            element.contribute(system);
        }
        double[] x = system.solve(solver);
        return new Solution(registry, x);
    }
}
