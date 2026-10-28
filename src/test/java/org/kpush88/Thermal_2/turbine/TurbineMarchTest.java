package org.kpush88.Thermal_2.turbine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Марш проточной части на опорной машине 10 МВт: воспроизводит ли пакет цикл {@code v3}.
 *
 * <p>Цикл ({@code SolveTest3}) задаёт давления отборов руками и получает мощности из одного
 * {@code η_oi = 0.85}. Здесь те же давления <b>вычисляются</b> — из площадей ступеней и расхода.
 * Совпадение давлений и есть предмет проверки: оно означает, что геометрия
 * ({@link ReferenceTurbine}) и модель ступени согласованы между собой и с циклом.
 *
 * <p>Числа в утверждениях — не «эталон из учебника», а зафиксированный результат калибровки:
 * они охраняют модель от незаметного сдвига. Поэтому допуски по давлениям узкие (0.5 %), а по
 * мощности — 1 %: мощность в этой модели зависит от явных потерь, и её разница с циклом
 * (+2 % у цикла) объяснена в {@link ReferenceTurbine}.
 */
class vTurbineMarchTest {

    private static final List<String> SECTION_NAMES =
            List.of("ЦВД", "ЦСД-1", "ЦСД-2", "ЦСД-3", "ЦНД");

    @Test
    @DisplayName("Отборы встают на давления цикла: 3.5 / 2.5 / 0.8 / 0.15 МПа и вакуум 0.004 МПа")
    void extractionPressuresMatchCycle() {
        TurbineResult result = ReferenceTurbine.march();

        assertEquals(5, result.sections().size(), "отсеков пять — по одному на отбор");
        for (int index = 0; index < ReferenceTurbine.SECTION_PRESSURES.length; index++) {
            double expected = ReferenceTurbine.SECTION_PRESSURES[index];
            TurbineResult.SectionResult section = result.sections().get(index);
            assertEquals(expected, section.outletPressure(), expected * 0.005,
                    () -> "давление за отсеком «" + section.name() + "» должно быть давлением "
                            + "отбора цикла: " + expected + " МПа");
        }
        assertEquals(0.004, result.outletPressure(), 0.00002,
                "давление перед конденсатором держит вакуум, его задаёт последняя ступень ЦНД");
    }

    @Test
    @DisplayName("Расходы по отсекам: G₀ минус отобранный пар, отбор берётся на выходе отсека")
    void flowsFollowExtractions() {
        TurbineResult result = ReferenceTurbine.march();

        double flow = ReferenceTurbine.INLET_FLOW;
        assertEquals(flow, result.sections().get(0).flow(), 1e-12,
                "в ЦВД идёт весь свежий пар");
        double[] extractionFlows = {ReferenceTurbine.HP2_FLOW, ReferenceTurbine.HP1_FLOW,
                ReferenceTurbine.DEAERATOR_FLOW, ReferenceTurbine.LP_FLOW, 0.0};
        for (int index = 0; index < result.sections().size(); index++) {
            TurbineResult.SectionResult section = result.sections().get(index);
            assertEquals(flow, section.flow(), 1e-9,
                    () -> "расход через отсек «" + section.name() + "»");
            assertEquals(extractionFlows[index], section.extractionFlow(), 1e-12,
                    () -> "отбор на выходе отсека «" + section.name() + "»");
            flow -= extractionFlows[index];
        }
        assertEquals(ReferenceTurbine.INLET_FLOW - ReferenceTurbine.HP2_FLOW
                        - ReferenceTurbine.HP1_FLOW - ReferenceTurbine.DEAERATOR_FLOW
                        - ReferenceTurbine.LP_FLOW,
                result.sections().get(4).flow(), 1e-9,
                "в конденсатор идёт то, что не забрали отборы");
    }

    @Test
    @DisplayName("КПД отсеков 0.80…0.86 и мощность 9.94 МВт: цикл получил бы 10.15 МВт")
    void sectionEfficienciesAndPower() {
        TurbineResult result = ReferenceTurbine.march();

        double[] efficiencies = {0.853, 0.839, 0.858, 0.841, 0.799};
        double[] powers = {3325.0, 657.0, 1710.0, 1833.0, 2418.0};
        for (int index = 0; index < SECTION_NAMES.size(); index++) {
            TurbineResult.SectionResult section = result.sections().get(index);
            assertEquals(SECTION_NAMES.get(index), section.name(), "порядок отсеков");
            assertEquals(efficiencies[index], section.efficiency(), 0.005,
                    () -> "η_oi отсека «" + section.name() + "»");
            assertEquals(powers[index], section.power(), powers[index] * 0.01,
                    () -> "мощность отсека «" + section.name() + "», кВт");
            assertTrue(section.efficiency() < 1.0,
                    () -> "КПД отсека «" + section.name() + "» не может быть единицей: "
                            + "потери ступеней считаются явно");
        }

        assertEquals(9943.0, result.internalPower(), 50.0,
                "внутренняя мощность: 116 против 133 кВт на кг/с у цикла — разница в потерях "
                        + "ступеней и в выходной скорости, которая не возвращается в поток");
        assertEquals(0.837, result.efficiency(), 0.005, "КПД проточной части в целом");
    }

