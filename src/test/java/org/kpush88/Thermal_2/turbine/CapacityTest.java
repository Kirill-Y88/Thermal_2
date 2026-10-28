package org.kpush88.Thermal_2.turbine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Пропускная способность: числа, на которых держится весь марш ступеней.
 *
 * <p>Проверяются не формулы «вообще», а состояния сечений реперного цикла
 * ({@code SolveTest3}: 14 МПа / 550 °C, отборы 3.5 / 2.5 / 0.8 / 0.15 МПа, вакуум 0.004 МПа).
 * Значения {@code k}, {@code β_кр} и {@code G_кр/(μ·F)} сняты с IF97 при энтальпиях этих сечений,
 * поэтому тест заодно фиксирует, что показатель изоэнтропы берётся из скорости звука, а не из
 * справочной константы 1.3.
 *
 * <p>Второй сюжет — обратимость: {@code pressure(flow(p)) == p} и {@code flow(pressure(G)) == G}.
 * Марш ступени опирается на обращение на каждом шаге, и «почти монотонная» функция здесь была бы
 * дороже, чем любая ошибка в коэффициентах.
 */
class CapacityTest {

    private final Steam steam = new Steam();

    @Test
    @DisplayName("Критический удельный расход убывает по ходу расширения: 15777 → 10.35 кг/(с·м²)")
    void criticalUnitFlowFallsAlongExpansion() {
        // Состояния сечений реперного цикла: p, h и снятые с IF97 значения G_кр/(μ·F).
        double[][] sections = {
                {14.0, 3461.0, 15777.259409096174},
                {3.5, 3108.8, 4482.842390035028},
                {2.5, 3034.5, 3308.737474913039},
                {0.8, 2818.6, 1182.9777237714256},
                {0.15, 2564.7, 274.44126470357406},
                {0.004, 2148.5, 10.346890312092597},
        };
        for (double[] section : sections) {
            double unit = Capacity.criticalUnitFlow(steam, section[0], section[1]);
            assertEquals(section[2], unit, 1e-6,
                    "G_кр/(μ·F) при p = " + section[0] + " МПа, h = " + section[1] + " кДж/кг");
        }
    }

    @Test
    @DisplayName("Показатель изоэнтропы считается по скорости звука: 1.2796 на входе, 3.1986 в вакууме")
    void exponentComesFromSpeedOfSound() {
        assertEquals(1.2796141656610103, steam.isentropicExponentPH(14.0, 3461.0), 1e-12,
                "k на входе в турбину близок к табличному 1.3");
        assertEquals(3.1985963798329635, steam.isentropicExponentPH(0.004, 2148.5), 1e-12,
                "k во влажном паре втрое больше: двухфазная скорость звука ниже");
        assertEquals(0.549438974998874, Capacity.criticalRatio(1.2796141656610103), 1e-12,
                "β_кр при k = 1.2796");
        assertEquals(0.33996567778563, Capacity.criticalRatio(3.1985963798329635), 1e-12,
                "β_кр при k = 3.1986: «табличное» 0.546 завысило бы расход в полтора раза");
    }

    @Test
    @DisplayName("Запирание: при β ниже критического расход не зависит от давления за решёткой")
    void chokedFlowDoesNotDependOnOutletPressure() {
        Capacity capacity = Capacity.of(steam, 14.0, 3461.0, 1.0, 0.96);

        assertEquals(0.96 * 15777.259409096174, capacity.maxFlow(), 1e-6,
                "G_кр = μ·F·(G_кр/(μ·F)) при F = 1 м²");
        assertEquals(capacity.maxFlow(), capacity.flow(0.004), 1e-9,
                "при β = 2.9·10⁻⁴ решётка заперта");
        assertEquals(capacity.maxFlow(), capacity.flow(7.0), 1e-9,
                "при β = 0.5, ниже β_кр = 0.5494, расход тот же");
        assertEquals(0.549438974998874 * 14.0, capacity.criticalPressure(), 1e-9,
                "критическое давление β_кр·p₀");
        assertTrue(capacity.choked(7.0), "7 МПа < 7.69 МПа — запирание");
        assertFalse(capacity.choked(7.8), "7.8 МПа > 7.69 МПа — течение дозвуковое");
    }

