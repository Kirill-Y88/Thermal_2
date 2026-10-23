package org.kpush88.Thermal_2.v3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Структура тепловой схемы v3: порты, связи и производный граф потоков — без балансов массы
 * и энергии и без расчёта состояний.
 *
 * <p>Собирается та же трасса регенеративного цикла, что и в v2 ({@code v2.SchemeStructureTest}):
 * котёл — турбина — три отбора — ПВД — деаэратор — ПНД, с питательным и конденсатным
 * насосами и замкнутым циклом через конденсатор. В v2 было 21 элемент, 52 «порта» (по два
 * конца на связь) и 26 рёбер графа; в v3 связь — самостоятельный объект с двумя портами,
 * поэтому 21 элемент, 26 связей и 52 конца связей ровно там, где в v2 были порты. Совпадение
 * чисел и есть проверка эквивалентности модели.
 *
 * <p>Отличие v3 — порты объявлены <b>классами</b> элементов, поэтому ассортимент стыков
 * задан конструкцией, а не сборкой: у котла нет второго патрубка воды, у турбины столько
 * отборов, сколько задано её конструктором, и у каждого отбора свой порт со своим
 * состоянием, а у ПВД ровно два дренажных входа. Эти правила проверяются здесь же —
 * {@link Scheme#link} отказывает в момент подключения, а {@link Scheme#validate()}
 * отчитывается о неполной схеме. Каждый переход «аппарат → аппарат» идёт через участок
 * трассы ({@link Pipe}), поэтому связь — только стык двух концов.
 */
class SchemeStructureTest {

    /** Собранная схема: аппараты, участки трассы и сама схема. */
    private record Trail(Scheme scheme,
                         Boiler boiler,
                         Turbine turbine,
                         SurfaceHeater hpHeater,
                         SurfaceHeater lpHeater,
                         Deaerator deaerator,
                         Condenser condenser,
                         Pump feedPump,
                         Pump condensatePump,
                         List<Pipe> pipes) {

        /** Сначала аппараты, затем участки трассы — в порядке сборки схемы. */
        List<Element> elements() {
            List<Element> all = new ArrayList<>(List.of(boiler, turbine, hpHeater,
                    deaerator, lpHeater, condenser, condensatePump, feedPump));
            all.addAll(pipes);
            return all;
        }

        /** Число концов связей на элементах — то, что в v2 называлось числом портов. */
        int ends() {
            int ends = 0;
            for (Element element : elements()) {
                ends += element.connections().size();
            }
            return ends;
        }
    }

    /** Имена связей — для сравнения списков без обхода в каждом тесте. */
    private static List<String> names(List<Connection> connections) {
        return connections.stream().map(Connection::getName).toList();
    }

    /** Имена портов — для проверки состава каналов потока. */
    private static List<String> portNames(List<Port> ports) {
        return ports.stream().map(Port::name).toList();
    }

    /**
     * Собирает трассу в два шага, как это будет делать загрузчик схемы: сначала элементы
     * ({@link Scheme#add}), затем связи «выход → вход» ({@link Scheme#link}).
     */
    private static Trail buildTrail() {
        // 1. Аппараты: котёл, турбина, подогреватели, деаэратор, конденсатор и насосы.
        Boiler boiler = new Boiler("Котёл");
        Turbine turbine = new Turbine("Турбина", 3);
        SurfaceHeater hpHeater = new SurfaceHeater("ПВД");
        Deaerator deaerator = new Deaerator("Деаэратор");
        SurfaceHeater lpHeater = new SurfaceHeater("ПНД");
        Condenser condenser = new Condenser("Конденсатор");
        Pump condensatePump = new Pump("Конденсатный насос");
        Pump feedPump = new Pump("Питательный насос");

        // 2. Участки трассы: каждый переход «аппарат → аппарат» идёт через трубу, контур
        //    которой задан её конструктором.
        Pipe liveSteam = new Pipe("Паропровод свежего пара", SpaceType.STEAM);
        Pipe extraction1 = new Pipe("Паропровод отбора 1", SpaceType.STEAM);
        Pipe extraction2 = new Pipe("Паропровод отбора 2", SpaceType.STEAM);
        Pipe extraction3 = new Pipe("Паропровод отбора 3", SpaceType.STEAM);
        Pipe hpDrain = new Pipe("Дренажная линия ПВД", SpaceType.STEAM);
        Pipe lpDrain = new Pipe("Дренажная линия ПНД", SpaceType.STEAM);
        Pipe exhaust = new Pipe("Паропровод в конденсатор", SpaceType.STEAM);
        Pipe lpWater = new Pipe("Линия воды за ПНД", SpaceType.WATER);
        Pipe pumpSuction = new Pipe("Всасывающая линия питательного насоса", SpaceType.WATER);
        Pipe feedwater = new Pipe("Напорная линия питательного насоса", SpaceType.WATER);
        Pipe hpWater = new Pipe("Линия воды за ПВД", SpaceType.WATER);
        Pipe condensate = new Pipe("Линия основного конденсата", SpaceType.WATER);
        Pipe pumpDischarge = new Pipe("Напорная линия конденсатного насоса", SpaceType.WATER);
        List<Pipe> pipes = List.of(liveSteam, extraction1, extraction2, extraction3,
                hpDrain, lpDrain, exhaust, lpWater, pumpSuction, feedwater, hpWater,
                condensate, pumpDischarge);

        Scheme scheme = new Scheme()
                .add(boiler)
                .add(turbine)
                .add(hpHeater)
                .add(deaerator)
                .add(lpHeater)
                .add(condenser)
                .add(condensatePump)
                .add(feedPump);
        for (Pipe pipe : pipes) {
            scheme.add(pipe);
        }

        // 3. Свежий пар: котёл -> паропровод -> турбина.
        scheme.link(boiler.steamOut(), liveSteam.in(), "свежий пар");
        scheme.link(liveSteam.out(), turbine.steamIn(), "свежий пар");

        // Отборы турбины -> греющие стороны подогревателей и деаэратор: у каждого отбора
        // свой порт, поэтому давление отбора задаётся связью этого отбора.
        scheme.link(turbine.extractionOut(1), extraction1.in(), "отбор 1");
        scheme.link(extraction1.out(), hpHeater.steamIn(), "отбор 1");
        scheme.link(turbine.extractionOut(2), extraction2.in(), "отбор 2");
        scheme.link(extraction2.out(), deaerator.steamIn(), "отбор 2");
        scheme.link(turbine.extractionOut(3), extraction3.in(), "отбор 3");
        scheme.link(extraction3.out(), lpHeater.steamIn(), "отбор 3");

        // Дренаж ПВД идёт в деаэратор, дренаж ПНД — в конденсатор.
        scheme.link(hpHeater.drainOut(), hpDrain.in(), "дренаж ПВД");
        scheme.link(hpDrain.out(), deaerator.drainIn(), "дренаж ПВД");
        scheme.link(lpHeater.drainOut(), lpDrain.in(), "дренаж ПНД");
        scheme.link(lpDrain.out(), condenser.drainIn(), "дренаж ПНД");

        // Выхлоп турбины конденсируется, конденсат через насос возвращается в ПНД.
        scheme.link(turbine.exhaustOut(), exhaust.in(), "выхлоп");
        scheme.link(exhaust.out(), condenser.exhaustIn(), "выхлоп");
        scheme.link(condenser.condensateOut(), condensate.in(), "основной конденсат");
        scheme.link(condensate.out(), condensatePump.waterIn(), "основной конденсат");
        scheme.link(condensatePump.waterOut(), pumpDischarge.in(), "конденсат после насоса");
        scheme.link(pumpDischarge.out(), lpHeater.waterIn(), "конденсат после насоса");

        // Тракт воды: ПНД -> деаэратор -> питательный насос -> ПВД -> котёл.
        scheme.link(lpHeater.waterOut(), lpWater.in(), "вода за ПНД");
        scheme.link(lpWater.out(), deaerator.waterIn(), "вода за ПНД");
        scheme.link(deaerator.waterOut(), pumpSuction.in(), "вода после деаэратора");
        scheme.link(pumpSuction.out(), feedPump.waterIn(), "вода после деаэратора");
        scheme.link(feedPump.waterOut(), feedwater.in(), "питательная вода");
        scheme.link(feedwater.out(), hpHeater.waterIn(), "питательная вода");
        scheme.link(hpHeater.waterOut(), hpWater.in(), "вода за ПВД");
        scheme.link(hpWater.out(), boiler.waterIn(), "вода за ПВД");

        return new Trail(scheme, boiler, turbine, hpHeater, lpHeater, deaerator, condenser,
                feedPump, condensatePump, pipes);
    }

    // ---------- Структура трассы ----------

    @Test
    @DisplayName("Трасса v3 эквивалентна v2: 21 элемент, 26 связей, 52 конца вместо 52 портов")
    void trailMatchesV2Structure() {
        Trail trail = buildTrail();

        assertEquals(21, trail.scheme().getElements().size());
        assertEquals(26, trail.scheme().getConnections().size());
        assertEquals(52, trail.ends(), "каждая связь даёт по концу на каждый из двух элементов");
    }

    @Test
    @DisplayName("Собранная трасса структурно корректна: валидатор не находит ни одной ошибки")
    void completeTrailIsValid() {
        Trail trail = buildTrail();
        List<String> problems = trail.scheme().validate();

        assertTrue(problems.isEmpty(), problems::toString);
        assertTrue(trail.scheme().danglingConnections().isEmpty(), "у трассы нет открытых концов");
    }

    @Test
    @DisplayName("Каждая связь помнит оба порта, и порт числит её среди своих связей")
    void everyConnectionKeepsBothPorts() {
        Trail trail = buildTrail();

        for (Connection connection : trail.scheme().getConnections()) {
            Port out = connection.getPortOut();
            Port in = connection.getPortIn();

            assertNotNull(out, connection::getName);
            assertNotNull(in, connection::getName);
            assertFalse(connection.isOpen(), connection::getName);
            assertTrue(out.isOutput(), () -> "связь начинается не с выхода: " + connection);
            assertTrue(in.isInput(), () -> "связь заканчивается не на входе: " + connection);
            assertTrue(out.element().getOutPutConnections(out).contains(connection),
                    () -> "порт не числит связь выходной: " + connection);
            assertTrue(in.element().getInPutConnections(in).contains(connection),
                    () -> "порт не числит связь входной: " + connection);
            assertEquals(out.space(), in.space(), "у связи один контур на обоих концах");
            assertNotSame(out.element(), in.element(), "связь не замыкается на одном элементе");
        }
    }

    @Test
    @DisplayName("Число связей на элементах соответствует типу элемента")
    void elementConnectionCountsMatchTheirType() {
        Trail trail = buildTrail();

        assertEquals(1, trail.boiler().getInPutConnections().size());
        assertEquals(1, trail.boiler().getOutPutConnections().size());

        assertEquals(1, trail.turbine().getInPutConnections().size());
        assertEquals(4, trail.turbine().getOutPutConnections().size(), "три отбора и выхлоп");

        assertEquals(2, trail.hpHeater().getInPutConnections().size());
        assertEquals(2, trail.hpHeater().getOutPutConnections().size());

        assertEquals(3, trail.deaerator().getInPutConnections().size());
        assertEquals(1, trail.deaerator().getOutPutConnections().size());

        assertEquals(2, trail.lpHeater().getInPutConnections().size());
        assertEquals(2, trail.lpHeater().getOutPutConnections().size());

        assertEquals(2, trail.condenser().getInPutConnections().size());
        assertEquals(1, trail.condenser().getOutPutConnections().size(),
                "дренаж конденсатора и есть конденсат, отдельного выхода нет");

        assertEquals(1, trail.feedPump().getInPutConnections().size());
        assertEquals(1, trail.feedPump().getOutPutConnections().size());
        assertEquals(1, trail.condensatePump().getInPutConnections().size());
        assertEquals(1, trail.condensatePump().getOutPutConnections().size());

        for (Pipe pipe : trail.pipes()) {
            assertEquals(1, pipe.getInPutConnections().size(), () -> "у трубы один вход: " + pipe);
            assertEquals(1, pipe.getOutPutConnections().size(), () -> "у трубы один выход: " + pipe);
        }
    }

    // ---------- Порядок и стороны ----------

    @Test
    @DisplayName("У каждого отбора свой порт: порядок отборов задан объявлением, а не сборкой")
    void turbineDeclaresPortPerExtractionInExpansionOrder() {
        Trail trail = buildTrail();
        Turbine turbine = trail.turbine();

        assertEquals(List.of("extraction1Out", "extraction2Out", "extraction3Out"),
                portNames(turbine.extractionOuts()));
        assertEquals(List.of("steamIn", "extraction1Out", "extraction2Out", "extraction3Out",
                        "exhaustOut"),
                portNames(turbine.getPorts()), "выхлоп объявлен последним");

        for (Port extraction : turbine.extractionOuts()) {
            assertEquals(1, turbine.portSize(extraction),
                    () -> "на порт отбора подключается ровно один паропровод: " + extraction);
        }
        assertEquals(List.of("отбор 1", "отбор 2", "отбор 3", "выхлоп"),
                names(turbine.getOutPutConnections()));
    }

    @Test
    @DisplayName("Порядок отборов не зависит от порядка link: порты объявлены по ходу расширения")
    void extractionOrderDoesNotDependOnWiringOrder() {
        Turbine turbine = new Turbine("Турбина", 3);
        Pipe third = new Pipe("Паропровод отбора 3", SpaceType.STEAM);
        Pipe first = new Pipe("Паропровод отбора 1", SpaceType.STEAM);
        Scheme scheme = new Scheme().add(turbine).add(third).add(first);

        scheme.link(turbine.extractionOut(3), third.in(), "отбор 3");
        scheme.link(turbine.extractionOut(1), first.in(), "отбор 1");

        assertEquals(List.of("отбор 1", "отбор 3"), names(turbine.getOutPutConnections()),
                "хотя третьим отбором связались раньше: порядок задают порты, а не link");
    }

    @Test
    @DisplayName("Отборы — разные узлы: давление первого отбора не влияет на третий")
    void eachExtractionKeepsItsOwnState() {
        Trail trail = buildTrail();
        Turbine turbine = trail.turbine();

        Connection hot = turbine.getOutPutConnections(turbine.extractionOut(1)).get(0);
        Connection cold = turbine.getOutPutConnections(turbine.extractionOut(3)).get(0);
        assertNotSame(hot, cold, "у каждого отбора своя связь, а значит своё состояние");

        hot.withState(12.0, 3100.0).withFlow(2.5);
        cold.withState(1.2, 2600.0).withFlow(1.0);

        assertEquals(12.0, hot.getPressure(), 1e-9);
        assertEquals(1.2, cold.getPressure(), 1e-9);
        assertEquals(2.5, hot.getFlow(), 1e-9);
        assertEquals(1.0, cold.getFlow(), 1e-9);
        assertNotEquals(hot.getPressure(), cold.getPressure());
    }

    @Test
    @DisplayName("Число отборов — свойство конструкции: лишнего отбора нет, отрицательное число отвергается")
    void extractionCountIsPartOfDesign() {
        Turbine condensing = new Turbine("Конденсационная", 0);
        assertTrue(condensing.extractionOuts().isEmpty(), "конденсационная турбина без отборов");
        assertEquals(List.of("steamIn", "exhaustOut"), portNames(condensing.getPorts()));
        assertThrows(IllegalArgumentException.class, () -> condensing.extractionOut(1));

        Turbine turbine = new Turbine("Турбина", 2);
        assertEquals(2, turbine.extractionOuts().size());
        assertThrows(IllegalArgumentException.class, () -> turbine.extractionOut(0),
                "нумерация отборов начинается с единицы");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> turbine.extractionOut(3));
        assertTrue(error.getMessage().contains("объявлено отборов — 2"), error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new Turbine("Кривая", -1));

        Pipe first = new Pipe("Паропровод отбора 1", SpaceType.STEAM);
        Pipe second = new Pipe("Второй паропровод отбора 1", SpaceType.STEAM);
        Scheme scheme = new Scheme().add(turbine).add(first).add(second);
        scheme.link(turbine.extractionOut(1), first.in(), "отбор 1");

        IllegalStateException busy = assertThrows(IllegalStateException.class,
                () -> scheme.link(turbine.extractionOut(1), second.in(), "ещё один отбор 1"));
        assertTrue(busy.getMessage().contains("объявлено не более 1"), busy.getMessage());
    }

    @Test
    @DisplayName("Мощность и КПД турбоагрегата — параметры режима с проверкой диапазона")
    void electricPowerAndEfficiencyAreChecked() {
        Turbine turbine = new Turbine("Турбина", 2);
        assertNull(turbine.getElectricPower(), "мощность не задана по умолчанию");
        assertNull(turbine.getElectricEfficiency(), "КПД не задан по умолчанию");

        turbine.withElectricPower(1040.6).withElectricEfficiency(0.985);

        assertEquals(1040.6, turbine.getElectricPower(), 1e-9);
        assertEquals(0.985, turbine.getElectricEfficiency(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> turbine.withElectricPower(0.0),
                "нулевая мощность — не задание расчёта");
        assertThrows(IllegalArgumentException.class, () -> turbine.withElectricPower(-1.0));
        assertThrows(IllegalArgumentException.class, () -> turbine.withElectricEfficiency(0.0));
        assertThrows(IllegalArgumentException.class, () -> turbine.withElectricEfficiency(1.2),
                "потерь больше, чем есть мощности, не бывает");
        assertEquals(1040.6, turbine.getElectricPower(), 1e-9,
                "отвергнутый параметр не переписывает принятый");
    }

    @Test
    @DisplayName("Стороны аппарата не смешиваются: связи фильтруются по контуру порта")
    void surfaceHeaterKeepsSidesApart() {
        SurfaceHeater hpHeater = buildTrail().hpHeater();

        assertEquals(List.of("отбор 1"), names(hpHeater.getInPutConnections(SpaceType.STEAM)));
        assertEquals(List.of("питательная вода"), names(hpHeater.getInPutConnections(SpaceType.WATER)));
        assertEquals(List.of("дренаж ПВД"), names(hpHeater.getOutPutConnections(SpaceType.STEAM)));
        assertEquals(List.of("вода за ПВД"), names(hpHeater.getOutPutConnections(SpaceType.WATER)));

        assertTrue(hpHeater.getInPutConnections(hpHeater.drainIn()).isEmpty(),
                "порт-коллектор объявлен, но в этой трассе дренаж в ПВД не приходит");
        assertEquals(5, hpHeater.getPorts().size(),
                "у ПВД пять портов: греющий пар, дренажи, дренаж, вода на входе и выходе");
    }

    @Test
    @DisplayName("Порядок элементов и связей воспроизводим от сборки к сборке")
    void orderIsDeterministic() {
        Trail first = buildTrail();
        Trail second = buildTrail();

        assertEquals(first.scheme().getElements().stream().map(Element::name).toList(),
                second.scheme().getElements().stream().map(Element::name).toList());
        assertEquals(first.scheme().getConnections().stream().map(Connection::getPortsUnames).toList(),
                second.scheme().getConnections().stream().map(Connection::getPortsUnames).toList());
    }

    // ---------- Порты и кратность ----------

    @Test
    @DisplayName("Порт объявляет направление, контур и кратность; имя порта проверяется")
    void portDeclaresDirectionSpaceAndCapacity() {
        Turbine turbine = new Turbine("Турбина", 3);
        Port steamIn = turbine.port("steamIn");
        Port extraction1Out = turbine.extractionOut(1);
        Port exhaustOut = turbine.exhaustOut();

        assertEquals(PortDirection.IN, steamIn.direction());
        assertEquals(SpaceType.STEAM, steamIn.space());
        assertTrue(steamIn.isRequired());
        assertFalse(steamIn.acceptsMany());
        assertEquals("1", steamIn.capacity());
        assertEquals("Турбина.steamIn", steamIn.label());

        assertEquals(PortDirection.OUT, extraction1Out.direction());
        assertEquals(SpaceType.STEAM, extraction1Out.space());
        assertFalse(extraction1Out.isRequired(), "отбор может быть не подключён");
        assertFalse(extraction1Out.acceptsMany(),
                "у отбора свой порт: второй патрубок на том же узле не нужен");
        assertEquals("0…1", extraction1Out.capacity());
        assertEquals("Турбина.extraction1Out", extraction1Out.label());

        assertEquals(PortDirection.OUT, exhaustOut.direction());
        assertTrue(exhaustOut.isRequired(), "выхлоп в конденсатор обязателен");
        assertEquals("1", exhaustOut.capacity());
        assertEquals(5, turbine.getPorts().size(), "вход, три отбора и выхлоп");

        assertSame(steamIn, turbine.findPort("steamIn").orElseThrow());
        assertSame(extraction1Out, turbine.port("extraction1Out"));
        assertTrue(turbine.owns(steamIn));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> turbine.port("extractionOut"));
        assertTrue(error.getMessage().contains("объявлены"), error.getMessage());
    }

    @Test
    @DisplayName("Связь сверх кратности отвергается: у трубы один вход и один выход")
    void secondLinkOnSinglePortIsRejected() {
        Pipe first = new Pipe("Паропровод 1", SpaceType.STEAM);
        Pipe second = new Pipe("Паропровод 2", SpaceType.STEAM);
        Pipe third = new Pipe("Паропровод 3", SpaceType.STEAM);
        Scheme scheme = new Scheme().add(first).add(second).add(third);

        scheme.link(first.out(), second.in(), "стык 1");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> scheme.link(third.out(), second.in(), "стык 2"));

        assertTrue(error.getMessage().contains("объявлено не более 1"), error.getMessage());
        assertEquals(1, second.portSize(second.in()));
    }

    @Test
    @DisplayName("Коллектор принимает несколько связей: в деаэратор может приходить несколько потоков воды")
    void collectorAcceptsSeveralLinks() {
        Deaerator deaerator = new Deaerator("Деаэратор");
        Pipe condensate = new Pipe("Основной конденсат", SpaceType.WATER);
        Pipe makeUp = new Pipe("Добавочная вода", SpaceType.WATER);
        Scheme scheme = new Scheme().add(deaerator).add(condensate).add(makeUp);

        scheme.link(condensate.out(), deaerator.waterIn(), "основной конденсат");
        scheme.link(makeUp.out(), deaerator.waterIn(), "добавочная вода");

        assertEquals(2, deaerator.portSize(deaerator.waterIn()));
        assertEquals(List.of("основной конденсат", "добавочная вода"),
                names(deaerator.getInPutConnections(deaerator.waterIn())));
    }

    @Test
    @DisplayName("Направление проверяется при подключении: связь начинается только с выхода")
    void directionIsCheckedWhenLinking() {
        Pipe first = new Pipe("Паропровод 1", SpaceType.STEAM);
        Pipe second = new Pipe("Паропровод 2", SpaceType.STEAM);
        Scheme scheme = new Scheme().add(first).add(second);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> scheme.link(first.in(), second.in(), "наоборот"));

        assertTrue(error.getMessage().contains("вход"), error.getMessage());
        assertEquals(0, scheme.getConnections().size(), "отвергнутая связь не попадает в схему");
    }

    // ---------- Проверки сборки ----------

    @Test
    @DisplayName("Пар в водяной порт не проходит: у связи один контур на обоих концах")
    void spaceMismatchIsRejectedAtLink() {
        Pipe steam = new Pipe("Паропровод", SpaceType.STEAM);
        Pipe water = new Pipe("Водовод", SpaceType.WATER);
        Scheme scheme = new Scheme().add(steam).add(water);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> scheme.link(steam.out(), water.in(), "переход"));

        assertTrue(error.getMessage().contains("контур меняется только внутри элемента"),
                error.getMessage());
        assertTrue(scheme.getConnections().isEmpty(), "отвергнутая связь не попадает в схему");
        assertEquals(0, steam.portSize(steam.out()), "и не числится на порту");
    }

    @Test
    @DisplayName("Порт элемента, не добавленного в схему, не принимается")
    void unregisteredPortIsRejected() {
        Pipe registered = new Pipe("Паропровод", SpaceType.STEAM);
        Pipe outside = new Pipe("Чужой паропровод", SpaceType.STEAM);
        Scheme scheme = new Scheme().add(registered);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> scheme.link(registered.out(), outside.in(), "стык"));

        assertTrue(error.getMessage().contains("не зарегистрированному"), error.getMessage());
    }

    @Test
    @DisplayName("Связи создаёт только схема: элемент с готовыми связями не регистрируется")
    void elementWithConnectionsCannotBeAdded() {
        Pipe first = new Pipe("Паропровод 1", SpaceType.STEAM);
        Pipe second = new Pipe("Паропровод 2", SpaceType.STEAM);
        Scheme scheme = new Scheme().add(first).add(second);
        scheme.link(first.out(), second.in(), "стык");

        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> scheme.add(second));
        assertTrue(duplicate.getMessage().contains("уже добавлен"), duplicate.getMessage());

        IllegalStateException foreign = assertThrows(IllegalStateException.class,
                () -> new Scheme().add(second));
        assertTrue(foreign.getMessage().contains("с готовыми связями"), foreign.getMessage());
    }

    // ---------- Каналы потока ----------

    @Test
    @DisplayName("Котёл: канал пересекает контуры — вода входит, пар выходит")
    void boilerChannelCrossesSpaces() {
        List<FlowChannel> channels = buildTrail().boiler().flowChannels();

        assertEquals(1, channels.size());
        FlowChannel channel = channels.get(0);
        assertEquals(SpaceType.STEAM, channel.space(), "контур канала — контур выхода");
        assertTrue(channel.crossesSpaces());
        assertEquals(List.of("waterIn"), portNames(channel.in()));
        assertEquals(List.of("steamOut"), portNames(channel.out()));
    }

    @Test
    @DisplayName("Турбина: один проток пара, но каждый отбор — отдельный выход канала")
    void turbineHasSingleSteamChannel() {
        List<FlowChannel> channels = buildTrail().turbine().flowChannels();

        assertEquals(1, channels.size(), "весь пар турбины — один контур");
        FlowChannel channel = channels.get(0);
        assertEquals(SpaceType.STEAM, channel.space());
        assertFalse(channel.crossesSpaces());
        assertEquals(List.of("steamIn"), portNames(channel.in()));
        assertEquals(List.of("extraction1Out", "extraction2Out", "extraction3Out", "exhaustOut"),
                portNames(channel.out()), "отборы по ходу расширения, выхлоп последний");
    }

    @Test
    @DisplayName("Поверхностный подогреватель: два канала, пар и вода не смешиваются")
    void surfaceHeaterHasTwoChannels() {
        List<FlowChannel> channels = buildTrail().hpHeater().flowChannels();

        assertEquals(2, channels.size());
        assertEquals(SpaceType.STEAM, channels.get(0).space());
        assertEquals(List.of("steamIn", "drainIn"), portNames(channels.get(0).in()));
        assertEquals(List.of("drainOut"), portNames(channels.get(0).out()));
        assertEquals(SpaceType.WATER, channels.get(1).space());
        assertEquals(List.of("waterIn"), portNames(channels.get(1).in()));
        assertEquals(List.of("waterOut"), portNames(channels.get(1).out()));
        assertFalse(channels.get(0).crossesSpaces(), "тепло идёт через стенку, а не потоком");
        assertFalse(channels.get(1).crossesSpaces());
    }

    @Test
    @DisplayName("Смешение внутри элемента: у деаэратора один канал, выход — вода")
    void deaeratorHasSingleMixingChannel() {
        List<FlowChannel> channels = buildTrail().deaerator().flowChannels();

        assertEquals(1, channels.size());
        assertEquals(SpaceType.WATER, channels.get(0).space());
        assertTrue(channels.get(0).crossesSpaces());
        assertEquals(List.of("steamIn", "drainIn", "waterIn"), portNames(channels.get(0).in()));
        assertEquals(List.of("waterOut"), portNames(channels.get(0).out()));
    }

    @Test
    @DisplayName("Каждый порт входит ровно в один канал, иначе валидатор сообщит")
    void everyPortBelongsToExactlyOneChannel() {
        Trail trail = buildTrail();

        for (Element element : trail.elements()) {
            Map<Port, Integer> seen = new IdentityHashMap<>();
            for (FlowChannel channel : element.flowChannels()) {
                channel.in().forEach(port -> seen.merge(port, 1, Integer::sum));
                channel.out().forEach(port -> seen.merge(port, 1, Integer::sum));
            }

            assertEquals(element.getPorts().size(), seen.size(),
                    () -> "не все порты попали в каналы: " + element);
            for (Port port : element.getPorts()) {
                assertEquals(1, seen.getOrDefault(port, 0),
                        () -> "порт " + port.label() + " в " + seen.getOrDefault(port, 0) + " каналах");
            }
        }
    }

    // ---------- Границы схемы ----------

    @Test
    @DisplayName("Связь «аппарат → аппарат» видна валидатору: между ними должен стоять участок трассы")
    void deviceToDeviceLinkIsReportedByValidate() {
        Boiler boiler = new Boiler("Котёл");
        Turbine turbine = new Turbine("Турбина", 1);
        Scheme scheme = new Scheme().add(boiler).add(turbine);
        scheme.link(boiler.steamOut(), turbine.steamIn(), "напрямую");

        List<String> problems = scheme.validate();

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("напрямую")),
                problems::toString);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("должен стоять трубопровод")),
                problems::toString);
    }

    @Test
    @DisplayName("Открытый конец — граница схемы: он виден, но ошибкой не считается")
    void openEndsAreDiagnosticsNotErrors() {
        Pipe pipe = new Pipe("Паропровод", SpaceType.STEAM);
        Scheme scheme = new Scheme().add(pipe);

        Connection fromBorder = scheme.openIn(pipe.in(), "из-за границы");
        Connection toBorder = scheme.openOut(pipe.out(), "за границу");

        assertTrue(scheme.validate().isEmpty(), () -> scheme.validate().toString());
        assertEquals(List.of(fromBorder, toBorder), scheme.danglingConnections());
        assertEquals(1, pipe.portSize(pipe.in()));
        assertEquals(1, pipe.portSize(pipe.out()));

        assertTrue(fromBorder.isOpen());
        assertNull(fromBorder.getPortOut());
        assertEquals("?_Паропровод", fromBorder.getElementsUnames());
        assertEquals("Паропровод_?", toBorder.getElementsUnames());
        assertEquals("Паропровод.out → ?", toBorder.getPortsUnames());

        assertThrows(IllegalStateException.class, () -> scheme.openIn(pipe.in(), "ещё один"),
                "кратность порта действует и для границы схемы");
    }

    @Test
    @DisplayName("Незанятый обязательный порт — ошибка валидатора, а не повод отказать в сборке")
    void requiredPortsAreReportedByValidator() {
        Pipe pipe = new Pipe("Паропровод", SpaceType.STEAM);
        Scheme scheme = new Scheme().add(pipe);

        List<String> problems = scheme.validate();

        assertEquals(2, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("Паропровод.in"), problems.get(0));
        assertTrue(problems.get(0).contains("не менее 1"), problems.get(0));
        assertTrue(problems.get(1).contains("Паропровод.out"), problems.get(1));
    }

    @Test
    @DisplayName("Тройник — ровно три конца: разветвление или слияние, иначе ошибка")
    void pipeTeeRequiresExactlyThreeEnds() {
        PipeTee tee = new PipeTee("Тройник", SpaceType.WATER);
        assertTrue(tee.validate().stream().anyMatch(problem -> problem.contains("а нужно три")),
                () -> tee.validate().toString());

        Pipe trunk = new Pipe("Напорная линия", SpaceType.WATER);
        Pipe first = new Pipe("Ответвление 1", SpaceType.WATER);
        Pipe second = new Pipe("Ответвление 2", SpaceType.WATER);
        Scheme scheme = new Scheme().add(tee).add(trunk).add(first).add(second);
        scheme.link(trunk.out(), tee.in(), "основной поток");
        scheme.link(tee.out(), first.in(), "основной поток");
        scheme.link(tee.out(), second.in(), "ответвление");

        assertEquals(3, tee.getInPutConnections().size() + tee.getOutPutConnections().size());
        assertTrue(tee.validate().isEmpty(), () -> tee.validate().toString());
        assertEquals(List.of("основной поток", "ответвление"),
                names(tee.getOutPutConnections(tee.out())), "порядок потоков на тройнике сохраняется");
    }

    @Test
    @DisplayName("Отрицательные давление и расход — ошибка схемы, а не элемента")
    void negativeStateIsReported() {
        Trail trail = buildTrail();
        Connection connection = trail.scheme().getConnections().get(0);
        connection.withState(-0.1, 3000.0).withFlow(-5.0);

        List<String> problems = trail.scheme().validate();

        assertEquals(2, problems.size(), problems::toString);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("отрицательное давление")),
                problems::toString);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("отрицательный расход")),
                problems::toString);
    }

    // ---------- Граф потоков ----------

    @Test
    @DisplayName("Граф потоков выводится из каналов: рёбер = |входы| × |выходы| на каждый канал")
    void flowGraphIsDerivedFromChannels() {
        Trail trail = buildTrail();
        SchemeGraph<Connection> graph = trail.scheme().flowGraph();

        assertEquals(trail.scheme().getConnections().size(), graph.vertexCount(),
                "вершины графа — сами связи");
        assertEquals(29, graph.edgeCount(), "1 котёл + 4 турбина + 2 ПВД + 3 деаэратор + 2 ПНД "
                + "+ 2 конденсатор + 2 насоса + 13 труб");
    }

    @Test
    @DisplayName("Граф не хранится: после каждой связи он пересчитывается заново")
    void flowGraphFollowsConstruction() {
        Pipe first = new Pipe("Паропровод 1", SpaceType.STEAM);
        Pipe second = new Pipe("Паропровод 2", SpaceType.STEAM);
        Pipe third = new Pipe("Паропровод 3", SpaceType.STEAM);
        Scheme scheme = new Scheme().add(first).add(second).add(third);

        assertEquals(0, scheme.flowGraph().vertexCount(), "до связей потока нет");

        Connection firstToSecond = scheme.link(first.out(), second.in(), "стык 1");
        assertEquals(1, scheme.flowGraph().vertexCount());
        assertEquals(0, scheme.flowGraph().edgeCount(), "предшественника ещё нет — ребра нет");

        Connection thirdToFirst = scheme.link(third.out(), first.in(), "стык 2");
        SchemeGraph<Connection> graph = scheme.flowGraph();

        assertEquals(2, graph.vertexCount());
        assertEquals(1, graph.edgeCount());
        assertTrue(graph.hasEdge(thirdToFirst, firstToSecond), "труба: вход → выход");
        assertFalse(graph.hasEdge(firstToSecond, thirdToFirst), "ребро направлено по потоку");
    }

    @Test
    @DisplayName("Паровой и водяной графы раздельны: переход контура внутри элемента — не ребро")
    void steamAndWaterGraphsAreSeparate() {
        Trail trail = buildTrail();
        Scheme scheme = trail.scheme();
        SchemeGraph<Connection> steam = scheme.flowGraph(SpaceType.STEAM);
        SchemeGraph<Connection> water = scheme.flowGraph(SpaceType.WATER);

        assertEquals(14, steam.vertexCount(), "7 паропроводов по два конца");
        assertEquals(12, water.vertexCount(), "6 водяных участков по два конца");
        assertEquals(scheme.getConnections().size(), steam.vertexCount() + water.vertexCount());
        assertEquals(13, steam.edgeCount());
        assertEquals(11, water.edgeCount());
        assertEquals(29 - 5, steam.edgeCount() + water.edgeCount(),
                "пять рёбер потеряны: переходы контура в котле (1) и конденсаторе (2) "
                        + "и два паровых входа деаэратора, которые смешиваются с водой");

        for (Connection connection : steam.getVertices()) {
            assertEquals(SpaceType.STEAM, connection.getSpace());
        }
        for (Connection connection : water.getVertices()) {
            assertEquals(SpaceType.WATER, connection.getSpace());
        }
    }


    @Test
    @DisplayName("Замкнутый контур — одна компонента сильной связности, топологический порядок невозможен")
    void closedLoopIsStronglyConnected() {
        Pump pump = new Pump("Питательный насос");
        SurfaceHeater heater = new SurfaceHeater("ПВД");
        Pipe suction = new Pipe("Всасывающая линия", SpaceType.WATER);
        Pipe discharge = new Pipe("Напорная линия", SpaceType.WATER);
        Scheme scheme = new Scheme().add(pump).add(heater).add(suction).add(discharge);

        Connection fromHeater = scheme.link(heater.waterOut(), suction.in(), "вода за ПВД");
        Connection toPump = scheme.link(suction.out(), pump.waterIn(), "вода за ПВД");
        Connection fromPump = scheme.link(pump.waterOut(), discharge.in(), "напор");
        Connection toHeater = scheme.link(discharge.out(), heater.waterIn(), "напор");

        SchemeGraph<Connection> water = scheme.flowGraph(SpaceType.WATER);

        assertEquals(4, water.vertexCount());
        assertEquals(4, water.edgeCount());
        assertTrue(water.hasCycle(), "вода возвращается в подогреватель");
        assertThrows(IllegalStateException.class, water::topologicalOrder);
        assertEquals(Set.of(fromHeater, toPump, fromPump, toHeater),
                water.reachableFrom(toPump), "из любого сечения достижим весь контур");

        List<Set<Connection>> components = water.stronglyConnectedComponents();
        assertEquals(1, components.size(), "контур решается совместно");
        assertEquals(Set.of(fromHeater, toPump, fromPump, toHeater), components.get(0));
    }

    @Test
    @DisplayName("Разомкнутая цепочка: топологический порядок совпадает с направлением потока")
    void openChainHasTopologicalOrder() {
        Pipe first = new Pipe("Паропровод 1", SpaceType.STEAM);
        Pipe second = new Pipe("Паропровод 2", SpaceType.STEAM);
        Pipe third = new Pipe("Паропровод 3", SpaceType.STEAM);
        Scheme scheme = new Scheme().add(first).add(second).add(third);

        Connection one = scheme.link(first.out(), second.in(), "стык 1");
        Connection two = scheme.link(second.out(), third.in(), "стык 2");

        SchemeGraph<Connection> steam = scheme.flowGraph(SpaceType.STEAM);

        assertFalse(steam.hasCycle());
        assertEquals(List.of(one, two), steam.topologicalOrder());
        assertEquals(2, steam.stronglyConnectedComponents().size(), "каждая связь — свой блок");
    }


}
