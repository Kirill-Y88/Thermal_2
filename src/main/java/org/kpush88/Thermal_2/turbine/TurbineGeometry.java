package org.kpush88.Thermal_2.turbine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Проточная часть турбины: частота вращения и отсеки в порядке хода расширения.
 *
 * <p>Верхний уровень геометрии. Здесь заканчиваются данные и начинается расчёт: всё, что нужно
 * маршу ({@link TurbineMarch}), — это частота вращения и список отсеков.
 *
 * <p><b>Порядок отсеков — не украшение.</b> Первый отсек начинается на свежем паре, каждый
 * следующий — за своим отбором. Марш идёт по этому списку, поэтому перестановка отсеков местами
 * даст не «другой вариант», а бессмысленный результат: давление должно падать. Проверить это
 * на уровне геометрии нельзя (давления — предмет расчёта, а не конструкции), поэтому
 * согласованность проверяется в марше: он падает, если вычисленное давление за отсеком
 * оказалось не ниже давления перед ним.
 *
 * <p>Частота вращения одна на всю турбину: у всех ступеней общий вал. Отсюда окружная скорость
 * {@code u = π·D·n/60}, и именно поэтому диаметры по ходу расширения растут — чтобы сохранить
 * наивыгоднейшее {@code u/c₁} при растущем объёмном расходе.
 *
 * <p>Класс неизменяемый.
 */
public final class TurbineGeometry {

    /** Номинальная частота вращения турбоагрегата, об/мин. */
    public static final double NOMINAL_ROTATION = 3000.0;

    private final double rotation;
    private final List<SectionGeometry> sections;

    private TurbineGeometry(Builder builder) {
        this.rotation = builder.rotation;
        this.sections = List.copyOf(builder.sections);
    }

    /** Сборщик геометрии: {@code TurbineGeometry.builder(3000).addSection(...).build()}. */
    public static Builder builder(double rotationRpm) {
        return new Builder(rotationRpm);
    }

    /** Частота вращения, об/мин. */
    public double rotation() {
        return rotation;
    }

    /** Отсеки в порядке хода расширения. */
    public List<SectionGeometry> sections() {
        return sections;
    }

    /** Число отсеков. */
    public int sectionCount() {
        return sections.size();
    }

    /** Отсек по номеру (с нуля, по ходу расширения). */
    public SectionGeometry section(int index) {
        return sections.get(index);
    }

    /** Всего ступеней в проточной части. */
    public int stageCount() {
        int total = 0;
        for (SectionGeometry section : sections) {
            total += section.stageCount();
        }
        return total;
    }

    /** Строка геометрии для протокола: вал и отсеки. */
    public String describe() {
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT,
                "n = %.0f об/мин, отсеков %d, ступеней %d",
                rotation, sectionCount(), stageCount()));
        for (SectionGeometry section : sections) {
            text.append(System.lineSeparator()).append("  ").append(section.describe());
        }
        return text.toString();
    }

    /** Сборщик геометрии с проверками при {@link #build()}. */
    public static final class Builder {

        private final double rotation;
        private final List<SectionGeometry> sections = new ArrayList<>();

        private Builder(double rotationRpm) {
            if (!(rotationRpm > 0)) {
                throw new IllegalArgumentException("частота вращения должна быть "
                        + "положительной: " + rotationRpm + " об/мин");
            }
            this.rotation = rotationRpm;
        }

        /** Добавляет отсек в конец проточной части. */
        public Builder addSection(SectionGeometry section) {
            if (section == null) {
                throw new IllegalArgumentException("отсек не задан");
            }
            sections.add(section);
            return this;
        }

        /** Собирает геометрию. */
        public TurbineGeometry build() {
            if (sections.isEmpty()) {
                throw new IllegalArgumentException("в проточной части нет ни одного отсека");
            }
            return new TurbineGeometry(this);
        }
    }
}
