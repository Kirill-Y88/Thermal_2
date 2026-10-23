package org.kpush88.Thermal_2.v3;

import java.util.List;

/**
 * Котёл: питательная вода превращается в свежий пар.
 *
 * <p>Единственный элемент схемы, где вода штатно становится паром, поэтому канал потока
 * пересекает контуры: {@code waterIn} (водяная сторона) → {@code steamOut} (паровая).
 * Валидатор требует, чтобы такой переход был объявлен явно: по умолчанию канал строится по
 * контуру портов, и тогда водяной и паровой каналы котла оказались бы без выходов и входов
 * соответственно.
 *
 * <p>Параметры котла (давление свежего пара, температура, КПД) появятся здесь вместе с
 * расчётом; сейчас элемент задаёт только порты и канал.
 */
public class Boiler extends Device {

    private final Port waterIn = inPort("waterIn", SpaceType.WATER);
    private final Port steamOut = outPort("steamOut", SpaceType.STEAM);

    public Boiler(String uname) {
        super(uname);
    }

    public Boiler(Long id, String uname) {
        super(id, uname);
    }

    /** Питательная вода. */
    public Port waterIn() {
        return waterIn;
    }

    /** Свежий пар. */
    public Port steamOut() {
        return steamOut;
    }

    @Override
    public ElementType elementType() {
        return ElementType.BOILER;
    }

    /** Вода становится паром: канал один и он пересекает контуры. */
    @Override
    public List<FlowChannel> flowChannels() {
        return List.of(new FlowChannel(SpaceType.STEAM, List.of(waterIn), List.of(steamOut)));
    }
}
