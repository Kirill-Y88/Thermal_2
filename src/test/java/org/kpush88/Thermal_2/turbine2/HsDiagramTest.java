package org.kpush88.Thermal_2.turbine2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kpush88.Thermal_2.turbine.Steam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Параметры hs-диаграммы второго поколения турбины.
 *
 * <p>Главная проверка — согласие с {@link Steam} первого поколения: если обе обёртки читают одно
 * и то же рабочее тело одинаково, числа двух моделей можно сравнивать. Дальше фиксируются
 * величины, ради которых класс и написан: энтропия по {@code (p, t)}, изоэнтропный перепад и
 * реальное расширение, параметры насыщения и точка hs-диаграммы.
 */
class HsDiagramTest {

    private final HsDiagram hs = new HsDiagram();
    private final Steam reference = new Steam();

    @Test
    @DisplayName("Обёртка v2 совпадает с turbine.Steam до 10⁻⁹")
    void agreesWithFirstGeneration() {
        assertEquals(reference.enthalpyPT(14.0, 550.0), hs.enthalpyPT(14.0, 550.0), 1e-9,
                "h(14 МПа, 550 °C)");
        assertEquals(reference.entropyPH(14.0, 3461.0), hs.entropyPH(14.0, 3461.0), 1e-9,
                "s(14 МПа, 3461)");
        assertEquals(reference.volumePH(14.0, 3461.0), hs.volumePH(14.0, 3461.0), 1e-15,
                "v(14 МПа)");
        assertEquals(reference.speedOfSoundPH(14.0, 3461.0), hs.speedOfSoundPH(14.0, 3461.0), 1e-9,
                "a(14 МПа)");
        assertEquals(reference.isentropicExponentPH(14.0, 3461.0),
                hs.isentropicExponentPH(14.0, 3461.0), 1e-12, "k(14 МПа)");
        assertEquals(reference.saturationTemperature(0.004), hs.saturationTemperature(0.004), 1e-9,
                "t_нас(0.004)");
        assertEquals(reference.saturatedLiquidEnthalpy(0.004), hs.saturatedLiquidEnthalpy(0.004),
                1e-9, "h′(0.004)");
        assertEquals(reference.saturatedVapourEnthalpy(0.004), hs.saturatedVapourEnthalpy(0.004),
                1e-9, "h″(0.004)");
        assertEquals(reference.saturatedLiquidVolume(0.004), hs.saturatedLiquidVolume(0.004),
                1e-15, "v′(0.004)");
    }

    @Test
    @DisplayName("Энтропия по (p, t): s(24 МПа, 540 °C) ≈ 6.17235, обратная — t по (p, s)")
    void readsEntropyByPressureAndTemperature() {
        double entropy = hs.entropyPT(24.0, 540.0);

        assertEquals(6.17235, entropy, 1e-5, "s(24 МПа, 540 °C)");
        assertEquals(540.0, hs.temperaturePS(24.0, entropy), 1e-2,
                "t по (24 МПа, s) возвращается в °C");
    }

    @Test
    @DisplayName("Изоэнтропный перепад 24 МПа/540 °C → 0.004 МПа: h_ks ≈ 1858.51")
    void computesIsentropicDropAndExpansion() {
        double hIn = hs.enthalpyPT(24.0, 540.0);
        double hKs = hs.isentropicEnthalpy(24.0, hIn, 0.004);
        double drop = hs.isentropicDrop(24.0, hIn, 0.004);

        assertEquals(1858.51, hKs, 1e-2, "h_ks(p_к = 0.004 МПа)");
        assertEquals(hIn - hKs, drop, 1e-9);
        assertEquals(drop, hs.isentropicDropPT(24.0, 540.0, 0.004), 1e-9,
                "перепад от (p, t) совпадает с перепадом от (p, h)");
        assertEquals(hKs, hs.expand(24.0, hIn, 0.004, 1.0), 1e-9, "η = 1 — изоэнтропа");
        assertEquals(hIn - 0.85 * drop, hs.expand(24.0, hIn, 0.004, 0.85), 1e-9);
        assertTrue(hs.expand(24.0, hIn, 0.004, 0.85) > hKs,
                "η < 1 даёт энтальпию выше изоэнтропной");
    }

