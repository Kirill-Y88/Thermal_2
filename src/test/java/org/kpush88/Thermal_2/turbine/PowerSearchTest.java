package org.kpush88.Thermal_2.turbine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Поверочный расчёт конденсационной турбины и поиск причины падения мощности.
 *
 * <p>Первый сценарий — прямой расчёт: по свежему пару, давлению в конденсаторе и геометрии
 * считается электрическая мощность и КПД каждой ступени и турбины в целом. Второй — диагностика:
 * известна измеренная мощность, ниже проектной; перебором радиального зазора δ одной ступени
 * находится его значение, объясняющее расхождение.
 */
class PowerSearchTest {

    /**
     * Ступень, которую считаем «подозреваемой» (изношенной по зазору). Берётся последняя: её
     * зазор влияет только на её собственную работу, поэтому мощность монотонно падает с ростом
     * зазора. У промежуточной ступени «возврат тепла» вниз по потоку может дать обратный знак.
     */
    private static final int SUSPECT_STAGE = CondensingTurbine.STAGES - 1;

    private PowerSearch search() {
        return new PowerSearch(CondensingTurbine.steam(), CondensingTurbine.geometry(),
                CondensingTurbine.input(), CondensingTurbine.EXHAUST_PRESSURE,
                CondensingTurbine.ELECTROMECHANICAL);
    }

    @Test
    @DisplayName("Прямой расчёт: выхлоп держит конденсатор, мощность и КПД считаются по ступеням")
    void condensingMarchEndsAtExhaustPressure() {
        PowerSearch search = search();
        TurbineResult result = search.march();

        assertEquals(CondensingTurbine.EXHAUST_PRESSURE, result.outletPressure(), 0.0,
                "давление за последней ступенью задаёт конденсатор, а не решётка");
        assertEquals(CondensingTurbine.STAGES, result.stages().size(), "ступеней в марше");
        assertEquals(1, result.sections().size(), "конденсационная турбина — один отсек");

        double internal = result.internalPower();
        assertEquals(internal * CondensingTurbine.ELECTROMECHANICAL, search.electricalPower(), 0.0,
                "N_э = N_вн · η_эм");

        assertTrue(internal > 0, "мощность положительна: " + internal + " кВт");
        assertTrue(result.efficiency() > 0 && result.efficiency() < 1,
                "КПД проточной части в (0, 1): " + result.efficiency());

        for (TurbineResult.StagePoint point : result.stages()) {
            StageResult stage = point.result();
            assertTrue(stage.efficiency() > 0 && stage.efficiency() <= 1,
                    () -> point.describe() + ": КПД ступени в (0, 1]");
            assertTrue(stage.outletPressure() < stage.inletPressure(),
                    () -> point.describe() + ": давление падает");
        }
    }

    @Test
    @DisplayName("Поиск зазора δ: по измеренной мощности восстанавливается износ ступени")
    void clearanceSearchRecoversWornStage() {
        PowerSearch search = search();
        double nominal = CondensingTurbine.nominalClearance(SUSPECT_STAGE);
        double projectPower = search.electricalPower();

        // имитируем износ: зазор целевой ступени вырос втрое
        double worn = nominal * 3.0;
        double wornPower = search.electricalPower(search.withClearance(0, SUSPECT_STAGE, worn));
        assertTrue(wornPower < projectPower,
                "увеличенный зазор снижает мощность: " + wornPower + " < " + projectPower);

        // диагностика: из проектной геометрии и измеренной мощности ищем зазор
        PowerSearch.SearchResult found = search.findClearance(wornPower, 0, SUSPECT_STAGE,
                nominal, nominal * 5.0);

        assertEquals(worn, found.parameter(), 1e-6,
                "найденный зазор совпадает с истинным износом");
        assertEquals(wornPower, found.achievedPower(), 1e-3,
                "мощность на найденной геометрии совпадает с измеренной");
    }
}
