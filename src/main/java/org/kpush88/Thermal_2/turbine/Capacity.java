package org.kpush88.Thermal_2.turbine;

/**
 * Пропускная способность решётки: сколько пара пройдёт через горло при заданных параметрах
 * перед ней и давлении за ней.
 *
 * <p>Уравнение — Сен-Венана и Ванцеля для сжимаемого течения через суживающееся сопло:
 * <pre>
 *     G = μ·F·√( 2·(p₀/v₀)·(k/(k−1))·(β^{2/k} − β^{(k+1)/k}) ),   β = p₂/p₀,
 * </pre>
 * где {@code p₀} — Па, {@code v₀} — м³/кг, {@code F} — площадь горла, м², {@code μ} — коэффициент
 * расхода. При {@code β ≤ β_кр} течение запирается и расход перестаёт зависеть от давления за
 * решёткой:
 * <pre>
 *     β_кр = (2/(k+1))^{k/(k−1)},   G_кр = μ·F·√( 2·(p₀/v₀)·k·(2/(k+1))^{(k+1)/(k−1)} ).
 * </pre>
 *
 * <p><b>Почему объект, а не набор статических функций.</b> В марше ступени функция расхода
 * вызывается десятки раз (бисекция при обращении), а из IF97 для неё нужны только два числа —
 * показатель изоэнтропы и удельный объём, оба на входе в решётку и от {@code p₂} не зависящие.
 * Здесь они считаются один раз в {@link #of}, а дальше идут чистые арифметические операции.
 *
 * <p><b>Что считается входом, а что выходом.</b> Класс умеет читать режим в обе стороны, и это
 * не «два алгоритма», а одна монотонная функция и её обращение:
 * <ul>
 *   <li>{@link #flow(double)} — задано давление за решёткой, получается расход (прогноз);</li>
 *   <li>{@link #pressure(double)} — задан расход, получается давление; в марше это давление за
 *       ступенью, в поверке — давление, которое нужно, чтобы пропустить измеренный расход.</li>
 * </ul>
 * Монотонность гарантирована физикой: с ростом {@code p₂} расход падает, поэтому обращение —
 * бисекция, а не «подбор с невязкой».
 *
 * <p><b>Запирание.</b> Если расход больше критического, давления за решёткой, которое его
 * пропустит, не существует: {@link #pressure} возвращает критическое давление и не притворяется,
 * что решение есть. Проверить это заранее можно по {@link #choked(double)} и {@link #maxFlow()}.
 *
 * <p>Класс неизменяемый: все величины посчитаны в {@link #of}.
 */
public final class Capacity {

    /** Давление на входе, МПа. */
    private final double inletPressure;

    /** Энтальпия на входе, кДж/кг. */
    private final double inletEnthalpy;

    /** Площадь горла, м². */
    private final double throatArea;

    /** Коэффициент расхода μ. */
    private final double discharge;

    /** Локальный показатель изоэнтропы k = a²/(p·v). */
    private final double exponent;

    /** Удельный объём на входе, м³/кг. */
    private final double inletVolume;

    /** Критическое отношение давлений β_кр. */
    private final double criticalRatio;

    /** Критический расход, кг/с. */
    private final double criticalFlow;

    private Capacity(double inletPressure, double inletEnthalpy, double throatArea, double discharge,
                     double exponent, double inletVolume, double criticalRatio, double criticalFlow) {
        this.inletPressure = inletPressure;
        this.inletEnthalpy = inletEnthalpy;
        this.throatArea = throatArea;
        this.discharge = discharge;
        this.exponent = exponent;
        this.inletVolume = inletVolume;
        this.criticalRatio = criticalRatio;
        this.criticalFlow = criticalFlow;
    }

