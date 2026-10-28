package org.kpush88.Thermal_2.turbine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kpush88.Thermal_2.v3.thermo.WaterSteam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Свойства рабочего тела для проточной части.
 *
 * <p>Главная проверка — согласие с обёрткой цикла {@link WaterSteam}: двойник и {@code v3}
 * должны видеть одно и то же рабочее тело, иначе их числа нельзя сравнивать. Дальше
 * фиксируются те свойства, которых у цикла не было и ради которых написана эта обёртка:
 * удельный объём, скорость звука, показатель изоэнтропы и степень сухости.
 */
class SteamTest {

    private final Steam steam = new Steam();

    @Test
    @DisplayName("Свежий пар: обёртка двойника совпадает с обёрткой цикла до 10⁻⁹ кДж/кг")
    void agreesWithCycleWrapper() {
        WaterSteam cycle = new WaterSteam();

        assertEquals(cycle.enthalpyPT(14.0, 550.0), steam.enthalpyPT(14.0, 550.0), 1e-9,
                "h(14 МПа, 550 °C)");
        assertEquals(cycle.entropyPH(14.0, 3461.0), steam.entropyPH(14.0, 3461.0), 1e-9,
                "s(14 МПа, 3461 кДж/кг)");
        assertEquals(cycle.saturationTemperature(0.004), steam.saturationTemperature(0.004), 1e-9,
                "t_нас(0.004 МПа)");
    }

    @Test
    @DisplayName("Состояние перед первой ступенью: v = 0.024764 м³/кг, a = 666.06 м/с, k = 1.2796")
    void readsStateBeforeFirstStage() {
        assertEquals(0.024763625087853392, steam.volumePH(14.0, 3461.0), 1e-15, "v(14 МПа)");
        assertEquals(666.0558507944346, steam.speedOfSoundPH(14.0, 3461.0), 1e-9, "a(14 МПа)");
        assertEquals(1.2796141656610103, steam.isentropicExponentPH(14.0, 3461.0), 1e-12,
                "k = a²/(p·v)");
        assertEquals(40.38, steam.densityPH(14.0, 3461.0), 0.01, "ρ = 1/v, кг/м³");
    }

    @Test
    @DisplayName("Влажный пар в последней ступени: x = 0.8334 при p = 0.004 МПа, h = 2148.5")
    void readsWetSteamAtExhaust() {
        assertEquals(0.8334052921144882, steam.vapourFractionPH(0.004, 2148.5), 1e-12,
                "x_к реперного цикла ниже эрозионного предела 0.86 — это и должен показать двойник");
        assertEquals(121.40356367708806, steam.saturatedLiquidEnthalpy(0.004), 1e-9, "h′");
        assertEquals(2553.7092563609326, steam.saturatedVapourEnthalpy(0.004), 1e-9, "h″");
        assertEquals(1.0, steam.vapourFractionPH(14.0, 3461.0), 1e-12,
                "в перегретом паре степень сухости равна единице");
    }

    @Test
    @DisplayName("Точка вне области IF97 отвергается с указанием параметров")
    void rejectsPointOutsideRange() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> steam.speedOfSoundPH(1000.0, 3461.0));

        assertTrue(error.getMessage().contains("вне области IF97"), error.getMessage());
        assertTrue(error.getMessage().contains("1000.0"), error.getMessage());
    }
}
