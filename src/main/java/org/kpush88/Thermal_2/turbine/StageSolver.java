package org.kpush88.Thermal_2.turbine;

import java.util.Locale;

/**
 * Расчёт одной ступени: по расходу находит давление за ступенью, по треугольникам скоростей —
 * работу и энтальпию за ступенью.
 *
 * <p><b>Порядок.</b>
 * <ol>
 *   <li>Пропускная способность сопловой решётки при известном расходе даёт давление за
 *       ступенью {@code p₂} ({@link Capacity}). Обратный ход — «сколько пара пройдёт при таком
 *       перепаде» — это поверочный расчёт ступени, и именно он нужен маршу: расход задан
 *       режимом, давление получается следствием.</li>
 *   <li>Располагаемый перепад {@code H = h₁ − h(p₂, s₁)} — изоэнтропный, от состояния перед
 *       ступенью до давления за ней.</li>
 *   <li>Перепад делится по степени реакции: {@code (1−ρ)·H} снимается в сопловой решётке,
 *       {@code ρ·H} — в рабочей.</li>
 *   <li>Скорости: {@code c₁ = φ·√(2(1−ρ)H)}, далее треугольник на входе в колесо
 *       ({@code w₁ = √((c₁u − u)² + c₁a²)}), затем {@code w₂ = ψ·√(2ρH + w₁²)} и треугольник на
 *       выходе при конструктивном угле β₂.</li>
 *   <li>Работа на окружности по уравнению Эйлера {@code W = u·(c₁u − c₂u)}, где окружные
 *       составляющие берутся в направлении вращения. Скорость выхода {@code c₂} направлена
 *       против вращения (рабочая решётка разворачивает поток), поэтому {@code c₂u = u − w₂u}.</li>
 *   <li>Из работы вычитаются эмпирические потери: вторичные, утечки, влажностные
 *       ({@link StageProfile}). Остаток — внутренняя работа ступени.</li>
 *   <li>Энтальпия за ступенью — из баланса энергии {@code h₂ = h₁ − W − c₂²/2}; по ней и
 *       давлению находится степень сухости, а по ней — влажностные потери. Степень сухости
 *       замыкается итерацией: три прохода фиксированной точки, потому что коэффициент потерь
 *       зависит от среднего по ступени {@code x̄}.</li>
 * </ol>
 *
 * <p><b>Что здесь приближено (и почему это осознанно).</b>
 * <ul>
 *   <li><b>Скорость перед ступенью не учитывается</b> ({@code c₀ = 0}). Выходная скорость
 *       предыдущей ступени уже вычтена из её энтальпии, а здесь не подхватывается: учёт и того,
 *       и другого дал бы компенсацию в пределах {@code c₂²/2} (единицы кДж/кг при перепаде
 *       ступени 30…50). Ошибка систематическая и малая; она уходит в калибровку η отсека.</li>
 *   <li><b>Пропускная способность считается по давлению за ступенью</b>, хотя сопловая решётка
 *       видит давление за собой, то есть выше на {@code ρ·Δp}. При ρ ≤ 0.15 разница меньше
 *       процента от перепада и с избытком покрывается коэффициентом расхода μ.</li>
 *   <li><b>Потери на входе в решётки</b> (кромочные, веерность, парциальные) отдельно не
 *       вводятся: они уже сидят в φ и ψ, которые для коротких лопаток берутся ниже.</li>
 * </ul>
 *
 * <p>Если треугольники не сходятся с перепадом (работа вышла больше располагаемого тепла или
 * отрицательной), расчёт падает с указанием величин: это признак несогласованной геометрии
 * (например, β₂ задан такой, что поток выходит почти по окружности и «докручивает» колесо), а
 * не «неудобного режима».
 */
public final class StageSolver {

    /** Джоулей в килоджоуле: перепады считаются в кДж/кг, скорости — в м/с. */
    private static final double KJ = 1000.0;

    /** Число проходов итерации по степени сухости (замыкание влажностных потерь). */
    private static final int WETNESS_PASSES = 3;

    private final Steam steam;
    private final double rotation;

    /** Расчёт ступеней турбины с частотой вращения из её геометрии. */
    public StageSolver(Steam steam, TurbineGeometry geometry) {
        this(steam, geometry.rotation());
    }

