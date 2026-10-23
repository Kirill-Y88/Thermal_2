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
 * <p>Параметры котла — давление и температура свежего пара — задаются здесь, потому что это
 * характеристика аппарата, а не состояния связи: котельная установка держит их в номинале, и
 * именно они превращают питательную воду в пар. Модель расчётного слоя
 * ({@code BoilerModel}) переводит их в состояние на выходе по IF97. КПД и теплота сгорания
 * появятся вместе с отчётом по расходу топлива: в состоянии рабочего тела (давление,
 * энтальпия, расход) им места нет.
 */
public class Boiler extends Device {

    private final Port waterIn = inPort("waterIn", SpaceType.WATER);
    private final Port steamOut = outPort("steamOut", SpaceType.STEAM);

    /** Давление свежего пара, МПа; {@code null} — не задано. */
    private Double steamPressure;

    /** Температура свежего пара, °C; {@code null} — не задано. */
    private Double steamTemperature;

    public Boiler(String uname) {
        super(uname);
    }

    public Boiler(Long id, String uname) {
        super(id, uname);
    }

    /**
     * Задаёт параметры свежего пара — состояние на выходе котла.
     *
     * <p>Обе величины нужны вместе: давление без температуры не определяет энтальпию, поэтому
     * модель работает, только когда заданы обе, и до тех пор не выдумывает состояние.
     *
     * @param pressureMPa давление свежего пара, МПа
     * @param temperatureC температура свежего пара, °C
     * @return этот котёл — для сборки схемы одной цепочкой
     * @throws IllegalArgumentException если давление не положительно или температура не выше
     *                                  нуля
     */
    public Boiler withFreshSteam(double pressureMPa, double temperatureC) {
        if (!(pressureMPa > 0)) {
            throw new IllegalArgumentException("у котла «" + name()
                    + "» давление свежего пара должно быть положительным: " + pressureMPa + " МПа");
        }
        if (!(temperatureC > 0)) {
            throw new IllegalArgumentException("у котла «" + name()
                    + "» температура свежего пара должна быть выше нуля: " + temperatureC + " °C");
        }
        this.steamPressure = pressureMPa;
        this.steamTemperature = temperatureC;
        return this;
    }

    /** Давление свежего пара, МПа; {@code null} — параметр не задан. */
    public Double getSteamPressure() {
        return steamPressure;
    }

    /** Температура свежего пара, °C; {@code null} — параметр не задан. */
    public Double getSteamTemperature() {
        return steamTemperature;
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
