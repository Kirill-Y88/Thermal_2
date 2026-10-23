package org.kpush88.Thermal_2.v3;

import java.util.List;

/**
 * Конденсатор: выхлоп турбины и дренажи конденсируются, отдавая тепло охлаждающей воде.
 *
 * <p>Порты: {@code exhaustIn} (выхлоп турбины), коллектор дренажей {@code drainIn} (0…3 —
 * дренажи подогревателей низкого давления) и {@code condensateOut} — основной конденсат.
 * Один канал, контур канала водяной: пар и дренаж на выходе становятся конденсатом.
 *
 * <p>Охлаждающая вода вне схемы: она не участвует в балансе рабочего тела, её расход и
 * нагрев задаются граничными условиями расчёта. Поэтому у конденсатора нет пары
 * «охлаждающая вода на входе/выходе» — это был бы третий контур, которого в тепловой схеме
 * блока нет.
 *
 * <p>Класс не наследует {@link SurfaceHeater}: у конденсатора нет {@code steamIn} и
 * {@code drainOut} (дренаж не уходит — он и есть конденсат), зато есть свой вход выхлопа.
 * Наследование потребовало бы отменять порты, а это признак неверной иерархии.
 */
public class Condenser extends Device {

    private final Port exhaustIn = inPort("exhaustIn", SpaceType.STEAM);
    private final Port drainIn = inPort("drainIn", SpaceType.STEAM, 0, 3);
    private final Port condensateOut = outPort("condensateOut", SpaceType.WATER);

    public Condenser(String uname) {
        super(uname);
    }

    public Condenser(Long id, String uname) {
        super(id, uname);
    }

    /** Выхлоп турбины. */
    public Port exhaustIn() {
        return exhaustIn;
    }

    /** Коллектор дренажей подогревателей низкого давления (может быть пуст). */
    public Port drainIn() {
        return drainIn;
    }

    /** Основной конденсат. */
    public Port condensateOut() {
        return condensateOut;
    }

    @Override
    public ElementType elementType() {
        return ElementType.CONDENSER;
    }

    /** Выхлоп и дренажи конденсируются: канал один, выход — конденсат. */
    @Override
    public List<FlowChannel> flowChannels() {
        return List.of(new FlowChannel(SpaceType.WATER,
                List.of(exhaustIn, drainIn), List.of(condensateOut)));
    }
}
