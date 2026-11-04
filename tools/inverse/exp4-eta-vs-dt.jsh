// exp4-eta-vs-dt.jsh -- можно ли по расчёту схемы определить у подогревателя
// одновременно "КПД" и недогрев? Загружается после InvModel.jsh:
//   jshell -q --class-path "$CP" tools/inverse/InvModel.jsh tools/inverse/exp4-eta-vs-dt.jsh
// Печатает: (1) что даёт расчёт по каждому аппарату (theta, eta, Q_воды/Q_пара),
// (2) зависимость eta1 от dt2 при замкнутой массе, (3) силу каналов в %/K.
import java.util.Locale;
import org.kpush88.Thermal_2.v3.thermo.WaterSteam;
class Probe {
    static final WaterSteam ws = new WaterSteam();
    static Connection pipe(Scheme s, String name) {
        for (Connection c : s.getConnections())
            if (c.getElementOut().name().equals(name)) return c;
        throw new IllegalStateException("нет трубы " + name);
    }
    static double t(CalculationJournal j, Connection c) {
        return ws.temperaturePH(j.value(c, Quantity.PRESSURE), j.value(c, Quantity.ENTHALPY));
    }
    static double h(CalculationJournal j, Connection c) { return j.value(c, Quantity.ENTHALPY); }
    // Строки: ПВД-2 (3.5 МПа), ПВД-1 (2.5 МПа), ПНД (0.15 МПа).
    // Столбцы: tsat, t_вх, t_вых, G_пара, G_воды, Q_воды, Q_пара, p_гр.
    static double[][] solve(double eta, double d1, double d2, double d3) {
        Inv.M m = Inv.build(eta, d1, d2, d3);
        CalculationJournal j = CalculationJournal.of(m.scheme);
        StateResolver.of(j).resolveTo(Stage.RESOLVED);
        FlowSystem.FlowSolution fs = FlowSystem.of(j).withFlow(m.live, Inv.G0).solve();
        Connection f1 = pipe(m.scheme, "f1"), f2 = pipe(m.scheme, "f2");
        Connection hpw = pipe(m.scheme, "hpw"), cdis = pipe(m.scheme, "cdis");
        Connection lpw = pipe(m.scheme, "lpw");
        Connection dr1 = pipe(m.scheme, "d1"), dr2 = pipe(m.scheme, "d2");
        // {пар, вода на входе, вода на выходе, дренаж на выходе, дренаж на входе}
        Connection[][] trio = {{m.s2, f2, hpw, dr2, null},
                               {m.s1, f1, f2, dr1, dr2},
                               {m.e4, cdis, lpw, null, null}};
        double[][] r = new double[3][8];
        for (int i = 0; i < 3; i++) {
            Connection st = trio[i][0], in = trio[i][1], out = trio[i][2];
            double gs = fs.flow(st), gw = fs.flow(out);
            r[i][0] = ws.saturationTemperature(j.value(st, Quantity.PRESSURE));
            r[i][1] = t(j, in);
            r[i][2] = t(j, out);
            r[i][3] = gs;
            r[i][4] = gw;
            r[i][5] = gw * (h(j, out) - h(j, in));
            double hDrain = trio[i][3] == null
                    ? ws.saturatedLiquidEnthalpy(j.value(st, Quantity.PRESSURE)) : h(j, trio[i][3]);
            // пар отдал: своя теплота + теплота входящего дренажа (каскад) до h дренажа
            r[i][6] = gs * (h(j, st) - hDrain);
            if (trio[i][4] != null) r[i][6] += fs.flow(trio[i][4]) * (h(j, trio[i][4]) - hDrain);
            r[i][7] = j.value(st, Quantity.PRESSURE);
        }
        return r;
    }
}

double[][] r0 = Probe.solve(0.85, 5.0, 5.0, 5.0);
System.out.println("== eta=0.85, недогревы 5/5/5: что даёт расчёт по каждому подогревателю ==");
System.out.println("  аппарат | p_гр,МПа | t_нас  | t_вх   | t_вых  | dt=t_нас-t_вых | theta=t_нас-t_вх"
        + " | eta=(t_вых-t_вх)/theta | G_пара | G_воды | Q_воды,кВт | Q_воды/Q_пара");
String[] nm = {"ПВД-2(3.5)", "ПВД-1(2.5)", "ПНД(0.15)"};
for (int i = 0; i < 3; i++) {
    double ts = r0[i][0], ti = r0[i][1], to = r0[i][2];
    System.out.println(String.format(Locale.ROOT,
        "  %-10s | %8.2f | %6.2f | %6.2f | %6.2f | %14.4f | %16.4f | %22.5f | %6.4f | %6.4f"
            + " | %10.1f | %.12f",
        nm[i], r0[i][7], ts, ti, to, ts - to, ts - ti, (to - ti) / (ts - ti), r0[i][3], r0[i][4],
        r0[i][5], r0[i][5] / r0[i][6]));
}
Inv.M mref = Inv.build(0.85, 5.0, 5.0, 5.0);
CalculationJournal jref = CalculationJournal.of(mref.scheme);
StateResolver.of(jref).resolveTo(Stage.RESOLVED);
FlowSystem.of(jref).withFlow(mref.live, Inv.G0).solve();
double pWater = jref.value(Probe.pipe(mref.scheme, "f2"), Quantity.PRESSURE);
System.out.println(String.format(Locale.ROOT,
    "  вода в ПВД при %.1f МПа; round-trip IF97 t=218.9589 -> h -> t: сдвиг %.1e K",
    pWater, Probe.ws.temperaturePH(pWater, Probe.ws.enthalpyPT(pWater, 218.9589)) - 218.9589));
