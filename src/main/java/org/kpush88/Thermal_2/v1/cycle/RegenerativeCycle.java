package org.kpush88.Thermal_2.v1.cycle;

import java.util.ArrayList;
import java.util.List;

import org.kpush88.Thermal_2.v1.core.FixedFlow;
import org.kpush88.Thermal_2.v1.core.LinearSolver;
import org.kpush88.Thermal_2.v1.core.OjAlgoSolver;
import org.kpush88.Thermal_2.v1.core.Scheme;
import org.kpush88.Thermal_2.v1.core.Solution;
import org.kpush88.Thermal_2.v1.core.Variable;
import org.kpush88.Thermal_2.v1.thermo.SteamState;
import org.kpush88.Thermal_2.v1.thermo.WaterSteam;

/**
 * Тепловая схема с регенеративным подогревом питательной воды:
 * <pre>
 *   котёл → турбина (отборы ПВД/деаэратор/ПНД) → конденсатор → ПНД → деаэратор →
 *   питательный насос → ПВД → котёл
 * </pre>
 *
 * <p>Порядок расчёта:
 * <ol>
 *   <li>энтальпии узловых точек считаются по IF97 ({@link WaterSteam}) при заданных
 *       давлениях и ηoi турбины;</li>
 *   <li>из элементов собирается {@link Scheme}: каждый элемент объявляет свои
 *       неизвестные (расходы) и вносит локальные балансы;</li>
 *   <li>СЛАУ решается решателем ojAlgo ({@link OjAlgoSolver});</li>
 *   <li>по расходам считаются мощности, теплоты и КПД.</li>
 * </ol>
 *
 * <p>Балансовые уравнения линейны относительно расходов (энтальпии уже известны),
 * поэтому внешняя итерация не требуется: все давления в прототипе — граничные условия.
 */
public final class RegenerativeCycle {

    private final WaterSteam steam = new WaterSteam();
    private final CycleDesign design;
    private final LinearSolver solver;

    public RegenerativeCycle(CycleDesign design) {
        this(design, new OjAlgoSolver());
    }

    public RegenerativeCycle(CycleDesign design, LinearSolver solver) {
        this.design = design;
        this.solver = solver;
    }

