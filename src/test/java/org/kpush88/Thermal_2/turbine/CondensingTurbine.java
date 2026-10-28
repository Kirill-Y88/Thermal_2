package org.kpush88.Thermal_2.turbine;

/**
 * Опорная конденсационная турбина для проверки поверочного расчёта и поиска причины падения
 * мощности.
 *
 * <p>Геометрия собирается поступенчато: каждая ступень получает горло, пропускающее заданный
 * расход при «своей» доле общего отношения давлений. Доли распределены геометрически от свежего
 * пара до давления в конденсаторе, поэтому давления по ступеням падают ровным отношением
 * {@code (p_к/p₀)^(1/N)} и ни одна ступень не запирается. Высота лопатки получается из площади
 * горла; на первых ступенях объёмный расход мал, поэтому там включена парциальность, а на
 * последних — сплошной подвод.
 */
final class CondensingTurbine {

    /** Свежий пар. */
    static final double INLET_PRESSURE = 2.0;
    static final double INLET_TEMPERATURE = 350.0;
    static final double INLET_FLOW = 2.0;

    /** Граница, которую держит конденсатор. */
    static final double EXHAUST_PRESSURE = 0.02;

    /** Электромеханический КПД турбоагрегата. */
    static final double ELECTROMECHANICAL = 0.98;

    /** Число ступеней. */
    static final int STAGES = 10;

    private static final Steam STEAM = new Steam();

    private static TurbineGeometry geometry;

    private CondensingTurbine() {
    }

    /** Рабочее тело. */
    static Steam steam() {
        return STEAM;
    }

    /** Режим конденсационной турбины без отборов. */
    static TurbineInput input() {
        return TurbineInput.condensing(INLET_PRESSURE, INLET_TEMPERATURE, INLET_FLOW);
    }

    /** Геометрия опорной конденсационной машины (собирается один раз). */
    static TurbineGeometry geometry() {
        if (geometry == null) {
            geometry = buildGeometry();
        }
        return geometry;
    }

    /** Номинальный радиальный зазор ступени по номеру. */
    static double nominalClearance(int stageIndex) {
        return geometry().section(0).stages().get(stageIndex).profile().clearance();
    }

    private static TurbineGeometry buildGeometry() {
        StageSolver solver = new StageSolver(STEAM, TurbineGeometry.NOMINAL_ROTATION);
        SectionGeometry.Builder section = SectionGeometry.named("ЦВД");

        double pressure = INLET_PRESSURE;
        double enthalpy = STEAM.enthalpyPT(INLET_PRESSURE, INLET_TEMPERATURE);
        double pressureRatio = Math.pow(EXHAUST_PRESSURE / INLET_PRESSURE, 1.0 / STAGES);

        for (int i = 0; i < STAGES; i++) {
            double targetPressure = pressure * pressureRatio;
            double diameter = 0.7 + 0.5 * i / (double) (STAGES - 1);

            StageProfile profile = profile(i);
            double throat = INLET_FLOW / Capacity.of(STEAM, pressure, enthalpy, 1.0,
                    profile.discharge()).flow(targetPressure);
            // Запас по пропускной способности: у промежуточных ступеней — против роста энтальпии
            // из-за износа выше по потоку, у последней — против большого перепада до конденсатора,
            // при котором она может оказаться на границе запирания.
            throat *= (i == STAGES - 1) ? 2.0 : 1.05;

            double targetHeight = 0.015 * Math.pow(0.15 / 0.015, i / (double) (STAGES - 1));
            double admission = throat / (Math.PI * diameter * targetHeight
                    * Math.sin(Math.toRadians(profile.nozzleAngle())));
            double height;
            if (admission <= 1.0) {
                height = targetHeight;
            } else {
                admission = 1.0;
                height = throat / (Math.PI * diameter
                        * Math.sin(Math.toRadians(profile.nozzleAngle())));
            }
            StageGeometry stage = StageGeometry.of(diameter, height, admission, profile);
            section.addStage(stage);

            StageResult result = solver.solve(stage, pressure, enthalpy, INLET_FLOW);
            pressure = result.outletPressure();
            enthalpy = result.outletEnthalpy();
        }
        return TurbineGeometry.builder(TurbineGeometry.NOMINAL_ROTATION)
                .addSection(section.build()).build();
    }

    /** Профиль ступени: тонкая хорда и малый зазор у первых ступеней, крупнее — у последних. */
    private static StageProfile profile(int index) {
        double chord = index < 3 ? 0.02 : 0.04;
        double clearance = index < 3 ? 0.0003 : 0.0006;
        return StageProfile.builder().chord(chord).clearance(clearance).build();
    }
}
