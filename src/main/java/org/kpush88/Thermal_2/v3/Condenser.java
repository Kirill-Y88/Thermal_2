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
 *
 * <p>Параметр аппарата — <b>переохлаждение конденсата</b> ниже температуры насыщения при
 * давлении выхлопа. Без него конденсат — насыщенная жидкость: тепло отводится охлаждающей
 * водой ровно настолько, чтобы пар сконденсировался. Переохлаждение задаётся, когда
 * конденсатор действительно охлаждает конденсат (или когда так задан режим), и тогда энтальпия
 * выхода считается при {@code t_нас(p_выхлопа) − Δt}. Давление выхлопа приходит из связи:
 * его держит режим, а не конденсатор.
 */
public class Condenser extends Device {

    private final Port exhaustIn = inPort("exhaustIn", SpaceType.STEAM);
    private final Port drainIn = inPort("drainIn", SpaceType.STEAM, 0, 3);
    private final Port condensateOut = outPort("condensateOut", SpaceType.WATER);

    /** Переохлаждение конденсата, K; {@code null} — конденсат на линии насыщения. */
    private Double subcooling;

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

    /**
     * Задаёт переохлаждение конденсата.
     *
     * @param kelvin переохлаждение ниже температуры насыщения, K
     * @return этот конденсатор — для сборки схемы одной цепочкой
     * @throws IllegalArgumentException если переохлаждение отрицательно
     */
    public Condenser withSubcooling(double kelvin) {
        if (!(kelvin >= 0)) {
            throw new IllegalArgumentException("у конденсатора «" + name()
                    + "» переохлаждение не может быть отрицательным: " + kelvin + " K");
        }
        this.subcooling = kelvin;
        return this;
    }

    /** Переохлаждение конденсата, K; {@code null} — конденсат на линии насыщения. */
    public Double getSubcooling() {
        return subcooling;
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
