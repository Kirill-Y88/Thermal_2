package org.kpush88.Thermal_2.v3;

/**
 * Насос: повышает давление воды, не меняя расхода и энтальпии (работа насоса в тепловом
 * балансе не учитывается на этом шаге).
 *
 * <p>Один вход и один выход, один водяной канал — вывод по контуру портов работает без
 * переопределения. Насос стоит на границе трактов: конденсатный — в конденсатном,
 * питательный — в питательном, и оба не меняют контур, поэтому портов пара у насоса нет.
 */
public class Pump extends Device {

    private final Port waterIn = inPort("waterIn", SpaceType.WATER);
    private final Port waterOut = outPort("waterOut", SpaceType.WATER);

    public Pump(String uname) {
        super(uname);
    }

    public Pump(Long id, String uname) {
        super(id, uname);
    }

    /** Вода на входе (всасывание). */
    public Port waterIn() {
        return waterIn;
    }

    /** Вода на выходе (напор). */
    public Port waterOut() {
        return waterOut;
    }

    @Override
    public ElementType elementType() {
        return ElementType.PUMP;
    }
}