    /**
     * Строит характеристику решётки по состоянию перед ней и площади горла.
     *
     * @param steam         свойства рабочего тела (два вызова IF97: объём и скорость звука)
     * @param inletPressure давление перед решёткой, МПа
     * @param inletEnthalpy энтальпия перед решёткой, кДж/кг
     * @param throatArea    площадь горла, м²
     * @param discharge     коэффициент расхода μ в (0, 1]
     * @return характеристика, готовая считать расход и обратное давление
     */
    public static Capacity of(Steam steam, double inletPressure, double inletEnthalpy,
                              double throatArea, double discharge) {
        requirePositive(inletPressure, "давление перед решёткой, МПа");
        requirePositive(throatArea, "площадь горла, м²");
        if (!(discharge > 0 && discharge <= 1)) {
            throw new IllegalArgumentException("коэффициент расхода μ должен быть в (0, 1]: "
                    + discharge);
        }
        double exponent = steam.isentropicExponentPH(inletPressure, inletEnthalpy);
        if (!(exponent > 1)) {
            throw new IllegalStateException("показатель изоэнтропы k = " + exponent
                    + " при p = " + inletPressure + " МПа, h = " + inletEnthalpy
                    + " кДж/кг: состояние не описывает сжимаемое течение");
        }
        double volume = steam.volumePH(inletPressure, inletEnthalpy);
        double ratio = criticalRatio(exponent);
        double unitCritical = criticalUnitFlow(exponent, inletPressure, volume);
        return new Capacity(inletPressure, inletEnthalpy, throatArea, discharge, exponent, volume,
                ratio, discharge * throatArea * unitCritical);
    }

    /** Критическое отношение давлений β_кр для показателя изоэнтропы {@code k}. */
    public static double criticalRatio(double exponent) {
        return Math.pow(2.0 / (exponent + 1.0), exponent / (exponent - 1.0));
    }

    /**
     * Критический удельный расход {@code G_кр/(μ·F)} при данных параметрах, кг/(с·м²).
     *
     * <p>Зависит только от состояния, поэтому по нему видно, во сколько раз различаются
     * пропускные способности отсеков. На входе в турбину (14 МПа, 3461 кДж/кг) это
     * 15 777 кг/(с·м²), а в последней ступени (0.004 МПа, 2148 кДж/кг) — 10.35 кг/(с·м²):
     * одинаковая площадь пропускает в полторы тысячи раз меньше пара.
     *
     * <p>Формула — предел общего уравнения при {@code β = β_кр}:
     * <pre>
     *     G_кр/(μ·F) = √( (p₀/v₀)·k·(2/(k+1))^{(k+1)/(k−1)} ).
     * </pre>
     * Множителя 2 перед скобкой здесь нет: он сокращается с {@code (k−1)/(k+1)} из
     * {@code 1 − β_кр^{(k−1)/k}}. Именно на этом месте легко ошибиться в √2, и тогда расход
     * скачком расходится с дозвуковой ветвью ровно на границе запирания — поэтому в
     * {@code CapacityTest} есть отдельная проверка непрерывности.
     */
    public static double criticalUnitFlow(Steam steam, double pressureMPa, double enthalpy) {
        double exponent = steam.isentropicExponentPH(pressureMPa, enthalpy);
        double volume = steam.volumePH(pressureMPa, enthalpy);
        return criticalUnitFlow(exponent, pressureMPa, volume);
    }

    private static double criticalUnitFlow(double exponent, double pressureMPa, double volume) {
        return Math.sqrt((pressureMPa * Steam.MPA / volume) * exponent
                * Math.pow(2.0 / (exponent + 1.0), (exponent + 1.0) / (exponent - 1.0)));
    }

