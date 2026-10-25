#!/usr/bin/env python3
"""Оцифровка графиков методички (рис. 8 и 9) для сверки справочных данных.

Назначение
----------
Скрипт измеряет положение синих кривых на растровой странице PDF методички
курсового проекта и переводит пиксели в значения коэффициентов расхода:

* **рис. 8** — `μ` для перегретого пара (сопловые решётки + рабочие при
  `Δβ = 105° / 130° / 145°`) от `b/l`;
* **рис. 9** — поправка на влажность `μ_вл / μ_пп` (сопла + рабочие при
  `p3 = 0,3; 0,2; 0,1; 0,05; 0`) от `y2`, %.

Результаты используются для проверки и обновления данных в
``theory/08-справочные-данные-методички.md`` (пп. 8.5.3, 8.5.4).

Подготовка страницы (растр 150 dpi; в PDF методички рис. 8 и рис. 9 —
стр. 18):

::

    pdftoppm -r 150 -png -f 18 -l 18 \
        "university/Методичка 2 по курс. проекту Самая Самая Последняя(Марина).pdf" /tmp/pg

Запуск::

    python3 tools/measure_metodichka_figures.py /tmp/pg-18.png

Зависимости: python3 + numpy + Pillow. Это автономная утилита для разовой
сверки с исходником, в код проекта (Maven) её зависимости не входят.

Оговорка
--------
Автоматически измеряются только гладкие **сплошные** кривые рис. 8 и 9.
Рис. 7 (`φ` и `ψ`) так измерить нельзя: линии штриховые, а подписи
`α1э`/`Δβ` соединены с кривыми чёрными линиями-выносками, которые
неотличимы от самих кривых. Поэтому для рис. 7 в модели используются
формулы методички (см. п. 8.5.2 файла 08).
"""

import sys

import numpy as np
from PIL import Image

# --- пороги распознавания -------------------------------------------------
DARK = 100         # все каналы < DARK  -> линия сетки / рамка (чёрная)
BLUE_DELTA = 60    # B - R > BLUE_DELTA и B - G > BLUE_DELTA -> синяя кривая

# --- измеряемые рисунки ---------------------------------------------------
# roi        : окно страницы (y — строки, x — столбцы), в котором ищем сетку
# y_labels   : подписи горизонтальных линий сетки (сверху вниз)
# x_labels   : подписи вертикальных линий сетки (слева направо)
# x_samples  : значения по оси X, для которых снимаются кривые
# curves     : кривые сверху вниз (порядок задан вручную по подписям рисунка)
FIGURES = [
    dict(
        title="Рис. 8 — коэффициент расхода μ для перегретого пара",
        roi=dict(y0=170, y1=620, x0=540, x1=960),
        y_labels=[1.00, 0.98, 0.96, 0.94, 0.92, 0.90, 0.88],
        x_labels=[0, 1, 2, 3, 4, 5],
        x_samples=[0, 1, 2, 3, 4, 5],
        x_axis="b/l",
        y_axis="μ",
        curves=["сопловые решётки", "рабочие Δβ=105°",
                "рабочие Δβ=130°", "рабочие Δβ=145°"],
    ),
    dict(
        title="Рис. 9 — влияние влажности на коэффициент расхода",
        roi=dict(y0=820, y1=1215, x0=440, x1=1000),
        y_labels=[1.06, 1.05, 1.04, 1.03, 1.02, 1.01, 1.00],
        x_labels=[0, 2, 4, 6, 8, 10, 12, 14, 16, 18],
        x_samples=[2, 4, 6, 8, 10, 12, 14, 16, 18],
        x_axis="y2, %",
        y_axis="μвл/μпп",
        curves=["(сопла)", "p3=0,3", "p3=0,2", "p3=0,1", "p3=0,05", "p3=0"],
    ),
]


def load(path):
    return np.array(Image.open(path).convert("RGB")).astype(int)


def dark_mask(img):
    return ((img[:, :, 0] < DARK) & (img[:, :, 1] < DARK)
            & (img[:, :, 2] < DARK))


def blue_mask(img):
    return ((img[:, :, 2] - img[:, :, 0] > BLUE_DELTA)
            & (img[:, :, 2] - img[:, :, 1] > BLUE_DELTA))


