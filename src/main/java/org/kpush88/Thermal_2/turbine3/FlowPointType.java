package org.kpush88.Thermal_2.turbine3;

public enum FlowPointType {
    ISENTROPIC,
    UNRESOLVED,
    NozzleBlade_IN, //вход в сопловую решетку
    NozzleBlade_OUT, // выход из сопловой решетки
    MovingBlade_OUT, // выход из рабочей решетки
    MovingBlade2_OUT, // выход из рабочей решетки
    RedirectingBlade_OUT, // выход из поворотной решетки

    // за каждой решеткой в дальнейшем будут добавлены новые точки, характеризующие потери при которых происходит возврат тепла и повышение энтальпии
}
