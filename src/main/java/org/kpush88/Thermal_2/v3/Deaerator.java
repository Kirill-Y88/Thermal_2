package org.kpush88.Thermal_2.v3;

import java.util.List;

/**
 * Деаэратор: пар и дренажи смешиваются с питательной водой, из воды удаляются газы.
 *
 * <p>Один канал: {@code steamIn} (греющий пар отбора), коллектор дренажей {@code drainIn}
 * (0…3) и {@code waterIn} (1…N — в деаэратор может приходить несколько потоков: основной
 * конденсат, добавочная вода) питают {@code waterOut}. Контур канала — водяной: на выходе
 * вода, пар и дренаж в ней растворены.
 *
 * <p>В регенеративной схеме деаэратор стоит на границе трактов: перед ним конденсатный
 * тракт низкого давления, после — питательный высокого. Порты не делят его на два канала:
 * массовый баланс у деаэратора один, а давление и температура в нём задаются режимом.
 *
 * <p><b>Рабочее давление</b> — параметр аппарата, а не следствие давления отбора. Пар приходит
 * в деаэратор из отбора, но по дороге дросселируется в подводящей линии и в соплах, поэтому
 * {@code p_отбора > p_деаэратора}; равенство давлений было бы частным случаем без потерь.
 * Рабочее давление задаёт состояние деаэрированной воды: она уходит из аппарата насыщенной при
 * этом давлении, и без параметра водяной тракт после деаэратора не замыкается — ни давление,
 * ни энтальпия питательной воды не следуют из остальных данных схемы.
 */
public class Deaerator extends Device {

    private final Port steamIn = inPort("steamIn", SpaceType.STEAM);
    private final Port drainIn = inPort("drainIn", SpaceType.STEAM, 0, 3);
    private final Port waterIn = inPort("waterIn", SpaceType.WATER, 1, Integer.MAX_VALUE);
    private final Port waterOut = outPort("waterOut", SpaceType.WATER);

    /** Рабочее давление в аппарате, МПа; {@code null} — не задано. */
    private Double pressure;

    public Deaerator(String uname) {
        super(uname);
    }

    public Deaerator(Long id, String uname) {
        super(id, uname);
    }

    /** Греющий пар. */
    public Port steamIn() {
        return steamIn;
    }

    /** Коллектор дренажей (может быть пуст). */
    public Port drainIn() {
        return drainIn;
    }

    /** Вода на входе: основной конденсат, добавочная вода — сколько угодно потоков. */
    public Port waterIn() {
        return waterIn;
    }

    /** Деаэрированная вода (питательная). */
    public Port waterOut() {
        return waterOut;
    }

    /**
     * Задаёт рабочее давление в деаэраторе.
     *
     * <p>Это давление в самом аппарате, а не в камере отбора: пар дросселируется по пути, и в
     * расчёте {@code p_отбора} остаётся тем, что задал режим. Состояние воды на выходе —
     * насыщенная жидкость при рабочем давлении.
     *
     * <p>Проверка «давление отбора выше рабочего» требует давления отбора и выполняется в модели
     * расчётного слоя ({@code DeaeratorModel}), где это давление известно.
     *
     * @param pressureMPa рабочее давление, МПа
     * @return этот деаэратор — для сборки схемы одной цепочкой
     * @throws IllegalArgumentException если давление не положительно
     */
    public Deaerator withPressure(double pressureMPa) {
        if (!(pressureMPa > 0)) {
            throw new IllegalArgumentException("у деаэратора «" + name()
                    + "» рабочее давление должно быть положительным: " + pressureMPa + " МПа");
        }
        this.pressure = pressureMPa;
        return this;
    }

    /** Рабочее давление в аппарате, МПа; {@code null} — параметр не задан. */
    public Double getPressure() {
        return pressure;
    }

    @Override
    public ElementType elementType() {
        return ElementType.DEAERATOR;
    }

    /** Пар, дренажи и вода смешиваются: канал один, выход — вода. */
    @Override
    public List<FlowChannel> flowChannels() {
        return List.of(new FlowChannel(SpaceType.WATER,
                List.of(steamIn, drainIn, waterIn), List.of(waterOut)));
    }
}