    @Test
    @DisplayName("Насыщение: t_нас, p_нас и энтропии s′, s″ согласованы")
    void readsSaturation() {
        double p = 0.004;
        double tSat = hs.saturationTemperature(p);

        assertEquals(28.9615, tSat, 1e-4, "t_нас(0.004 МПа)");
        assertEquals(p, hs.saturationPressure(tSat), 1e-6, "обратная функция t_нас → p_нас");
        assertEquals(121.4036, hs.saturatedLiquidEnthalpy(p), 1e-3, "h′");
        assertEquals(2553.7093, hs.saturatedVapourEnthalpy(p), 1e-3, "h″");
        assertEquals(hs.entropyPH(p, hs.saturatedLiquidEnthalpy(p)), hs.saturatedLiquidEntropy(p),
                1e-3, "s′ совпадает с s(p, h′)");
        assertEquals(hs.entropyPH(p, hs.saturatedVapourEnthalpy(p)), hs.saturatedVapourEntropy(p),
                1e-3, "s″ совпадает с s(p, h″)");
        assertTrue(hs.saturatedVapourEntropy(p) > hs.saturatedLiquidEntropy(p));
        assertTrue(hs.saturatedVapourVolume(p) > hs.saturatedLiquidVolume(p));
    }

    @Test
    @DisplayName("Влажный пар в выхлопе: x = 0.8334; перегрев и точка hs-диаграммы")
    void readsWetSteamAndBuildsPoint() {
        double p = 0.004;
        double h = 2148.5;

        assertEquals(0.8334052921144882, hs.vapourFractionPH(p, h), 1e-12);
        assertTrue(hs.isWet(p, h));
        assertFalse(hs.isSuperheated(p, h));
        assertEquals(0.0, hs.superheat(p, h), 1e-6, "в двухфазной области перегрев нулевой");

        assertEquals(1.0, hs.vapourFractionPH(14.0, 3461.0), 1e-12,
                "в перегретом паре x = 1");
        assertTrue(hs.isSuperheated(14.0, 3461.0));
        assertTrue(hs.superheat(14.0, 3461.0) > 0.0);

        HsDiagram.Point wet = hs.pointPH(p, h);
        assertTrue(wet.isWet());
        assertEquals(0.8334052921144882, wet.vapourFraction(), 1e-12);
        assertTrue(wet.describe().contains("x="), wet.describe());

        HsDiagram.Point superheated = hs.pointPT(14.0, 550.0);
        assertTrue(superheated.isSuperheated());
        assertTrue(superheated.describe().contains("перегр"), superheated.describe());
    }

    @Test
    @DisplayName("Обратная задача насыщения: s″(p_н) = s, p_н ≈ 3.1045 МПа")
    void invertsSaturatedVapourEntropy() {
        double entropy = hs.entropyPT(24.0, 540.0);
        double pressure = hs.saturationPressureByEntropy(entropy);

        assertEquals(3.104469, pressure, 1e-5, "p_н для s(24 МПа, 540 °C)");
        assertEquals(entropy, hs.saturatedVapourEntropy(pressure), 1e-9,
                "обратная функция возвращает точку на линии насыщения");
        assertTrue(hs.saturationPressureByEntropy(6.0) > hs.saturationPressureByEntropy(7.0),
                "s″ убывает с давлением: большей энтропии соответствует меньшее давление");
        for (double p : new double[]{0.004, 0.1, 1.0, 3.0, 10.0, 20.0}) {
            assertEquals(p, hs.saturationPressureByEntropy(hs.saturatedVapourEntropy(p)), 1e-6,
                    "круговой расчёт s″ → p при p = " + p + " МПа");
        }
        assertThrows(IllegalArgumentException.class, () -> hs.saturationPressureByEntropy(10.0),
                "выше s″ при нижней границе изоэнтропа не пересекает насыщение");
        assertThrows(IllegalArgumentException.class, () -> hs.saturationPressureByEntropy(3.0),
                "ниже s″ при 22 МПа обратная задача у критической точки не решается");
    }

