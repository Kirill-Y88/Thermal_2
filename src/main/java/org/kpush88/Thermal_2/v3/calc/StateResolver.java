package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.Scheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Разрешение состояний: доводит журнал от входных данных до балансов, повторяя модели
 * элементов до неподвижной точки.
 *
 * <p>Конвейер этапов тот же, что объявлен в {@link Stage}: {@code INPUT} фиксирует то, что
 * занёс загрузчик схемы; на {@code NORMALIZED} участки трассы переносят состояние;
 * на {@code RESOLVED} аппараты вычисляют то, что следует из параметров (пока — неизменность
 * энтальпии в насосе: работа насоса ещё не учитывается); на {@code SOLVED} сходятся массовые
 * балансы каналов и энтальпия смешения. Каждый этап закрывается снапшотом
 * ({@link CalculationJournal#capture}), поэтому вклад этапа всегда виден отдельно
 * ({@link SchemeState#diff}).
 *
 * <p><b>Почему проходы повторяются.</b> Модель видит только одно сечение и его соседей по
 * элементу; порядок элементов в схеме не гарантирует, что к моменту вызова всё уже известно.
 * Повторные проходы дают то же, что дала бы топологическая сортировка, но без неё: каждая
 * запись монотонна (пишется только неизвестное), поэтому число записей ограничено числом
 * величин в схеме, и проходы обязательно заканчиваются. Если они не закончились заведомо
 * достаточное число раз — это ошибка в модели, и она сообщается, а не замалчивается.
 *
 * <p><b>Что резолвер не делает.</b> Он не решает систему уравнений: если в канале неизвестно
 * больше одного расхода, баланс не «додумывается», а остаётся в отчёте {@link Report#missing()}.
 * Поэтому после {@code SOLVED} на реальной схеме часть величин останется незаданной — это и
 * есть постановка задачи для СЛАУ.
 */
public final class StateResolver {

    /** Модели по умолчанию: сначала балансы (они дают расходы смешению), затем переносы. */
    private static final List<ElementModel> DEFAULT_MODELS = List.of(
            new MassBalance(), new MixerModel(), new LineModel(), new TeeModel(), new PumpModel());

    private final CalculationJournal journal;
    private final List<ElementModel> models;

    private StateResolver(CalculationJournal journal, List<ElementModel> models) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.models = List.copyOf(models);
    }

    /** Резолвер для журнала со стандартным набором моделей. */
    public static StateResolver of(CalculationJournal journal) {
        return new StateResolver(journal, DEFAULT_MODELS);
    }

    /**
     * Добавляет модель. Порядок моделей влияет только на число проходов, но не на результат:
     * проходы повторяются, пока хоть одна модель что-то добавляет.
     */
    public StateResolver with(ElementModel model) {
        Objects.requireNonNull(model, "model");
        List<ElementModel> extended = new ArrayList<>(models);
        extended.add(model);
        return new StateResolver(journal, extended);
    }

    /** Доводит расчёт до этапа {@link Stage#SOLVED} включительно. */
    public Report resolve() {
        return resolveTo(Stage.SOLVED);
    }

    /**
     * Доводит расчёт до указанного этапа включительно.
     *
     * <p>Повторный вызов продолжает с того этапа, который ещё не зафиксирован: журнал можно
     * остановить на {@code NORMALIZED}, посмотреть снапшот и потом продолжить с
     * {@code RESOLVED}. Уже зафиксированные этапы не пересчитываются — иначе снапшот перестал
     * бы быть снимком.
     *
     * @param limit этап, на котором нужно остановиться; {@link Stage#RESULT} пока не
     *              заполняется и трактуется как {@link Stage#SOLVED}
     * @throws IllegalStateException если модели не сошлись за ограниченное число проходов
     */
    public Report resolveTo(Stage limit) {
        Objects.requireNonNull(limit, "limit");
        Stage target = limit.isAfter(Stage.SOLVED) ? Stage.SOLVED : limit;
        List<StageResult> results = new ArrayList<>();
        while (journal.isOpen() && journal.pendingStage().ordinal() <= target.ordinal()) {
            Stage stage = journal.pendingStage();
            if (stage == Stage.INPUT) {
                journal.capture(Stage.INPUT);
                results.add(new StageResult(Stage.INPUT, 0, 0));
                continue;
            }
            results.add(run(stage));
            journal.capture(stage);
        }
        Stage reached = journal.states().isEmpty() ? null : journal.last().stage();
        return new Report(reached, results, journal.missing());
    }

    /** Один этап: модели по элементам, проходы до неподвижной точки. */
    private StageResult run(Stage stage) {
        Scheme scheme = journal.scheme();
        int bound = scheme.getConnections().size() * Quantity.values().length + 1;
        int passes = 0;
        int writes = 0;
        boolean added;
        do {
            passes++;
            added = false;
            for (Element element : scheme.getElements()) {
                for (ElementModel model : models) {
                    if (model.stage() != stage || !model.supports(element)) {
                        continue;
                    }
                    StateAccess access = new StateAccess(journal, stage, element.name());
                    int written = model.resolve(element, access);
                    writes += written;
                    added |= written > 0;
                }
            }
        } while (added && passes < bound);

        if (added) {
            throw new IllegalStateException("разрешение состояний на этапе " + stage.name()
                    + " (" + stage.title() + ") не сошлось за " + passes
                    + " проходов: модели продолжают добавлять величины");
        }
        return new StageResult(stage, passes, writes);
    }

    /** Итог одного этапа: сколько прошло проходов и сколько величин появилось. */
    public record StageResult(Stage stage, int passes, int writes) {

        public StageResult {
            Objects.requireNonNull(stage, "stage");
        }

        /** Подпись «NORMALIZED (перенос по участкам трассы): 2 прохода, 6 величин». */
        public String describe() {
            if (passes == 0) {
                return stage.name() + " (" + stage.title() + "): зафиксирован без проходов";
            }
            return stage.name() + " (" + stage.title() + "): " + passes(passes) + ", "
                    + writes + " величин";
        }

        private static String passes(int passes) {
            int lastTwo = passes % 100;
            int last = passes % 10;
            String word;
            if (last == 1 && lastTwo != 11) {
                word = "проход";
            } else if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) {
                word = "прохода";
            } else {
                word = "проходов";
            }
            return passes + " " + word;
        }
    }


    /**
     * Отчёт разрешения: до какого этапа дошёл расчёт, что сделал каждый этап и чего не хватает.
     *
     * <p>{@link #missing()} — не ошибка, а задание следующему шагу: перечисленные величины не
     * следуют из уже известных, и их определит система балансов (или недостающие параметры
     * конструкции).
     */
    public record Report(Stage reached, List<StageResult> stages,
                         List<SchemeState.Missing> missing) {

        public Report {
            stages = List.copyOf(stages);
            missing = List.copyOf(missing);
        }

        /** Величин не хватает: расчёт неполон. */
        public boolean isComplete() {
            return missing.isEmpty();
        }

        /** Сколько величин записали этапы этого вызова. */
        public int writes() {
            int total = 0;
            for (StageResult result : stages) {
                total += result.writes();
            }
            return total;
        }

        /** Многострочный отчёт: этапы, вклад каждого и список незаданного. */
        public String describe() {
            StringBuilder text = new StringBuilder(reached != null
                    ? "Разрешение состояний: достигнут этап " + reached.name()
                            + " (" + reached.title() + ")"
                    : "Разрешение состояний: журнал пуст");
            if (stages.isEmpty()) {
                text.append(System.lineSeparator())
                        .append("  этот вызов ничего не делал: этап уже зафиксирован");
            }
            for (StageResult result : stages) {
                text.append(System.lineSeparator()).append("  ").append(result.describe());
            }
            text.append(System.lineSeparator()).append("  не задано: ").append(missing.size())
                    .append(" величин");
            for (SchemeState.Missing item : missing) {
                text.append(System.lineSeparator()).append("    ").append(item.describe());
            }
            return text.toString();
        }

        @Override
        public String toString() {
            return describe();
        }
    }
}

