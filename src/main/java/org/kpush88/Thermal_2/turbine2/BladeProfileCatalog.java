package org.kpush88.Thermal_2.turbine2;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Справочник геометрических характеристик профилей МЭИ.
 *
 * <p>Источник: {@code university/Геометрические характеристики профилей МЭИ.docx}, таблица
 * «Геометрические характеристики профилей МЭИ». Это не расчётные данные, а паспортные
 * характеристики решёток: по ним выбирают хорду, относительный шаг и проверяют, попадает ли
 * режим в область, для которой профиль рассчитан.
 *
 * <p><b>Что означают поля.</b>
 * <ul>
 *   <li>{@code optimalOutletAngleDeg} — оптимальный выходной угол: α₁опт для сопловых
 *       профилей и β₂опт для рабочих;</li>
 *   <li>{@code inletAngleDeg} — входной угол: α₀ для сопловых и β₁ для рабочих. В исходной
 *       таблице этот столбец подписан «α₁расч, β₂расч», но диапазоны 70…120° у сопловых
 *       профилей и 20…30° у рабочих, а также сами обозначения {@code С-90-09А} и
 *       {@code Р-23-14А} однозначно указывают на входной угол. В классе он назван честно;</li>
 *   <li>{@code optimalPitchToChordRatio} — относительный шаг {@code t_опт = t/b};</li>
 *   <li>{@code optimalMach} — {@code (M₁t)опт} для сопловых и {@code (M₂t)опт} для рабочих;
 *       запись «до 0.90» хранится как диапазон {@code [0, 0.90]};</li>
 *   <li>{@code chordCm}, {@code areaCm2}, {@code minimumInertiaCm4},
 *       {@code minimumSectionModulusCm3} — хорда, площадь сечения, минимальный момент инерции
 *       и минимальный момент сопротивления изгибу в единицах таблицы (см, см², см⁴, см³).</li>
 * </ul>
 *
 * <p>В источнике встречаются опечатки: кириллическая «З» вместо «33» в обозначениях
 * {@code С-90-33А} и {@code Р-60-33А}, точка вместо запятой в десятичных дробях и значения
 * вида {@code 14-1,7}. В справочнике обозначения нормализованы до {@code С-90-33А} и
 * {@code Р-60-33А}, а диапазон записан как 1.4…1.7. Метод {@link #find(String)} понимает и
 * исходное написание с «З».
 *
 * <p><b>Расширение.</b> Каждая строка таблицы — один явный вызов {@code nozzle(...)} или
 * {@code rotor(...)}. Новая строка добавляется в список {@code entries()} без изменения API;
 * дубликаты обозначений проверяются при инициализации класса.
 *
 * <p><b>Связь с курсовым проектом.</b> Опорные {@link StageAssumptions.GeometryAssumptions}
 * используют хорду 0.0606 м и шаг 0.8 из профиля {@code С-90-09А}, а также хорду 0.0259 м и
 * шаг 0.65 из профиля {@code Р-23-14А}. Справочник позволяет позже выбирать эти величины по
 * обозначению профиля, а не держать их в допущениях вручную.
 */
public final class BladeProfileCatalog {

    /** Назначение профиля: сопловая или рабочая решётка. */
    public enum Kind {
        NOZZLE("сопловой"),
        ROTOR("рабочий");

        private final String title;

        Kind(String title) {
            this.title = title;
        }

        /** Русское название для протокола. */
        public String title() {
            return title;
        }
    }

    /**
     * Замкнутый числовой диапазон.
     *
     * <p>Для записей «до X» нижняя граница равна нулю: числа Маха, углы и относительный шаг
     * неотрицательны, поэтому {@code [0, X]} — естественная запись. Единицы задаются полем
     * {@link Entry}, сам диапазон их не хранит.
     */
    public record Range(double min, double max) {

        public Range {
            if (!Double.isFinite(min) || !Double.isFinite(max)) {
                throw new IllegalArgumentException("границы диапазона должны быть конечными: "
                        + min + "…" + max);
            }
            if (min < 0.0) {
                throw new IllegalArgumentException("нижняя граница диапазона не может быть "
                        + "отрицательной: " + min);
            }
            if (max < min) {
                throw new IllegalArgumentException("верхняя граница диапазона " + max
                        + " меньше нижней " + min);
            }
        }

        /** Диапазон {@code [min, max]}. */
        public static Range of(double min, double max) {
            return new Range(min, max);
        }

        /** Диапазон «до {@code max}», то есть {@code [0, max]}. */
        public static Range upTo(double max) {
            return new Range(0.0, max);
        }

        /** Входит ли значение в диапазон, включая границы. */
        public boolean contains(double value) {
            return value >= min && value <= max;
        }

        /** Строка диапазона для протокола. */
        public String describe() {
            return min == 0.0 ? "до " + number(max) : number(min) + "…" + number(max);
        }

        private static String number(double value) {
            if (value == Math.rint(value)) {
                return String.format(Locale.ROOT, "%.0f", value);
            }
            return String.format(Locale.ROOT, "%.3f", value)
                    .replaceAll("0+$", "")
                    .replaceAll("\\.$", "");
        }
    }

    /**
     * Одна строка справочника.
     *
     * @param designation               обозначение профиля, например {@code С-90-09А}
     * @param kind                      сопловой или рабочий
     * @param optimalOutletAngleDeg     α₁опт или β₂опт, град
     * @param inletAngleDeg             α₀ или β₁, град
     * @param optimalPitchToChordRatio  относительный шаг t/b
     * @param optimalMach               (M₁t)опт или (M₂t)опт
     * @param chordCm                   хорда b₁ для сопловых или b₂ для рабочих, см
     * @param areaCm2                   площадь f₁ или f₂, см²
     * @param minimumInertiaCm4         минимальный момент инерции I_мин, см⁴
     * @param minimumSectionModulusCm3  минимальный момент сопротивления W_мин, см³
     */
    public record Entry(
            String designation,
            Kind kind,
            Range optimalOutletAngleDeg,
            Range inletAngleDeg,
            Range optimalPitchToChordRatio,
            Range optimalMach,
            double chordCm,
            double areaCm2,
            double minimumInertiaCm4,
            double minimumSectionModulusCm3) {

        public Entry {
            if (designation == null || designation.isBlank()) {
                throw new IllegalArgumentException("обозначение профиля не задано");
            }
            if (kind == null) {
                throw new IllegalArgumentException("назначение профиля не задано");
            }
            requireRange(optimalOutletAngleDeg, "оптимальный выходной угол");
            requireRange(inletAngleDeg, "входной угол");
            requireRange(optimalPitchToChordRatio, "относительный шаг t/b");
            requireRange(optimalMach, "оптимальное число Маха");
            requirePositive(chordCm, "хорда b, см");
            requirePositive(areaCm2, "площадь сечения f, см²");
            requirePositive(minimumInertiaCm4, "момент инерции I_мин, см⁴");
            requirePositive(minimumSectionModulusCm3, "момент сопротивления W_мин, см³");
        }

        /** Хорда в СИ, м. */
        public double chordM() {
            return chordCm / 100.0;
        }

        /** Площадь сечения в СИ, м². */
        public double areaM2() {
            return areaCm2 * 1.0e-4;
        }

        /** Минимальный момент инерции в СИ, м⁴. */
        public double minimumInertiaM4() {
            return minimumInertiaCm4 * 1.0e-8;
        }

        /** Минимальный момент сопротивления в СИ, м³. */
        public double minimumSectionModulusM3() {
            return minimumSectionModulusCm3 * 1.0e-6;
        }

        /** Сопловой ли профиль. */
        public boolean isNozzle() {
            return kind == Kind.NOZZLE;
        }

        /** Строка профиля для протокола. */
        public String describe() {
            return String.format(Locale.ROOT,
                    "%s (%s): α₁опт/β₂опт = %s°, α₀/β₁ = %s°, t/b = %s, M = %s, "
                            + "b = %s см, f = %s см², I_мин = %s см⁴, W_мин = %s см³",
                    designation, kind.title(),
                    optimalOutletAngleDeg.describe(), inletAngleDeg.describe(),
                    optimalPitchToChordRatio.describe(), optimalMach.describe(),
                    Range.number(chordCm), Range.number(areaCm2),
                    Range.number(minimumInertiaCm4), Range.number(minimumSectionModulusCm3));
        }

        private static void requireRange(Range value, String what) {
            if (value == null) {
                throw new IllegalArgumentException(what + " не задан");
            }
        }

        private static void requirePositive(double value, String what) {
            if (!(value > 0.0) || !Double.isFinite(value)) {
                throw new IllegalArgumentException(what + " должно быть положительным: " + value);
            }
        }
    }

    private static final List<Entry> ENTRIES = entries();

    private BladeProfileCatalog() {
    }

    /** Все профили в порядке таблицы. Список неизменяемый. */
    public static List<Entry> all() {
        return ENTRIES;
    }

    /** Число профилей в справочнике. */
    public static int size() {
        return ENTRIES.size();
    }

    /** Профили указанного назначения в порядке таблицы. */
    public static List<Entry> byKind(Kind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("назначение профиля не задано");
        }
        return ENTRIES.stream().filter(entry -> entry.kind() == kind).toList();
    }

    /** Только сопловые профили. */
    public static List<Entry> nozzles() {
        return byKind(Kind.NOZZLE);
    }

    /** Только рабочие профили. */
    public static List<Entry> rotors() {
        return byKind(Kind.ROTOR);
    }

    /**
     * Находит профиль по обозначению.
     *
     * <p>Регистр и пробелы по краям не важны; кириллическая «З» в номере понимается как «3»,
     * поэтому находится и исходное написание {@code С-90-ЗЗА}.
     */
    public static Optional<Entry> find(String designation) {
        if (designation == null || designation.isBlank()) {
            return Optional.empty();
        }
        String key = normalize(designation);
        return ENTRIES.stream()
                .filter(entry -> normalize(entry.designation()).equals(key))
                .findFirst();
    }

    /** Находит профиль или сообщает, что его нет. */
    public static Entry require(String designation) {
        return find(designation).orElseThrow(() -> new IllegalArgumentException(
                "профиль «" + designation + "» не найден в справочнике МЭИ"));
    }

    private static List<Entry> entries() {
        List<Entry> profiles = List.of(
                // Сопловые решётки, тип С.
                nozzle("С-90-09А",
                        Range.of(8.0, 11.0), Range.of(70.0, 120.0), Range.of(0.72, 0.85),
                        Range.upTo(0.9), 6.06, 3.45, 0.416, 0.471),
                nozzle("С-90-12А",
                        Range.of(10.0, 14.0), Range.of(70.0, 120.0), Range.of(0.72, 0.87),
                        Range.upTo(0.85), 5.25, 4.09, 0.591, 0.575),
                nozzle("С-90-15А",
                        Range.of(13.0, 17.0), Range.of(70.0, 120.0), Range.of(0.7, 0.85),
                        Range.upTo(0.85), 5.15, 3.3, 0.36, 0.45),
                nozzle("С-90-18А",
                        Range.of(16.0, 20.0), Range.of(70.0, 120.0), Range.of(0.7, 0.8),
                        Range.upTo(0.85), 4.71, 2.72, 0.243, 0.333),
                nozzle("С-90-22А",
                        Range.of(20.0, 24.0), Range.of(70.0, 120.0), Range.of(0.7, 0.8),
                        Range.upTo(0.9), 4.5, 2.35, 0.167, 0.265),
                nozzle("С-90-27А",
                        Range.of(24.0, 30.0), Range.of(70.0, 120.0), Range.of(0.65, 0.75),
                        Range.upTo(0.9), 4.5, 2.03, 0.116, 0.195),
                nozzle("С-90-33А",
                        Range.of(30.0, 36.0), Range.of(70.0, 120.0), Range.of(0.62, 0.75),
                        Range.upTo(0.9), 4.5, 1.84, 0.09, 0.163),
                nozzle("С-90-38А",
                        Range.of(35.0, 42.0), Range.of(70.0, 120.0), Range.of(0.6, 0.73),
                        Range.upTo(0.9), 4.5, 1.75, 0.081, 0.141),
                nozzle("С-55-15А",
                        Range.of(12.0, 18.0), Range.of(45.0, 75.0), Range.of(0.72, 0.87),
                        Range.upTo(0.9), 4.5, 4.41, 1.195, 0.912),
                nozzle("С-55-20А",
                        Range.of(17.0, 23.0), Range.of(45.0, 75.0), Range.of(0.7, 0.85),
                        Range.upTo(0.9), 4.15, 2.15, 0.273, 0.275),
                nozzle("С-45-25А",
                        Range.of(21.0, 28.0), Range.of(35.0, 65.0), Range.of(0.6, 0.75),
                        Range.upTo(0.9), 4.58, 3.3, 0.703, 0.536),
                nozzle("С-60-30А",
                        Range.of(27.0, 34.0), Range.of(45.0, 85.0), Range.of(0.52, 0.7),
                        Range.upTo(0.9), 3.46, 1.49, 0.118, 0.154),
                nozzle("С-65-20А",
                        Range.of(17.0, 23.0), Range.of(45.0, 85.0), Range.of(0.6, 0.7),
                        Range.upTo(0.9), 4.5, 2.26, 0.338, 0.348),
                nozzle("С-70-25А",
                        Range.of(22.0, 28.0), Range.of(55.0, 90.0), Range.of(0.5, 0.67),
                        Range.upTo(0.9), 4.5, 1.89, 0.242, 0.235),
                nozzle("С-90-12Б",
                        Range.of(10.0, 14.0), Range.of(70.0, 120.0), Range.of(0.72, 0.87),
                        Range.of(0.85, 1.15), 5.66, 3.31, 0.388, 0.42),
                nozzle("С-90-15Б",
                        Range.of(13.0, 17.0), Range.of(70.0, 120.0), Range.of(0.7, 0.85),
                        Range.of(0.85, 1.15), 5.2, 3.21, 0.326, 0.413),
                nozzle("С-90-12Р",
                        Range.of(10.0, 14.0), Range.of(70.0, 120.0), Range.of(0.58, 0.68),
                        Range.of(1.4, 1.8), 4.09, 2.3, 0.237, 0.324),
                nozzle("С-90-15Р",
                        Range.of(13.0, 17.0), Range.of(70.0, 120.0), Range.of(0.55, 0.65),
                        Range.of(1.4, 1.7), 4.2, 2.0, 0.153, 0.238),
                // Рабочие решётки, тип Р.
                rotor("Р-23-14А",
                        Range.of(12.0, 16.0), Range.of(20.0, 30.0), Range.of(0.6, 0.75),
                        Range.upTo(0.95), 2.59, 2.44, 0.43, 0.39),
                rotor("Р-26-17А",
                        Range.of(15.0, 19.0), Range.of(23.0, 35.0), Range.of(0.6, 0.7),
                        Range.upTo(0.95), 2.57, 2.07, 0.215, 0.225),
                rotor("Р-30-21А",
                        Range.of(19.0, 24.0), Range.of(25.0, 40.0), Range.of(0.58, 0.68),
                        Range.upTo(0.9), 2.56, 1.85, 0.205, 0.234),
                rotor("Р-35-25А",
                        Range.of(22.0, 28.0), Range.of(30.0, 50.0), Range.of(0.55, 0.65),
                        Range.upTo(0.85), 2.54, 1.62, 0.131, 0.168),
                rotor("Р-46-29А",
                        Range.of(25.0, 32.0), Range.of(44.0, 60.0), Range.of(0.45, 0.58),
                        Range.upTo(0.85), 2.56, 1.22, 0.071, 0.112),
                rotor("Р-60-33А",
                        Range.of(30.0, 36.0), Range.of(47.0, 65.0), Range.of(0.43, 0.55),
                        Range.upTo(0.85), 2.56, 1.02, 0.044, 0.079),
                rotor("Р-60-38А",
                        Range.of(35.0, 42.0), Range.of(55.0, 75.0), Range.of(0.41, 0.51),
                        Range.upTo(0.85), 2.61, 0.76, 0.018, 0.035),
                rotor("Р-23-14Ак",
                        Range.of(12.0, 16.0), Range.of(20.0, 30.0), Range.of(0.6, 0.75),
                        Range.upTo(0.95), 2.59, 2.35, 0.387, 0.331),
                rotor("Р-26-17Ак",
                        Range.of(15.0, 19.0), Range.of(23.0, 45.0), Range.of(0.6, 0.7),
                        Range.upTo(0.95), 2.57, 1.81, 0.152, 0.165),
                rotor("Р-27-17Б",
                        Range.of(15.0, 19.0), Range.of(23.0, 45.0), Range.of(0.57, 0.65),
                        Range.of(0.8, 1.15), 2.54, 2.06, 0.296, 0.296),
                rotor("Р-27-17Бк",
                        Range.of(15.0, 19.0), Range.of(23.0, 45.0), Range.of(0.57, 0.68),
                        Range.of(0.85, 1.15), 2.54, 1.79, 0.216, 0.216),
                rotor("Р-30-21Б",
                        Range.of(19.0, 24.0), Range.of(23.0, 40.0), Range.of(0.55, 0.65),
                        Range.of(0.85, 1.1), 2.01, 1.11, 0.073, 0.101),
                rotor("Р-35-25Б",
                        Range.of(22.0, 28.0), Range.of(30.0, 50.0), Range.of(0.55, 0.65),
                        Range.of(0.85, 1.1), 2.52, 1.51, 0.126, 0.159),
                rotor("Р-21-18Р",
                        Range.of(16.0, 20.0), Range.of(19.0, 24.0), Range.of(0.6, 0.7),
                        Range.of(1.3, 1.6), 2.0, 1.16, 0.118, 0.142),
                rotor("Р-25-22Р",
                        Range.of(20.0, 24.0), Range.of(23.0, 27.0), Range.of(0.54, 0.67),
                        Range.of(1.35, 1.6), 2.0, 0.99, 0.084, 0.1)
        );
        for (int first = 0; first < profiles.size(); first++) {
            for (int second = first + 1; second < profiles.size(); second++) {
                if (normalize(profiles.get(first).designation())
                        .equals(normalize(profiles.get(second).designation()))) {
                    throw new IllegalStateException("в справочнике повторяется профиль "
                            + profiles.get(first).designation());
                }
            }
        }
        return profiles;
    }

    private static Entry nozzle(String designation, Range outletAngle, Range inletAngle,
                                Range pitchRatio, Range mach, double chordCm, double areaCm2,
                                double inertiaCm4, double sectionModulusCm3) {
        return new Entry(designation, Kind.NOZZLE, outletAngle, inletAngle, pitchRatio, mach,
                chordCm, areaCm2, inertiaCm4, sectionModulusCm3);
    }

    private static Entry rotor(String designation, Range outletAngle, Range inletAngle,
                               Range pitchRatio, Range mach, double chordCm, double areaCm2,
                               double inertiaCm4, double sectionModulusCm3) {
        return new Entry(designation, Kind.ROTOR, outletAngle, inletAngle, pitchRatio, mach,
                chordCm, areaCm2, inertiaCm4, sectionModulusCm3);
    }

    private static String normalize(String designation) {
        return designation.trim().toUpperCase(Locale.ROOT).replace('З', '3');
    }
}
