package org.kpush88.Thermal_2.v1.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Результат решения схемы: значения всех неизвестных, доступ по {@link Variable}. */
public final class Solution {

    private final Map<Variable, Double> values = new LinkedHashMap<>();

    Solution(UnknownRegistry registry, double[] x) {
        for (Variable variable : registry.variables()) {
            values.put(variable, x[registry.indexOf(variable)]);
        }
    }

    public double value(Variable variable) {
        Double v = values.get(variable);
        if (v == null) {
            throw new IllegalArgumentException("Нет значения для переменной: " + variable);
        }
        return v;
    }

    public Map<Variable, Double> asMap() {
        return Map.copyOf(values);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        values.forEach((variable, value) ->
                sb.append(String.format(Locale.ROOT, "  %-16s = %+.6f%n", variable, value)));
        return sb.toString();
    }
}
