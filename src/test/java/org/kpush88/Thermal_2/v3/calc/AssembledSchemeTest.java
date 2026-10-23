package org.kpush88.Thermal_2.v3.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Три шага работы со схемой в одном тесте: собрать её из элементов, задать параметры на
 * собранной схеме, запустить расчёт и прочитать результат по этапам.
 *
 * <p><b>Зачем этот тест, если есть стенд.</b> {@link RegenerativeScheme} отдаёт схему вместе с
 * параметрами: они приходят в конструкторы элементов, поэтому по тесту не видно ни из чего схема
 * собрана, ни где задан режим. Здесь все три шага сделаны явно и по порядку: сначала элементы и
 * связи «выход → вход» ({@link Scheme#add}, {@link Scheme#link}) без единого числа, затем
 * {@code with…} на аппаратах и {@code setPressure} на связях, и только потом расчёт.
 *
 * <p><b>Что печатается.</b> Заданные параметры, сводка по этапам ({@link StageData#summary}) и
 * таблица «сечение × этап» ({@link StageData#table}), затем показатели цикла
 * ({@link CycleResult#describe()}). Проверки относятся не к печати, а к значениям: у каждого этапа
 * своя — то, что он обязан был добавить.
 *
 * <p><b>Почему сборка повторена, а не взята из стенда.</b> Это и есть содержание теста: схема —
 * данные, а не скрытая часть стенда. Схема та же, что в {@link RegenerativeScheme#of(CycleInputs)},
 * поэтому числа совпадают с реперными: D₀ ≈ 1.0021 кг/с, КПД брутто ≈ 46.06 %.
 */
class AssembledSchemeTest {

    @Test
    @DisplayName("Сборка → параметры → расчёт: элементы, режим на схеме и результаты по этапам")
    void assembledSchemeIsCalculatedWithParametersSetInPlace() {
        // ---------- Шаг 1. Схема из элементов ----------

        // Аппараты. Параметров режима здесь ещё нет: конструктор задаёт только состав портов
        // (у турбины — число отборов), а не режим.
        Boiler boiler = new Boiler("Котёл");
        Turbine turbine = new Turbine("Турбина", 3);
        SurfaceHeater hpHeater = new SurfaceHeater("ПВД");
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

        // Свежий пар: котёл → паропровод → турбина.
        Connection liveSteam = scheme.link(boiler.steamOut(), live.in(), "свежий пар");
        Connection atTurbine = scheme.link(live.out(), turbine.steamIn(), "свежий пар");

        // Отборы: у каждого свой порт, поэтому давление задаётся связью этого отбора.
        Connection hpExtraction = scheme.link(turbine.extractionOut(1), extraction1.in(), "отбор 1");
        Connection hpHeaterSteam = scheme.link(extraction1.out(), hpHeater.steamIn(), "отбор 1");
        Connection deaeratorExtraction = scheme.link(turbine.extractionOut(2), extraction2.in(),
                "отбор 2");
        scheme.link(extraction2.out(), deaerator.steamIn(), "отбор 2");
        Connection lpExtraction = scheme.link(turbine.extractionOut(3), extraction3.in(),
                "отбор 3");
        scheme.link(extraction3.out(), lpHeater.steamIn(), "отбор 3");

        // Дренажи: ПВД отдаёт дренаж в деаэратор, ПНД — в конденсатор.
        scheme.link(hpHeater.drainOut(), hpDrain.in(), "дренаж ПВД");
        scheme.link(hpDrain.out(), deaerator.drainIn(), "дренаж ПВД");
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
        scheme.link(feedPump.waterOut(), feedDischarge.in(), "питательная вода");
        scheme.link(feedDischarge.out(), hpHeater.waterIn(), "питательная вода");
        scheme.link(hpHeater.waterOut(), hpWater.in(), "питательная вода");
        Connection feedwater = scheme.link(hpWater.out(), feedwaterPipe.in(), "питательная вода");
        scheme.link(feedwaterPipe.out(), boiler.waterIn(), "питательная вода");

        // Собранная схема — это структура: аппараты и стыки, без режима.
        assertEquals(22, scheme.getElements().size(),
                "восемь аппаратов и четырнадцать участков трассы");
        assertEquals(27, scheme.getConnections().size(), "по связи на каждый стык «выход → вход»");
        assertTrue(scheme.validate().isEmpty(),
                () -> "схема собрана с ошибками: " + scheme.validate());
        assertTrue(scheme.danglingConnections().isEmpty(), "цикл замкнут: открытых концов нет");
        assertNull(liveSteam.getPressure(), "у связей пока нет ни одного параметра режима");

        // ---------- Шаг 2. Параметры на собранной схеме ----------

        // Свежий пар и задание мощности — параметры цикла.
        boiler.withFreshSteam(24.0, 540.0);
        turbine.withInternalEfficiency(0.85)
                .withElectricPower(1040.6)
                .withElectricEfficiency(0.985);
        // Недогрев в поверхностных подогревателях и рабочее давление деаэратора.
        hpHeater.withSubcooling(5.0);
        lpHeater.withSubcooling(5.0);
        deaerator.withPressure(0.6);
        // Напоры насосов: конденсатный поднимает давление до линии основного конденсата,
        // питательный — до давления перед котлом.
        condensatePump.withPressureRise(1.0 - 0.004).withEfficiency(0.8);
        feedPump.withPressureRise(24.0 - 0.6).withEfficiency(0.8);
        // Давления отборов и вакуум — это состояние сечений, поэтому задаются на связях.
        hpExtraction.setPressure(3.5);
        deaeratorExtraction.setPressure(0.7);
        lpExtraction.setPressure(0.12);
        exhaust.setPressure(0.004);

        System.out.println("Параметры, заданные на собранной схеме:");
        System.out.println("  котёл: p₀ = " + boiler.getSteamPressure() + " МПа, t₀ = "
                + boiler.getSteamTemperature() + " °C");
        System.out.println("  турбина: N_э = " + turbine.getElectricPower() + " кВт, ηoi = "
                + turbine.getInternalEfficiency() + ", η_эм = "
                + turbine.getElectricEfficiency());
        System.out.println("  отборы: " + hpExtraction.getPressure() + " / "
                + deaeratorExtraction.getPressure() + " / " + lpExtraction.getPressure()
                + " МПа, вакуум " + exhaust.getPressure() + " МПа");
        System.out.println("  деаэратор: " + deaerator.getPressure() + " МПа, недогрев: "
                + hpHeater.getSubcooling() + " K, напоры насосов: " + feedPump.getPressureRise()
                + " / " + condensatePump.getPressureRise() + " МПа");

        // Параметры дошли до схемы: аппараты помнят свои, связи — давления режима.
        assertEquals(24.0, boiler.getSteamPressure(), 1e-12, "котёл помнит свежий пар");
        assertEquals(1040.6, turbine.getElectricPower(), 1e-12, "турбина помнит задание мощности");
        assertEquals(3.5, hpExtraction.getPressure(), 1e-12, "давление отбора задано на связи");
        assertEquals(23.4, feedPump.getPressureRise(), 1e-12, "напор питательного насоса");
        assertNull(liveSteam.getEnthalpy(), "энтальпий в схеме ещё нет: параметры — это данные");

        // ---------- Шаг 3. Расчёт и результаты по этапам ----------

        CycleResult result = CycleSolver.of(scheme).calculate();
        List<StageData> stages = StageData.of(result.journal());
        List<Connection> traced = List.of(liveSteam, atTurbine, hpExtraction,
                deaeratorExtraction, lpExtraction, exhaust, condensate, feedwater);

        System.out.println(StageData.summary(stages));
        System.out.println(StageData.table(stages, traced));
        System.out.println(result.describe());

        assertEquals(List.of(Stage.INPUT, Stage.NORMALIZED, Stage.RESOLVED, Stage.SOLVED,
                        Stage.RESULT), stages.stream().map(StageData::stage).toList(),
                "журнал фиксирует этапы по порядку расчёта");

        // Этап 1, INPUT: режим — это четыре давления, остальное неизвестно.
        StageData input = StageData.of(result.journal(), Stage.INPUT);
        assertEquals(4, input.state().filled(),
                "на входе — давления отборов и вакуум: " + input.state().describe());
        assertEquals(3.5, input.value(hpExtraction, Quantity.PRESSURE), 1e-12,
                "давление отбора — исходные данные, а не результат");
        assertNull(input.value(atTurbine, Quantity.ENTHALPY),
                "энтальпию свежего пара задаёт котёл, а не режим");

        // Этап 2, NORMALIZED: известное давление доходит по участку трассы до аппарата.
        StageData normalized = StageData.of(result.journal(), Stage.NORMALIZED);
        assertEquals(3.5, normalized.value(hpHeaterSteam, Quantity.PRESSURE), 1e-12,
                "труба перенесла давление отбора на вход ПВД");
        assertEquals(Stage.NORMALIZED, normalized.origin(hpHeaterSteam, Quantity.PRESSURE).stage(),
                "величину на другом конце участка записала труба");
        assertNull(normalized.value(atTurbine, Quantity.PRESSURE),
                "свежий пар ещё не посчитан: котёл работает на этапе RESOLVED");

        // Этап 3, RESOLVED: аппараты посчитали состояния — давления и энтальпии, без расходов.
        StageData resolved = StageData.of(result.journal(), Stage.RESOLVED);
        assertEquals(24.0, resolved.value(atTurbine, Quantity.PRESSURE), 1e-9,
                "котёл задал давление свежего пара");
        double liveEnthalpy = resolved.value(atTurbine, Quantity.ENTHALPY);
        assertTrue(liveEnthalpy > 3000 && liveEnthalpy < 3500, "h₀ = " + liveEnthalpy);
        assertEquals(Stage.RESOLVED, resolved.origin(atTurbine, Quantity.ENTHALPY).stage(),
                "энтальпию посчитал аппарат");
        assertNull(resolved.value(liveSteam, Quantity.FLOW),
                "расходы решаются на следующем этапе: " + resolved.describe());

        // Этап 4, SOLVED: балансы добавили расходы — и только их.
        StageData solved = StageData.of(result.journal(), Stage.SOLVED);
        assertTrue(solved.changes().stream().allMatch(change -> change.quantity() == Quantity.FLOW),
                "балансы добавляют только расходы: " + solved.state().describe());
        assertEquals(1.0021, solved.value(liveSteam, Quantity.FLOW), 1e-3,
                () -> "D₀ в реперном режиме: " + solved.value(liveSteam, Quantity.FLOW));
        assertEquals(0.1771, solved.value(hpExtraction, Quantity.FLOW), 2e-3,
                () -> "отбор на ПВД: " + solved.value(hpExtraction, Quantity.FLOW));
        assertEquals(0.6462, solved.value(exhaust, Quantity.FLOW), 2e-3,
                () -> "выхлоп в конденсатор: " + solved.value(exhaust, Quantity.FLOW));
        assertEquals(solved.value(liveSteam, Quantity.FLOW),
                solved.value(feedwater, Quantity.FLOW), 1e-9,
                "цикл замкнут: вся питательная вода возвращается в котёл");
        assertTrue(solved.missing().isEmpty(), "после балансов незаданных величин нет");

        // Этап 5, RESULT: нового состояния сечения нет — показатели живут в CycleResult.
        StageData outcome = StageData.of(result.journal(), Stage.RESULT);
        assertTrue(outcome.changes().isEmpty(),
                "мощности и КПД — свёртка состояний, а не состояние сечения");
        assertEquals(1040.6, result.calculatedElectricPower(), 1e-6,
                "задание по мощности выполнено");
        assertEquals(0.0, result.balanceResidual(), 1e-6,
                "Q_котла + N_н − N_i − Q_к = 0: " + result.describe());
        assertEquals(0.4606, result.grossEfficiency(), 1e-3,
                "КПД брутто реперного режима: " + result.grossEfficiency());
        assertTrue(result.netEfficiency() < result.grossEfficiency(),
                "работа насосов вычитается из КПД нетто: " + result.netEfficiency());

        // После расчёта схема снова держит только параметры: посчитанное — в журнале результата.
        assertNull(liveSteam.getFlow(), "схема не превратилась в результат расчёта");
    }
}
