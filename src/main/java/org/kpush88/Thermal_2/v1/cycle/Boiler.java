package org.kpush88.Thermal_2.v1.cycle;

import org.kpush88.Thermal_2.v1.core.Element;
import org.kpush88.Thermal_2.v1.core.EquationSystem;
import org.kpush88.Thermal_2.v1.core.Solution;
import org.kpush88.Thermal_2.v1.core.UnknownRegistry;
import org.kpush88.Thermal_2.v1.core.Variable;

/**
 * Котёл (парогенератор): питательная вода → свежий пар.
 *
 * <p>Вносит уравнение баланса массы {@code G0 − Gпв = 0} (узел 1-вход / 1-выход).
 * Тепловая нагрузка вычисляется по решению:
 * {@code Q = Gпв · (h0 − hпв)}.
 */
public final class Boiler implements Element {

    private final Variable feedwater;
    private final Variable liveSteam;
    private final double feedwaterEnthalpy;
    private final double liveSteamEnthalpy;

    public Boiler(Variable feedwater, Variable liveSteam,
                  double feedwaterEnthalpy, double liveSteamEnthalpy) {
        this.feedwater = feedwater;
        this.liveSteam = liveSteam;
        this.feedwaterEnthalpy = feedwaterEnthalpy;
        this.liveSteamEnthalpy = liveSteamEnthalpy;
    }

    @Override
    public String name() {
        return "Котёл";
    }

    @Override
    public void declareUnknowns(UnknownRegistry registry) {
        registry.register(feedwater);
        registry.register(liveSteam);
    }

    @Override
    public void contribute(EquationSystem system) {
        int mass = system.equationFor(this, "баланс массы");
        system.term(mass, liveSteam, 1.0);
        system.term(mass, feedwater, -1.0);
    }

    /** Теплота, подведённая в цикле, кВт (при расходе в кг/с). */
    public double heatDuty(Solution solution) {
        return solution.value(liveSteam) * (liveSteamEnthalpy - feedwaterEnthalpy);
    }

    /** Удельная теплота, подведённая 1 кг свежего пара, кДж/кг. */
    public double specificHeat() {
        return liveSteamEnthalpy - feedwaterEnthalpy;
    }
}
