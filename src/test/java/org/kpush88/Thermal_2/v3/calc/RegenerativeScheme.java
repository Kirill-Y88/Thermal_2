package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Boiler;
import org.kpush88.Thermal_2.v3.Condenser;
import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Deaerator;
import org.kpush88.Thermal_2.v3.Pipe;
import org.kpush88.Thermal_2.v3.Pump;
import org.kpush88.Thermal_2.v3.Scheme;
import org.kpush88.Thermal_2.v3.SpaceType;
import org.kpush88.Thermal_2.v3.SurfaceHeater;
import org.kpush88.Thermal_2.v3.Turbine;

import java.util.Objects;

/**
 * Стенд регенеративного цикла: котёл → турбина с тремя отборами → конденсатор → конденсатный
 * насос → ПНД → деаэратор → питательный насос → ПВД → котёл.
 *
 * <p>Параметры берутся из {@link CycleInputs}: это та же схема, что считает прототип
 * {@code v1.cycle.RegenerativeCycle} (24 МПа / 540 °C, отборы 3.5 / 0.7 / 0.12 МПа, рабочее
 * давление деаэратора 0.6 МПа, вакуум 0.004 МПа, недогрев 5 K, ηoi = 0.85), с одним отличием от
 * v1: там давления отбора и деаэратора совпадали — дросселирования в подводящей линии не было.
 * Другие стартовые параметры дают другой расчёт по той же схеме ({@link #of(CycleInputs)}).
 *
 * <p>Один стенд на несколько проверок: СЛАУ ({@link FlowSystemTest}) берёт из него расходы,
 * внешняя итерация ({@link CycleSolverTest}) — мощности и КПД, этапы расчёта
 * ({@link CycleStagesTest}) — состояния после каждого шага, сами модели проверяются отдельно.
 * Схема собирается заново на каждый вызов: журнал расчёта пишет в связи, и общий стенд между
 * тестами протащил бы посчитанные величины как входные данные.
 */
final class RegenerativeScheme {

    final CycleInputs inputs;
    final Scheme scheme;
    final Boiler boiler;
    final Turbine turbine;
    final SurfaceHeater hpHeater;
    final Deaerator deaerator;
    final SurfaceHeater lpHeater;
    final Condenser condenser;
    final Pump condensatePump;
    final Pump feedPump;

    /** Расход свежего пара (D₀) — граничное условие системы балансов. */
    final Connection liveSteam;
    /** Свежий пар перед турбиной. */
    final Connection atTurbine;
    final Connection hpExtraction;
    final Connection deaeratorExtraction;
    final Connection lpExtraction;
    final Connection exhaust;
    /** Конденсат после конденсатора. */
    final Connection condensate;
    /** Питательная вода перед котлом. */
    final Connection feedwater;

    private RegenerativeScheme(CycleInputs inputs, Scheme scheme, Boiler boiler, Turbine turbine,
                               SurfaceHeater hpHeater, Deaerator deaerator,
                               SurfaceHeater lpHeater, Condenser condenser,
                               Pump condensatePump, Pump feedPump,
                               Connection liveSteam, Connection atTurbine,
                               Connection hpExtraction, Connection deaeratorExtraction,
                               Connection lpExtraction, Connection exhaust,
                               Connection condensate, Connection feedwater) {
        this.inputs = inputs;
        this.scheme = scheme;
        this.boiler = boiler;
        this.turbine = turbine;
        this.hpHeater = hpHeater;
        this.deaerator = deaerator;
        this.lpHeater = lpHeater;
        this.condenser = condenser;
        this.condensatePump = condensatePump;
        this.feedPump = feedPump;
        this.liveSteam = liveSteam;
        this.atTurbine = atTurbine;
        this.hpExtraction = hpExtraction;
        this.deaeratorExtraction = deaeratorExtraction;
        this.lpExtraction = lpExtraction;
        this.exhaust = exhaust;
        this.condensate = condensate;
        this.feedwater = feedwater;
    }

    /** Стенд с реперными параметрами: теми же, что в расчёте v1. */
    static RegenerativeScheme reference() {
        return of(CycleInputs.reference());
    }

