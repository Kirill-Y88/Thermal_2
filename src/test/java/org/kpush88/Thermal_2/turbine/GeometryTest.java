package org.kpush88.Thermal_2.turbine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Геометрия проточной части: площади, окружная скорость и сборка отсеков.
 *
 * <p>Проверяются величины, от которых зависит весь дальнейший расчёт: площадь горла (по ней
 * считается расход), окружная скорость (по ней — треугольники), отношение b/l и δ/l (по ним —
 * потери). Ошибка в {@code sin α₁} или в парциальности не «немного сдвинет» результат, а
 * изменит площадь в разы, поэтому числа фиксируются явно.
 */
class GeometryTest {

    private static final double SIN_12 = Math.sin(Math.toRadians(12.0));

    @Test
    @DisplayName("Площадь кольца и горла, окружная скорость: D = 0.9 м, l = 20 мм, ε = 0.5")
    void computesAreasAndCircumferentialSpeed() {
        StageGeometry stage = StageGeometry.of(0.9, 0.02, 0.5, StageProfile.defaults());

        assertEquals(Math.PI * 0.9 * 0.02 * 0.5, stage.annulusArea(), 1e-12,
                "площадь кольца π·D·l·ε");
        assertEquals(Math.PI * 0.9 * 0.02 * 0.5 * SIN_12, stage.throatArea(), 1e-15,
                "горло сопловой решётки — кольцо, наклонённое на α₁");
        assertEquals(Math.PI * 45.0, stage.circumferentialSpeed(3000.0), 1e-12,
                "u = π·D·n/60 = π·45 при D = 0.9 м и n = 3000 об/мин");
        assertEquals(3.0, stage.aspectRatio(), 1e-12, "b/l = 60/20 мм");
        assertEquals(0.03, stage.clearanceRatio(), 1e-12, "δ/l = 0.6/20 мм");
    }

    @Test
    @DisplayName("Парциальность уменьшает горло пропорционально: ε = 0.1 в десять раз")
    void admissionScalesThroatLinearly() {
        StageProfile profile = StageProfile.defaults();
        StageGeometry full = StageGeometry.of(0.9, 0.02, 1.0, profile);
        StageGeometry partial = full.withAdmission(0.1);

        assertEquals(0.1 * full.throatArea(), partial.throatArea(), 1e-15,
                "при ε = 0.1 через горло проходит в десять раз меньше пара");
        assertEquals(0.9, partial.meanDiameter(), 1e-12, "копия помнит диаметр");
        assertEquals(1.0, full.admission(), 1e-12, "исходная ступень не изменилась");
    }

    @Test
    @DisplayName("Отсек из пары «первая — последняя»: размеры растут геометрически")
    void interpolatesStagesGeometrically() {
        StageProfile profile = StageProfile.defaults();
        StageGeometry first = StageGeometry.of(0.9, 0.02, 0.3, profile);
        StageGeometry last = StageGeometry.of(1.0, 0.08, 1.0, profile);

        SectionGeometry section = SectionGeometry.named("ЦНД")
                .extractionTo("конденсатор")
                .withStages(3, first, last)
                .build();

        assertEquals(3, section.stageCount(), "число ступеней");
        assertEquals(0.9, section.stages().get(0).meanDiameter(), 1e-12, "первая ступень");
        assertEquals(1.0, section.stages().get(2).meanDiameter(), 1e-12, "последняя ступень");
        assertEquals(Math.sqrt(0.9 * 1.0), section.stages().get(1).meanDiameter(), 1e-12,
                "средняя ступень — геометрическое среднее диаметров");
        assertEquals(Math.sqrt(0.02 * 0.08), section.stages().get(1).height(), 1e-12,
                "и высот тоже: объём растёт в постоянном отношении на ступень");
        assertEquals(Math.sqrt(0.3), section.stages().get(1).admission(), 1e-12,
                "парциальность растёт к единице по тому же закону");
        assertEquals("конденсатор", section.extraction(), "имя потребителя отбора");
        assertTrue(section.hasExtraction(), "отсек заканчивается отбором");
    }