    /**
     * Расход через решётку при заданном давлении за ней, кг/с.
     *
     * @param outletPressure давление за решёткой, МПа; должно быть ниже давления перед ней
     * @return расход, кг/с; при {@code β ≤ β_кр} — критический, от {@code outletPressure} не
     *         зависящий
     */
    public double flow(double outletPressure) {
        if (!(outletPressure < inletPressure)) {
            throw new IllegalArgumentException("давление за решёткой (" + outletPressure
                    + " МПа) должно быть ниже давления перед ней (" + inletPressure + " МПа)");
        }
        double beta = outletPressure / inletPressure;
        if (beta <= criticalRatio) {
            return criticalFlow;
        }
        double term = Math.pow(beta, 2.0 / exponent) - Math.pow(beta, (exponent + 1.0) / exponent);
        return discharge * throatArea
                * Math.sqrt(2.0 * (inletPressure * Steam.MPA / inletVolume)
                * (exponent / (exponent - 1.0)) * term);
    }

    /**
     * Давление за решёткой, при котором она пропустит заданный расход, МПа.
     *
     * <p>Обращение {@link #flow(double)} бисекцией: функция строго монотонна, поэтому отрезок
     * делится пополам до относительной точности {@code 1e-12} по давлению (около 40 делений).
     * Если расход не меньше критического, решения нет — возвращается критическое давление
     * {@code β_кр·p₀}; вызывающая сторона может отличить этот случай по {@link #choked(double)}.
     *
     * @param flow расход, кг/с; должен быть положительным
     * @return давление за решёткой, МПа
     */
    public double pressure(double flow) {
        requirePositive(flow, "расход через решётку, кг/с");
        if (flow >= criticalFlow) {
            return criticalRatio * inletPressure;
        }
        double low = criticalRatio * inletPressure;
        double high = inletPressure * (1.0 - 1.0e-9);
        if (flow(high) >= flow) {
            return high;
        }
        for (int step = 0; step < 200; step++) {
            double middle = 0.5 * (low + high);
            if (flow(middle) > flow) {
                low = middle;
            } else {
                high = middle;
            }
            if (high - low <= inletPressure * 1.0e-12) {
                break;
            }
        }
        return 0.5 * (low + high);
    }

    /** Запирается ли решётка при давлении за ней {@code outletPressure}: {@code β ≤ β_кр}. */
    public boolean choked(double outletPressure) {
        return outletPressure <= criticalRatio * inletPressure;
    }

    /** Максимальный (критический) расход через решётку, кг/с. */
    public double maxFlow() {
        return criticalFlow;
    }

    /** Критическое давление за решёткой {@code β_кр·p₀}, МПа. */
    public double criticalPressure() {
        return criticalRatio * inletPressure;
    }

    /** Давление перед решёткой, МПа. */
    public double inletPressure() {
        return inletPressure;
    }

    /** Энтальпия перед решёткой, кДж/кг. */
    public double inletEnthalpy() {
        return inletEnthalpy;
    }

    /** Площадь горла, м². */
    public double throatArea() {
        return throatArea;
    }

    /** Коэффициент расхода μ. */
    public double dischargeCoefficient() {
        return discharge;
    }

    /** Локальный показатель изоэнтропы k. */
    public double exponent() {
        return exponent;
    }

    /** Удельный объём перед решёткой, м³/кг. */
    public double inletVolume() {
        return inletVolume;
    }

    /** Критическое отношение давлений β_кр. */
    public double criticalRatio() {
        return criticalRatio;
    }

    /** Строка для протокола: состояние входа, площадь и предел по расходу. */
    public String describe() {
        return String.format(java.util.Locale.ROOT,
                "p₀=%.4f МПа, h₀=%.1f кДж/кг, v₀=%.6f м³/кг, k=%.4f, β_кр=%.4f, "
                        + "F=%.4e м², μ=%.3f, G_кр=%.4f кг/с",
                inletPressure, inletEnthalpy, inletVolume, exponent, criticalRatio,
                throatArea, discharge, criticalFlow);
    }

    private static void requirePositive(double value, String what) {
        if (!(value > 0)) {
            throw new IllegalArgumentException(what + " должно быть положительным: " + value);
        }
    }
}
