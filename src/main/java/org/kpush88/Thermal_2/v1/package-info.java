/**
 * Прототип (v1) расчёта тепловой схемы паротурбинной установки.
 *
 * <p>Схема: {@code котёл → турбина (3 отбора) → конденсатор → ПНД → деаэратор →
 * питательный насос → ПВД → котёл}. Реализованы компоненты:
 * <ul>
 *   <li>{@link org.kpush88.Thermal_2.v1.cycle.Boiler котёл};</li>
 *   <li>{@link org.kpush88.Thermal_2.v1.cycle.Turbine турбина} (многоотсечная, с отборами);</li>
 *   <li>{@link org.kpush88.Thermal_2.v1.cycle.Deaerator деаэратор} (смешивающий подогреватель);</li>
 *   <li>{@link org.kpush88.Thermal_2.v1.cycle.SurfaceHeater ПВД/ПНД} (поверхностные подогреватели);</li>
 *   <li>{@link org.kpush88.Thermal_2.v1.cycle.Condenser конденсатор};</li>
 *   <li>{@link org.kpush88.Thermal_2.v1.cycle.FeedwaterPump питательный насос}.</li>
 * </ul>
 *
 * <p>Слои пакета:
 * <ol>
 *   <li><b>thermo</b> — термодинамические свойства воды и пара через IF97
 *       ({@link org.kpush88.Thermal_2.v1.thermo.WaterSteam});</li>
 *   <li><b>core</b> — домен + генерация/сборка балансовых уравнений
 *       ({@link org.kpush88.Thermal_2.v1.core.Scheme}, {@link org.kpush88.Thermal_2.v1.core.EquationSystem});</li>
 *   <li><b>cycle</b> — элементы схемы и их «сшивка» в расчёт
 *       ({@link org.kpush88.Thermal_2.v1.cycle.RegenerativeCycle}).</li>
 * </ol>
 *
 * <p>Метод расчёта: при фиксированных (из IF97) энтальпиях точек балансовые
 * уравнения линейны относительно расходов, поэтому они собираются в СЛАУ и решаются
 * один раз решателем ojAlgo (LU). Внешняя итерация (при необходимости уточнять
 * энтальпии по найденным расходам/давлениям) вводится отдельно — здесь она не нужна,
 * так как все давления заданы как граничные условия схемы.
 *
 * <p>Демонстрация — {@link org.kpush88.Thermal_2.v1.CyclePrototype}.
 */
package org.kpush88.Thermal_2.v1;
