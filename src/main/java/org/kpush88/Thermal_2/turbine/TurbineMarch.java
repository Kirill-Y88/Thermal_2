package org.kpush88.Thermal_2.turbine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Марш проточной части: ступень за ступенью, отсек за отсеком.
 *
 * <p>Единственный вход — режим ({@link TurbineInput}) и геометрия; единственный выход —
 * {@link TurbineResult}. Давления отборов и выхлопа <b>вычисляются</b>: каждая ступень берёт
 * столько перепада, сколько нужно, чтобы через её горло прошёл заданный расход. Это и есть
 * поверочный расчёт: в реальной машине давления отборов не задаются — они получаются сами,
 * потому что расход один на всю проточную часть, а площади ступеней разные.
 *
 * <p><b>Что означают расхождения с циклом.</b> Цикл ({@code v3}) задаёт давления отборов руками
 * и получает энтальпии через один η_oi на всю турбину. Если марш по этой геометрии даёт другие
 * давления, то одно из двух: либо геометрия не соответствует режиму цикла, либо режим не
 * соответствует геометрии. Модель не «подгоняет» давления под цикл — она показывает расхождение,
 * и это её основная ценность при диагностике: именно так ищут ступень, которая перегружена или,
 * наоборот, заперта.
 *
 * <p><b>Где марш останавливается.</b> Ошибка выдаётся в трёх случаях, и каждый означает
 * конкретную поломку модели, а не «неудобный режим»: ступень не может пропустить расход (её
 * горло меньше), давление за ступенью не падает (треугольники не сходятся с перепадом), пар
 * кончился на отборе (сумма отборов больше расхода). Молча продолжать марш в этих случаях
 * нельзя: следующие числа были бы уже бессмысленными.
 */
public final class TurbineMarch {

    private final Steam steam;
    private final TurbineGeometry geometry;
    private final StageSolver solver;

    /** Марш по геометрии турбины: частота вращения берётся из неё же. */
    public TurbineMarch(Steam steam, TurbineGeometry geometry) {
        if (steam == null) {
            throw new IllegalArgumentException("рабочее тело не задано");
        }
        if (geometry == null) {
            throw new IllegalArgumentException("геометрия проточной части не задана");
        }
        this.steam = steam;
        this.geometry = geometry;
        this.solver = new StageSolver(steam, geometry);
    }

    /** Рабочее тело. */
    public Steam steam() {
        return steam;
    }

    /** Геометрия, по которой идёт марш. */
    public TurbineGeometry geometry() {
        return geometry;
    }

    /** Решатель ступеней (для расчёта одиночной ступени вне марша). */
    public StageSolver solver() {
        return solver;
    }

    /**
     * Проходит проточную часть при заданном режиме.
     *
     * @param input давление, температура и расход свежего пара, расходы отборов
     * @return состояния по ступеням, отсеки и мощность
     */
    /** Марш без закреплённого давления на выхлопе: выхлоп получается из геометрии и расхода. */
    public TurbineResult solve(TurbineInput input) {
        return solve(input, null);
    }

    /**
     * Марш конденсационной турбины с давлением в конденсаторе, закреплённым на входе.
     *
     * <p>Последняя ступень последнего отсека расширяется ровно до {@code exhaustPressure}:
     * давление за ней держит конденсатор, а не пропускная способность решётки. Все остальные
     * ступени считаются как раньше — по пропускной способности. Если последняя ступень не
     * пропускает расход при таком перепаде (её горло мало), марш падает — это и есть
     * диагностический признак перегрузки или засорения последней ступени.
     *
     * @param input           режим: давление, температура и расход свежего пара, отборы
     * @param exhaustPressure давление в конденсаторе, МПа
     * @return состояния по ступеням, отсеки и мощность
     */
    public TurbineResult solve(TurbineInput input, double exhaustPressure) {
        if (!(exhaustPressure > 0)) {
            throw new IllegalArgumentException("давление в конденсаторе должно быть "
                    + "положительным: " + exhaustPressure + " МПа");
        }
        return solve(input, Double.valueOf(exhaustPressure));
    }

