package org.kpush88.Thermal_2.turbine;

import java.util.Locale;

/**
 * Геометрия ступени: средний диаметр, высота лопатки, парциальность и профиль решёток.
 *
 * <p>Четыре величины и определяют всё, что ступень делает с паром:
 * <ul>
 *   <li><b>средний диаметр D</b> задаёт окружную скорость {@code u = π·D·n/60}, а значит, и
 *       наивыгоднейшее отношение {@code u/c₁} (для активной ступени около 0.45…0.5);</li>
 *   <li><b>высота лопатки l</b> вместе с диаметром — площадь кольца {@code π·D·l}, через
 *       которую проходит пар; чем ниже лопатка, тем больше доля потерь на концах;</li>
 *   <li><b>парциальность ε</b> — доля окружности, занятая соплами. Первые ступени
 *       конденсационной турбины имеют малый объёмный расход, и сплошное кольцо сопл было бы
 *       слишком велико: пар просто не заполнил бы его. Парциальность — это конструктивный
 *       ответ на mismatch «площадь кольца против объёмного расхода», поэтому она входит в
 *       геометрию, а не подбирается расчётом;</li>
 *   <li><b>профиль</b> — углы и коэффициенты ({@link StageProfile}).</li>
 * </ul>
 *
 * <p><b>Площадь горла.</b> Расход считается по горлу сопловой решётки, а не по кольцу:
 * {@code F_г = π·D·l·ε·sin α₁}. Множитель {@code sin α₁} — потому что горло стоит
 * перпендикулярно потоку, выходящему под углом α₁ к окружности. Из этого сразу видно, почему
 * в первой ступени парциальность около 0.1: при D = 0.9 м и l = 0.02 м кольцо даёт 0.057 м²,
 * а нужно 6·10⁻⁴ м² — разница как раз в десять раз.
 *
 * <p>Класс неизменяемый; {@link #withAdmission(double)} возвращает копию с другой
 * парциальностью (удобно для вариантов одной и той же ступени).
 */
public final class StageGeometry {

    private final double meanDiameter;
    private final double height;
    private final double admission;
    private final StageProfile profile;

    private StageGeometry(double meanDiameter, double height, double admission,
                          StageProfile profile) {
        this.meanDiameter = meanDiameter;
        this.height = height;
        this.admission = admission;
        this.profile = profile;
    }

    /** Ступень со сплошным подводом пара (ε = 1). */
    public static StageGeometry of(double meanDiameter, double height, StageProfile profile) {
        return of(meanDiameter, height, 1.0, profile);
    }

    /**
     * Ступень с заданной парциальностью.
     *
     * @param meanDiameter средний диаметр D, м
     * @param height       высота лопатки l, м
     * @param admission    парциальность ε в (0, 1]
     * @param profile      профиль решёток
     */
    public static StageGeometry of(double meanDiameter, double height, double admission,
                                   StageProfile profile) {
        requirePositive(meanDiameter, "средний диаметр ступени D, м");
        requirePositive(height, "высота лопатки l, м");
        if (!(admission > 0 && admission <= 1)) {
            throw new IllegalArgumentException("парциальность ε должна быть в (0, 1]: "
                    + admission);
        }
        if (profile == null) {
            throw new IllegalArgumentException("профиль ступени не задан");
        }
        return new StageGeometry(meanDiameter, height, admission, profile);
    }

    /** Средний диаметр D, м. */
    public double meanDiameter() {
        return meanDiameter;
    }

    /** Высота лопатки l, м. */
    public double height() {
        return height;
    }

    /** Парциальность ε. */
    public double admission() {
        return admission;
    }

    /** Профиль решёток. */
    public StageProfile profile() {
        return profile;
    }

    /** Площадь кольца на среднем диаметре {@code π·D·l·ε}, м². */
    public double annulusArea() {
        return Math.PI * meanDiameter * height * admission;
    }

    /** Площадь горла сопловой решётки {@code π·D·l·ε·sin α₁}, м². */
    public double throatArea() {
        return annulusArea() * Math.sin(Math.toRadians(profile.nozzleAngle()));
    }

    /** Окружная скорость на среднем диаметре при частоте вращения {@code rpm}, м/с. */
    public double circumferentialSpeed(double rpm) {
        return Math.PI * meanDiameter * rpm / 60.0;
    }

    /** Отношение хорды к высоте b/l — аргумент вторичных потерь. */
    public double aspectRatio() {
        return profile.chord() / height;
    }

    /** Отношение радиального зазора к высоте δ/l — аргумент потерь на утечки. */
    public double clearanceRatio() {
        return profile.clearance() / height;
    }

    /** Копия ступени с другой парциальностью. */
    public StageGeometry withAdmission(double value) {
        return of(meanDiameter, height, value, profile);
    }

    /** Строка ступени для протокола. */
    public String describe() {
        return String.format(Locale.ROOT,
                "D=%.3f м, l=%.1f мм, ε=%.3f, F_г=%.4e м², b/l=%.2f, δ/l=%.3f",
                meanDiameter, height * 1000.0, admission, throatArea(), aspectRatio(),
                clearanceRatio());
    }

    private static void requirePositive(double value, String what) {
        if (!(value > 0)) {
            throw new IllegalArgumentException(what + " должно быть положительным: " + value);
        }
    }
}
