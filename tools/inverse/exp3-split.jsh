import java.util.Locale;
System.out.println("== eta=0.85, масса замкнута подбором dt3(ПНД); что делает dt2(ПВД-1) ==");
System.out.println("  dt2  |  dt3   | G отб.ПВД-1(2.5) | G отб.ПВД-2(3.5) | G отб.деаэр | G отб.ПНД | Gк      | Nэ");
for (double dt2 : new double[]{0, 2.5, 5, 10, 20, 40}) {
    double[] r = Nest.innerDG(0.85, dt2);
    double[] v = Inv.evaluate(0.85, 5.0, dt2, r[0]);
    System.out.println(String.format(Locale.ROOT, "  %4.1f | %6.3f | %13.4f | %15.4f | %10.4f | %9.4f | %7.4f | %8.1f",
        dt2, r[0], v[2], v[3], v[4], v[5], v[0], v[1]));
}
