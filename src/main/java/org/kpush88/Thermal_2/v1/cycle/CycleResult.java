package org.kpush88.Thermal_2.v1.cycle;

import java.util.List;
import java.util.Locale;

import org.kpush88.Thermal_2.v1.thermo.SteamState;

/**
 * Результаты теплового расчёта схемы.
 *
 * <p>Расходы нормированы на расход питательной воды (в расчёте он принят 1 кг/с),
 * поэтому их удобно читать и как доли, и как кг/с; мощности и теплоты — в кВт.
 */
public record CycleResult(
        double feedwaterFlow,
        double hpExtractionFlow,
        double deaeratorExtractionFlow,
        double lpExtractionFlow,
        double exhaustFlow,
        double condensateFlow,
        double turbineInternalPower,
        double pumpPower,
        double boilerDuty,
        double condenserDuty,
        double netInternalPower,
        double netElectricPower,
        double grossEfficiency,
        double netEfficiency,
        List<SteamState> steamPoints) {

    /** Формирует текстовый отчёт. */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Тепловой расчёт паротурбинной установки (v1) ===%n".formatted());
        sb.append(String.format(Locale.ROOT,
                "Расход питательной воды принят Gпв = %.3f кг/с (все расходы ниже — доли)%n%n",
                feedwaterFlow));

        sb.append("Отборы и потоки (доли от Gпв):\n");
        sb.append(flow("G0 (свежий пар) ", feedwaterFlow));
        sb.append(flow("Gотб ПВД        ", hpExtractionFlow));
        sb.append(flow("Gотб деаэратора ", deaeratorExtractionFlow));
        sb.append(flow("Gотб ПНД        ", lpExtractionFlow));
        sb.append(flow("Gвых (в конд.)  ", exhaustFlow));
        sb.append(flow("Gк (конденсат)  ", condensateFlow));
        sb.append(String.format(Locale.ROOT, "  Сумма отборов    = %.5f%n%n",
                hpExtractionFlow + deaeratorExtractionFlow + lpExtractionFlow));

        sb.append(String.format(Locale.ROOT, "Мощности и теплоты (кВт при Gпв = 1 кг/с):%n"));
        sb.append(power("Внутренняя мощность турбины Nт", turbineInternalPower));
        sb.append(power("Мощность питательного насоса Nн", pumpPower));
        sb.append(power("Полезная мощность (нетто, внутр.)", netInternalPower));
        sb.append(power("Полезная мощность (электрич.)", netElectricPower));
        sb.append(power("Теплота, подведённая в котле Q", boilerDuty));
        sb.append(power("Теплота, отведённая в конденсаторе Qк", condenserDuty));
        sb.append(String.format(Locale.ROOT, "%n"));

        sb.append("КПД:\n");
        sb.append(String.format(Locale.ROOT, "  КПД брутто (по внутр. мощности) = %.2f %% %n", grossEfficiency * 100.0));
        sb.append(String.format(Locale.ROOT, "  КПД нетто                       = %.2f %% %n%n", netEfficiency * 100.0));

        sb.append("Точки схемы:\n");
        for (SteamState point : steamPoints) {
            sb.append(point.format()).append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static String flow(String label, double value) {
        return String.format(Locale.ROOT, "  %-16s = %.5f%n", label, value);
    }

    private static String power(String label, double value) {
        return String.format(Locale.ROOT, "  %-34s = %10.2f%n", label, value);
    }
}
