package org.kpush88.Thermal_2.turbine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Расчёт одной ступени: давления, треугольники скоростей и потери — на ступенях опорной машины.
 *
 * <p>Первая ступень ЦВД ({@link ReferenceTurbine#referenceStage()}) — та, по которой настраивалась
 * вся проточная часть, поэтому её числа выписаны полностью: p₂, работа, КПД, обе скорости и
 * {@code u/c₁}. Если расчёт ступени сдвинется, это видно здесь, а не в мощности турбины на
 * четвёртом знаке.
 *
 * <p>Второй сюжет — <b>баланс энергии</b>. Работа, выходная скорость и энтальпия за ступенью
 * связаны уравнением {@code h₂ = h₁ − W − c₂²/2}, и это единственное место, где модель проверяется
 * без обращения к «эталонным» числам: баланс обязан замыкаться до последнего знака, иначе где-то
 * потерян или дважды учтён килоджоуль.
 *
 * <p>Третий сюжет — <b>отказы</b>. Ступень отказывается считать в трёх случаях, и все три здесь
 * проверены: расход больше критического, треугольники не сходятся (работа отрицательна) и давление
 * за ступенью не ниже, чем перед ней.
 */
class StageSolverTest {

    /** Состояние перед первой ступенью ЦВД: свежий пар цикла v3 (берётся из опорной машины). */
    private static final double INLET_PRESSURE = ReferenceTurbine.INLET_PRESSURE;
    private static final double INLET_TEMPERATURE = ReferenceTurbine.INLET_TEMPERATURE;
    private static final double INLET_FLOW = ReferenceTurbine.INLET_FLOW;

    private final Steam steam = ReferenceTurbine.steam();
    private final StageSolver solver = ReferenceTurbine.stageSolver();
    private final StageGeometry reference = ReferenceTurbine.referenceStage();
    private final double inletEnthalpy = steam.enthalpyPT(INLET_PRESSURE, INLET_TEMPERATURE);

    private StageResult solveReference() {
        return solver.solve(reference, INLET_PRESSURE, inletEnthalpy, INLET_FLOW);
    }

    @Test
    @DisplayName("Опорная ступень ЦВД: p₂ = 12.188 МПа, W = 38.01 кДж/кг, η = 0.803")
    void referenceStageMatchesDesign() {
        StageResult stage = solveReference();

        assertEquals(12.187637, stage.outletPressure(), 0.0005,
                "давление за ступенью задаёт давление отбора ПВД-2 через десять ступеней ЦВД");
        assertEquals(47.3369, stage.isentropicDrop(), 0.01, "располагаемый перепад, кДж/кг");
        assertEquals(38.0129, stage.internalWork(), 0.01, "внутренняя работа ступени, кДж/кг");
        assertEquals(0.8030, stage.efficiency(), 0.001, "η_oi ступени");
        assertEquals(3421.7410, stage.outletEnthalpy(), 0.5, "энтальпия за ступенью, кДж/кг");

        assertEquals(283.322, stage.velocity(), 0.1, "c₁ на выходе из сопловой решётки, м/с");
        assertEquals(49.666, stage.exitVelocity(), 0.1, "c₂ на выходе из ступени, м/с");
        assertEquals(133.518, stage.circumferentialSpeed(), 0.01,
                "u = π·D·n/60 при D = 0.85 м и n = 3000 об/мин");
        assertEquals(0.4713, stage.velocityRatio(), 0.001,
                "u/c₁ — наивыгоднейшее отношение для активной ступени 0.45…0.50");

        assertEquals(1.0, stage.outletQuality(), 1e-9,
                "первая ступень ЦВД работает на перегретом паре: влажностных потерь нет");
        assertFalse(stage.choked(),
                "при расходе 9.4128 кг/с горло ступени не заперто: запас почти в 1.4 раза");
    }

    @Test
    @DisplayName("Баланс энергии: h₂ = h₁ − W − c₂²/2 замыкается, а потери делятся на три части")
    void energyBalanceClosesAndLossesSplit() {
        StageResult stage = solveReference();

        double exitLoss = stage.exitVelocity() * stage.exitVelocity() / 2000.0;
        assertEquals(inletEnthalpy - stage.outletEnthalpy(), stage.internalWork() + exitLoss,
                1e-9, "энтальпия за ступенью получается из баланса энергии, а не подбирается");

        assertEquals(9.3240, stage.totalLoss(), 0.01,
                "все потери ступени H − W, включая выходную скорость");
        // Выходная скорость из работы вычтена, но в следующую ступень не возвращается (c₀ = 0):
        // в потери она попадает целиком, и это самая крупная их часть у коротких лопаток.
        assertEquals(1.2334, exitLoss, 0.01, "c₂²/2 — потери с выходной скоростью, кДж/кг");
        assertEquals(2.8333, stage.empiricalLoss(), 0.01, "вторичные + утечки + влажностные");
        assertEquals(5.2574, stage.totalLoss() - exitLoss - stage.empiricalLoss(), 0.01,
                "остаток — профильные потери, они сидят в φ и ψ и отдельно не считаются");
    }

    @Test
    @DisplayName("Потери считаются по профилю: ζ_вт·(b/l)·H, ζ_упл·(δ/l)·H, влажностные — ноль")
    void lossesFollowProfileFormulas() {
        StageResult stage = solveReference();
        StageProfile profile = reference.profile();

        assertEquals(profile.secondaryLoss() * reference.aspectRatio() * stage.isentropicDrop(),
                stage.secondaryLoss(), 1e-9,
                "вторичные потери: доля от (b/l)·H, поэтому короткая лопатка теряет больше");
        assertEquals(profile.leakageLoss() * reference.clearanceRatio() * stage.isentropicDrop(),
                stage.leakageLoss(), 1e-9, "утечки: доля от (δ/l)·H");
        assertEquals(0.0, stage.wetnessLoss(), 1e-12,
                "на перегретом паре влажностных потерь нет");

        assertTrue(stage.secondaryLoss() > stage.leakageLoss(),
                "у опорной ступени b/l = " + reference.aspectRatio() + " — вторичные потери "
                        + "заметно больше утечек");
    }

    @Test
    @DisplayName("Влажный пар в ЦНД: x падает до 0.911, влажностные потери 3.47 кДж/кг")
    void wetStageLosesOnDroplets() {
        StageGeometry wet = ReferenceTurbine.wetStage();
        // Состояние перед ЦНД — то, что дал марш опорной машины за ЦСД-3 (см. TurbineMarchTest):
        // 0.150 МПа, 2549.4 кДж/кг, и пар, который не забрали отборы.
        double inletPressure = 0.15;
        double inletEnthalpy = 2549.4;
        double condenserFlow = ReferenceTurbine.INLET_FLOW - ReferenceTurbine.HP2_FLOW
                - ReferenceTurbine.HP1_FLOW - ReferenceTurbine.DEAERATOR_FLOW
                - ReferenceTurbine.LP_FLOW;
        double inletQuality = steam.vapourFractionPH(inletPressure, inletEnthalpy);

        StageResult stage = solver.solve(wet, inletPressure, inletEnthalpy, condenserFlow);

        assertEquals(0.072646, stage.outletPressure(), 0.0005, "давление за первой ступенью ЦНД");
        assertEquals(0.9112, stage.outletQuality(), 0.001,
                "через одну ступень ЦНД пар становится влажным на 9 %");
        assertEquals(85.8581, stage.internalWork(), 0.02, "внутренняя работа, кДж/кг");
        assertEquals(0.7591, stage.efficiency(), 0.001,
                "η первой ступени ЦНД ниже, чем в ЦВД: влажностные потери плюс длинная лопатка");

        double meanQuality = 0.5 * (inletQuality + stage.outletQuality());
        assertEquals(wet.profile().wetnessLoss() * (1.0 - meanQuality) * stage.isentropicDrop(),
                stage.wetnessLoss(), 0.001,
                "влажностные потери считаются по средней сухости ступени x̄; расхождение в "
                        + "третьем знаке — три прохода итерации по x̄, а не расхождение формул");
        assertTrue(stage.wetnessLoss() > 0.0 && stage.outletQuality() < inletQuality,
                "по ходу расширения сухость только падает: " + inletQuality + " → "
                        + stage.outletQuality());
    }

    @Test
    @DisplayName("Запирание: на критическом расходе давление за ступенью упирается в β_кр·p₁")
    void stageChokesAtCriticalFlow() {
        Capacity capacity = Capacity.of(steam, INLET_PRESSURE, inletEnthalpy,
                reference.throatArea(), reference.profile().discharge());

        assertEquals(13.319092, capacity.maxFlow(), 0.0005,
                "через горло F = " + reference.throatArea() + " м² при 14 МПа пройдёт не больше");
        StageResult choked = solver.solve(reference, INLET_PRESSURE, inletEnthalpy,
                capacity.maxFlow());

        assertEquals(capacity.criticalPressure(), choked.outletPressure(), 1e-9,
                "при запертом горле давление за решёткой равно β_кр·p₁");
        assertEquals(7.6921, choked.outletPressure(), 0.0005, "β_кр = 0.5494 при 14 МПа");
        assertTrue(choked.choked(), "при расходе 13.32 кг/с решётка заперта");
        assertEquals(0.533, choked.efficiency(), 0.001,
                "запертая ступень отдаёт вдвое меньше КПД: перепад вырос, а u осталось прежним");

        StageResult belowCritical = solver.solve(reference, INLET_PRESSURE, inletEnthalpy,
                capacity.maxFlow() * 0.999);
        assertFalse(belowCritical.choked(), "на 0.1 % ниже критического расхода запирания нет");
        assertTrue(belowCritical.outletPressure() > choked.outletPressure(),
                "и давление за ступенью сразу поднимается: " + belowCritical.outletPressure()
                        + " против " + choked.outletPressure() + " МПа");
    }

    @Test
    @DisplayName("Расход больше критического: отказ с числами, а не выдуманное давление")
    void tooMuchFlowIsRefused() {
        Capacity capacity = Capacity.of(steam, INLET_PRESSURE, inletEnthalpy,
                reference.throatArea(), reference.profile().discharge());
        double impossible = capacity.maxFlow() * 1.1;

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> solver.solve(reference, INLET_PRESSURE, inletEnthalpy, impossible));

        assertTrue(failure.getMessage().contains("не пропустит"), () -> "ошибка объясняет, "
                + "что горло мало: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("14.6510"), () -> "назван заданный расход: "
                + failure.getMessage());
        assertTrue(failure.getMessage().contains("13.3191"), () -> "назван критический расход: "
                + failure.getMessage());
    }

    @Test
    @DisplayName("Пятикратная высота лопатки: работа отрицательна — расчёт падает на треугольниках")
    void oversizedStageBreaksVelocityTriangles() {
        StageGeometry huge = StageGeometry.of(reference.meanDiameter(), reference.height() * 5.0,
                reference.admission(), reference.profile());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> solver.solve(huge, INLET_PRESSURE, inletEnthalpy, INLET_FLOW));

        assertTrue(failure.getMessage().contains("треугольники скоростей не сходятся"),
                () -> "ошибка называет причину: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("u/c₁ = 2.63"), () -> "и показывает, что "
                + "окружная скорость вдвое больше c₁: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("-0.747"), () -> "и величину работы: "
                + failure.getMessage());
    }

    @Test
    @DisplayName("Располагаемый перепад: от энтальпии входа до p₂ по изоэнтропе; при p₂ ≥ p₁ — отказ")
    void availableDropRequiresFallingPressure() {
        assertEquals(47.3369, solver.availableDrop(INLET_PRESSURE, inletEnthalpy, 12.187637), 0.01,
                "тот же перепад, что и в расчёте ступени: он не зависит от расхода");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> solver.availableDrop(INLET_PRESSURE, inletEnthalpy, INLET_PRESSURE));
        assertTrue(failure.getMessage().contains("не ниже"), () -> "ошибка называет границу: "
                + failure.getMessage());
    }
}