def group_indices(idx, gap=3):
    """Склеивает соседние индексы (толщина линии) в группы, отдаёт их центры."""
    groups = []
    for i in idx:
        if groups and i - groups[-1][-1] <= gap:
            groups[-1].append(i)
        else:
            groups.append([i])
    return [float(np.mean(g)) for g in groups]


def grid_lines(mask, axis, lo, hi, span_lo, span_hi, thr_frac=0.5):
    """Ищет длинные линии сетки.

    axis=0 — горизонтальные линии (различаются по строкам);
    axis=1 — вертикальные линии (различаются по столбцам);
    span_* — диапазон вдоль линии, в котором считаются чёрные пиксели.
    """
    if axis == 0:
        counts = mask[lo:hi, span_lo:span_hi].sum(axis=1)
    else:
        counts = mask[span_lo:span_hi, lo:hi].sum(axis=0)
    thr = thr_frac * (span_hi - span_lo)
    idx = [i for i, c in enumerate(counts) if c > thr]
    return [lo + p for p in group_indices(idx, gap=4)]


def linear_map(positions, labels, name):
    """Строит линейную зависимость значение = f(позиция) по линиям сетки."""
    if len(positions) != len(labels):
        raise SystemExit(
            f"{name}: найдено {len(positions)} линий сетки, ожидалось "
            f"{len(labels)} — уточните roi/порог в FIGURES")
    a, b = np.polyfit(positions, labels, 1)
    resid = max(abs(a * p + b - v) for p, v in zip(positions, labels))
    return a, b, resid


def curve_values(bmask, x, y0, y1, half=2):
    """Позиции (строки) кривых в столбце x, сверху вниз, в пределах [y0, y1]."""
    col = bmask[y0:y1, x - half:x + half + 1].any(axis=1)
    return [y0 + p for p in
            group_indices([i for i, v in enumerate(col) if v], gap=3)]


def measure(path, fig):
    img = load(path)
    dmask, bmask = dark_mask(img), blue_mask(img)
    roi = fig["roi"]

    rows = grid_lines(dmask, 0, roi["y0"], roi["y1"], roi["x0"], roi["x1"])
    cols = grid_lines(dmask, 1, roi["x0"], roi["x1"], roi["y0"], roi["y1"])
    ya, yb, yres = linear_map(rows, fig["y_labels"], "y")
    xa, xb, xres = linear_map(cols, fig["x_labels"], "x")

    print(f"=== {fig['title']} ===")
    print(f"  сетка Y: строки {[round(r) for r in rows]} -> {fig['y_labels']}"
          f"  (невязка {yres:.4f})")
    print(f"  сетка X: столбцы {[round(c) for c in cols]} -> {fig['x_labels']}"
          f"  (невязка {xres:.4f})")
    print(f"  точность: {abs(xa):.3f} ед/px по {fig['x_axis']}, "
          f"{abs(ya):.4f} ед/px по {fig['y_axis']}")

    table = {}
    for xs in fig["x_samples"]:
        x = int(round((xs - xb) / xa))
        vals = [ya * r + yb for r in
                curve_values(bmask, x, roi["y0"], roi["y1"])]
        table[xs] = vals
        txt = "  ".join(f"{v:.4f}" for v in vals)
        mark = "" if len(vals) == len(fig["curves"]) else "   <-- точек не 4/6"
        print(f"  {fig['x_axis']} = {xs:>3} (x={x:>4}): [{txt}]{mark}")

    print(f"  аппроксимация {fig['y_axis']} = A + B·({fig['x_axis']}), "
          "сверху вниз:")
    n = len(fig["curves"])
    for k in range(n):
        pts = [(xs, vals[k]) for xs, vals in table.items() if len(vals) > k]
        if len(pts) < 2:
            print(f"    {fig['curves'][k]:<20} — недостаточно точек")
            continue
        b, a = np.polyfit([p[0] for p in pts], [p[1] for p in pts], 1)
        print(f"    {fig['curves'][k]:<20} A={a:.4f}  B={b:+.4f}"
              f"  ({len(pts)} точек)")
    print()


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    for path in sys.argv[1:]:
        print(f"### файл {path}\n")
        for fig in FIGURES:
            measure(path, fig)


if __name__ == "__main__":
    main()
