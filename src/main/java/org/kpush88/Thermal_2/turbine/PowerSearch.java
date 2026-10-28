package org.kpush88.Thermal_2.turbine;

import java.util.Locale;
import java.util.function.DoubleFunction;

/**
 * Поверочный расчёт конденсационной турбины и поиск причины отклонения мощности.
 *
 * <p>Класс связывает то, что раздельно живёт в {@link TurbineMarch}: на старте задаются давление
 * в конденсаторе {@code exhaustPressure} (его держит конденсатор, а не пропускная способность
 * решётки) и электромеханический КПД {@code electromechanicalEfficiency} (потери трения и
 * генератора), который превращает внутреннюю мощность марша в электрическую:
 * <pre>
 *     N_э = N_вн · η_эм.
 * </pre>
 *
 * <p>Вторая половина класса — <b>поиск причины</b> расхождения мощности. Если измеренная мощность
 * ниже проектной, предполагается износ одного параметра геометрии (для первого шага — радиальный
 * зазор δ одной ступени) и бисекцией ищется его значение, при котором расчёт совпадает с
 * измерением. Одна целевая величина (мощность) определяет один параметр; варьировать сразу
 * несколько — вырожденная задача, поэтому каждый поиск меняет ровно один параметр.
 */
public final class PowerSearch {

    private final Steam steam;
    private final TurbineGeometry base;
    private final TurbineInput input;
    private final double exhaustPressure;
    private final double electromechanicalEfficiency;

    /**
     * @param steam                       рабочее тело
     * @param base                        базовая (проектная) геометрия проточной части
     * @param input                       режим: давление, температура и расход свежего пара
     * @param exhaustPressure             давление в конденсаторе, МПа
     * @param electromechanicalEfficiency электромеханический КПД η_эм в (0, 1]
     */
    public PowerSearch(Steam steam, TurbineGeometry base, TurbineInput input,
                       double exhaustPressure, double electromechanicalEfficiency) {
        if (steam == null) {
            throw new IllegalArgumentException("рабочее тело не задано");
        }
        if (base == null) {
            throw new IllegalArgumentException("геометрия турбины не задана");
        }
        if (input == null) {
            throw new IllegalArgumentException("режим турбины не задан");
        }
        if (!(exhaustPressure > 0)) {
            throw new IllegalArgumentException("давление в конденсаторе должно быть "
                    + "положительным: " + exhaustPressure + " МПа");
        }
        if (!(electromechanicalEfficiency > 0 && electromechanicalEfficiency <= 1)) {
            throw new IllegalArgumentException("электромеханический КПД η_эм должен быть в (0, 1]: "
                    + electromechanicalEfficiency);
        }
        this.steam = steam;
        this.base = base;
        this.input = input;
        this.exhaustPressure = exhaustPressure;
        this.electromechanicalEfficiency = electromechanicalEfficiency;
    }

    /** Марш по базовой геометрии. */
    public TurbineResult march() {
        return march(base);
    }

    /** Марш по заданной геометрии (варианту базовой). */
    public TurbineResult march(TurbineGeometry geometry) {
        return new TurbineMarch(steam, geometry).solve(input, exhaustPressure);
    }

    /** Электрическая мощность на базовой геометрии, кВт. */
    public double electricalPower() {
        return electricalPower(base);
    }

    /** Электрическая мощность на заданной геометрии, кВт. */
    public double electricalPower(TurbineGeometry geometry) {
        return march(geometry).internalPower() * electromechanicalEfficiency;
    }

    /** Давление в конденсаторе, МПа. */
    public double exhaustPressure() {
        return exhaustPressure;
    }

    /** Электромеханический КПД η_эм. */
    public double electromechanicalEfficiency() {
        return electromechanicalEfficiency;
    }

    /**
     * Копия базовой геометрии с другим радиальным зазором одной ступени.
     *
     * <p>Все прочие размеры и профили остаются как в базовой: меняется только {@code δ}. Нужна
     * как «что если» — например, чтобы построить изношенную геометрию и сравнить её мощность с
     * проектной.
     */
    public TurbineGeometry withClearance(int sectionIndex, int stageIndex, double clearance) {
        return withStageProfile(sectionIndex, stageIndex,
                stageProfile(sectionIndex, stageIndex).withClearance(clearance));
    }

