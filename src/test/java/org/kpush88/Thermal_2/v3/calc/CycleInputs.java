package org.kpush88.Thermal_2.v3.calc;

/**
 * Стартовые параметры цикла — всё, что в методике задаётся до расчёта: параметры свежего пара,
 * давления отборов, рабочее давление деаэратора, вакуум, давление питательной воды, КПД турбины
 * и насосов, недогрев и задание по электрической мощности.
 *
 * <p><b>Зачем отдельный объект.</b> Стенд {@link RegenerativeScheme} собирает схему из десятка
 * чисел. Пока они константы, «другой режим» — это правка констант, то есть другой тест. Здесь
 * параметры — данные: схему строит {@link RegenerativeScheme#of(CycleInputs)}, а
 * {@link CycleStagesTest} задаёт свои и получает другой расчёт по той же схеме. Значения по
 * умолчанию ({@link #reference()}) совпадают с реперным расчётом {@code v1}.
 *
 * <p><b>Проверки.</b> Одиночные значения проверяются при задании (давления положительны, КПД в
 * (0, 1]), связи между ними — при сборке ({@link Builder#build()}): каждый следующий отбор
 * обязан быть ниже предыдущего, а пар на деаэратор приходит дросселированным, поэтому
 * {@code p_отбора > p_деаэратора}. Схема с обратным порядком давлений не «плохо считается», а
 * не имеет физического смысла, поэтому ошибка называется сразу и с обоими числами.
 */
final class CycleInputs {

    private final double livePressure;
    private final double liveTemperature;
    private final double highPressureExtraction;
    private final double deaeratorExtraction;
    private final double deaeratorPressure;
    private final double lowPressureExtraction;
    private final double condenserPressure;
    private final double internalEfficiency;
    private final double subcooling;
    private final double pumpEfficiency;
    private final double condensatePressure;
    private final double feedwaterPressure;
    private final double electricPower;
    private final double electricEfficiency;

    private CycleInputs(Builder builder) {
        this.livePressure = builder.livePressure;
        this.liveTemperature = builder.liveTemperature;
        this.highPressureExtraction = builder.highPressureExtraction;
        this.deaeratorExtraction = builder.deaeratorExtraction;
        this.deaeratorPressure = builder.deaeratorPressure;
        this.lowPressureExtraction = builder.lowPressureExtraction;
        this.condenserPressure = builder.condenserPressure;
        this.internalEfficiency = builder.internalEfficiency;
        this.subcooling = builder.subcooling;
        this.pumpEfficiency = builder.pumpEfficiency;
        this.condensatePressure = builder.condensatePressure;
        this.feedwaterPressure = builder.feedwaterPressure;
        this.electricPower = builder.electricPower;
        this.electricEfficiency = builder.electricEfficiency;
    }

    /** Сборщик параметров: начинает с реперных значений, меняется только заданное. */
    static Builder builder() {
        return new Builder();
    }

    /** Реперные параметры: те, с которыми считается цикл {@code v1}. */
    static CycleInputs reference() {
        return builder().build();
    }

    /** Давление свежего пара, МПа. */
    double livePressure() {
        return livePressure;
    }

    /** Температура свежего пара, °C. */
    double liveTemperature() {
        return liveTemperature;
    }

    /** Давление отбора на ПВД, МПа. */
    double highPressureExtraction() {
        return highPressureExtraction;
    }

    /** Давление отбора на деаэратор, МПа. */
    double deaeratorExtraction() {
        return deaeratorExtraction;
    }

    /** Рабочее давление деаэратора, МПа. */
    double deaeratorPressure() {
        return deaeratorPressure;
    }

    /** Давление отбора на ПНД, МПа. */
    double lowPressureExtraction() {
        return lowPressureExtraction;
    }

    /** Давление в конденсаторе (вакуум), МПа. */
    double condenserPressure() {
        return condenserPressure;
    }

    /** Внутренний относительный КПД турбины ηoi. */
    double internalEfficiency() {
        return internalEfficiency;
    }

    /** Недогрев питательной воды в поверхностных подогревателях, K. */
    double subcooling() {
        return subcooling;
    }

    /** КПД насосов η_н. */
    double pumpEfficiency() {
        return pumpEfficiency;
    }

    /** Давление в линии основного конденсата после конденсатного насоса, МПа. */
    double condensatePressure() {
        return condensatePressure;
    }