    @Test
    @DisplayName("Непрерывность на границе запирания: дозвуковая ветвь сходится с G_кр")
    void flowIsContinuousAtCriticalRatio() {
        Capacity capacity = Capacity.of(steam, 3.5, 3108.8, 0.002, 0.96);

        // Чуть выше β_кр — расход считается по общему уравнению, чуть ниже — по критическому.
        double justAbove = capacity.flow(capacity.criticalPressure() * (1.0 + 1.0e-6));

        assertEquals(capacity.maxFlow(), justAbove, capacity.maxFlow() * 1.0e-6,
                "на границе запирания обе ветви обязаны давать одно и то же; расхождение в √2 "
                        + "означало бы неверный коэффициент критической формулы");
    }

    @Test
    @DisplayName("Дозвуковой режим: расход растёт при понижении давления за решёткой")
    void subcriticalFlowGrowsAsOutletPressureFalls() {
        Capacity capacity = Capacity.of(steam, 0.15, 2564.7, 0.016, 0.96);

        double atHigh = capacity.flow(0.14);
        double atMiddle = capacity.flow(0.10);
        double atLow = capacity.flow(0.08);

        assertTrue(atHigh < atMiddle && atMiddle < atLow,
                "монотонность: " + atHigh + " < " + atMiddle + " < " + atLow);
        assertTrue(atLow < capacity.maxFlow(),
                "при β = 0.533 > β_кр = 0.4729 расход ещё ниже критического");
    }

    @Test
    @DisplayName("Обращение: pressure(flow(p)) возвращает то же давление с точностью 10⁻⁹ МПа")
    void outletPressureInvertsFlow() {
        Capacity capacity = Capacity.of(steam, 0.15, 2564.7, 0.016, 0.96);

        for (double pressure : new double[]{0.14, 0.10, 0.085}) {
            double flow = capacity.flow(pressure);
            assertEquals(pressure, capacity.pressure(flow), 1e-9,
                    "давление за решёткой при расходе " + flow + " кг/с");
        }
    }

    @Test
    @DisplayName("Обращение: flow(pressure(G)) возвращает тот же расход")
    void flowInvertsPressure() {
        Capacity capacity = Capacity.of(steam, 3.5, 3108.8, 0.002, 0.96);

        for (double flow : new double[]{1.0, 5.0, 8.0}) {
            double pressure = capacity.pressure(flow);
            assertEquals(flow, capacity.flow(pressure), 1e-9,
                    "расход при давлении " + pressure + " МПа");
        }
    }

    @Test
    @DisplayName("Сверхкритический расход: давления не существует, возвращается критическое")
    void tooLargeFlowHasNoSolution() {
        Capacity capacity = Capacity.of(steam, 0.8, 2818.6, 0.005, 0.96);

        double impossible = 1.5 * capacity.maxFlow();

        assertEquals(capacity.criticalPressure(), capacity.pressure(impossible), 1e-12,
                "решения нет — марш должен видеть предел, а не выдуманное давление");
    }

    @Test
    @DisplayName("Ошибки входа называют величину: давление за решёткой, μ, площадь")
    void rejectsNonsenseInput() {
        Capacity capacity = Capacity.of(steam, 0.8, 2818.6, 0.005, 0.96);

        IllegalArgumentException samePressure = assertThrows(IllegalArgumentException.class,
                () -> capacity.flow(0.8));
        assertTrue(samePressure.getMessage().contains("0.8"), samePressure.getMessage());

        IllegalArgumentException badDischarge = assertThrows(IllegalArgumentException.class,
                () -> Capacity.of(steam, 0.8, 2818.6, 0.005, 1.2));
        assertTrue(badDischarge.getMessage().contains("1.2"), badDischarge.getMessage());

        IllegalArgumentException badArea = assertThrows(IllegalArgumentException.class,
                () -> Capacity.of(steam, 0.8, 2818.6, 0.0, 0.96));
        assertTrue(badArea.getMessage().contains("площадь"), badArea.getMessage());
    }
}
