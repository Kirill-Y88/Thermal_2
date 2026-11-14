package org.kpush88.Thermal_2.turbine3;

public enum FlowPointType {
    ISENTROPIC,
    UNRESOLVED,
    NOZZLE_BLADE_IN, //вход в сопловую решетку
    NOZZLE_BLADE_OUT, // выход из сопловой решетки
    MOVING_BLADE_OUT, // выход из рабочей решетки
    MOVING_BLADE_2_OUT, // выход из рабочей решетки
    REDIRECTING_BLADE_OUT, // выход из поворотной решетки

    // потери за первой рабочей решёткой
    MOVING_BLADE_PROFILE_LOSS,
    MOVING_BLADE_SECONDARY_LOSS,
    MOVING_BLADE_TIP_LEAKAGE_LOSS,

    // потери за второй рабочей решёткой
    MOVING_BLADE_2_PROFILE_LOSS,
    MOVING_BLADE_2_SECONDARY_LOSS,
    MOVING_BLADE_2_TIP_LEAKAGE_LOSS;




    public boolean isConcrete() {
        return this != ISENTROPIC && this != UNRESOLVED;
    }

    // за каждой решеткой в дальнейшем будут добавлены новые точки, характеризующие потери при которых происходит возврат тепла и повышение энтальпии
}
