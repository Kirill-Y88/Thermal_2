package org.kpush88.Thermal_2.turbine2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Справочник профилей МЭИ: полнота таблицы, единицы и поиск по обозначению.
 *
 * <p>Проверяются не только отдельные числа, но и то, что таблица перенесена целиком: 18 сопловых
 * и 15 рабочих строк. Если при расширении справочника потеряется строка или появится дубликат,
 * это должно упасть здесь, а не всплыть при выборе профиля в расчёте.
 */
class BladeProfileCatalogTest {

    @Test
    @DisplayName("Справочник содержит все 33 строки таблицы: 18 сопловых и 15 рабочих")
    void loadsWholeTable() {
        assertEquals(33, BladeProfileCatalog.size());
        assertEquals(18, BladeProfileCatalog.nozzles().size());
        assertEquals(15, BladeProfileCatalog.rotors().size());
        assertEquals(BladeProfileCatalog.nozzles(),
                BladeProfileCatalog.byKind(BladeProfileCatalog.Kind.NOZZLE));
        assertEquals(BladeProfileCatalog.rotors(),
                BladeProfileCatalog.byKind(BladeProfileCatalog.Kind.ROTOR));
        assertEquals("С-90-09А", BladeProfileCatalog.all().get(0).designation());
        assertEquals("Р-25-22Р", BladeProfileCatalog.all().get(32).designation());
    }

    @Test
    @DisplayName("С-90-09А: хорда 6.06 см, шаг 0.72…0.85, α₁ = 8…11°")
    void storesNozzleReference() {
        BladeProfileCatalog.Entry profile = BladeProfileCatalog.require("С-90-09А");

        assertEquals(BladeProfileCatalog.Kind.NOZZLE, profile.kind());
        assertTrue(profile.isNozzle());
        assertEquals(8.0, profile.optimalOutletAngleDeg().min(), 1e-12);
        assertEquals(11.0, profile.optimalOutletAngleDeg().max(), 1e-12);
        assertEquals(70.0, profile.inletAngleDeg().min(), 1e-12);
        assertEquals(120.0, profile.inletAngleDeg().max(), 1e-12);
        assertEquals(0.72, profile.optimalPitchToChordRatio().min(), 1e-12);
        assertEquals(0.85, profile.optimalPitchToChordRatio().max(), 1e-12);
        assertTrue(profile.optimalMach().contains(0.90));
        assertFalse(profile.optimalMach().contains(0.91));
        assertEquals(6.06, profile.chordCm(), 1e-12);
        assertEquals(0.0606, profile.chordM(), 1e-12);
        assertEquals(3.45, profile.areaCm2(), 1e-12);
        assertEquals(0.416, profile.minimumInertiaCm4(), 1e-12);
        assertEquals(0.471, profile.minimumSectionModulusCm3(), 1e-12);
    }

    @Test
    @DisplayName("Р-23-14А: рабочая решётка, хорда 2.59 см, шаг 0.60…0.75")
    void storesRotorReference() {
        BladeProfileCatalog.Entry profile = BladeProfileCatalog.require("Р-23-14А");

        assertEquals(BladeProfileCatalog.Kind.ROTOR, profile.kind());
        assertFalse(profile.isNozzle());
        assertEquals(12.0, profile.optimalOutletAngleDeg().min(), 1e-12);
        assertEquals(16.0, profile.optimalOutletAngleDeg().max(), 1e-12);
        assertEquals(20.0, profile.inletAngleDeg().min(), 1e-12);
        assertEquals(30.0, profile.inletAngleDeg().max(), 1e-12);
        assertEquals(0.60, profile.optimalPitchToChordRatio().min(), 1e-12);
        assertEquals(0.75, profile.optimalPitchToChordRatio().max(), 1e-12);
        assertTrue(profile.optimalMach().contains(0.95));
        assertEquals(2.59, profile.chordCm(), 1e-12);
        assertEquals(0.0259, profile.chordM(), 1e-12);
        assertEquals(2.44e-4, profile.areaM2(), 1e-15);
        assertEquals(0.43e-8, profile.minimumInertiaM4(), 1e-18);
        assertEquals(0.39e-6, profile.minimumSectionModulusM3(), 1e-15);
    }

    @Test
    @DisplayName("Поиск не зависит от регистра и понимает опечатку «З» вместо «33»")
    void findsDesignations() {
        assertEquals("С-90-33А", BladeProfileCatalog.require(" С-90-ЗЗА ").designation());
        assertEquals("С-90-09А", BladeProfileCatalog.require("с-90-09а").designation());
        assertEquals("Р-23-14Ак", BladeProfileCatalog.require("р-23-14ак").designation());
        assertTrue(BladeProfileCatalog.find("нет такого профиля").isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> BladeProfileCatalog.require("нет такого профиля"));
    }

    @Test
    @DisplayName("Диапазон проверяет границы и не допускает перевёрнутые значения")
    void validatesRanges() {
        BladeProfileCatalog.Range range = BladeProfileCatalog.Range.of(1.4, 1.7);

        assertTrue(range.contains(1.4));
        assertTrue(range.contains(1.7));
        assertFalse(range.contains(1.3));
        assertThrows(IllegalArgumentException.class,
                () -> BladeProfileCatalog.Range.of(1.7, 1.4));
        assertThrows(IllegalArgumentException.class,
                () -> BladeProfileCatalog.Range.of(-0.1, 0.1));
    }
}
