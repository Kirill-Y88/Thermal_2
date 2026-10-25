// InvModel.jsh -- модель обратной задачи для jshell: собирает схему теста SolveTest3
// с параметрами (eta_oi, dt1, dt2, dt3), считает наблюдения (G_к, N_э, отборы)
// и решает обратную задачу Ньютоном. Загружается первым; см. tools/inverse/README.md.
import org.kpush88.Thermal_2.v3.*;
import org.kpush88.Thermal_2.v3.calc.*;
import java.util.Locale;
class Inv {
    static final double G0 = 9.4128, GK = 7.3557, NE = 10000.0;
    static final class M { Scheme scheme; Connection live, e1, e2, e3, e4, ex, cond, s1, s2; }
    static M build(double eta, double dt1, double dt2, double dt3) {
        Boiler boiler = new Boiler("b");
        Turbine turbine = new Turbine("t", 4);
        SurfaceHeater hp1 = new SurfaceHeater("hp1");
        SurfaceHeater hp2 = new SurfaceHeater("hp2");
        Deaerator dea = new Deaerator("dea");
        SurfaceHeater lp = new SurfaceHeater("lp");
        Condenser cond = new Condenser("c");
        Pump cp = new Pump("cp");
        Pump fp = new Pump("fp");
        Pipe live = new Pipe("live", SpaceType.STEAM);
        Pipe x1 = new Pipe("x1", SpaceType.STEAM);
        Pipe x2 = new Pipe("x2", SpaceType.STEAM);
        Pipe x3 = new Pipe("x3", SpaceType.STEAM);
        Pipe x4 = new Pipe("x4", SpaceType.STEAM);
        Pipe xEx = new Pipe("xEx", SpaceType.STEAM);
        Pipe d1 = new Pipe("d1", SpaceType.STEAM);
        Pipe d2 = new Pipe("d2", SpaceType.STEAM);
        Pipe dLp = new Pipe("dLp", SpaceType.STEAM);
        Pipe cpipe = new Pipe("cpipe", SpaceType.WATER);
        Pipe cdis = new Pipe("cdis", SpaceType.WATER);
        Pipe lpw = new Pipe("lpw", SpaceType.WATER);
        Pipe dw = new Pipe("dw", SpaceType.WATER);
        Pipe f1 = new Pipe("f1", SpaceType.WATER);
        Pipe f2 = new Pipe("f2", SpaceType.WATER);
        Pipe hpw = new Pipe("hpw", SpaceType.WATER);
        Pipe fwp = new Pipe("fwp", SpaceType.WATER);
        Scheme s = new Scheme().add(boiler).add(turbine).add(hp1).add(hp2).add(dea).add(lp)
            .add(cond).add(cp).add(fp).add(live).add(x1).add(x2).add(x3).add(x4).add(xEx)
            .add(d1).add(d2).add(dLp).add(cpipe).add(cdis).add(lpw).add(dw).add(f1).add(f2).add(hpw).add(fwp);
        s.link(boiler.steamOut(), live.in(), "live");
        Connection atT = s.link(live.out(), turbine.steamIn(), "live");
        Connection ex1 = s.link(turbine.extractionOut(1), x1.in(), "x1");
        Connection st2 = s.link(x1.out(), hp2.steamIn(), "x1");
        Connection ex2 = s.link(turbine.extractionOut(2), x2.in(), "x2");
        Connection st1 = s.link(x2.out(), hp1.steamIn(), "x2");
        Connection ex3 = s.link(turbine.extractionOut(3), x3.in(), "x3");
        s.link(x3.out(), dea.steamIn(), "x3");
        Connection ex4 = s.link(turbine.extractionOut(4), x4.in(), "x4");
        s.link(x4.out(), lp.steamIn(), "x4");
        s.link(hp2.drainOut(), d2.in(), "d2");
        s.link(d2.out(), hp1.drainIn(), "d2");
        s.link(hp1.drainOut(), d1.in(), "d1");
        s.link(d1.out(), dea.drainIn(), "d1");
        s.link(lp.drainOut(), dLp.in(), "dLp");
        s.link(dLp.out(), cond.drainIn(), "dLp");
        Connection ex = s.link(turbine.exhaustOut(), xEx.in(), "ex");
        s.link(xEx.out(), cond.exhaustIn(), "ex");
        Connection condC = s.link(cond.condensateOut(), cpipe.in(), "cond");
        s.link(cpipe.out(), cp.waterIn(), "cond");
        s.link(cp.waterOut(), cdis.in(), "cond");
        s.link(cdis.out(), lp.waterIn(), "cond");
        s.link(lp.waterOut(), lpw.in(), "lpw");
        s.link(lpw.out(), dea.waterIn(), "lpw");
        s.link(dea.waterOut(), dw.in(), "dw");
        s.link(dw.out(), fp.waterIn(), "dw");
        s.link(fp.waterOut(), f1.in(), "fw");
        s.link(f1.out(), hp1.waterIn(), "fw");
        s.link(hp1.waterOut(), f2.in(), "fw");
        s.link(f2.out(), hp2.waterIn(), "fw");
        s.link(hp2.waterOut(), hpw.in(), "fw");
        Connection fwC = s.link(hpw.out(), fwp.in(), "fw");
        s.link(fwp.out(), boiler.waterIn(), "fw");
        boiler.withFreshSteam(14.0, 550.0);
        turbine.withInternalEfficiency(eta).withElectricPower(NE).withElectricEfficiency(0.985);
        hp1.withSubcooling(dt2);
        hp2.withSubcooling(dt1);
        lp.withSubcooling(dt3);
        dea.withPressure(0.6);
        cp.withPressureRise(1.0 - 0.004).withEfficiency(0.8);
        fp.withPressureRise(20.0 - 0.6).withEfficiency(0.8);
        ex1.setPressure(3.5);
        ex2.setPressure(2.5);
        ex3.setPressure(0.8);
        ex4.setPressure(0.15);
        ex.setPressure(0.004);
        M m = new M();
        m.scheme = s; m.live = atT; m.e1 = ex1; m.e2 = ex2; m.e3 = ex3; m.e4 = ex4;
        m.ex = ex; m.cond = condC; m.s1 = st1; m.s2 = st2;
        return m;
    }
    static double fl(CalculationJournal j, Connection c) { return j.value(c, Quantity.FLOW); }
    static double en(CalculationJournal j, Connection c) { return j.value(c, Quantity.ENTHALPY); }
    static double[] evaluate(double eta, double dt1, double dt2, double dt3) {
        M m = build(eta, dt1, dt2, dt3);
        CalculationJournal j = CalculationJournal.of(m.scheme);
        StateResolver.of(j).resolveTo(Stage.RESOLVED);
        FlowSystem.FlowSolution fs = FlowSystem.of(j).withFlow(m.live, G0).solve();
        double gk = fs.flow(m.cond);
        double ni = fl(j, m.live) * en(j, m.live)
                - (fl(j, m.e1) * en(j, m.e1) + fl(j, m.e2) * en(j, m.e2)
                   + fl(j, m.e3) * en(j, m.e3) + fl(j, m.e4) * en(j, m.e4) + fl(j, m.ex) * en(j, m.ex));
        return new double[]{gk, 0.985 * ni, fs.flow(m.s1), fs.flow(m.s2), fs.flow(m.e3), fs.flow(m.e4)};
    }
    static double[] res(double eta, double dt2, double dt3) {
        try {
            if (dt2 < -3 || dt2 > 40 || dt3 < -3 || dt3 > 40) return new double[]{Double.NaN, Double.NaN};
            double[] v = evaluate(eta, 5.0, dt2, dt3);
            return new double[]{(v[0] - GK) / GK, (v[1] - NE) / NE};
        } catch (RuntimeException e) { return new double[]{Double.NaN, Double.NaN}; }
    }
    static double[] newton(double eta, double d2, double d3, int maxIter, boolean verbose) {
        for (int it = 0; it < maxIter; it++) {
            double[] f = res(eta, d2, d3);
            double n0 = Math.hypot(f[0], f[1]);
            if (verbose) System.out.println(String.format(Locale.ROOT, "    it %2d: dt2=%9.4f dt3=%9.4f  rG=%+.3e rN=%+.3e |r|=%.3e", it, d2, d3, f[0], f[1], n0));
            if (!(n0 > 1e-10)) break;
            double h = 0.05;
            double[] fA = res(eta, d2 + h, d3);
            double[] fB = res(eta, d2, d3 + h);
            double a11 = (fA[0] - f[0]) / h, a21 = (fA[1] - f[1]) / h;
            double a12 = (fB[0] - f[0]) / h, a22 = (fB[1] - f[1]) / h;
            double det = a11 * a22 - a12 * a21;
            if (!Double.isFinite(det) || Math.abs(det) < 1e-16) break;
            double s2 = (-f[0] * a22 + f[1] * a12) / det;
            double s3 = (-f[1] * a11 + f[0] * a21) / det;
            double t = 1.0, best = n0, b2 = d2, b3 = d3;
            for (int k = 0; k < 14; k++) {
                double[] ft = res(eta, d2 + t * s2, d3 + t * s3);
                double nt = Math.hypot(ft[0], ft[1]);
                if (Double.isFinite(nt) && nt < best) { best = nt; b2 = d2 + t * s2; b3 = d3 + t * s3; break; }
                t *= 0.5;
            }
            if (!(best < n0)) break;
            d2 = b2; d3 = b3;
        }
        return new double[]{d2, d3};
    }
    static void run(String tag, double eta, double d2, double d3, boolean verbose) {
        double[] r = newton(eta, d2, d3, 25, verbose);
        double[] v = evaluate(eta, 5.0, r[0], r[1]);
        System.out.println(String.format(Locale.ROOT,
            "%s eta=%.2f старт(%5.1f,%5.1f) -> dt2=%9.4f dt3=%9.4f | Gk=%.4f Nэ=%.1f | G1=%.4f G2=%.4f G3=%.4f G4=%.4f",
            tag, eta, d2, d3, r[0], r[1], v[0], v[1], v[2], v[3], v[4], v[5]));
    }
}
