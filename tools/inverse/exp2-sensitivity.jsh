import java.util.Locale;
double h = 0.5;
double[] b0 = Inv.evaluate(0.85, 5.0, 5.0, 5.0);
double[] b2 = Inv.evaluate(0.85, 5.0, 5.0 + h, 5.0);
double[] b3 = Inv.evaluate(0.85, 5.0, 5.0, 5.0 + h);
double dGk2 = (b2[0] - b0[0]) / h, dGk3 = (b3[0] - b0[0]) / h;
double dN2 = (b2[1] - b0[1]) / h, dN3 = (b3[1] - b0[1]) / h;
System.out.println("== J в точке (dt2,dt3)=(5,5), eta=0.85 (абсолютные единицы) ==");
System.out.println(String.format(Locale.ROOT, "  dGk/dt2=%+.5f kg/s на K   dGk/dt3=%+.5f", dGk2, dGk3));
System.out.println(String.format(Locale.ROOT, "  dN/dt2 =%+.1f кВт на K    dN/dt3 =%+.1f", dN2, dN3));
double det = dGk2 * dN3 - dGk3 * dN2;
System.out.println(String.format(Locale.ROOT, "  det=%.5f   (масштаб: dGk/dt2*dN/dt3=%.5f, dGk/dt3*dN/dt2=%.5f)", det, dGk2 * dN3, dGk3 * dN2));
double nrm = Math.hypot(dGk2, dGk3) * Math.hypot(dN3, dN2) / Math.abs(det);
System.out.println(String.format(Locale.ROOT, "  число обусловленности (2x2, нормы Фробениуса) ~ %.1f", nrm));
class Nest {
    static double[] innerDG(double eta, double dt2) {
        double a = 3.0, b = 7.0;
        double fa = Inv.res(eta, dt2, a)[0], fb = Inv.res(eta, dt2, b)[0];
        for (int i = 0; i < 30 && Math.abs(fb) > 1e-12; i++) {
            if (!Double.isFinite(fa) || !Double.isFinite(fb) || fa == fb) break;
            double c = b - fb * (b - a) / (fb - fa);
            if (c < 0) c = 0.0;
            if (c > 40) c = 40.0;
            a = b; fa = fb; b = c; fb = Inv.res(eta, dt2, b)[0];
        }
        return new double[]{b, fb, Inv.res(eta, dt2, b)[1]};
    }
}
System.out.println("== вложенная итерация: dt3 из баланса массы, остаток по мощности ==");
System.out.println("   eta |  dt2 |   dt3   | r_G(масса) | r_N(мощн.), %");
for (double eta : new double[]{0.80, 0.83, 0.85, 0.87}) {
    for (double dt2 : new double[]{0, 5, 10, 20, 30, 40}) {
        double[] r = Nest.innerDG(eta, dt2);
        System.out.println(String.format(Locale.ROOT, "  %.2f | %4.0f | %8.4f | %+.2e | %+8.3f", eta, dt2, r[0], r[1], r[2] * 100));
    }
    System.out.println();
}
