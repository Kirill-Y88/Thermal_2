package org.kpush88.Thermal_2.turbine3;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class Stage {

    private StageKind kind;
    private String name;
    private int number;

    // Геометрия
    private double meanDiameterM;
    private double rootDiameterM;
    private double bladeHeightM;
    private double nozzleHeightM;
    private double reaction;
    private double admission;
    private double speedRatio;

    // Результаты
    private double heatDropKJkg;
    private double usedHeatDropKJkg;
    private double efficiency;
    private double powerKW;

    private final List<RowDetail> rows = new ArrayList<>();
    private final Map<FlowPointType, FlowPoint> flowPointMap = new EnumMap<>(FlowPointType.class);

    /** Добавить в ступень только фактическую точку с конкретным типом. */
    public void addFlowPoint(FlowPoint point) {
        if (point == null || point.getPointType() == null || !point.getPointType().isConcrete()) {
            throw new IllegalArgumentException("в ступень можно добавить только точку с конкретным "
                    + "типом, получено: " + (point == null ? null : point.getPointType()));
        }
        flowPointMap.put(point.getPointType(), point);
    }

    /** Обязательные точки ступени для проверки полноты расчёта. */
    public EnumSet<FlowPointType> requiredPointTypes() {
        if (kind == null) {
            throw new IllegalStateException("тип ступени не задан");
        }
        if (kind == StageKind.REGULATING) {
            return EnumSet.of(
                    FlowPointType.NOZZLE_BLADE_OUT,
                    FlowPointType.MOVING_BLADE_OUT,
                    FlowPointType.REDIRECTING_BLADE_OUT,
                    FlowPointType.MOVING_BLADE_2_OUT,
                    FlowPointType.MOVING_BLADE_PROFILE_LOSS,
                    FlowPointType.MOVING_BLADE_SECONDARY_LOSS,
                    FlowPointType.MOVING_BLADE_TIP_LEAKAGE_LOSS,
                    FlowPointType.MOVING_BLADE_2_PROFILE_LOSS,
                    FlowPointType.MOVING_BLADE_2_SECONDARY_LOSS,
                    FlowPointType.MOVING_BLADE_2_TIP_LEAKAGE_LOSS);
        }
        return EnumSet.of(
                FlowPointType.NOZZLE_BLADE_OUT,
                FlowPointType.MOVING_BLADE_OUT,
                FlowPointType.MOVING_BLADE_PROFILE_LOSS,
                FlowPointType.MOVING_BLADE_SECONDARY_LOSS,
                FlowPointType.MOVING_BLADE_TIP_LEAKAGE_LOSS);
    }

    /** Проверка: ступень полностью заполнена (конец любого расчёта). */
    public void validateComplete() {
        List<String> missing = new ArrayList<>();
        if (kind == null) {
            missing.add("kind");
        }
        if (name == null || name.isBlank()) {
            missing.add("name");
        }
        if (number <= 0) {
            missing.add("number");
        }
        addIfFalse(meanDiameterM > 0, "meanDiameterM", missing);
        addIfFalse(rootDiameterM > 0, "rootDiameterM", missing);
        addIfFalse(bladeHeightM > 0, "bladeHeightM", missing);
        addIfFalse(nozzleHeightM > 0, "nozzleHeightM", missing);
        addIfFalse(reaction >= 0.0 && reaction < 1.0, "reaction", missing);
        addIfFalse(admission > 0.0 && admission <= 1.0, "admission", missing);
        addIfFalse(speedRatio > 0.0, "speedRatio", missing);
        addIfFalse(heatDropKJkg > 0.0, "heatDropKJkg", missing);
        addIfFalse(usedHeatDropKJkg > 0.0, "usedHeatDropKJkg", missing);
        addIfFalse(efficiency > 0.0 && efficiency < 1.0, "efficiency", missing);
        addIfFalse(powerKW > 0.0, "powerKW", missing);
        if (rows.isEmpty()) {
            missing.add("rows");
        }
        for (FlowPointType required : requiredPointTypes()) {
            if (!flowPointMap.containsKey(required)) {
                missing.add("точка " + required);
            }
        }
        for (FlowPointType key : flowPointMap.keySet()) {
            if (!key.isConcrete()) {
                missing.add("недопустимый тип точки в карте: " + key);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("ступень заполнена не полностью: " + missing);
        }
    }

    private static void addIfFalse(boolean condition, String field, List<String> missing) {
        if (!condition) {
            missing.add(field);
        }
    }
}