    @Test
    @DisplayName("Выхлоп: h = 2143.7 кДж/кг, x = 0.831 — у цикла 2148.5 и 0.833")
    void exhaustMatchesCycle() {
        TurbineResult result = ReferenceTurbine.march();

        assertEquals(2143.7, result.outletEnthalpy(), 2.0, "энтальпия на выхлопе, кДж/кг");
        assertEquals(0.8314, result.outletQuality(), 0.002, "степень сухости на выхлопе");
        // Цикл получает те же величины из одного η_oi = 0.85 на всю турбину; расхождение — это
        // цена поступенчатого расчёта с явными потерями (см. ReferenceTurbine).
        assertEquals(2148.5, result.outletEnthalpy(), 0.01 * 2148.5,
                "энтальпия выхлопа против цикла v3");
        assertEquals(0.833, result.outletQuality(), 0.005, "сухость выхлопа против цикла v3");
        assertTrue(result.outletQuality() > 0.80,
                () -> "сухость на выхлопе не должна проваливаться ниже 0.80 — иначе последние "
                        + "ступени работают на каплях: " + result.outletQuality());
    }

    @Test
    @DisplayName("Машина спроектирована, а не подобрана: u/c₁ = 0.45…0.57 на всех 27 ступенях")
    void everyStageSitsInTheWorkingRange() {
        TurbineResult result = ReferenceTurbine.march();

        assertEquals(27, result.stages().size(), "10 + 2 + 5 + 5 + 5 ступеней");
        assertEquals(27, ReferenceTurbine.geometry().stageCount(), "ступени марша = ступени геометрии");

        double minRatio = Double.MAX_VALUE;
        double maxRatio = 0.0;
        double pressure = ReferenceTurbine.INLET_PRESSURE;
        double quality = 1.0;
        for (TurbineResult.StagePoint point : result.stages()) {
            StageResult stage = point.result();
            minRatio = Math.min(minRatio, stage.velocityRatio());
            maxRatio = Math.max(maxRatio, stage.velocityRatio());

            assertTrue(stage.outletPressure() < stage.inletPressure(),
                    () -> point.describe() + ": давление обязано падать на каждой ступени");
            assertEquals(pressure, stage.inletPressure(), pressure * 1e-9,
                    () -> point.describe() + ": ступень начинается там, где кончилась предыдущая");
            assertTrue(stage.outletQuality() <= quality + 1e-12,
                    () -> point.describe() + ": сухость не может расти по ходу расширения");
            pressure = stage.outletPressure();
            quality = stage.outletQuality();
        }

        assertEquals(0.448, minRatio, 0.01, "минимальное u/c₁ по проточной части");
        assertEquals(0.564, maxRatio, 0.01, "максимальное u/c₁ по проточной части");
        assertFalse(result.stages().get(0).result().choked(),
                "первая ступень ЦВД работает на расчётном режиме, а не на запертом горле");
    }

    @Test
    @DisplayName("Опечатка в имени отбора не проходит молча: иначе отбор просто выключился бы")
    void unknownExtractionIsNamed() {
        TurbineMarch march = new TurbineMarch(ReferenceTurbine.steam(), ReferenceTurbine.geometry());
        TurbineInput typo = TurbineInput.of(ReferenceTurbine.INLET_PRESSURE,
                ReferenceTurbine.INLET_TEMPERATURE, ReferenceTurbine.INLET_FLOW,
                Map.of("ПВД1", ReferenceTurbine.HP1_FLOW));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> march.solve(typo), "«ПВД1» и «ПВД-1» — разные имена отборов");

        assertTrue(failure.getMessage().contains("ПВД1"), () -> "в ошибке назван виновник: "
                + failure.getMessage());
        assertTrue(failure.getMessage().contains("ПВД-1"), () -> "в ошибке перечислены "
                + "известные отборы: " + failure.getMessage());
    }

    @Test
    @DisplayName("Отбор больше оставшегося пара: марш падает, а не считает отрицательный расход")
    void extractionBiggerThanFlowFails() {
        TurbineMarch march = new TurbineMarch(ReferenceTurbine.steam(), ReferenceTurbine.geometry());
        Map<String, Double> greedy = new LinkedHashMap<>(ReferenceTurbine.input().extractionFlows());
        greedy.put("ПНД", 100.0);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> march.solve(ReferenceTurbine.input().withExtractions(greedy)));

        assertTrue(failure.getMessage().contains("весь оставшийся пар"),
                () -> "ошибка должна объяснять, что пар кончился: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("ПНД"), () -> "и называть отбор: "
                + failure.getMessage());
    }
}
