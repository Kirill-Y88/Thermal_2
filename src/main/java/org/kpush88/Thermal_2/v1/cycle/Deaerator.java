package org.kpush88.Thermal_2.v1.cycle;

import org.kpush88.Thermal_2.v1.core.Element;
import org.kpush88.Thermal_2.v1.core.EquationSystem;
import org.kpush88.Thermal_2.v1.core.Solution;
import org.kpush88.Thermal_2.v1.core.UnknownRegistry;
import org.kpush88.Thermal_2.v1.core.Variable;

/**
 * Деаэратор — смешивающий подогреватель: в него входят отбор, дренаж ПВД и вода
 * после ПНД, а выходит питательная вода.
 *
 * <p>Вносит <b>тепловой баланс смешения</b>:
 * <pre>
 *   Gотб·h2 + Gдр1·hдр1 + Gк·hПНД = Gпв·hд
 * </pre>
 *
 * <p>Баланс массы деаэратора {@code Gотб + Gдр1 + Gк = Gпв} отдельного уравнения не
 * требует: в замкнутом цикле одно массовое уравнение линейно зависит от остальных
 * (замыкание цикла). Деаэратор выбран «замыкающим» узлом — его баланс массы
 * выполняется автоматически через балансы турбины и конденсатора
 * (см. {@link #massBalanceResidual(Solution)} для контроля).
 */
public final class Deaerator implements Element {

    private final Variable hpHeaterDrain;
    private final Variable extraction;
    private final Variable condensate;
    private final Variable feedwater;

    private final double hpHeaterDrainEnthalpy;
    private final double extractionEnthalpy;
    private final double condensateEnthalpy;
    private final double feedwaterEnthalpy;

    public Deaerator(Variable hpHeaterDrain,
                     Variable extraction,
                     Variable condensate,
                     Variable feedwater,
                     double hpHeaterDrainEnthalpy,
                     double extractionEnthalpy,
                     double condensateEnthalpy,
                     double feedwaterEnthalpy) {
        this.hpHeaterDrain = hpHeaterDrain;
        this.extraction = extraction;
        this.condensate = condensate;
        this.feedwater = feedwater;
        this.hpHeaterDrainEnthalpy = hpHeaterDrainEnthalpy;
        this.extractionEnthalpy = extractionEnthalpy;
        this.condensateEnthalpy = condensateEnthalpy;
        this.feedwaterEnthalpy = feedwaterEnthalpy;
    }

    @Override
    public String name() {
        return "Деаэратор";
    }

    @Override
    public void declareUnknowns(UnknownRegistry registry) {
        registry.register(hpHeaterDrain);
        registry.register(extraction);
        registry.register(condensate);
        registry.register(feedwater);
    }

    @Override
    public void contribute(EquationSystem system) {
        int heat = system.equationFor(this, "тепловой баланс");
        system.term(heat, extraction, extractionEnthalpy);
        system.term(heat, hpHeaterDrain, hpHeaterDrainEnthalpy);
        system.term(heat, condensate, condensateEnthalpy);
        system.term(heat, feedwater, -feedwaterEnthalpy);
    }

    /** Невязка баланса массы деаэратора (должна быть ≈ 0 — цикл замкнут). */
    public double massBalanceResidual(Solution solution) {
        return solution.value(extraction)
                + solution.value(hpHeaterDrain)
                + solution.value(condensate)
                - solution.value(feedwater);
    }
}
