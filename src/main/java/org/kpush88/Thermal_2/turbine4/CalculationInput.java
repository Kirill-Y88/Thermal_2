package org.kpush88.Thermal_2.turbine4;

/**
 * Исходные данные расчёта.
 *
 * <p>Конкретные записи реализуют этот интерфейс и возвращают свой тип расчёта.
 * Благодаря этому оркестратор не запускает конструкторский расчёт с данными
 * поверочного расчёта и наоборот.
 */
public interface CalculationInput {

    CalculationType calculationType();
}