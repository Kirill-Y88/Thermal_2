package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;

/**
 * Величина состояния в сечении — то, что журнал ({@link CalculationJournal}) пишет по
 * отдельности и с отдельным происхождением ({@link Origin}).
 *
 * <p>Давление и энтальпия вместе определяют состояние рабочего тела, расход — величину
 * потока. Разделение на три величины нужно потому, что происхождение у них разное: давление
 * отбора задаётся как ГУ, энтальпия за насосом считается по работе насоса, а расход —
 * только из балансов массы и энергии. Флаг «поле рассчитано» на всю связь сразу этого
 * различия не выражает.
 *
 * <p>Чтение и запись выполняются здесь же: связь хранит значения полями ({@code null} —
 * «неизвестно»), а величина знает, какое из них ей соответствует. Благодаря этому журнал не
 * содержит ветвлений на три случая, а новая величина добавляется одним константным
 * элементом.
 */
public enum Quantity {

    /** Давление рабочего тела, МПа. */
    PRESSURE("давление", "p", "МПа") {
        @Override
        public Double read(Connection connection) {
            return connection.getPressure();
        }

        @Override
        public void write(Connection connection, double value) {
            connection.setPressure(value);
        }
    },

    /** Энтальпия рабочего тела, кДж/кг. */
    ENTHALPY("энтальпия", "h", "кДж/кг") {
        @Override
        public Double read(Connection connection) {
            return connection.getEnthalpy();
        }

        @Override
        public void write(Connection connection, double value) {
            connection.setEnthalpy(value);
        }
    },

    /** Расход рабочего тела, кг/с. */
    FLOW("расход", "G", "кг/с") {
        @Override
        public Double read(Connection connection) {
            return connection.getFlow();
        }

        @Override
        public void write(Connection connection, double value) {
            connection.setFlow(value);
        }
    };

    private final String title;
    private final String symbol;
    private final String unit;

    Quantity(String title, String symbol, String unit) {
        this.title = title;
        this.symbol = symbol;
        this.unit = unit;
    }

    /** Текущее значение величины в связи; {@code null} — неизвестно. */
    public abstract Double read(Connection connection);

    /** Записывает величину в связь; {@code null} означает «неизвестно» и так не пишется. */
    public abstract void write(Connection connection, double value);

    /** Название величины для отчётов и сообщений об ошибках. */
    public String title() {
        return title;
    }

    /** Обозначение в таблицах: {@code p}, {@code h}, {@code G}. */
    public String symbol() {
        return symbol;
    }

    /** Единица измерения. */
    public String unit() {
        return unit;
    }

    /** Подпись «название, единица» для сообщений: «давление, МПа». */
    public String describe() {
        return title + ", " + unit;
    }
}
