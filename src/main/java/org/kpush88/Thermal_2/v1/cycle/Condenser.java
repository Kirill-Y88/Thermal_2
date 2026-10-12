package org.kpush88.Thermal_2.v1.cycle;

import org.kpush88.Thermal_2.v1.core.Element;
import org.kpush88.Thermal_2.v1.core.EquationSystem;
import org.kpush88.Thermal_2.v1.core.Solution;
import org.kpush88.Thermal_2.v1.core.UnknownRegistry;
import org.kpush88.Thermal_2.v1.core.Variable;

/**
 * Конденсатор: смешение выхлопа турбины и дренажа ПНД с получением конденсата.
 *
 * <p>Вносит уравнение баланса массы {@code Gвых + Gдр3 = Gк}. Теплота, отводимая в
 * конденсаторе (теплота конденсации), вычисляется методом {@link #heatRejection(Solution)}.
 */
public final class Condenser implements Element {

    private final Variable exhaust;
    private final Variable lpHeaterDrain;
    private final Variable condensate;
    private final double exhaustEnthalpy;
    private final double drainEnthalpy;
    private final double condensateEnthalpy;

    public Condenser(Variable exhaust, Variable lpHeaterDrain, Variable condensate,
                     double exhaustEnthalpy, double drainEnthalpy, double condensateEnthalpy) {
        this.exhaust = exhaust;
        this.lpHeaterDrain = lpHeaterDrain;
        this.condensate = condensate;
        this.exhaustEnthalpy = exhaustEnthalpy;
        this.drainEnthalpy = drainEnthalpy;
        this.condensateEnthalpy = condensateEnthalpy;
    }

    @Override
    public String name() {
        return "Конденсатор";
    }

    @Override
    public void declareUnknowns(UnknownRegistry registry) {
        registry.register(exhaust);
        registry.register(lpHeaterDrain);
        registry.register(condensate);
    }

    @Override
    public void contribute(EquationSystem system) {
        int mass = system.equationFor(this, "баланс массы");
        system.term(mass, exhaust, 1.0);
        system.term(mass, lpHeaterDrain, 1.0);
        system.term(mass, condensate, -1.0);
    }

    /** Теплота, отводимая в конденсаторе, кВт (при расходе в кг/с). */
    public double heatRejection(Solution solution) {
        return solution.value(exhaust) * (exhaustEnthalpy - condensateEnthalpy)
                + solution.value(lpHeaterDrain) * (drainEnthalpy - condensateEnthalpy);
    }
}
