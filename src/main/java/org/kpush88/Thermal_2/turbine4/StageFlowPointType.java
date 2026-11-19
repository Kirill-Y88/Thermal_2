package org.kpush88.Thermal_2.turbine4;

public enum StageFlowPointType {
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

}
