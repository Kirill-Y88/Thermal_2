package org.kpush88.Thermal_2.v4;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Connection {

    private Long id;

    /** Элемент, с выхода которого начинается связь; {@code null} — начало схемы (граница). */
    private Element elementOut;

    /** Элемент, на вход которого приходит связь; {@code null} — конец схемы (граница). */
    private Element elementIn;

    /** Подпись связи для отчётов: среда, начинающая участок («свежий пар», «дренаж ПВД»). */
    private String uname;

    /** Давление рабочего тела, МПа; {@code null} — неизвестно. */
    @Setter
    private Double pressure;

    /** Энтальпия рабочего тела, кДж/кг; {@code null} — неизвестно. */
    @Setter
    private Double enthalpy;

    /** Расход рабочего тела, кг/с; {@code null} — неизвестно. */
    @Setter
    private Double flow;



}
