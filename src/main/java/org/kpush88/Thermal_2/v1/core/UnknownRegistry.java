package org.kpush88.Thermal_2.v1.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр неизвестных — аналог нумерации степеней свободы в МКЭ.
 *
 * <p>Сопоставляет {@link Variable} ↔ индекс столбца матрицы. При изменении топологии
 * (добавлении/удалении элемента) нумерация пересчитывается автоматически — править
 * существующий код не нужно.
 */
public final class UnknownRegistry {

    private final Map<Variable, Integer> index = new LinkedHashMap<>();

    /** Регистрирует неизвестную (идемпотентно) и возвращает её индекс столбца. */
    public int register(Variable variable) {
        return index.computeIfAbsent(variable, key -> index.size());
    }

    /** Индекс столбца для ранее зарегистрированной неизвестной. */
    public int indexOf(Variable variable) {
        Integer i = index.get(variable);
        if (i == null) {
            throw new IllegalArgumentException("Неизвестная не зарегистрирована: " + variable);
        }
        return i;
    }

    public int size() {
        return index.size();
    }

    public List<Variable> variables() {
        return List.copyOf(index.keySet());
    }

    public Map<Variable, Integer> asMap() {
        return Collections.unmodifiableMap(index);
    }
}