    /**
     * @param steam    рабочее тело
     * @param rotation частота вращения вала, об/мин
     */
    public StageSolver(Steam steam, double rotation) {
        if (steam == null) {
            throw new IllegalArgumentException("рабочее тело не задано");
        }
        if (!(rotation > 0)) {
            throw new IllegalArgumentException("частота вращения должна быть положительной: "
                    + rotation + " об/мин");
        }
        this.steam = steam;
        this.rotation = rotation;
    }

    /** Частота вращения вала, об/мин. */
    public double rotation() {
        return rotation;
    }

    /**
     * Считает ступень при заданном расходе.
     *
     * <p>Давление за ступенью <b>вычисляется</b>: это поверочный ход, нужный маршу, где расход
     * задан режимом, а перепад получается следствием пропускной способности решётки.
     *
     * @param stage           геометрия и профиль ступени
     * @param inletPressure   давление перед ступенью, МПа
     * @param inletEnthalpy   энтальпия перед ступенью, кДж/кг
     * @param flow            расход через ступень, кг/с
     * @return состояние за ступенью, работа, КПД и доли потерь
     * @throws IllegalStateException если ступень не может пропустить такой расход
     */
    public StageResult solve(StageGeometry stage, double inletPressure, double inletEnthalpy,
                             double flow) {
        requireStageAndFlow(stage, flow);
        Capacity capacity = Capacity.of(steam, inletPressure, inletEnthalpy, stage.throatArea(),
                stage.profile().discharge());
        if (flow > capacity.maxFlow()) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "ступень D=%.3f м, l=%.1f мм не пропустит %.4f кг/с: критический расход "
                            + "через её горло F=%.3e м² равен %.4f кг/с",
                    stage.meanDiameter(), stage.height() * 1000.0, flow, stage.throatArea(),
                    capacity.maxFlow()));
        }
        return solveGiven(stage, inletPressure, inletEnthalpy, capacity.pressure(flow),
                capacity);
    }

    /**
     * Считает ступень при заданном расходе и <b>заданном</b> давлении за ней.
     *
     * <p>Это режим последней ступени конденсационной турбины: давление за ней держит не
     * пропускная способность решётки, а конденсатор. Перепад задан извне, а решётка только
     * проверяется на то, что она в состоянии пропустить расход при таком перепаде: если горло
     * мало, ступень запирается и расчёт отказывается — так же, как в {@link #solve} при расходе
     * выше критического.
     *
     * @param stage           геометрия и профиль ступени
     * @param inletPressure   давление перед ступенью, МПа
     * @param inletEnthalpy   энтальпия перед ступенью, кДж/кг
     * @param flow            расход через ступень, кг/с
     * @param outletPressure  давление за ступенью, МПа (давление в конденсаторе)
     * @return состояние за ступенью, работа, КПД и доли потерь
     * @throws IllegalStateException если ступень не может пропустить расход при заданном перепаде
     */
    public StageResult solveToPressure(StageGeometry stage, double inletPressure,
                                       double inletEnthalpy, double flow, double outletPressure) {
        requireStageAndFlow(stage, flow);
        Capacity capacity = Capacity.of(steam, inletPressure, inletEnthalpy, stage.throatArea(),
                stage.profile().discharge());
        double passable = capacity.flow(outletPressure);
        if (flow > passable) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "ступень D=%.3f м, l=%.1f мм не пропустит %.4f кг/с при давлении за ней "
                            + "%.4f МПа: её горло F=%.3e м² при таком перепаде пропускает %.4f кг/с",
                    stage.meanDiameter(), stage.height() * 1000.0, flow, outletPressure,
                    stage.throatArea(), passable));
        }
        return solveGiven(stage, inletPressure, inletEnthalpy, outletPressure, capacity);
    }

    /**
     * Общая часть расчёта ступени: треугольники скоростей, потери и итерация по влажности.
     *
     * <p>Давление за ступенью уже известно — либо из обращения пропускной способности
     * ({@link #solve}), либо задано извне ({@link #solveToPressure}). От него зависит только
     * располагаемый перепад; всё остальное от способа задания не зависит.
     */
    private StageResult solveGiven(StageGeometry stage, double inletPressure,
                                   double inletEnthalpy, double outletPressure,
                                   Capacity capacity) {
        double isentropicDrop = availableDrop(inletPressure, inletEnthalpy, outletPressure);
        StageProfile profile = stage.profile();

        double circumferentialSpeed = stage.circumferentialSpeed(rotation);
        double nozzleDrop = (1.0 - profile.reaction()) * isentropicDrop;
        double velocity = profile.nozzleVelocity() * Math.sqrt(2.0 * nozzleDrop * KJ);
        double alpha = Math.toRadians(profile.nozzleAngle());
        double c1u = velocity * Math.cos(alpha);
        double c1a = velocity * Math.sin(alpha);

        double w1 = Math.hypot(c1u - circumferentialSpeed, c1a);
        double rotorDrop = profile.reaction() * isentropicDrop;
        double w2 = profile.rotorVelocity() * Math.sqrt(2.0 * rotorDrop * KJ + w1 * w1);
        double beta = Math.toRadians(profile.rotorAngle());
        double w2u = w2 * Math.cos(beta);
        double c2u = circumferentialSpeed - w2u;
        double exitVelocity = Math.hypot(c2u, w2 * Math.sin(beta));
        double eulerWork = circumferentialSpeed * (c1u - c2u) / KJ;

        double secondaryLoss = profile.secondaryLoss() * stage.aspectRatio() * isentropicDrop;
        double leakageLoss = profile.leakageLoss() * stage.clearanceRatio() * isentropicDrop;

        double inletQuality = steam.vapourFractionPH(inletPressure, inletEnthalpy);
        double outletQuality = inletQuality;
        double wetnessLoss = 0.0;
        double internalWork = 0.0;
        double outletEnthalpy = inletEnthalpy;
        for (int pass = 0; pass < WETNESS_PASSES; pass++) {
            wetnessLoss = profile.wetnessLoss() * (1.0 - 0.5 * (inletQuality + outletQuality))
                    * isentropicDrop;
            internalWork = eulerWork - secondaryLoss - leakageLoss - wetnessLoss;
            outletEnthalpy = inletEnthalpy - internalWork
                    - exitVelocity * exitVelocity / (2.0 * KJ);
            outletQuality = steam.vapourFractionPH(outletPressure, outletEnthalpy);
        }

        if (!(internalWork > 0) || internalWork > isentropicDrop) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "ступень D=%.3f м, l=%.1f мм, α₁=%.1f°, β₂=%.1f°: работа %.3f кДж/кг при "
                            + "располагаемом перепаде %.3f кДж/кг (u = %.1f м/с, c₁ = %.1f м/с, "
                            + "u/c₁ = %.2f, потери %.3f кДж/кг) — треугольники скоростей не "
                            + "сходятся с перепадом",
                    stage.meanDiameter(), stage.height() * 1000.0, profile.nozzleAngle(),
                    profile.rotorAngle(), internalWork, isentropicDrop, circumferentialSpeed,
                    velocity, circumferentialSpeed / velocity,
                    secondaryLoss + leakageLoss + wetnessLoss));
        }

        return new StageResult(inletPressure, inletEnthalpy, outletPressure, outletEnthalpy,
                outletQuality, isentropicDrop, internalWork, internalWork / isentropicDrop,
                velocity, exitVelocity, circumferentialSpeed, circumferentialSpeed / velocity,
                secondaryLoss, leakageLoss, wetnessLoss, capacity.choked(outletPressure));
    }

    private static void requireStageAndFlow(StageGeometry stage, double flow) {
        if (stage == null) {
            throw new IllegalArgumentException("ступень не задана");
        }
        if (!(flow > 0)) {
            throw new IllegalArgumentException("расход через ступень должен быть положительным: "
                    + flow + " кг/с");
        }
    }


    /**
     * Располагаемый перепад ступени {@code h₁ − h(p₂, s₁)}, кДж/кг.
     *
     * <p>Изоэнтропа от состояния перед ступенью, поэтому перепад считается и тогда, когда
     * давление за ступенью задано извне (например, последняя ступень перед конденсатором, где
     * давление держит вакуум, а не пропускная способность решётки).
     */
    public double availableDrop(double inletPressure, double inletEnthalpy, double outletPressure) {
        if (!(outletPressure < inletPressure)) {
            throw new IllegalArgumentException("давление за ступенью " + outletPressure
                    + " МПа не ниже, чем перед ней " + inletPressure + " МПа");
        }
        double entropy = steam.entropyPH(inletPressure, inletEnthalpy);
        return inletEnthalpy - steam.enthalpyPS(outletPressure, entropy);
    }

    /** Рабочее тело, с которым считает этот решатель. */
    public Steam steam() {
        return steam;
    }
}