double[] innerDG(double eta, double dt2) {
    double a = 3.0, b = 7.0;
    double fa = Inv.res(eta, dt2, a)[0], fb = Inv.res(eta, dt2, b)[0];
    for (int i = 0; i < 30 && Math.abs(fb) > 1e-12; i++) {
        if (!Double.isFinite(fa) || !Double.isFinite(fb) || fa == fb) break;
        double c = b - fb * (b - a) / (fb - fa);
        if (c < 0) c = 0.0;
        if (c > 40) c = 40.0;
        a = b; fa = fb; b = c; fb = Inv.res(eta, dt2, b)[0];
    }
    return new double[]{b, Inv.res(eta, dt2, b)[1]};
}
System.out.println();
System.out.println("== масса замкнута (dt3 из G_к), меняем dt2(ПВД-1): одна кривая или две величины? ==");
System.out.println("   dt2  | t_вых ПВД-1 | theta1 | eta1=1-dt2/theta1 | (t_вых-t_вх)/theta1 | dt3    "
        + "| Nэ, кВт  | G_отб ПВД-1");
for (double dt2 : new double[]{0, 5, 10, 20, 40}) {
    double[] m3 = innerDG(0.85, dt2);
    double[][] r = Probe.solve(0.85, 5.0, dt2, m3[0]);
    double[] v = Inv.evaluate(0.85, 5.0, dt2, m3[0]);
    double ts = r[1][0], ti = r[1][1], to = r[1][2];
    System.out.println(String.format(Locale.ROOT,
        "  %5.1f | %11.3f | %6.2f | %17.5f | %20.5f | %6.3f | %8.1f | %8.4f",
        dt2, to, ts - ti, 1.0 - dt2 / (ts - ti), (to - ti) / (ts - ti), m3[0], v[1], v[2]));
}
System.out.println();
System.out.println("== сила каналов на один подогреватель (ПВД-1): % на K ==");
double gA = Probe.solve(0.85, 5.0, 0.0, innerDG(0.85, 0.0)[0])[1][3];
double gB = Probe.solve(0.85, 5.0, 5.0, innerDG(0.85, 5.0)[0])[1][3];
System.out.println(String.format(Locale.ROOT,
    "  G_отб(ПВД-1): %.4f -> %.4f кг/с  =>  dG/G = %+.2f %% на K недогрева",
    gA, gB, (gB / gA - 1.0) * 100.0 / 5.0));
double nA = Inv.evaluate(0.85, 5.0, 0.0, innerDG(0.85, 0.0)[0])[1];
double nB = Inv.evaluate(0.85, 5.0, 5.0, innerDG(0.85, 5.0)[0])[1];
System.out.println(String.format(Locale.ROOT,
    "  N_э: %.1f -> %.1f кВт  =>  dN/N = %+.4f %% на K (%+.1f кВт/K)",
    nA, nB, (nB / nA - 1.0) * 100.0 / 5.0, (nB - nA) / 5.0));
double hsMinusHdr = r0[1][5] / r0[1][3];
double cpDrain = (Probe.ws.saturatedLiquidEnthalpy(2.5) - Probe.ws.enthalpyPT(2.5, 213.96)) / 10.0;
System.out.println(String.format(Locale.ROOT,
    "  h_пара-h_др (ПВД-1) = %.1f кДж/кг, c_p дренажа ~ %.2f кДж/(кг*K)", hsMinusHdr, cpDrain));
System.out.println(String.format(Locale.ROOT,
    "  канал «температура дренажа»: охлаждение дренажа на 10 K меняет G_отб на %+.2f %%"
        + " (%+.2f %% на K)", cpDrain * 10.0 / hsMinusHdr * 100.0, cpDrain / hsMinusHdr * 100.0));
// Пара (dt, t_др) против одной только dt: линеаризация баланса аппарата
// G_отб = G_в(h_вых-h_вх)/(h_пара-h_др). В v3 ОД нет, поэтому это оценка,
// а не прогон: t_др входит только через h_др в знаменателе.
double gw = r0[1][4], gs1 = r0[1][3];
double dGdDt = -gw * cpDrain / hsMinusHdr;      // кг/с на K недогрева
double dGdTdr = gs1 * cpDrain / hsMinusHdr;     // кг/с на K охлаждения дренажа
double a11 = -1.0, a12 = 0.0;                   // t_вых: 1 K недогрева = -1 K, от t_др не зависит
double a21 = dGdDt / gs1 * 100.0, a22 = dGdTdr / gs1 * 100.0;
double det2 = a11 * a22 - a12 * a21;
double cond2 = Math.hypot(a11, a21) * Math.hypot(a12, a22) / Math.abs(det2);
System.out.println();
System.out.println("== пара (dt, t_др) при двух наблюдениях (t_вых, G_отб): оценка по балансу ==");
System.out.println(String.format(Locale.ROOT,
    "  dG_отб/dt = %+.4f кг/с на K (%+.2f %%/K),  dG_отб/dt_др = %+.4f кг/с на K (%+.2f %%/K)",
    dGdDt, a21, dGdTdr, a22));
System.out.println(String.format(Locale.ROOT,
    "  J = [[%+.2f, %+.2f], [%+.2f, %+.2f]]  det = %+.3f  cond ~ %.1f  (ср. cond ~ 1e4 у пары dt2/eta_oi)",
    a11, a12, a21, a22, det2, cond2));
