/**
 * Расчётный слой v3: этапы расчёта, журнал и снапшоты состояний.
 *
 * <p>Схема ({@link org.kpush88.Thermal_2.v3.Scheme}) описывает конструкцию: элементы, порты и
 * связи. Состояние рабочего тела живёт в связях, но меняется не напрямую, а через
 * {@link org.kpush88.Thermal_2.v3.calc.CalculationJournal}: он записывает величину и её
 * происхождение ({@link org.kpush88.Thermal_2.v3.calc.Origin}) — этап и владельца. Снапшот
 * ({@link org.kpush88.Thermal_2.v3.calc.SchemeState}) — неизменяемая копия всех сечений на
 * этапе, поэтому история расчёта сохраняется целиком.
 *
 * <p>Этапы: {@link org.kpush88.Thermal_2.v3.calc.Stage#INPUT} → {@code NORMALIZED} →
 * {@code RESOLVED} → {@code SOLVED} → {@code RESULT}. Сейчас реализованы только механизм
 * этапов и снимков: систему балансов и решение СЛАУ добавит следующий шаг, и он же заполнит
 * {@code SOLVED}. Незаданные величины уже сейчас перечисляет
 * {@link org.kpush88.Thermal_2.v3.calc.SchemeState#missing()} — это и есть постановка задачи
 * для системы уравнений.
 */
package org.kpush88.Thermal_2.v3.calc;
