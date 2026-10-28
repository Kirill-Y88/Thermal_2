package org.kpush88.Thermal_2.turbine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Отсек: ступени между двумя отборами (или между входом и первым отбором, отбором и
 * конденсатором).
 *
 * <p>Отсек — единица, в которой считаются две вещи, интересные эксплуатации: его КПД
 * {@code η_oi = Σh_i / H_отсека} и его пропускная способность. Расход внутри отсека постоянен:
 * пар уходит только на границах, поэтому один отсек — это один расход и один марш ступеней.
 *
 * <p><b>Как задаются ступени.</b> Либо списком ({@link Builder#addStage}), когда геометрия
 * известна поступенчато, либо парой «первая — последняя» с числом ступеней
 * ({@link Builder#withStages(int, StageGeometry, StageGeometry)}). Второй способ — не
 * упрощение, а способ описания, которым пользуются паспорта: «в отсеке восемь ступеней,
 * диаметр 0.9 м, высота растёт с 20 до 80 мм». Промежуточные размеры считаются геометрической
 * прогрессией, потому что именно так растёт удельный объём пара по ходу расширения (а значит,
 * и площадь, которую нужно ему предоставить при той же осевой скорости).
 *
 * <p>Профиль у промежуточных ступеней берётся от первой: они работают в одном диапазоне
 * влажности и углов. Если нужен профиль, меняющийся по отсеку, ступени задаются списком.
 *
 * <p>Класс неизменяемый.
 */
public final class SectionGeometry {

    private final String name;
    private final String extraction;
    private final List<StageGeometry> stages;

    private SectionGeometry(Builder builder) {
        this.name = builder.name;
        this.extraction = builder.extraction;
        this.stages = List.copyOf(builder.stages);
    }

    /** Сборщик отсека: {@code SectionGeometry.named("ЦВД").withStages(...).build()}. */
    public static Builder named(String name) {
        return new Builder(name);
    }

    /** Имя отсека для отчётов («ЦВД», «ЦСД-1», «ЦНД»). */
    public String name() {
        return name;
    }

    /** Куда уходит пар на выходе отсека ({@code null} — выхлоп в конденсатор). */
    public String extraction() {
        return extraction;
    }

    /** Есть ли на выходе отсека отбор (иначе это выхлоп). */
    public boolean hasExtraction() {
        return extraction != null;
    }

    /** Ступени в порядке хода расширения. */
    public List<StageGeometry> stages() {
        return stages;
    }

    /** Число ступеней в отсеке. */
    public int stageCount() {
        return stages.size();
    }

    /** Первая ступень отсека. */
    public StageGeometry firstStage() {
        return stages.get(0);
    }

    /** Последняя ступень отсека. */
    public StageGeometry lastStage() {
        return stages.get(stages.size() - 1);
    }

    /** Строка отсека для протокола. */
    public String describe() {
        return String.format(Locale.ROOT,
                "%s: ступеней %d, D = %.3f…%.3f м, l = %.1f…%.1f мм, ε = %.3f…%.3f, %s",
                name, stageCount(), firstStage().meanDiameter(), lastStage().meanDiameter(),
                firstStage().height() * 1000.0, lastStage().height() * 1000.0,
                firstStage().admission(), lastStage().admission(),
                hasExtraction() ? "отбор на " + extraction : "выхлоп в конденсатор");
    }

    /** Сборщик отсека с проверками при {@link #build()}. */
    public static final class Builder {

        private final String name;
        private final List<StageGeometry> stages = new ArrayList<>();
        private String extraction;

        private Builder(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("у отсека должно быть имя");
            }
            this.name = name;
        }

        /** Куда уходит пар на выходе отсека. */
        public Builder extractionTo(String consumer) {
            this.extraction = consumer;
            return this;
        }

        /** Добавляет ступень в конец отсека. */
        public Builder addStage(StageGeometry stage) {
            if (stage == null) {
                throw new IllegalArgumentException("ступень не задана");
            }
            stages.add(stage);
            return this;
        }

        /** Добавляет ступени в порядке хода расширения. */
        public Builder addStages(List<StageGeometry> sectionStages) {
            if (sectionStages == null || sectionStages.isEmpty()) {
                throw new IllegalArgumentException("список ступеней отсека «" + name + "» пуст");
            }
            sectionStages.forEach(this::addStage);
            return this;
        }

        /**
         * Задаёт отсек парой «первая — последняя» и числом ступеней.
         *
         * <p>Размеры промежуточных ступеней интерполируются геометрически: диаметр и высота
         * растут в постоянном отношении, потому что удельный объём пара по ходу расширения
         * тоже растёт примерно в постоянном отношении на ступень. Профиль берётся от первой
         * ступени.
         *
         * @param count число ступеней отсека (≥ 1)
         * @param first первая ступень
         * @param last  последняя ступень; при {@code count = 1} не используется
         */
        public Builder withStages(int count, StageGeometry first, StageGeometry last) {
            if (count < 1) {
                throw new IllegalArgumentException("в отсеке «" + name + "» не может быть "
                        + count + " ступеней");
            }
            if (first == null) {
                throw new IllegalArgumentException("первая ступень отсека «" + name
                        + "» не задана");
            }
            if (count == 1) {
                return addStage(first);
            }
            if (last == null) {
                throw new IllegalArgumentException("последняя ступень отсека «" + name
                        + "» не задана");
            }
            double diameterRatio = last.meanDiameter() / first.meanDiameter();
            double heightRatio = last.height() / first.height();
            double admissionRatio = last.admission() / first.admission();
            for (int index = 0; index < count; index++) {
                double share = (double) index / (count - 1);
                double diameter = first.meanDiameter() * Math.pow(diameterRatio, share);
                double height = first.height() * Math.pow(heightRatio, share);
                double admission = first.admission() * Math.pow(admissionRatio, share);
                stages.add(StageGeometry.of(diameter, height, admission, first.profile()));
            }
            return this;
        }

        /** Собирает отсек. */
        public SectionGeometry build() {
            if (stages.isEmpty()) {
                throw new IllegalArgumentException("в отсеке «" + name + "» нет ни одной ступени");
            }
            if (extraction != null && extraction.isBlank()) {
                throw new IllegalArgumentException("в отсеке «" + name + "» отбор назван пусто");
            }
            return new SectionGeometry(this);
        }
    }
}
