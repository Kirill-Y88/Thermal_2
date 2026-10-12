package org.kpush88.Thermal_2.v1.core;

/**
 * Граничное условие схемы: фиксирует расход потока (например, расход питательной воды).
 *
 * <p>Уравнение: {@code G = value}. Замыкает систему балансов замкнутого цикла —
 * без «якоря» по расходу она имела бы бесконечное семейство решений (все расходы,
 * умноженные на константу, тоже решение).
 */
public final class FixedFlow implements Element {

    private final Variable stream;
    private final double value;

    public FixedFlow(Variable stream, double value) {
        this.stream = stream;
        this.value = value;
    }

    @Override
    public String name() {
        return "Граничное условие";
    }

    @Override
    public void declareUnknowns(UnknownRegistry registry) {
        registry.register(stream);
    }

    @Override
    public void contribute(EquationSystem system) {
        int row = system.equationFor(this, "заданный расход");
        system.term(row, stream, 1.0);
        system.constant(row, value);
    }
}