    /**
     * Давление питательной воды за питательным насосом (перед ПВД и котлом), МПа.
     *
     * <p>Это напор питательного насоса, а не давление в деаэраторе: насос поднимает воду с
     * {@link #deaeratorPressure()} до этого значения, и дальше вода идёт по тракту с тем же
     * давлением. По умолчанию равно {@link #livePressure()} — так считает прототип {@code v1};
     * если нужен запас на гидравлику котла, задаётся выше давления свежего пара
     * ({@link Builder#feedwaterPressure(double)}).
     */
    double feedwaterPressure() {
        return feedwaterPressure;
    }

    /** Заданная электрическая мощность N_э, кВт. */
    double electricPower() {
        return electricPower;
    }

    /** КПД турбоагрегата по мощности η_эм. */
    double electricEfficiency() {
        return electricEfficiency;
    }

    /** Отчёт одной строкой: все стартовые параметры подряд. */
    String describe() {
        return "Стартовые параметры: p₀ = " + livePressure + " МПа, t₀ = " + liveTemperature
                + " °C, отборы " + highPressureExtraction + " / " + deaeratorExtraction + " / "
                + lowPressureExtraction + " МПа, деаэратор " + deaeratorPressure
                + " МПа, конденсатор " + condenserPressure + " МПа, ηoi = " + internalEfficiency
                + ", недогрев " + subcooling + " K, η_н = " + pumpEfficiency + ", конденсат "
                + condensatePressure + " МПа, питательная вода " + feedwaterPressure
                + " МПа, N_э = " + electricPower + " кВт, η_эм = " + electricEfficiency;
    }

    @Override
    public String toString() {
        return describe();
    }

    /** Сборщик стартовых параметров; по умолчанию — репертный расчёт {@code v1}. */
    static final class Builder {

        /*
         * Значения по умолчанию — реперный цикл v1: 24 МПа / 540 °C, отборы 3.5 / 0.7 / 0.12 МПа,
         * деаэратор 0.6 МПа, вакуум 0.004 МПа, ηoi = 0.85, недогрев 5 K, η_н = 0.8, конденсат
         * 1.0 МПа, задание 1040.6 кВт при η_эм = 0.985. Меняется только то, что задано сеттером.
         */
        private double livePressure = 24.0;
        private double liveTemperature = 540.0;
        private double highPressureExtraction = 3.5;
        private double deaeratorExtraction = 0.7;
        private double deaeratorPressure = 0.6;
        private double lowPressureExtraction = 0.12;
        private double condenserPressure = 0.004;
        private double internalEfficiency = 0.85;
        private double subcooling = 5.0;
        private double pumpEfficiency = 0.8;
        private double condensatePressure = 1.0;
        /** {@code NaN} — не задано явно: берётся давление свежего пара, как в прототипе v1. */
        private double feedwaterPressure = Double.NaN;
        private double electricPower = 1040.6;
        private double electricEfficiency = 0.985;

        private Builder() {
        }

        /** Параметры свежего пара: давление, МПа и температура, °C. */
        Builder liveSteam(double pressureMPa, double temperatureC) {
            this.livePressure = positive(pressureMPa, "давление свежего пара, МПа");
            this.liveTemperature = positive(temperatureC, "температура свежего пара, °C");
            return this;
        }

        /** Давление отбора на ПВД, МПа. */
        Builder highPressureExtraction(double pressureMPa) {
            this.highPressureExtraction = positive(pressureMPa, "давление отбора на ПВД, МПа");
            return this;
        }

        /** Давление отбора на деаэратор, МПа. */
        Builder deaeratorExtraction(double pressureMPa) {
            this.deaeratorExtraction = positive(pressureMPa, "давление отбора на деаэратор, МПа");
            return this;
        }

        /** Рабочее давление деаэратора, МПа. */
        Builder deaeratorPressure(double pressureMPa) {
            this.deaeratorPressure = positive(pressureMPa, "рабочее давление деаэратора, МПа");
            return this;
        }

        /** Давление отбора на ПНД, МПа. */
        Builder lowPressureExtraction(double pressureMPa) {
            this.lowPressureExtraction = positive(pressureMPa, "давление отбора на ПНД, МПа");
            return this;
        }

        /** Давление в конденсаторе, МПа. */
        Builder condenserPressure(double pressureMPa) {
            this.condenserPressure = positive(pressureMPa, "давление в конденсаторе, МПа");
            return this;
        }

        /** Внутренний относительный КПД турбины ηoi. */
        Builder internalEfficiency(double efficiency) {
            this.internalEfficiency = fraction(efficiency, "внутренний относительный КПД ηoi");
            return this;
        }

