package org.kpush88.Thermal_2.v1.cycle;

import org.kpush88.Thermal_2.v1.core.Element;
import org.kpush88.Thermal_2.v1.core.EquationSystem;
import org.kpush88.Thermal_2.v1.core.UnknownRegistry;
import org.kpush88.Thermal_2.v1.core.Variable;

/**
 * Поверхностный подогреватель (ПВД или ПНД).
 *
 * <p>Вносит тепловой баланс отсека подогрева (баланс массы поверхностного аппарата
 * выполняется «по построению»: основной поток воды — одна и та же переменная на входе
 * и выходе, дренаж — отдельный поток, равный расходу отбора):
 * <pre>
 *   Gотб · (hотб − hдр) = Gводы · (hвых − hвх)
 * </pre>
 * где {@code hдр} — энтальпия дренажа (насыщенная жидкость при давлении отбора),
 * {@code hвых} — энтальпия недогретой воды на выходе.
 */
public final class SurfaceHeater implements Element {

    private final String name;
    private final Variable extraction;
    private final Variable water;
    private final double extractionEnthalpy;
    private final double drainEnthalpy;
    private final double waterInletEnthalpy;
    private final double waterOutletEnthalpy;

    public SurfaceHeater(String name,
                         Variable extraction,
                         Variable water,
                         double extractionEnthalpy,
                         double drainEnthalpy,
                         double waterInletEnthalpy,
                         double waterOutletEnthalpy) {
        this.name = name;
        this.extraction = extraction;
        this.water = water;
        this.extractionEnthalpy = extractionEnthalpy;
        this.drainEnthalpy = drainEnthalpy;
        this.waterInletEnthalpy = waterInletEnthalpy;
        this.waterOutletEnthalpy = waterOutletEnthalpy;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void declareUnknowns(UnknownRegistry registry) {
        registry.register(extraction);
        registry.register(water);
    }

    @Override
    public void contribute(EquationSystem system) {
        int heat = system.equationFor(this, "тепловой баланс");
        system.term(heat, extraction, extractionEnthalpy - drainEnthalpy);
        system.term(heat, water, -(waterOutletEnthalpy - waterInletEnthalpy));
    }
}