    @Test
    @DisplayName("Изоэнтропное разбиение 24 МПа/540 °C → 0.004 МПа: 515.49 + 944.77")
    void splitsIsentropicDropAtSaturation() {
        double pIn = 24.0;
        double hIn = hs.enthalpyPT(pIn, 540.0);
        double pOut = 0.004;
        double drop = hs.isentropicDrop(pIn, hIn, pOut);

        HsDiagram.HeatDropSplit split = hs.splitDrop(pIn, hIn, pOut);

        // Допуск 0.01 кДж/кг: эталон считался по s(p,t), а метод — по s(p,h). Разница путей
        // ~1.2e-5 кДж/(кг·K) даёт до 0.004 кДж/кг; сумма частей проверяется точно ниже.
        assertEquals(515.492406, split.superheated(), 1e-2, "H_перегр");
        assertEquals(944.772802, split.wet(), 1e-2, "H_влажн");
        assertEquals(drop, split.total(), 1e-9, "сумма частей равна располагаемому перепаду");
        assertEquals(split.superheated(), hs.superheatedDrop(pIn, hIn, pOut), 1e-12);
        assertEquals(split.wet(), hs.wetDrop(pIn, hIn, pOut), 1e-12);
        assertEquals(split.wet() / drop, split.wetFraction(), 1e-12);
        assertFalse(split.isFullySuperheated());
        assertFalse(split.isFullyWet());
        assertTrue(split.wetFraction() > 0.0 && split.wetFraction() < 1.0);
        assertTrue(split.describe().contains("H_перегр"), split.describe());
    }

    @Test
    @DisplayName("Разбиение действительного перепада: 24 МПа/540 °C → 0.004 МПа, η = 0.87")
    void splitsActualDropAtSaturation() {
        double pIn = 24.0;
        double hIn = hs.enthalpyPT(pIn, 540.0);
        double pOut = 0.004;
        double hOut = hs.expand(pIn, hIn, pOut, 0.87);

        HsDiagram.HeatDropSplit split = hs.splitDrop(pIn, hIn, pOut, hOut);

        assertEquals(hIn - hOut, split.total(), 1e-9, "сумма частей равна действительному перепаду");
        assertEquals(526.930996, split.superheated(), 1e-2, "H_перегр по действительной линии");
        assertEquals(743.499735, split.wet(), 1e-2, "H_влажн по действительной линии");
        assertEquals(split.superheated(), hs.superheatedDrop(pIn, hIn, pOut, hOut), 1e-12);
        assertEquals(split.wet(), hs.wetDrop(pIn, hIn, pOut, hOut), 1e-12);
        assertTrue(split.superheated() > hs.splitDrop(pIn, hIn, pOut).superheated(),
                "действительная линия пересекает насыщение при меньшем давлении, поэтому "
                        + "перегретая часть больше");
    }

    @Test
    @DisplayName("Перепад целиком в перегретом паре: 24 МПа/540 °C → 10 МПа")
    void keepsFullySuperheatedDrop() {
        double pIn = 24.0;
        double hIn = hs.enthalpyPT(pIn, 540.0);
        double pOut = 10.0;

        HsDiagram.HeatDropSplit isentropic = hs.splitDrop(pIn, hIn, pOut);
        assertEquals(0.0, isentropic.wet(), 1e-12);
        assertEquals(isentropic.total(), isentropic.superheated(), 1e-12);
        assertTrue(isentropic.isFullySuperheated());
        assertEquals(0.0, isentropic.wetFraction(), 1e-12);

        double hOut = hs.expand(pIn, hIn, pOut, 0.9);
        HsDiagram.HeatDropSplit real = hs.splitDrop(pIn, hIn, pOut, hOut);
        assertEquals(hIn - hOut, real.superheated(), 1e-12);
        assertEquals(0.0, real.wet(), 1e-12);
        assertTrue(real.isFullySuperheated());
    }