        /** Недогрев питательной воды в поверхностных подогревателях, K. */
        Builder subcooling(double kelvin) {
            if (!(kelvin >= 0)) {
                throw new IllegalArgumentException("недогрев не может быть отрицательным: " + kelvin);
            }
            this.subcooling = kelvin;
            return this;
        }

        /** КПД насосов η_н. */
        Builder pumpEfficiency(double efficiency) {
            this.pumpEfficiency = fraction(efficiency, "КПД насосов η_н");
            return this;
        }

        /** Давление в линии основного конденсата после конденсатного насоса, МПа. */
        Builder condensatePressure(double pressureMPa) {
            this.condensatePressure = positive(pressureMPa, "давление основного конденсата, МПа");
            return this;
        }

        /**
         * Давление питательной воды за питательным насосом, МПа.
         *
         * <p>Если не задано, берётся давление свежего пара: так считает прототип {@code v1}, где
         * напор питательного насоса равен {@code p₀ − p_д} и запас на гидравлику котла не
         * моделируется. Чтобы запас был, задаётся выше {@code p₀}, например {@code 1.3 · p₀}.
         */
        Builder feedwaterPressure(double pressureMPa) {
            this.feedwaterPressure = positive(pressureMPa, "давление питательной воды, МПа");
            return this;
        }

        /** Заданная электрическая мощность N_э, кВт. */
        Builder electricPower(double kilowatts) {
            this.electricPower = positive(kilowatts, "электрическая мощность N_э, кВт");
            return this;
        }

        /** КПД турбоагрегата по мощности η_эм. */
        Builder electricEfficiency(double efficiency) {
            this.electricEfficiency = fraction(efficiency, "КПД турбоагрегата η_эм");
            return this;
        }

        /**
         * Собирает параметры и проверяет их согласованность.
         *
         * <p>Если давление питательной воды не задано, оно берётся равным давлению свежего пара
         * (так считает прототип {@code v1}). Заданное ниже давления свежего пара — ошибка: вода
         * не может войти в котёл с давлением меньше давления пара на выходе.
         *
         * @throws IllegalArgumentException если давления идут не по ходу расширения пара, пар
         *                                  на деаэратор не может дросселироваться до рабочего
         *                                  давления или питательная вода слабее свежего пара
         */
        CycleInputs build() {
            if (Double.isNaN(feedwaterPressure)) {
                feedwaterPressure = livePressure;
            }
            if (feedwaterPressure < livePressure) {
                throw new IllegalArgumentException("давление питательной воды ("
                        + feedwaterPressure + " МПа) не может быть ниже давления свежего пара ("
                        + livePressure + " МПа)");
            }
            requireBelow(condenserPressure, lowPressureExtraction, "давление в конденсаторе",
                    "давление отбора на ПНД");
            requireBelow(lowPressureExtraction, deaeratorExtraction, "давление отбора на ПНД",
                    "давление отбора на деаэратор");
            requireBelow(deaeratorExtraction, highPressureExtraction,
                    "давление отбора на деаэратор", "давление отбора на ПВД");
            requireBelow(highPressureExtraction, livePressure, "давление отбора на ПВД",
                    "давление свежего пара");
            requireBelow(deaeratorPressure, deaeratorExtraction, "рабочее давление деаэратора",
                    "давление отбора на деаэратор");
            requireBelow(condenserPressure, condensatePressure, "давление в конденсаторе",
                    "давление основного конденсата");
            requireBelow(deaeratorPressure, condensatePressure, "рабочее давление деаэратора",
                    "давление основного конденсата");
            requireBelow(deaeratorPressure, feedwaterPressure, "рабочее давление деаэратора",
                    "давление питательной воды");
            return new CycleInputs(this);
        }

        private static double positive(double value, String what) {
            if (!(value > 0)) {
                throw new IllegalArgumentException(what + " должно быть положительным: " + value);
            }
            return value;
        }

        private static double fraction(double value, String what) {
            if (!(value > 0 && value <= 1)) {
                throw new IllegalArgumentException(what + " должен быть в (0, 1]: " + value);
            }
            return value;
        }

        private static void requireBelow(double lower, double upper, String lowerName,
                                         String upperName) {
            if (!(lower < upper)) {
                throw new IllegalArgumentException(lowerName + " (" + lower
                        + " МПа) должно быть ниже: " + upperName + " (" + upper + " МПа)");
            }
        }
    }
}
