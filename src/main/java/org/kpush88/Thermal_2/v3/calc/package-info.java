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
 * {@code RESOLVED} → {@code SOLVED} → {@code RESULT}. Журнал и снапшоты — механизм этапов;
 * расчёт по этапам ведёт {@link org.kpush88.Thermal_2.v3.calc.StateResolver}: он применяет
 * модели элементов ({@link org.kpush88.Thermal_2.v3.calc.ElementModel}) через
 * {@link org.kpush88.Thermal_2.v3.calc.StateAccess}, повторяя проходы до неподвижной точки.
 *
 * <p>Модели пока не знают термодинамики: труба переносит состояние, тройник и смеситель
 * считают смешение, насос сохраняет энтальпию, массовый баланс канала определяет расход,
 * если он единственный неизвестный. Всё, что требует IF97 или решения системы уравнений,
 * остаётся незаданным и перечисляется в
 * {@link org.kpush88.Thermal_2.v3.calc.SchemeState#missing()} — это и есть постановка задачи
 * для следующих шагов (термодинамика и СЛАУ). {@code RESULT} не заполняется.
 */
package org.kpush88.Thermal_2.v3.calc;
