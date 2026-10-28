package org.kpush88.Thermal_2.turbine;

import java.util.List;
import java.util.Locale;

/**
 * Результат марша проточной части: состояния по ступеням, отсеки и мощность.
 *
 * <p>Запись хранит <b>все</b> ступени ({@link StagePoint}), а не только границы отсеков. Это
 * главное её назначение: КПД отсека 0.85 сам по себе ничего не говорит о том, <i>где</i> потерян
 * перепад, а пошаговый список показывает это сразу — по {@code velocityRatio}, по доле
 * влажностных потерь, по {@code choked}.
 *
 * @param stages         ступени в порядке хода расширения
 * @param sections       отсеки в порядке хода расширения
 * @param inletEnthalpy  энтальпия свежего пара, кДж/кг
 * @param outletPressure давление перед конденсатором, МПа
 * @param outletEnthalpy энтальпия на выхлопе, кДж/кг
 * @param outletQuality  степень сухости на выхлопе
 * @param internalPower  внутренняя мощность Σ G_отсека·ΣW_ступени, кВт
 * @param efficiency     КПД проточной части Σ(G·ΣW)/Σ(G·H_отсека)
 */
public record TurbineResult(
        List<StagePoint> stages,
        List<SectionResult> sections,
        double inletEnthalpy,
        double outletPressure,
        double outletEnthalpy,
        double outletQuality,
        double internalPower,
        double efficiency) {

    /** Марш обязан вернуть столько ступеней, сколько их в геометрии, и все отсеки. */
    public TurbineResult {
        stages = List.copyOf(stages);
        sections = List.copyOf(sections);
        if (stages.isEmpty() || sections.isEmpty()) {
            throw new IllegalArgumentException("марш не может вернуть пустую проточную часть");
        }
    }

    /** Отсек по имени (имя — из {@link SectionGeometry#name()}). */
    public SectionResult section(String name) {
        for (SectionResult section : sections) {
            if (section.name().equals(name)) {
                return section;
            }
        }
        throw new IllegalArgumentException("в результате нет отсека «" + name + "»");
    }

    /** Ступени отсека по имени. */
    public List<StagePoint> stagesOf(String name) {
        return stages.stream().filter(stage -> stage.section().equals(name)).toList();
    }

    /** Строка результата для протокола: отсеки и итог. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        for (SectionResult section : sections) {
            text.append(String.format(Locale.ROOT,
                    "%s: G = %.4f кг/с, %.3f → %.3f МПа, h %.1f → %.1f кДж/кг, "
                            + "H = %.1f, W = %.1f кДж/кг, η = %.3f, N = %.0f кВт%n",
                    section.name(), section.flow(), section.inletPressure(),
                    section.outletPressure(), section.inletEnthalpy(),
                    section.outletEnthalpy(), section.availableDrop(), section.internalWork(),
                    section.efficiency(), section.power()));
        }
        text.append(String.format(Locale.ROOT,
                "выхлоп: %.4f МПа, %.1f кДж/кг, x = %.3f; N_вн = %.0f кВт, η = %.3f",
                outletPressure, outletEnthalpy, outletQuality, internalPower, efficiency));
        return text.toString();
    }

    /**
     * Одна ступень марша: где она стоит и что с ней произошло.
     *
     * @param section имя отсека
     * @param number  номер ступени внутри отсека (с 1)
     * @param result  состояние и работа ступени
     */
    public record StagePoint(String section, int number, StageResult result) {

        public StagePoint {
            if (section == null || section.isBlank()) {
                throw new IllegalArgumentException("ступень марша без имени отсека");
            }
            if (number < 1) {
                throw new IllegalArgumentException("номер ступени внутри отсека считается с 1: "
                        + number);
            }
            if (result == null) {
                throw new IllegalArgumentException("результат ступени не задан");
            }
        }

        /** Строка ступени для протокола. */
        public String describe() {
            return String.format(Locale.ROOT,
                    "%s-%d: %.3f → %.3f МПа, H = %.1f, W = %.1f кДж/кг, η = %.3f, "
                            + "u/c₁ = %.2f, x = %.3f%s",
                    section, number, result.inletPressure(), result.outletPressure(),
                    result.isentropicDrop(), result.internalWork(), result.efficiency(),
                    result.velocityRatio(), result.outletQuality(),
                    result.choked() ? ", заперта" : "");
        }
    }

    /**
     * Отсек в результате: границы, расход, КПД и мощность.
     *
     * <p>{@code availableDrop} — перепад от входа отсека до его выхода по изоэнтропе,
     * {@code internalWork} — сумма работ ступеней. Их отношение и есть КПД отсека η_oi, та самая
     * величина, которую цикл задаёт параметром; здесь она получается из геометрии.
     *
     * @param name           имя отсека
     * @param extraction     потребитель отбора на выходе ({@code null} — выхлоп)
     * @param extractionFlow расход отбора, кг/с
     * @param flow           расход через отсек, кг/с
     * @param inletPressure  давление на входе, МПа
     * @param inletEnthalpy  энтальпия на входе, кДж/кг
     * @param outletPressure давление на выходе, МПа
     * @param outletEnthalpy энтальпия на выходе, кДж/кг
     * @param outletQuality  степень сухости на выходе
     * @param availableDrop  располагаемый перепад отсека, кДж/кг
     * @param internalWork   сумма работ ступеней, кДж/кг
     * @param efficiency     η_oi отсека
     * @param power          внутренняя мощность отсека, кВт
     */
    public record SectionResult(
            String name,
            String extraction,
            double extractionFlow,
            double flow,
            double inletPressure,
            double inletEnthalpy,
            double outletPressure,
            double outletEnthalpy,
            double outletQuality,
            double availableDrop,
            double internalWork,
            double efficiency,
            double power) {

        public SectionResult {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("отсек без имени");
            }
            if (!(flow > 0)) {
                throw new IllegalArgumentException("расход через отсек «" + name
                        + "» должен быть положительным: " + flow + " кг/с");
            }
        }
    }
}

