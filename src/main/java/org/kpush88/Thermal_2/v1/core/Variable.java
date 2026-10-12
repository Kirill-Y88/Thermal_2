package org.kpush88.Thermal_2.v1.core;

import java.util.Objects;

/**
 * Идентификатор неизвестной величины (расхода) системы балансовых уравнений.
 *
 * <p>Один и тот же {@code Variable} в разных элементах схемы — это одна и та же
 * неизвестная. Именно так задаётся связь элементов: например, отбор в ПВД и дренаж
 * ПВД в деаэратор — это один и тот же {@code Variable} (расход отбора), поэтому
 * в уравнениях автоматически появляется нужная связь без ручного указания индексов.
 */
public record Variable(String name) {

    public Variable {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    public static Variable of(String name) {
        return new Variable(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
