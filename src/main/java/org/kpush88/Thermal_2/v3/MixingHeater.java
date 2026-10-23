package org.kpush88.Thermal_2.v3;

import java.util.List;

/**
 * Смешивающий подогреватель: греющий пар вводится прямо в воду, поэтому контуры
 * объединяются внутри элемента.
 *
 * <p>Один канал: {@code steamIn} и коллектор дренажей {@code drainIn} (0…3) вместе с
 * {@code waterIn} питают {@code waterOut}. Контур канала — водяной, потому что выход несёт
 * воду: пар и дренаж растворяются в ней, и дальше это один поток. В графе потоков все три
 * входа дают рёбра в единственный выход — ровно то, что записывает массовый баланс.
 *
 * <p>Отличие от {@link SurfaceHeater} принципиальное: там два канала (тепло через стенку),
 * здесь один (смешение). Поэтому класс не наследует поверхностный подогреватель — иначе
 * пришлось бы отменять его каналы и порт {@code drainOut}, которого у смешивающего
 * подогревателя нет.
 */
public class MixingHeater extends Device {

    private final Port steamIn = inPort("steamIn", SpaceType.STEAM);
    private final Port drainIn = inPort("drainIn", SpaceType.STEAM, 0, 3);
    private final Port waterIn = inPort("waterIn", SpaceType.WATER);
    private final Port waterOut = outPort("waterOut", SpaceType.WATER);

    public MixingHeater(String uname) {
        super(uname);
    }

    public MixingHeater(Long id, String uname) {
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

    /** Нагреваемая вода на входе. */
    public Port waterIn() {
        return waterIn;
    }

    /** Смесь пара, дренажей и воды на выходе. */
    public Port waterOut() {
        return waterOut;
    }

    @Override
    public ElementType elementType() {
        return ElementType.MIXING_HEATER;
    }

    /** Пар и дренажи смешиваются с водой: канал один, выход — вода. */
    @Override
    public List<FlowChannel> flowChannels() {
        return List.of(new FlowChannel(SpaceType.WATER,
                List.of(steamIn, drainIn, waterIn), List.of(waterOut)));
    }
}
