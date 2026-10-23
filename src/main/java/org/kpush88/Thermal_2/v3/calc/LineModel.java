package org.kpush88.Thermal_2.v3.calc;

import org.kpush88.Thermal_2.v3.Connection;
import org.kpush88.Thermal_2.v3.Element;
import org.kpush88.Thermal_2.v3.Pipe;

/**
 * Труба без потерь: состояние среды на выходе равно состоянию на входе.
 *
 * <p>Это и есть весь этап {@link Stage#NORMALIZED}: параметры, заданные на одном конце участка
 * трассы, появляются на другом, поэтому аппарат получает на своих портах уже заполненное
 * сечение, а не ссылку на соседа. Потери давления и теплопотери появятся в этой же модели,
 * когда у {@link Pipe} будут коэффициенты: тогда перенос перестанет быть тождественным, но
 * место переноса не изменится.
 *
 * <p>Перенос симметричен: если известно состояние за трубой (например, давление в камере
 * регулирующей ступени задано как ГУ), оно попадает и на вход. Для трубы без потерь это
 * верно в обе стороны, а направление течения задаётся расходами, а не этой моделью.
 *
 * <p>Расход сюда не входит: {@code G_in = G_out} — это массовый баланс канала
 * ({@link MassBalance}, этап {@link Stage#SOLVED}), а не свойство участка трассы.
 */
final class LineModel implements ElementModel {

    @Override
    public String title() {
        return "труба без потерь";
    }

    @Override
    public Stage stage() {
        return Stage.NORMALIZED;
    }

    @Override
    public boolean supports(Element element) {
        return element instanceof Pipe;
    }

    @Override
    public int resolve(Element element, StateAccess access) {
        Pipe pipe = (Pipe) element;
        Connection in = RuleSupport.connection(element, pipe.in());
        Connection out = RuleSupport.connection(element, pipe.out());
        if (in == null || out == null) {
            return 0;
        }
        int writes = 0;
        for (Quantity quantity : RuleSupport.STATE) {
            writes += RuleSupport.align(access, in, out, quantity,
                    "труба без потерь переносит состояние без изменений");
        }
        return writes;
    }
}
