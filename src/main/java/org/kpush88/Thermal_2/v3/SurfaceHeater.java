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
 *
 * <p>Параметр аппарата — <b>недогрев</b> питательной воды до температуры насыщения греющего
 * пара. Он и замыкает водяную сторону: вода уходит из подогревателя с температурой
 * {@code t_нас(p_пара) − Δt}, то есть энтальпия выхода считается без баланса тепла. Дренаж при
 * этом — насыщенная жидкость при давлении греющего пара, потому что пар в аппарате
 * конденсируется. Баланс тепла (сколько пара нужно, чтобы так нагреть воду) — уравнение
 * расхода, его место в системе балансов, а не в модели состояния.
 */
public class SurfaceHeater extends Device {

    private final Port steamIn = inPort("steamIn", SpaceType.STEAM);
    private final Port drainIn = inPort("drainIn", SpaceType.STEAM, 0, 2);
    private final Port drainOut = outPort("drainOut", SpaceType.STEAM);
    private final Port waterIn = inPort("waterIn", SpaceType.WATER);
    private final Port waterOut = outPort("waterOut", SpaceType.WATER);

    /** Недогрев питательной воды до насыщения греющего пара, K; {@code null} — не задан. */
    private Double subcooling;

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

    /**
     * Задаёт недогрев питательной воды до температуры насыщения греющего пара.
     *
     * <p>Недогрев — величина положительная и меньше температуры насыщения: нулевой недогрев
     * означал бы кипение воды в трубках, а недогрев больше температуры насыщения — охлаждение
     * вместо нагрева. Проверка второй границы требует давления и выполняется в модели
     * расчётного слоя ({@code SurfaceHeaterModel}), где это давление известно.
     *
     * @param kelvin недогрев, K
     * @return этот подогреватель — для сборки схемы одной цепочкой
     * @throws IllegalArgumentException если недогрев отрицателен
     */
    public SurfaceHeater withSubcooling(double kelvin) {
        if (!(kelvin >= 0)) {
            throw new IllegalArgumentException("у подогревателя «" + name()
                    + "» недогрев не может быть отрицательным: " + kelvin + " K");
        }
        this.subcooling = kelvin;
        return this;
    }

    /** Недогрев питательной воды, K; {@code null} — параметр не задан. */
    public Double getSubcooling() {
        return subcooling;
    }

    @Override
    public ElementType elementType() {
        return ElementType.SURFACE_HEATER;
    }
}