    @Test
    @DisplayName("Перепад целиком во влажном паре: вход 0.004 МПа, h = 2400 кДж/кг")
    void keepsFullyWetDrop() {
        double pIn = 0.004;
        double hIn = 2400.0;
        double pOut = 0.002;
        double drop = hs.isentropicDrop(pIn, hIn, pOut);

        HsDiagram.HeatDropSplit split = hs.splitDrop(pIn, hIn, pOut);
        assertEquals(0.0, split.superheated(), 1e-12);
        assertEquals(drop, split.wet(), 1e-12);
        assertTrue(split.isFullyWet());
        assertEquals(1.0, split.wetFraction(), 1e-12);

        double hOut = hIn - 0.8 * drop;
        assertTrue(hs.vapourFractionPH(pOut, hOut) < 1.0, "конец тоже должен быть влажным");
        HsDiagram.HeatDropSplit real = hs.splitDrop(pIn, hIn, pOut, hOut);
        assertEquals(0.0, real.superheated(), 1e-12);
        assertEquals(hIn - hOut, real.wet(), 1e-12);
        assertTrue(real.isFullyWet());
    }

    @Test
    @DisplayName("Граница насыщения: конец изоэнтропы ровно при p_н даёт нулевую влажную часть")
    void stopsAtSaturationBoundary() {
        double pIn = 24.0;
        double hIn = hs.enthalpyPT(pIn, 540.0);
        double boundaryPressure = hs.saturationPressureByEntropy(hs.entropyPH(pIn, hIn));

        HsDiagram.HeatDropSplit split = hs.splitDrop(pIn, hIn, boundaryPressure);

        assertEquals(0.0, split.wet(), 1e-9, "конец изоэнтропы лежит на линии насыщения");
        assertEquals(split.total(), split.superheated(), 1e-12);
        assertTrue(split.isFullySuperheated());
    }

    @Test
    @DisplayName("Отрицательный теплоперепад отвергается с указанием причины")
    void rejectsImpossibleSplits() {
        double pIn = 24.0;
        double hIn = hs.enthalpyPT(pIn, 540.0);

        IllegalArgumentException isentropic = assertThrows(IllegalArgumentException.class,
                () -> hs.splitDrop(pIn, hIn, 30.0));
        assertTrue(isentropic.getMessage().contains("отрицательный"), isentropic.getMessage());

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
                () -> hs.splitDrop(pIn, hIn, 0.004, hIn + 10.0));
        assertTrue(actual.getMessage().contains("отрицательный"), actual.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> new HsDiagram.HeatDropSplit(-1.0, 10.0));
    }

    @Test
    @DisplayName("Точка вне области IF97 отвергается с указанием параметров")
    void rejectsPointOutsideRange() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> hs.enthalpyPT(1000.0, 540.0));

        assertTrue(error.getMessage().contains("вне области IF97"), error.getMessage());
        assertTrue(error.getMessage().contains("1000.0"), error.getMessage());
    }

    @Test
    @DisplayName("Обратная задача: перепад 175 кДж/кг → 1.4997 МПа, перепад восстанавливается")
    void findsPressureByIsentropicDrop() {
        double hIn = hs.enthalpyPT(2.716, 430.0);

        double pressure = hs.pressureForIsentropicDrop(2.716, hIn, 175.0);

        assertEquals(1.4997, pressure, 1e-3);
        assertEquals(175.0, hs.isentropicDrop(2.716, hIn, pressure), 1e-6,
                "давление должно возвращать заданный перепад");
        assertTrue(hs.pressureForIsentropicDrop(2.716, hIn, 100.0) > pressure,
                "меньшему перепаду соответствует большее давление");
    }

    @Test
    @DisplayName("Обратная задача вне области: перепад ≤ 0 или больше перепада до 0.000612 МПа")
    void rejectsImpossiblePressureByDrop() {
        double hIn = hs.enthalpyPT(2.716, 430.0);
        double total = hs.isentropicDrop(2.716, hIn, 0.000612);

        assertThrows(IllegalArgumentException.class,
                () -> hs.pressureForIsentropicDrop(2.716, hIn, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> hs.pressureForIsentropicDrop(2.716, hIn, total));
        assertThrows(IllegalArgumentException.class,
                () -> hs.pressureForIsentropicDrop(0.0001, hIn, 10.0));
    }

}