    /** Выполняет расчёт схемы и возвращает результаты. */
    public CycleResult calculate() {
        double p0 = design.liveSteamPressure();
        double t0 = design.liveSteamTemperature();
        double p1 = design.hpExtractionPressure();
        double p2 = design.deaeratorPressure();
        double p3 = design.lpExtractionPressure();
        double pCondenser = design.condenserPressure();
        double eta = design.turbineEfficiency();

        // 1. Термодинамика: энтальпии точек (IF97).
        double h0 = steam.enthalpyPT(p0, t0);
        double s0 = steam.entropyPT(p0, t0);
        double h1 = expand(h0, s0, p1, eta);
        double h2 = expand(h1, steam.entropyPH(p1, h1), p2, eta);
        double h3 = expand(h2, steam.entropyPH(p2, h2), p3, eta);
        double hExhaust = expand(h3, steam.entropyPH(p3, h3), pCondenser, eta);

        double hDrain1 = steam.saturatedLiquidEnthalpy(p1);
        double hDrain3 = steam.saturatedLiquidEnthalpy(p3);
        double hCondensate = steam.saturatedLiquidEnthalpy(pCondenser);

        double hDeaeratorOutlet = steam.saturatedLiquidEnthalpy(p2);
        double vPump = steam.saturatedLiquidVolume(p2);
        double pressureRise = p0 - p2;
        double pumpSpecificWork = vPump * pressureRise * 1000.0 / design.pumpEfficiency();
        double hAfterPump = hDeaeratorOutlet + pumpSpecificWork;

        double hFeedwater = steam.subcooledLiquidEnthalpy(p1, design.heaterUndercooling());
        double hLpHeaterOutlet = steam.subcooledLiquidEnthalpy(p3, design.heaterUndercooling());

        // 2. Топология: общие переменные = потоки (одна переменная связывает элементы).
        Variable feedwater = Variable.of("Gпв");
        Variable liveSteam = Variable.of("G0");
        Variable hpExtraction = Variable.of("Gотб_ПВД");
        Variable deaeratorExtraction = Variable.of("Gотб_Д");
        Variable lpExtraction = Variable.of("Gотб_ПНД");
        Variable exhaust = Variable.of("Gвых");
        Variable condensate = Variable.of("Gк");

        // 3. Элементы схемы. Переиспользование одной переменной задаёт связи:
        //    дренаж ПВД = расход отбора ПВД; дренаж ПНД = расход отбора ПНД.
        Boiler boiler = new Boiler(feedwater, liveSteam, hFeedwater, h0);
        Turbine turbine = new Turbine(liveSteam, hpExtraction, deaeratorExtraction, lpExtraction,
                exhaust, h0, h1, h2, h3, hExhaust);
        SurfaceHeater hpHeater = new SurfaceHeater("ПВД", hpExtraction, feedwater,
                h1, hDrain1, hAfterPump, hFeedwater);
        SurfaceHeater lpHeater = new SurfaceHeater("ПНД", lpExtraction, condensate,
                h3, hDrain3, hCondensate, hLpHeaterOutlet);
        Deaerator deaerator = new Deaerator(hpExtraction, deaeratorExtraction, condensate, feedwater,
                hDrain1, h2, hLpHeaterOutlet, hDeaeratorOutlet);
        Condenser condenser = new Condenser(exhaust, lpExtraction, condensate,
                hExhaust, hDrain3, hCondensate);
        FeedwaterPump pump = new FeedwaterPump(feedwater, vPump, pressureRise, design.pumpEfficiency());

        Scheme scheme = new Scheme()
                .add(new FixedFlow(feedwater, 1.0))
                .add(boiler)
                .add(turbine)
                .add(hpHeater)
                .add(lpHeater)
                .add(deaerator)
                .add(condenser)
                .add(pump);

        // 4. Сборка и решение СЛАУ (ojAlgo).
        Solution solution = scheme.solve(solver);

        // 5. Энергетические показатели.
        double boilerDuty = boiler.heatDuty(solution);
        double turbinePower = turbine.internalPower(solution);
        double pumpPower = pump.power(solution);
        double condenserDuty = condenser.heatRejection(solution);
        double netInternal = turbinePower - pumpPower;
        double netElectric = turbinePower * design.electromechanicalEfficiency() - pumpPower;

        // 6. Точки для отчёта.
        List<SteamState> points = new ArrayList<>();
        points.add(SteamState.ofPT("Свежий пар (перед турбиной)", steam, p0, t0));
        points.add(SteamState.ofPH("Отбор ПВД", steam, p1, h1));
        points.add(SteamState.ofPH("Отбор на деаэратор", steam, p2, h2));
        points.add(SteamState.ofPH("Отбор ПНД", steam, p3, h3));
        points.add(SteamState.ofPH("Выхлоп в конденсатор", steam, pCondenser, hExhaust));
        points.add(SteamState.ofPH("Конденсат", steam, pCondenser, hCondensate));
        points.add(SteamState.ofPH("После ПНД", steam, p3, hLpHeaterOutlet));
        points.add(SteamState.ofPH("После деаэратора", steam, p2, hDeaeratorOutlet));
        points.add(SteamState.ofPH("После ПВД (перед котлом)", steam, p1, hFeedwater));

        return new CycleResult(
                solution.value(feedwater),
                solution.value(hpExtraction),
                solution.value(deaeratorExtraction),
                solution.value(lpExtraction),
                solution.value(exhaust),
                solution.value(condensate),
                turbinePower,
                pumpPower,
                boilerDuty,
                condenserDuty,
                netInternal,
                netElectric,
                turbinePower / boilerDuty,
                netInternal / boilerDuty,
                List.copyOf(points));
    }

    /** Изоэнтропное расширение до давления {@code pOut} с внутренним относительным КПД ηoi. */
    private double expand(double inletEnthalpy, double inletEntropy, double pOut, double efficiency) {
        double isentropicEnthalpy = steam.enthalpyPS(pOut, inletEntropy);
        return inletEnthalpy - efficiency * (inletEnthalpy - isentropicEnthalpy);
    }
}
