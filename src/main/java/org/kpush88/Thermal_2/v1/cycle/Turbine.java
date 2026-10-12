package org.kpush88.Thermal_2.v1.cycle;

import org.kpush88.Thermal_2.v1.core.Element;
import org.kpush88.Thermal_2.v1.core.EquationSystem;
import org.kpush88.Thermal_2.v1.core.Solution;
import org.kpush88.Thermal_2.v1.core.UnknownRegistry;
import org.kpush88.Thermal_2.v1.core.Variable;

/**
 * Турбина с тремя регенеративными отборами.
 *
 * <p>Вносит только уравнение баланса массы (узел 1-вход / 4-выхода):
 * {@code G0 − Gотб1 − Gотб2 − Gотб3 − Gвых = 0}.
 *
 * <p>Мощность отсеков считается по решению методом {@link #internalPower(Solution)}
 * (энтальпии отсеков заданы извне, рассчитаны по IF97 с учётом ηoi).
 */
public final class Turbine implements Element {

    private final Variable liveSteam;
    private final Variable hpExtraction;
    private final Variable deaeratorExtraction;
    private final Variable lpExtraction;
    private final Variable exhaust;

    private final double hLiveSteam;
    private final double hHpExtraction;
    private final double hDeaeratorExtraction;
    private final double hLpExtraction;
    private final double hExhaust;

    public Turbine(Variable liveSteam,
                   Variable hpExtraction,
                   Variable deaeratorExtraction,
                   Variable lpExtraction,
                   Variable exhaust,
                   double hLiveSteam,
                   double hHpExtraction,
                   double hDeaeratorExtraction,
                   double hLpExtraction,
                   double hExhaust) {
        this.liveSteam = liveSteam;
        this.hpExtraction = hpExtraction;
        this.deaeratorExtraction = deaeratorExtraction;
        this.lpExtraction = lpExtraction;
        this.exhaust = exhaust;
        this.hLiveSteam = hLiveSteam;
        this.hHpExtraction = hHpExtraction;
        this.hDeaeratorExtraction = hDeaeratorExtraction;
        this.hLpExtraction = hLpExtraction;
        this.hExhaust = hExhaust;
    }

    @Override
    public String name() {
        return "Турбина";
    }

    @Override
    public void declareUnknowns(UnknownRegistry registry) {
        registry.register(liveSteam);
        registry.register(hpExtraction);
        registry.register(deaeratorExtraction);
        registry.register(lpExtraction);
        registry.register(exhaust);
    }

    @Override
    public void contribute(EquationSystem system) {
        int mass = system.equationFor(this, "баланс массы");
        system.term(mass, liveSteam, 1.0);
        system.term(mass, hpExtraction, -1.0);
        system.term(mass, deaeratorExtraction, -1.0);
        system.term(mass, lpExtraction, -1.0);
        system.term(mass, exhaust, -1.0);
    }

    /**
     * Внутренняя мощность турбины, кВт (при расходе в кг/с):
     * {@code N = G0·(h0−h1) + (G0−Gотб1)·(h1−h2) + (G0−Gотб1−Gотб2)·(h2−h3)
     * + Gвых·(h3−hк)}.
     */
    public double internalPower(Solution solution) {
        double g0 = solution.value(liveSteam);
        double g1 = solution.value(hpExtraction);
        double g2 = solution.value(deaeratorExtraction);
        double g3 = solution.value(lpExtraction);
        return g0 * (hLiveSteam - hHpExtraction)
                + (g0 - g1) * (hHpExtraction - hDeaeratorExtraction)
                + (g0 - g1 - g2) * (hDeaeratorExtraction - hLpExtraction)
                + (g0 - g1 - g2 - g3) * (hLpExtraction - hExhaust);
    }
}
