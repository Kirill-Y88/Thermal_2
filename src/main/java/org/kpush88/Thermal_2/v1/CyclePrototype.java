package org.kpush88.Thermal_2.v1;

import org.kpush88.Thermal_2.v1.cycle.CycleDesign;
import org.kpush88.Thermal_2.v1.cycle.CycleResult;
import org.kpush88.Thermal_2.v1.cycle.RegenerativeCycle;

/**
 * Демонстрация прототипа v1: расчёт схемы «котёл → турбина (3 отбора) → конденсатор →
 * ПНД → деаэратор → ПЭН → ПВД → котёл» с IF97 и решателем ojAlgo.
 *
 * <p>Запуск: {@code mvn -o exec:java -Dexec.mainClass=org.kpush88.Thermal_2.v1.CyclePrototype}
 * либо обычный {@code main(...)} из IDE.
 */
public final class CyclePrototype {

    private CyclePrototype() {
    }

    public static void main(String[] args) {
        System.out.println("### Вариант 1: типовая схема (p0 = 24 МПа, t0 = 540 °C, pк = 4 кПа)");
        CycleResult base = new RegenerativeCycle(CycleDesign.reference()).calculate();
        System.out.println(base.report());

        System.out.println("### Вариант 2: изменён отбор на ПВД (p1: 3.5 → 4.5 МПа)");
        CycleDesign variant = new CycleDesign(24.0, 540.0, 4.5, 0.6, 0.12, 0.004, 0.85, 5.0, 0.8, 0.985);
        CycleResult changed = new RegenerativeCycle(variant).calculate();
        System.out.println(changed.report());

        System.out.printf(java.util.Locale.ROOT,
                "Изменение КПД нетто: %.2f %% → %.2f %% (Δ = %+.2f п.п.)%n",
                base.netEfficiency() * 100.0,
                changed.netEfficiency() * 100.0,
                (changed.netEfficiency() - base.netEfficiency()) * 100.0);
    }
}
