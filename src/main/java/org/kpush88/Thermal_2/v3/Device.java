package org.kpush88.Thermal_2.v3;

/**
 * Аппарат — элемент схемы, который не является участком трассы: котёл, турбина,
 * подогреватель, деаэратор, конденсатор, насос.
 *
 * <p>Класс абстрактный: у каждого аппарата свои порты и своя модель, поэтому конкретные
 * виды — {@link Boiler}, {@link Turbine}, {@link SurfaceHeater}, {@link MixingHeater},
 * {@link Deaerator}, {@link Condenser}, {@link Pump}. Общего у них здесь только одно:
 * аппарат — не участок трассы, поэтому связь «аппарат → аппарат» запрещена — между ними
 * должен стоять {@link Line} ({@link Scheme#validate()}).
 *
 * <p>Связи аппарата задаются схемой: {@link Scheme#link} добавляет их к портам, объявленным
 * классом, поэтому число и порядок связей определяются конструкцией элемента, а не сборкой
 * трассы.
 */
public abstract class Device extends Element {

    protected Device(String uname) {
        super(uname);
    }

    protected Device(Long id, String uname) {
        super(id, uname);
    }
}
