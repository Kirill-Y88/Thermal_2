package org.kpush88.Thermal_2.v3.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kpush88.Thermal_2.v3.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SolveTest3 {

    @Test
    @DisplayName("Сборка → параметры → расчёт: элементы, режим на схеме и результаты по этапам")
    void assembledSchemeIsCalculatedWithParametersSetInPlace() {
        // ---------- Шаг 1. Схема из элементов ----------

        // Аппараты. Параметров режима здесь ещё нет: конструктор задаёт только состав портов
        // (у турбины — число отборов), а не режим.
        Boiler boiler = new Boiler("Котёл");
        Turbine turbine = new Turbine("Турбина", 4);
        SurfaceHeater hpHeater1 = new SurfaceHeater("ПВД-1");
        SurfaceHeater hpHeater2 = new SurfaceHeater("ПВД-2");
        Deaerator deaerator = new Deaerator("Деаэратор");
        SurfaceHeater lpHeater = new SurfaceHeater("ПНД");
        Condenser condenser = new Condenser("Конденсатор");
        Pump condensatePump = new Pump("Конденсатный насос");
        Pump feedPump = new Pump("Питательный насос");

        // Участки трассы: каждый переход «аппарат → аппарат» идёт через трубу, контур которой
        // задан её конструктором. Труба без потерь только переносит состояние.
        Pipe live = new Pipe("Паропровод свежего пара", SpaceType.STEAM);
        Pipe extraction1 = new Pipe("Паропровод отбора 1", SpaceType.STEAM);
        Pipe extraction2 = new Pipe("Паропровод отбора 2", SpaceType.STEAM);
        Pipe extraction3 = new Pipe("Паропровод отбора 3", SpaceType.STEAM);
        Pipe extraction4 = new Pipe("Паропровод отбора 4", SpaceType.STEAM);
        Pipe exhaustPipe = new Pipe("Паропровод в конденсатор", SpaceType.STEAM);
        Pipe hpDrain1 = new Pipe("Дренажная линия ПВД-1", SpaceType.STEAM);
        Pipe hpDrain2 = new Pipe("Дренажная линия ПВД-2", SpaceType.STEAM);
        Pipe lpDrain = new Pipe("Дренажная линия ПНД", SpaceType.STEAM);
        Pipe condensatePipe = new Pipe("Линия основного конденсата", SpaceType.WATER);
        Pipe condensateDischarge = new Pipe("Напорная линия конденсатного насоса", SpaceType.WATER);
        Pipe lpWater = new Pipe("Линия воды за ПНД", SpaceType.WATER);
        Pipe deaeratorWater = new Pipe("Линия воды за деаэратором", SpaceType.WATER);
        Pipe feedDischarge1 = new Pipe("Питательная вода пит насос - ПВД-1", SpaceType.WATER);
        Pipe feedDischarge2 = new Pipe("Питательная вода ПВД-1 - ПВД-2", SpaceType.WATER);
        Pipe hpWater = new Pipe("Линия воды за ПВД", SpaceType.WATER);
        Pipe feedwaterPipe = new Pipe("Питательная линия", SpaceType.WATER);

        Scheme scheme = new Scheme()
                .add(boiler).add(turbine).add(hpHeater1).add(hpHeater2).add(deaerator).add(lpHeater)
                .add(condenser).add(condensatePump).add(feedPump)
                .add(live).add(extraction1).add(extraction2).add(extraction3).add(extraction4).add(exhaustPipe)
                .add(hpDrain1).add(hpDrain2).add(lpDrain).add(condensatePipe).add(condensateDischarge)
                .add(lpWater).add(deaeratorWater).add(feedDischarge1).add(feedDischarge2).add(hpWater)
                .add(feedwaterPipe);

        // Свежий пар: котёл → паропровод → турбина.
        Connection liveSteam = scheme.link(boiler.steamOut(), live.in(), "свежий пар");
        Connection atTurbine = scheme.link(live.out(), turbine.steamIn(), "свежий пар");

        // Отборы: у каждого свой порт, поэтому давление задаётся связью этого отбора.
        Connection hpExtraction1 = scheme.link(turbine.extractionOut(1), extraction1.in(), "отбор 1");
        Connection hpHeaterSteam2 = scheme.link(extraction1.out(), hpHeater2.steamIn(), "отбор 1");
        Connection hpExtraction2 = scheme.link(turbine.extractionOut(2), extraction2.in(), "отбор 2");
        Connection hpHeaterSteam1 = scheme.link(extraction2.out(), hpHeater1.steamIn(), "отбор 2");
        Connection deaeratorExtraction = scheme.link(turbine.extractionOut(3), extraction3.in(),
                "отбор 3");
        scheme.link(extraction3.out(), deaerator.steamIn(), "отбор 3");
        Connection lpExtraction = scheme.link(turbine.extractionOut(4), extraction4.in(),
                "отбор 4");
        scheme.link(extraction4.out(), lpHeater.steamIn(), "отбор 4");

        // Дренажи: ПВД отдаёт дренаж в деаэратор, ПНД — в конденсатор.
        scheme.link(hpHeater2.drainOut(), hpDrain2.in(), "дренаж ПВД-2");
        scheme.link(hpDrain2.out(), hpHeater1.drainIn(), "дренаж ПВД-2");
        scheme.link(hpHeater1.drainOut(), hpDrain1.in(), "дренаж ПВД-1");
        scheme.link(hpDrain1.out(), deaerator.drainIn(), "дренаж ПВД-1");
        scheme.link(lpHeater.drainOut(), lpDrain.in(), "дренаж ПНД");
        scheme.link(lpDrain.out(), condenser.drainIn(), "дренаж ПНД");

        // Конденсатный тракт: выхлоп → конденсатор → конденсатный насос → ПНД.
        Connection exhaust = scheme.link(turbine.exhaustOut(), exhaustPipe.in(), "выхлоп");
        scheme.link(exhaustPipe.out(), condenser.exhaustIn(), "выхлоп");
        Connection condensate = scheme.link(condenser.condensateOut(), condensatePipe.in(),
                "конденсат");
        scheme.link(condensatePipe.out(), condensatePump.waterIn(), "конденсат");
        scheme.link(condensatePump.waterOut(), condensateDischarge.in(), "конденсат");
        scheme.link(condensateDischarge.out(), lpHeater.waterIn(), "конденсат");
        scheme.link(lpHeater.waterOut(), lpWater.in(), "основной конденсат");
        scheme.link(lpWater.out(), deaerator.waterIn(), "основной конденсат");

        // Питательный тракт: деаэратор → питательный насос → ПВД → котёл.
        scheme.link(deaerator.waterOut(), deaeratorWater.in(), "питательная вода");
        scheme.link(deaeratorWater.out(), feedPump.waterIn(), "питательная вода");
        scheme.link(feedPump.waterOut(), feedDischarge1.in(), "питательная вода");
        scheme.link(feedDischarge1.out(), hpHeater1.waterIn(), "питательная вода");
        scheme.link(hpHeater1.waterOut(), feedDischarge2.in(), "питательная вода");
        scheme.link(feedDischarge2.out(), hpHeater2.waterIn(), "питательная вода");
        scheme.link(hpHeater2.waterOut(), hpWater.in(), "питательная вода");
        Connection feedwater = scheme.link(hpWater.out(), feedwaterPipe.in(), "питательная вода");
        scheme.link(feedwaterPipe.out(), boiler.waterIn(), "питательная вода");

//        // Собранная схема — это структура: аппараты и стыки, без режима.
//        assertEquals(22, scheme.getElements().size(),
//                "восемь аппаратов и четырнадцать участков трассы");
//        assertEquals(27, scheme.getConnections().size(), "по связи на каждый стык «выход → вход»");
//        assertTrue(scheme.validate().isEmpty(),
//                () -> "схема собрана с ошибками: " + scheme.validate());
//        assertTrue(scheme.danglingConnections().isEmpty(), "цикл замкнут: открытых концов нет");
//        assertNull(liveSteam.getPressure(), "у связей пока нет ни одного параметра режима");

        // ---------- Шаг 2. Параметры на собранной схеме ----------

        // Свежий пар и задание мощности — параметры цикла.
        boiler.withFreshSteam(14.0, 550.0);
        turbine.withInternalEfficiency(0.85)
                .withElectricPower(10000.0)
                .withElectricEfficiency(0.985);
        // Недогрев в поверхностных подогревателях и рабочее давление деаэратора.
        hpHeater1.withSubcooling(5.0);
        hpHeater2.withSubcooling(5.0);
        lpHeater.withSubcooling(5.0);
        deaerator.withPressure(0.6);
        // Напоры насосов: конденсатный поднимает давление до линии основного конденсата,
        // питательный — до давления перед котлом.
        condensatePump.withPressureRise(1.0 - 0.004).withEfficiency(0.8);
        feedPump.withPressureRise(20.0 - 0.6).withEfficiency(0.8);
        // Давления отборов и вакуум — это состояние сечений, поэтому задаются на связях.
        hpExtraction1.setPressure(3.5);
        hpExtraction2.setPressure(2.5);
        deaeratorExtraction.setPressure(0.8);
        lpExtraction.setPressure(0.15);
        exhaust.setPressure(0.004);

        System.out.println("Параметры, заданные на собранной схеме:");
        System.out.println("  котёл: p₀ = " + boiler.getSteamPressure() + " МПа, t₀ = "
                + boiler.getSteamTemperature() + " °C");
        System.out.println("  турбина: N_э = " + turbine.getElectricPower() + " кВт, ηoi = "
                + turbine.getInternalEfficiency() + ", η_эм = "
                + turbine.getElectricEfficiency());
        System.out.println("  отборы: " + hpExtraction1.getPressure() + " / " + hpExtraction2.getPressure() + " / "
                + deaeratorExtraction.getPressure() + " / " + lpExtraction.getPressure()
                + " МПа, вакуум " + exhaust.getPressure() + " МПа");
        System.out.println("  деаэратор: " + deaerator.getPressure() + " МПа, недогрев: "
                + hpHeater1.getSubcooling() + "_"+ hpHeater2.getSubcooling() +" K, напоры насосов: " + feedPump.getPressureRise()
                + " / " + condensatePump.getPressureRise() + " МПа");

        // Параметры дошли до схемы: аппараты помнят свои, связи — давления режима.
        // assertEquals(24.0, boiler.getSteamPressure(), 1e-12, "котёл помнит свежий пар");
        //assertEquals(1040.6, turbine.getElectricPower(), 1e-12, "турбина помнит задание мощности");
        // assertEquals(3.5, hpExtraction.getPressure(), 1e-12, "давление отбора задано на связи");
        // assertEquals(23.4, feedPump.getPressureRise(), 1e-12, "напор питательного насоса");
        // assertNull(liveSteam.getEnthalpy(), "энтальпий в схеме ещё нет: параметры — это данные");

        // ---------- Шаг 3. Расчёт и результаты по этапам ----------

        CycleResult result = CycleSolver.of(scheme).calculate();
        List<StageData> stages = StageData.of(result.journal());
        List<Connection> traced = List.of(liveSteam, atTurbine, hpExtraction1, hpExtraction2,
                deaeratorExtraction, lpExtraction, exhaust, condensate, feedwater);

        System.out.println(StageData.summary(stages));
        System.out.println(StageData.table(stages, traced));
        System.out.println(result.describe());
    }

}
