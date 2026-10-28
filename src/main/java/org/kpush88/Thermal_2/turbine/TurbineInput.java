package org.kpush88.Thermal_2.turbine;

import java.util.Map;

/**
 * Режим турбины: что подаётся на вход и сколько пара уходит в отборы.
 *
 * <p>Это всё, что нужно маршу ({@link TurbineMarch}) кроме геометрии. Давления отборов здесь не
 * задаются — наоборот: они получаются следствием расхода и площадей. Задать их здесь было бы
 * повторением того, что уже знает цикл, и через шаг расчёт разошёлся бы сам с собой.
 *
 * <p><b>Расходы отборов.</b> Ключ — не имя отсека, а имя потребителя из геометрии
 * ({@link SectionGeometry#extraction()}): «ПВД-1», «деаэратор». Так режим читается как
 * эксплуатационная ведомость, и не нужно помнить, какой отсек как пронумерован. Отбор, которого
 * нет в карте, считается нулевым — конденсационная турбина с отключённым ПВД не требует
 * переписывать карту, достаточно её не заполнять.
 *
 * <p>Расход за отсеком — это разность: {@code G_(i+1) = G_i − G_отб(i)}. Сумма отборов должна
 * оставлять положительный расход, иначе марш падает с указанием, на каком отборе пар кончился.
 *
 * @param inletPressure   давление свежего пара, МПа
 * @param inletTemperature температура свежего пара, °C
 * @param inletFlow       расход свежего пара, кг/с
 * @param extractionFlows расходы отборов по имени потребителя, кг/с (может быть пустой)
 */
public record TurbineInput(
        double inletPressure,
        double inletTemperature,
        double inletFlow,
        Map<String, Double> extractionFlows) {

    /** Проверяет режим и копирует карту отборов, чтобы вход нельзя было изменить снаружи. */
    public TurbineInput {
        if (!(inletPressure > 0)) {
            throw new IllegalArgumentException("давление свежего пара должно быть "
                    + "положительным: " + inletPressure + " МПа");
        }
        if (!(inletFlow > 0)) {
            throw new IllegalArgumentException("расход свежего пара должен быть положительным: "
                    + inletFlow + " кг/с");
        }
        extractionFlows = extractionFlows == null ? Map.of() : Map.copyOf(extractionFlows);
        for (Map.Entry<String, Double> extraction : extractionFlows.entrySet()) {
            if (extraction.getKey() == null || extraction.getKey().isBlank()) {
                throw new IllegalArgumentException("отбор без имени в режиме турбины");
            }
            if (extraction.getValue() == null || !(extraction.getValue() >= 0)) {
                throw new IllegalArgumentException("расход отбора «" + extraction.getKey()
                        + "» не может быть отрицательным: " + extraction.getValue() + " кг/с");
            }
        }
    }

    /** Режим без отборов: вся мощность на одном расходе. */
    public static TurbineInput condensing(double inletPressure, double inletTemperature,
                                          double inletFlow) {
        return new TurbineInput(inletPressure, inletTemperature, inletFlow, Map.of());
    }

    /** Режим с отборами, заданными парами «потребитель — расход». */
    public static TurbineInput of(double inletPressure, double inletTemperature, double inletFlow,
                                 Map<String, Double> extractionFlows) {
        return new TurbineInput(inletPressure, inletTemperature, inletFlow, extractionFlows);
    }

    /** Расход отбора по имени потребителя, кг/с; отсутствующий отбор — ноль. */
    public double extraction(String consumer) {
        Double value = extractionFlows.get(consumer);
        return value == null ? 0.0 : value;
    }

    /** Копия режима с другим расходом свежего пара (для поиска режима по мощности). */
    public TurbineInput withFlow(double flow) {
        return new TurbineInput(inletPressure, inletTemperature, flow, extractionFlows);
    }

    /** Копия режима с другими расходами отборов. */
    public TurbineInput withExtractions(Map<String, Double> flows) {
        return new TurbineInput(inletPressure, inletTemperature, inletFlow, flows);
    }

    /** Режим в виде строки для протокола. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append(String.format(java.util.Locale.ROOT,
                "p₀ = %.3f МПа, t₀ = %.1f °C, G₀ = %.4f кг/с", inletPressure,
                inletTemperature, inletFlow));
        if (!extractionFlows.isEmpty()) {
            text.append(", отборы: ");
            boolean first = true;
            for (Map.Entry<String, Double> extraction : extractionFlows.entrySet()) {
                if (!first) {
                    text.append(", ");
                }
                text.append(String.format(java.util.Locale.ROOT, "%s %.4f кг/с",
                        extraction.getKey(), extraction.getValue()));
                first = false;
            }
        }
        return text.toString();
    }
}