    /**
     * Ищет радиальный зазор δ указанной ступени, при котором мощность равна целевой.
     *
     * <p>Все прочие параметры геометрии остаются как в базовой. Бисекция на отрезке
     * {@code [lo, hi]}: мощность монотонно падает с ростом зазора (утечки ∝ δ/l), поэтому решение
     * единственно.
     *
     * @param targetPower  целевая электрическая мощность, кВт
     * @param sectionIndex номер отсека (с нуля)
     * @param stageIndex   номер ступени внутри отсека (с нуля)
     * @param lo           нижняя граница зазора, м
     * @param hi           верхняя граница зазора, м
     */
    public SearchResult findClearance(double targetPower, int sectionIndex, int stageIndex,
                                      double lo, double hi) {
        DoubleFunction<TurbineGeometry> variant = clearance ->
                withClearance(sectionIndex, stageIndex, clearance);
        return find(targetPower, lo, hi, variant);
    }

    /**
     * Общий поиск одного скалярного параметра геометрии под целевую мощность.
     *
     * <p>Бисекция по параметру: мощность обязана быть монотонной функцией параметра на отрезке
     * {@code [lo, hi]}, иначе решение не единственно и поиск отказывается.
     *
     * @param targetPower целевая электрическая мощность, кВт
     * @param lo          нижняя граница параметра
     * @param hi          верхняя граница параметра
     * @param variant     как пересобрать геометрию при заданном значении параметра
     */
    public SearchResult find(double targetPower, double lo, double hi,
                             DoubleFunction<TurbineGeometry> variant) {
        if (!(lo < hi)) {
            throw new IllegalArgumentException("интервал поиска должен быть lo < hi: "
                    + lo + " … " + hi);
        }
        if (!(targetPower > 0)) {
            throw new IllegalArgumentException("целевая мощность должна быть положительной: "
                    + targetPower + " кВт");
        }
        double powerLo = electricalPower(variant.apply(lo));
        double powerHi = electricalPower(variant.apply(hi));
        if (powerLo == powerHi) {
            throw new IllegalStateException("мощность не зависит от параметра на интервале: "
                    + powerLo + " кВт и при " + lo + ", и при " + hi);
        }
        boolean increasing = powerHi > powerLo;
        double minPower = Math.min(powerLo, powerHi);
        double maxPower = Math.max(powerLo, powerHi);
        if (!(targetPower >= minPower && targetPower <= maxPower)) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "целевая мощность %.4f кВт вне диапазона [%.4f, %.4f] кВт, который даёт "
                            + "параметр на интервале [%g, %g]",
                    targetPower, minPower, maxPower, lo, hi));
        }

        double low = lo;
        double high = hi;
        for (int step = 0; step < 100; step++) {
            double middle = 0.5 * (low + high);
            double power = electricalPower(variant.apply(middle));
            boolean takeLowerHalf = increasing ? power < targetPower : power > targetPower;
            if (takeLowerHalf) {
                low = middle;
            } else {
                high = middle;
            }
        }
        double parameter = 0.5 * (low + high);
        TurbineGeometry geometry = variant.apply(parameter);
        TurbineResult result = march(geometry);
        double achieved = result.internalPower() * electromechanicalEfficiency;
        return new SearchResult(parameter, geometry, result, achieved, achieved - targetPower);
    }

    private StageProfile stageProfile(int sectionIndex, int stageIndex) {
        return base.section(sectionIndex).stages().get(stageIndex).profile();
    }

    private TurbineGeometry withStageProfile(int sectionIndex, int stageIndex,
                                             StageProfile profile) {
        TurbineGeometry.Builder turbine = TurbineGeometry.builder(base.rotation());
        for (int s = 0; s < base.sectionCount(); s++) {
            SectionGeometry section = base.section(s);
            SectionGeometry.Builder builder = SectionGeometry.named(section.name());
            if (section.hasExtraction()) {
                builder.extractionTo(section.extraction());
            }
            for (int i = 0; i < section.stageCount(); i++) {
                StageGeometry stage = section.stages().get(i);
                StageProfile stageProfile = (s == sectionIndex && i == stageIndex)
                        ? profile : stage.profile();
                builder.addStage(StageGeometry.of(stage.meanDiameter(), stage.height(),
                        stage.admission(), stageProfile));
            }
            turbine.addSection(builder.build());
        }
        return turbine.build();
    }

    /**
     * Результат поиска: найденное значение параметра, геометрия, марш и достигнутая мощность.
     *
     * @param parameter     найденное значение параметра (например, δ, м)
     * @param geometry      геометрия, при которой мощность достигнута
     * @param result        марш по этой геометрии
     * @param achievedPower достигнутая электрическая мощность, кВт
     * @param residual      невязка по мощности {@code achievedPower − targetPower}, кВт
     */
    public record SearchResult(double parameter, TurbineGeometry geometry, TurbineResult result,
                               double achievedPower, double residual) {
    }
}

