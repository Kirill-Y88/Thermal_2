package org.kpush88.Thermal_2.v3;

import java.util.Objects;

/**
 * Порт элемента — неизменяемое описание места, к которому подключается связь: имя,
 * направление, контур и кратность (сколько связей допускается).
 *
 * <p>Порт — не связь и не хранит состояние: состояние рабочего тела живёт в связи
 * ({@link Connection}), потому что стык у двух элементов один и параметры в нём одни. Порт
 * отвечает только на вопрос «куда можно подключиться» и с какой кратностью, и объявляется
 * один раз в конструкторе класса элемента ({@link Element#declarePort}).
 *
 * <p>Кратность ({@code min}…{@code max}) — это и есть формализация «сколько концов у
 * элемента»: у котла и трубы порты 1…1, у коллектора дренажей 0…3, у отборов турбины 0…N.
 * Ограничение проверяется при подключении ({@link Scheme#link}) и валидатором
 * ({@link Element#validate()}), поэтому «второй патрубок» больше не может появиться
 * случайно.
 *
 * <p>Равенство и хеш-код не переопределяются: порт существует в единственном экземпляре у
 * своего элемента, сравнение — по идентичности объекта (как у связи).
 */
public final class Port {

    private final Element element;
    private final String name;
    private final PortDirection direction;
    private final SpaceType space;
    private final int min;
    private final int max;

    /**
     * Создаётся только элементом через {@link Element#declarePort}: порт без владельца или
     * порт, объявленный в обход элемента, нарушил бы учёт связей.
     */
    Port(Element element, String name, PortDirection direction, SpaceType space, int min, int max) {
        this.element = Objects.requireNonNull(element, "element");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.space = Objects.requireNonNull(space, "space");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("у порта элемента «" + element.name()
                    + "» должно быть имя");
        }
        if (min < 0) {
            throw new IllegalArgumentException("порт «" + name
                    + "»: минимальная кратность не может быть отрицательной: " + min);
        }
        if (max < 1) {
            throw new IllegalArgumentException("порт «" + name
                    + "»: максимальная кратность должна быть хотя бы 1: " + max);
        }
        if (min > max) {
            throw new IllegalArgumentException("порт «" + name + "»: кратность " + min
                    + " больше максимума " + max);
        }
        this.name = name;
        this.min = min;
        this.max = max;
    }

    /** Элемент, которому принадлежит порт. */
    public Element element() {
        return element;
    }

    /** Имя порта внутри элемента: «waterIn», «drainIn», «extractionOut». */
    public String name() {
        return name;
    }

    /** Направление: вход или выход. */
    public PortDirection direction() {
        return direction;
    }

    /** Контур аппарата: паровая или водяная сторона. */
    public SpaceType space() {
        return space;
    }

    /** Минимальное число связей на порту. */
    public int min() {
        return min;
    }

    /** Максимальное число связей на порту. */
    public int max() {
        return max;
    }

    /** Порт — вход элемента. */
    public boolean isInput() {
        return direction == PortDirection.IN;
    }

    /** Порт — выход элемента. */
    public boolean isOutput() {
        return direction == PortDirection.OUT;
    }

    /** Порт обязателен: схема без него неполна. */
    public boolean isRequired() {
        return min > 0;
    }

    /** На порт можно подключить больше одной связи (коллектор дренажей, отборы турбины). */
    public boolean acceptsMany() {
        return max > 1;
    }

    /** Максимальная кратность не ограничена. */
    public boolean isUnbounded() {
        return max == Integer.MAX_VALUE;
    }

    /** Кратность для отчётов: «1», «0…3», «0…N». */
    public String capacity() {
        if (min == max) {
            return String.valueOf(min);
        }
        return min + "…" + (isUnbounded() ? "N" : String.valueOf(max));
    }

    /** Подпись «элемент.порт» для отчётов и сообщений об ошибках. */
    public String label() {
        return element.name() + "." + name;
    }

    @Override
    public String toString() {
        return label() + " [" + direction.title() + ", " + space.title() + ", " + capacity() + "]";
    }
}