    /** Стенд из заданных стартовых параметров: давления, температуры, КПД и задание мощности. */
    static RegenerativeScheme of(CycleInputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        Boiler boiler = new Boiler("Котёл")
                .withFreshSteam(inputs.livePressure(), inputs.liveTemperature());
        Turbine turbine = new Turbine("Турбина", 3)
                .withInternalEfficiency(inputs.internalEfficiency())
                .withElectricPower(inputs.electricPower())
                .withElectricEfficiency(inputs.electricEfficiency());
        SurfaceHeater hpHeater = new SurfaceHeater("ПВД").withSubcooling(inputs.subcooling());
        Deaerator deaerator = new Deaerator("Деаэратор").withPressure(inputs.deaeratorPressure());
        SurfaceHeater lpHeater = new SurfaceHeater("ПНД").withSubcooling(inputs.subcooling());
        Condenser condenser = new Condenser("Конденсатор");
        Pump condensatePump = new Pump("Конденсатный насос")
                .withPressureRise(inputs.condensatePressure() - inputs.condenserPressure())
                .withEfficiency(inputs.pumpEfficiency());
        Pump feedPump = new Pump("Питательный насос")
                .withPressureRise(inputs.livePressure() - inputs.deaeratorPressure())
                .withEfficiency(inputs.pumpEfficiency());

        Pipe live = new Pipe("Паропровод свежего пара", SpaceType.STEAM);
        Pipe extraction1 = new Pipe("Паропровод отбора 1", SpaceType.STEAM);
        Pipe extraction2 = new Pipe("Паропровод отбора 2", SpaceType.STEAM);
        Pipe extraction3 = new Pipe("Паропровод отбора 3", SpaceType.STEAM);
        Pipe exhaustPipe = new Pipe("Паропровод в конденсатор", SpaceType.STEAM);
        Pipe hpDrain = new Pipe("Дренажная линия ПВД", SpaceType.STEAM);
        Pipe lpDrain = new Pipe("Дренажная линия ПНД", SpaceType.STEAM);
        Pipe condensatePipe = new Pipe("Линия основного конденсата", SpaceType.WATER);
        Pipe condensateDischarge = new Pipe("Напорная линия конденсатного насоса", SpaceType.WATER);
        Pipe lpWater = new Pipe("Линия воды за ПНД", SpaceType.WATER);
        Pipe deaeratorWater = new Pipe("Линия воды за деаэратором", SpaceType.WATER);
        Pipe feedDischarge = new Pipe("Напорная линия питательного насоса", SpaceType.WATER);
        Pipe hpWater = new Pipe("Линия воды за ПВД", SpaceType.WATER);
        Pipe feedwaterPipe = new Pipe("Питательная линия", SpaceType.WATER);

        Scheme scheme = new Scheme()
                .add(boiler).add(turbine).add(hpHeater).add(deaerator).add(lpHeater)
                .add(condenser).add(condensatePump).add(feedPump)
                .add(live).add(extraction1).add(extraction2).add(extraction3).add(exhaustPipe)
                .add(hpDrain).add(lpDrain).add(condensatePipe).add(condensateDischarge)
                .add(lpWater).add(deaeratorWater).add(feedDischarge).add(hpWater)
                .add(feedwaterPipe);

        // 1. Свежий пар: котёл → паропровод → турбина.
        Connection liveSteam = scheme.link(boiler.steamOut(), live.in(), "свежий пар");
        Connection atTurbine = scheme.link(live.out(), turbine.steamIn(), "свежий пар");

        // 2. Отборы: у каждого свой порт, поэтому давление задаётся связью этого отбора.
        Connection hpExtraction = scheme.link(turbine.extractionOut(1), extraction1.in(), "отбор 1");
        scheme.link(extraction1.out(), hpHeater.steamIn(), "отбор 1");
        Connection deaeratorExtraction = scheme.link(turbine.extractionOut(2), extraction2.in(),
                "отбор 2");
        scheme.link(extraction2.out(), deaerator.steamIn(), "отбор 2");
        Connection lpExtraction = scheme.link(turbine.extractionOut(3), extraction3.in(), "отбор 3");
        scheme.link(extraction3.out(), lpHeater.steamIn(), "отбор 3");

        // 3. Дренажи: ПВД отдаёт дренаж в деаэратор, ПНД — в конденсатор.
        scheme.link(hpHeater.drainOut(), hpDrain.in(), "дренаж ПВД");
        scheme.link(hpDrain.out(), deaerator.drainIn(), "дренаж ПВД");
        scheme.link(lpHeater.drainOut(), lpDrain.in(), "дренаж ПНД");
        scheme.link(lpDrain.out(), condenser.drainIn(), "дренаж ПНД");

        // 4. Конденсатный тракт: выхлоп → конденсатор → конденсатный насос → ПНД.
        Connection exhaust = scheme.link(turbine.exhaustOut(), exhaustPipe.in(), "выхлоп");
        scheme.link(exhaustPipe.out(), condenser.exhaustIn(), "выхлоп");
        Connection condensate = scheme.link(condenser.condensateOut(), condensatePipe.in(),
                "конденсат");
        scheme.link(condensatePipe.out(), condensatePump.waterIn(), "конденсат");
        scheme.link(condensatePump.waterOut(), condensateDischarge.in(), "конденсат");
        scheme.link(condensateDischarge.out(), lpHeater.waterIn(), "конденсат");
        scheme.link(lpHeater.waterOut(), lpWater.in(), "основной конденсат");
        scheme.link(lpWater.out(), deaerator.waterIn(), "основной конденсат");

        // 5. Питательный тракт: деаэратор → питательный насос → ПВД → котёл.
        scheme.link(deaerator.waterOut(), deaeratorWater.in(), "питательная вода");
        scheme.link(deaeratorWater.out(), feedPump.waterIn(), "питательная вода");
        scheme.link(feedPump.waterOut(), feedDischarge.in(), "питательная вода");
        scheme.link(feedDischarge.out(), hpHeater.waterIn(), "питательная вода");
        scheme.link(hpHeater.waterOut(), hpWater.in(), "питательная вода");
        Connection feedwater = scheme.link(hpWater.out(), feedwaterPipe.in(), "питательная вода");
        scheme.link(feedwaterPipe.out(), boiler.waterIn(), "питательная вода");

        // 6. Режим: давления отборов и вакуум. Расходы не задаются — их решает СЛАУ.
        hpExtraction.setPressure(inputs.highPressureExtraction());
        deaeratorExtraction.setPressure(inputs.deaeratorExtraction());
        lpExtraction.setPressure(inputs.lowPressureExtraction());
        exhaust.setPressure(inputs.condenserPressure());

        return new RegenerativeScheme(inputs, scheme, boiler, turbine, hpHeater, deaerator, lpHeater,
                condenser, condensatePump, feedPump, liveSteam, atTurbine, hpExtraction,
                deaeratorExtraction, lpExtraction, exhaust, condensate, feedwater);
    }
}