    @Test
    @DisplayName("Проточная часть: отсеки по порядку, ступени суммируются")
    void assemblesTurbine() {
        StageProfile profile = StageProfile.defaults();
        SectionGeometry highPressure = SectionGeometry.named("ЦВД")
                .extractionTo("ПВД-1")
                .withStages(2, StageGeometry.of(0.9, 0.02, 0.2, profile),
                        StageGeometry.of(0.9, 0.04, 0.4, profile))
                .build();
        SectionGeometry lowPressure = SectionGeometry.named("ЦНД")
                .withStages(3, StageGeometry.of(1.1, 0.1, 0.7, profile),
                        StageGeometry.of(1.2, 0.3, 1.0, profile))
                .build();

        TurbineGeometry turbine = TurbineGeometry.builder(3000.0)
                .addSection(highPressure)
                .addSection(lowPressure)
                .build();

        assertEquals(2, turbine.sectionCount(), "отсеков два");
        assertEquals(5, turbine.stageCount(), "ступеней 2 + 3");
        assertEquals("ЦВД", turbine.section(0).name(), "первый отсек — по ходу расширения");
        assertEquals(3000.0, turbine.rotation(), 1e-12, "общий вал");
        assertFalse(turbine.section(1).hasExtraction(), "последний отсек заканчивается выхлопом");
        assertNull(turbine.section(1).extraction(), "у выхлопа нет потребителя");
        assertTrue(turbine.describe().contains("ЦВД"), turbine.describe());
        assertTrue(turbine.describe().contains("ступеней 5"), turbine.describe());
    }

    @Test
    @DisplayName("Бессмысленная геометрия отвергается с указанием величины")
    void rejectsNonsenseGeometry() {
        StageProfile profile = StageProfile.defaults();

        IllegalArgumentException zeroDiameter = assertThrows(IllegalArgumentException.class,
                () -> StageGeometry.of(0.0, 0.02, 1.0, profile));
        assertTrue(zeroDiameter.getMessage().contains("0.0"), zeroDiameter.getMessage());

        IllegalArgumentException badAdmission = assertThrows(IllegalArgumentException.class,
                () -> StageGeometry.of(0.9, 0.02, 0.0, profile));
        assertTrue(badAdmission.getMessage().contains("парциальность"), badAdmission.getMessage());

        IllegalArgumentException emptySection = assertThrows(IllegalArgumentException.class,
                () -> SectionGeometry.named("ЦВД").build());
        assertTrue(emptySection.getMessage().contains("ни одной ступени"),
                emptySection.getMessage());

        IllegalArgumentException emptyTurbine = assertThrows(IllegalArgumentException.class,
                () -> TurbineGeometry.builder(3000.0).build());
        assertTrue(emptyTurbine.getMessage().contains("ни одного отсека"),
                emptyTurbine.getMessage());

        IllegalArgumentException zeroRotation = assertThrows(IllegalArgumentException.class,
                () -> TurbineGeometry.builder(0.0));
        assertTrue(zeroRotation.getMessage().contains("0.0"), zeroRotation.getMessage());
    }

    @Test
    @DisplayName("Профиль: диапазоны углов и коэффициентов проверяются при сборке")
    void rejectsNonsenseProfile() {
        IllegalArgumentException rightAngle = assertThrows(IllegalArgumentException.class,
                () -> StageProfile.builder().nozzleAngle(90.0).build());
        assertTrue(rightAngle.getMessage().contains("вне диапазона"), rightAngle.getMessage());

        IllegalArgumentException overUnity = assertThrows(IllegalArgumentException.class,
                () -> StageProfile.builder().nozzleVelocity(1.05).build());
        assertTrue(overUnity.getMessage().contains("1.05"), overUnity.getMessage());

        assertEquals(0.96, StageProfile.defaults().nozzleVelocity(), 1e-12,
                "профиль по умолчанию собран и помнит φ");
    }
}
