package org.kpush88.Thermal_2.turbine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Опорная турбина 10 МВт — геометрия, откалиброванная по циклу {@code v3} ({@code SolveTest3}).
 *
 * <p><b>Зачем она нужна.</b> Цикл задаёт давления отборов руками (3.5 / 2.5 / 0.8 / 0.15 МПа,
 * вакуум 0.004 МПа) и получает энтальпии одним {@code η_oi = 0.85} на всю турбину. Здесь те же
 * границы получены из геометрии: марш ({@link TurbineMarch}) не знает давлений отборов — он знает
 * площади ступеней, и давления получаются следствием расхода. Совпадение с циклом и есть проверка
 * того, что пакет считает ту же турбину, что и цикл.
 *
 * <p><b>Как получены высоты.</b> В каждом отсеке отношение давлений разложено на ступени
 * геометрически (удельный объём растёт в постоянном отношении на ступень), а высота каждой ступени
 * найдена обратным ходом по пропускной способности: биссекция по {@code l} до тех пор, пока
 * {@link StageSolver} при расходе отсека не даст точно целевое давление за ступенью. Поэтому высоты
 * не «круглые»: 0.2 % по высоте сдвигают давление отсека на проценты, и подобрать их на глаз
 * нельзя. Округление — до микрометра, оно уже не меняет давления в четвёртом знаке.
 *
 * <p><b>Что получилось против цикла.</b>
 * <pre>
 *     давления отборов   3.500 / 2.500 / 0.800 / 0.150 / 0.004 МПа   — совпадают;
 *     мощность N_вн      9.94 МВт против 10.15 МВт у цикла           — −2 %;
 *     выхлоп             h = 2143.7 кДж/кг, x = 0.831 против 2148.5 и 0.833.
 * </pre>
 * Остаток мощности — не ошибка калибровки, а разница моделей. Цикл берёт один {@code η_oi = 0.85} и
 * переносит кинетическую энергию пара из отсека в отсек неявно. Марш считает по ступеням: работу
 * каждой ступени уменьшают явные потери (вторичные, утечки, влажностные), а выходная скорость
 * ступени из баланса энергии вычитается и в следующую ступень не возвращается
 * ({@code c₀ = 0} — упрощение, описанное в {@link StageSolver}). На пяти границах отсеков это и
 * даёт около двух процентов. Подгонять геометрию под 10.15 МВт значило бы прятать эту разницу в
 * высотах ступеней — то есть выдавать приближение модели за свойство машины.
 *
 * <p><b>Почему размеры именно такие.</b> Диаметры растут по ходу расширения (0.85 → 1.25 м), чтобы
 * окружная скорость и {@code u/c₁ ≈ 0.45…0.57} оставались в рабочем диапазоне при растущем объёмном
 * расходе. Парциальность падает там, где объёмный расход мал (ε = 0.12 у ЦВД, ε = 1 в ЦНД): при том
 * же горле это даёт более длинную лопатку, а значит меньшие {@code b/l} и {@code δ/l}. Хорда тонкая
 * (18…30 мм), потому что для коротких лопаток толстый профиль — это вторичные потери, съедающие
 * проценты КПД. Угол β₂ = 14…18° выбран по минимуму выходной скорости: с ростом β₂ растёт
 * {@code c₂}, и работа падает быстрее, чем растут потери.
 *
 * <p>Класс — фикстура тестов: он не входит в основной код и не является частью API пакета.
 */
final class ReferenceTurbine {

    /** Свежий пар цикла v3: 14 МПа, 550 °C, 9.4128 кг/с. */
    static final double INLET_PRESSURE = 14.0;
    static final double INLET_TEMPERATURE = 550.0;
    static final double INLET_FLOW = 9.4128;

    /** Расходы отборов цикла v3 по именам потребителей, кг/с. */
    static final double HP2_FLOW = 0.3858;
    static final double HP1_FLOW = 1.1071;
    static final double DEAERATOR_FLOW = 0.5643;
    static final double LP_FLOW = 1.1359;

    /** Давления отборов и вакуум цикла v3, МПа — то, что должно получиться из геометрии. */
    static final double[] SECTION_PRESSURES = {3.5, 2.5, 0.8, 0.15, 0.004};

    private static final Steam STEAM = new Steam();

    /**
     * Отсек опорной машины: размеры, профиль и высоты ступеней.
     *
     * <p>Диаметр и парциальность постоянны внутри отсека — так собирают цилиндры: высота лопатки
     * растёт, а диаметр держится, пока хватает окружной скорости.
     *
     * @param name      имя отсека
     * @param consumer  потребитель отбора на выходе ({@code null} — выхлоп)
     * @param diameter  средний диаметр ступеней отсека, м
     * @param admission парциальность ε
     * @param profile   профиль решёток отсека
     * @param heightsMm высоты лопаток по ступеням, мм (подобраны по пропускной способности)
     */
    private record SectionSpec(String name, String consumer, double diameter, double admission,
                               StageProfile profile, double[] heightsMm) {
    }

