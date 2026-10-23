package org.kpush88.Thermal_2.v3;

import java.util.List;

/**
 * Тройник — участок трассы, в котором поток разветвляется или смешивается.
 *
 * <p>Разветвление и слияние выражаются только внутри элемента: две связи с одного выхода
 * турбины неотличимы от второго патрубка на том же выходе, поэтому место разделения потоков
 * должно быть отдельным элементом схемы. Тройник — участок трассы, а не аппарат: он
 * находится между двумя аппаратами, не меняет состояние рабочего тела, а только
 * перераспределяет расходы ({@code G_in = G_out1 + G_out2} для разветвления).
 *
 * <p>Порты тройника — коллекторы кратности 1…2, но всего концов ровно три: либо один вход и
 * два выхода (разветвление), либо два входа и один выход (слияние). Это проверяет
 * {@link #validateElement(List)}: кратность порта допускает два потока, а форма тройника —
 * нет. Порядок связей на порту сохраняется, поэтому основной поток и ответвление
 * различимы; балансы расходов появятся здесь на шаге расчёта.
 */
public class PipeTee extends Line {

    private final Port in = inPort("in", getSpace(), 1, 2);
    private final Port out = outPort("out", getSpace(), 1, 2);

    public PipeTee(String uname, SpaceType space) {
        super(uname, space);
    }

    public PipeTee(Long id, String uname, SpaceType space) {
        super(id, uname, space);
    }

    /** Вход тройника: один или два потока. */
    public Port in() {
        return in;
    }

    /** Выход тройника: один или два потока. */
    public Port out() {
        return out;
    }

    @Override
    public ElementType elementType() {
        return ElementType.PIPE_TEE;
    }

    /** У тройника ровно три конца: 1 + 2 или 2 + 1. */
    @Override
    protected void validateElement(List<String> problems) {
        int ends = getInPutConnections().size() + getOutPutConnections().size();
        if (ends != 3) {
            problems.add("у тройника «" + name() + "» " + ends
                    + " конца, а нужно три: один вход и два выхода или два входа и один выход");
        }
    }
}
