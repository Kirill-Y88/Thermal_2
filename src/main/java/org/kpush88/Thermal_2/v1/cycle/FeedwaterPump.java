package org.kpush88.Thermal_2.v1.cycle;

import org.kpush88.Thermal_2.v1.core.Element;
import org.kpush88.Thermal_2.v1.core.EquationSystem;
import org.kpush88.Thermal_2.v1.core.Solution;
import org.kpush88.Thermal_2.v1.core.UnknownRegistry;
import org.kpush88.Thermal_2.v1.core.Variable;

/**
 * Питательный насос (ПЭН): повышает давление питательной воды от давления
 * деаэратора до давления котла.
 *
 * <p>Напорный элемент «вход = выход»: расход сохраняется (общая переменная
 * {@code Gпв}), поэтому уравнение баланса массы не вносится — изменяется только
 * давление. Удельная работа насоса {@code w = v·Δp / ηн}, а прирост энтальпии
 * {@code w} учтён в энтальпии воды на входе ПВД.
 */
public final class FeedwaterPump implements Element {

    private static final double MPA_TO_KJ_PER_M3 = 1000.0;

    private final Variable feedwater;
    private final double specificVolume;
    private final double pressureRise;
    private final double efficiency;

    public FeedwaterPump(Variable feedwater, double specificVolume, double pressureRise, double efficiency) {
        this.feedwater = feedwater;
        this.specificVolume = specificVolume;
        this.pressureRise = pressureRise;
        this.efficiency = efficiency;
    }

    @Override
    public String name() {
        return "Питательный насос";
    }

    @Override
    public void declareUnknowns(UnknownRegistry registry) {
        registry.register(feedwater);
    }

    @Override
    public void contribute(EquationSystem system) {
        // Напорный элемент: баланс массы выполняется по построению (общая переменная).
        // В уравнение балансовой системы отдельного вклада не вносит.
    }

    /** Удельная работа насоса, кДж/кг. */
    public double specificWork() {
        return specificVolume * pressureRise * MPA_TO_KJ_PER_M3 / efficiency;
    }

    /** Мощность насоса, кВт (при расходе в кг/с). */
    public double power(Solution solution) {
        return solution.value(feedwater) * specificWork();
    }
}