    private static final SectionSpec[] SECTIONS = {
            new SectionSpec("ЦВД", "ПВД-2", 0.85, 0.12, profile(0.018, 0.00025, 14.0),
                    new double[]{13.199, 14.975, 16.988, 19.267, 21.850,
                            24.776, 28.090, 31.843, 36.093, 40.906}),
            new SectionSpec("ЦСД-1", "ПВД-1", 0.85, 0.20, profile(0.020, 0.00030, 14.0),
                    new double[]{24.817, 28.893}),
            new SectionSpec("ЦСД-2", "Деаэратор", 1.00, 0.18, profile(0.025, 0.00040, 15.0),
                    new double[]{25.160, 30.915, 37.969, 46.621, 57.228}),
            new SectionSpec("ЦСД-3", "ПНД", 1.10, 0.35, profile(0.030, 0.00050, 16.0),
                    new double[]{27.484, 37.182, 50.161, 67.832, 91.923}),
            new SectionSpec("ЦНД", null, 1.25, 1.00, profile(0.030, 0.00060, 18.0),
                    new double[]{28.516, 54.620, 105.399, 205.027, 401.541}),
    };

    private static TurbineGeometry geometry;
    private static TurbineResult result;

    private ReferenceTurbine() {
    }

    /**
     * Профиль решёток отсека: хорда, радиальный зазор и угол выхода рабочей решётки β₂.
     *
     * <p>Остальные величины — значения {@link StageProfile} по умолчанию (α₁ = 12°, ρ = 0.08,
     * φ = 0.96, ψ = 0.94, μ = 0.96 и коэффициенты потерь): они относятся к типу ступени, а не к
     * отсеку, и в калибровке не участвовали. Хорда и зазор растут от ЦВД к ЦНД вместе с высотой
     * лопатки — это конструктивное требование к жёсткости, а не подгонка.
     */
    static StageProfile profile(double chord, double clearance, double rotorAngle) {
        return StageProfile.builder()
                .chord(chord)
                .clearance(clearance)
                .rotorAngle(rotorAngle)
                .build();
    }

    /** Первая ступень ЦВД — та, по которой настраивалась вся проточная часть. */
    static StageGeometry referenceStage() {
        return geometry().section(0).stages().get(0);
    }

    /** Первая ступень ЦНД — ступень, работающая уже на влажном паре. */
    static StageGeometry wetStage() {
        return geometry().section(4).stages().get(0);
    }

    /** Решатель ступеней на валу опорной машины. */
    static StageSolver stageSolver() {
        return new StageSolver(STEAM, TurbineGeometry.NOMINAL_ROTATION);
    }

    /** Геометрия опорной машины: 5 отсеков, 27 ступеней. */
    static TurbineGeometry geometry() {
        if (geometry == null) {
            TurbineGeometry.Builder turbine =
                    TurbineGeometry.builder(TurbineGeometry.NOMINAL_ROTATION);
            for (SectionSpec spec : SECTIONS) {
                SectionGeometry.Builder section = SectionGeometry.named(spec.name());
                if (spec.consumer() != null) {
                    section.extractionTo(spec.consumer());
                }
                for (double heightMm : spec.heightsMm()) {
                    section.addStage(StageGeometry.of(spec.diameter(), heightMm / 1000.0,
                            spec.admission(), spec.profile()));
                }
                turbine.addSection(section.build());
            }
            geometry = turbine.build();
        }
        return geometry;
    }

    /** Режим цикла v3 на этой геометрии. */
    static TurbineInput input() {
        Map<String, Double> extractions = new LinkedHashMap<>();
        extractions.put("ПВД-2", HP2_FLOW);
        extractions.put("ПВД-1", HP1_FLOW);
        extractions.put("Деаэратор", DEAERATOR_FLOW);
        extractions.put("ПНД", LP_FLOW);
        return TurbineInput.of(INLET_PRESSURE, INLET_TEMPERATURE, INLET_FLOW, extractions);
    }

    /** Марш по опорной машине в режиме цикла v3 (считается один раз на весь набор тестов). */
    static TurbineResult march() {
        if (result == null) {
            result = new TurbineMarch(STEAM, geometry()).solve(input());
        }
        return result;
    }

    /** Рабочее тело, с которым считает опорная машина. */
    static Steam steam() {
        return STEAM;
    }
}