    private TurbineResult solve(TurbineInput input, Double exhaustPressure) {
        requireKnownExtractions(input);

        double inletEnthalpy = steam.enthalpyPT(input.inletPressure(),
                input.inletTemperature());
        double pressure = input.inletPressure();
        double enthalpy = inletEnthalpy;
        double flow = input.inletFlow();

        List<TurbineResult.StagePoint> points = new ArrayList<>();
        List<TurbineResult.SectionResult> sections = new ArrayList<>();
        double internalPower = 0.0;
        double availableEnergy = 0.0;

        List<SectionGeometry> sectionList = geometry.sections();
        for (int s = 0; s < sectionList.size(); s++) {
            SectionGeometry section = sectionList.get(s);
            double sectionInletPressure = pressure;
            double sectionInletEnthalpy = enthalpy;
            double sectionWork = 0.0;
            int number = 0;
            List<StageGeometry> stageList = section.stages();
            for (int i = 0; i < stageList.size(); i++) {
                StageGeometry stage = stageList.get(i);
                boolean pinnedExhaust = exhaustPressure != null
                        && s == sectionList.size() - 1
                        && i == stageList.size() - 1;
                StageResult result = pinnedExhaust
                        ? solver.solveToPressure(stage, pressure, enthalpy, flow, exhaustPressure)
                        : solver.solve(stage, pressure, enthalpy, flow);
                points.add(new TurbineResult.StagePoint(section.name(), ++number, result));
                sectionWork += result.internalWork();
                pressure = result.outletPressure();
                enthalpy = result.outletEnthalpy();
            }

            String consumer = section.extraction();
            double extracted = consumer == null ? 0.0 : input.extraction(consumer);
            double availableDrop = solver.availableDrop(sectionInletPressure,
                    sectionInletEnthalpy, pressure);
            double power = sectionWork * flow;
            sections.add(new TurbineResult.SectionResult(section.name(), consumer, extracted,
                    flow, sectionInletPressure, sectionInletEnthalpy, pressure, enthalpy,
                    steam.vapourFractionPH(pressure, enthalpy), availableDrop, sectionWork,
                    sectionWork / availableDrop, power));
            internalPower += power;
            availableEnergy += availableDrop * flow;

            if (extracted > 0) {
                flow -= extracted;
                if (flow <= 0) {
                    throw new IllegalStateException("отбор «" + consumer + "» забирает "
                            + extracted + " кг/с — это весь оставшийся пар: расход перед отсеком "
                            + "«" + section.name() + "» был " + (flow + extracted) + " кг/с");
                }
            }
        }

        return new TurbineResult(points, sections, inletEnthalpy, pressure, enthalpy,
                steam.vapourFractionPH(pressure, enthalpy), internalPower,
                availableEnergy > 0 ? internalPower / availableEnergy : 0.0);
    }

    /**
     * Проверяет, что в режиме названы только те отборы, которые есть в геометрии.
     *
     * <p>Иначе опечатка в имени отбора («ПВД1» вместо «ПВД-1») молча означала бы отключённый
     * отбор: карта не нашла пары, расход нулевой, и турбина посчиталась бы как конденсационная.
     * Такую ошибку видно только по расхождению мощностей, поэтому она называется сразу.
     */
    private void requireKnownExtractions(TurbineInput input) {
        Set<String> known = new LinkedHashSet<>();
        for (SectionGeometry section : geometry.sections()) {
            if (section.extraction() != null) {
                known.add(section.extraction());
            }
        }
        for (String consumer : input.extractionFlows().keySet()) {
            if (!known.contains(consumer)) {
                throw new IllegalArgumentException("в геометрии нет отбора «" + consumer
                        + "»; известны: " + (known.isEmpty() ? "ни одного"
                        : String.join(", ", known)));
            }
        }
    }
}
