package org.kpush88.Thermal_2.v3;

/**
 * Поверхностный подогреватель (ПВД, ПНД): греющий пар конденсируется на трубках, питательная
 * вода нагревается внутри них.
 *
 * <p>Два независимых канала, и оба выводятся автоматически по контурам портов. Паровая
 * сторона: {@code steamIn} (греющий пар отбора) и коллектор дренажей {@code drainIn} (0…2 —
 * дренажи подогревателей выше по ходу) питают {@code drainOut}. Водяная сторона:
 * {@code waterIn} → {@code waterOut}.
 *
 * <p>Тепло переходит с паровой стороны на водяную внутри аппарата, поэтому в графе потоков
 * ребра «пар → вода» нет: это уравнение теплопередачи элемента, а не путь среды. Смешения
 * дренажа и воды в поверхностном подогревателе не происходит — контуры разделены стенкой,
 * поэтому каналов именно два.
 *
 * <p>Почему дренажей не больше двух: коллектор {@code drainIn} принимает дренаж ближайшего
 * подогревателя и, при каскадной схеме, ещё один поток; больше — признак того, что схема
 * собрана неверно. Ограничение объявлено кратностью порта, и переполнение отвергается при
 * подключении ({@link Scheme#link}).
 */
public class SurfaceHeater extends Device {

    private final Port steamIn = inPort("steamIn", SpaceType.STEAM);
    private final Port drainIn = inPort("drainIn", SpaceType.STEAM, 0, 2);
    private final Port drainOut = outPort("drainOut", SpaceType.STEAM);
    private final Port waterIn = inPort("waterIn", SpaceType.WATER);
    private final Port waterOut = outPort("waterOut", SpaceType.WATER);

    public SurfaceHeater(String uname) {
        super(uname);
    }

    public SurfaceHeater(Long id, String uname) {
        super(id, uname);
    }

    /** Греющий пар отбора. */
    public Port steamIn() {
        return steamIn;
    }

    /** Коллектор дренажей с подогревателей выше по ходу (может быть пуст). */
    public Port drainIn() {
        return drainIn;
    }

    /** Дренаж греющего пара. */
    public Port drainOut() {
        return drainOut;
    }

    /** Нагреваемая вода на входе. */
    public Port waterIn() {
        return waterIn;
    }

    /** Нагреваемая вода на выходе. */
    public Port waterOut() {
        return waterOut;
    }

    @Override
    public ElementType elementType() {
        return ElementType.SURFACE_HEATER;
    }
}
