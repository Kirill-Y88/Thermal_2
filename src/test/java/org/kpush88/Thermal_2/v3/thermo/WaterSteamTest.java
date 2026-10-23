package org.kpush88.Thermal_2.v3.thermo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Свойства воды и пара по IF97: реперные точки теплового расчёта.
 *
 * <p>Проверяются не «формулы вообще», а те числа, на которых держатся модели: состояние
 * свежего пара (24 МПа, 540 °C), изоэнтропа до давления конденсатора, насыщение при давлении
 * отбора 3.5 МПа и при вакууме 0.004 МПа. Значения насыщения совпадают с теми, что занесены в
 * {@code theory/09-диагностика-и-идентификация.md} для той же демонстрационной схемы, — значит,
 * расчётный слой v3 считает по той же таблице, что и прототип v1.
 *
 * <p>Единицы — инженерные: МПа, °C, кДж/кг, м³/кг. Именно их ждут модели, и именно их потеря
 * (например, забытый перевод в кельвины) была бы самой дорогой ошибкой обёртки.
 */
class WaterSteamTest {

    private final WaterSteam steam = new WaterSteam();

    @Test
    @DisplayName("Свежий пар: h и s по p и t в инженерных единицах")
    void readsFreshSteamState() {
        double enthalpy = steam.enthalpyPT(24.0, 540.0);

        assertEquals(3318.7754, enthalpy, 1e-3, "h(24 МПа, 540 °C)");
        assertEquals(6.17235, steam.entropyPH(24.0, enthalpy), 1e-5, "s(24 МПа, 540 °C)");
        assertEquals(540.0, steam.temperaturePH(24.0, enthalpy), 1e-2,
                "температура по (p, h) возвращается в °C; обратная функция IF97 даёт ~0.01 K");
    }

    @Test
    @DisplayName("Изоэнтропа: расширение до вакуума даёт h_ks = 1858.51 кДж/кг")
    void expandsIsentropically() {
        double entropy = steam.entropyPH(24.0, 3318.7754);

        assertEquals(1858.51, steam.enthalpyPS(0.004, entropy), 1e-2,
                "h_ks(p_к = 0.004 МПа) — то же число, что в theory/06; допуск — точность "
                        + "обратной функции IF97");
    }

    @Test
    @DisplayName("Насыщение при давлении отбора: t_нас, h′ и h″")
    void readsSaturationAtExtractionPressure() {
        assertEquals(242.5617, steam.saturationTemperature(3.5), 1e-4, "t_нас(3.5 МПа)");
        assertEquals(1049.7753, steam.saturatedLiquidEnthalpy(3.5), 1e-3, "h′(3.5 МПа)");
        assertEquals(2802.7435, steam.saturatedVapourEnthalpy(3.5), 1e-3, "h″(3.5 МПа)");
    }

    @Test
    @DisplayName("Насыщение при вакууме: конденсат и удельный объём для работы насоса")
    void readsSaturationInCondenser() {
        assertEquals(28.9615, steam.saturationTemperature(0.004), 1e-4, "t_нас(0.004 МПа)");
        assertEquals(121.4036, steam.saturatedLiquidEnthalpy(0.004), 1e-3, "h′(0.004 МПа)");
        assertEquals(0.00100410, steam.saturatedLiquidVolume(0.004), 1e-8, "v′(0.004 МПа)");
    }

    @Test
    @DisplayName("Точка вне области IF97 отвергается с указанием параметров")
    void rejectsPointOutsideRange() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> steam.enthalpyPT(1000.0, 540.0));

        assertTrue(error.getMessage().contains("вне области IF97"), error.getMessage());
        assertTrue(error.getMessage().contains("1000.0"), error.getMessage());
    }
}
