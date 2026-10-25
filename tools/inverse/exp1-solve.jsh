// exp1-solve.jsh -- опыт A/B: Ньютон по (dt2, dt3) при заданном и при варьируемом eta_oi.
// Запуск: jshell InvModel.jsh exp1-solve.jsh (см. README).
System.out.println("== A. eta=0.85 задан (паспорт), итерация по (dt2,dt3) ==");
Inv.run("A1", 0.85, 5, 5, true);
Inv.run("A2", 0.85, 0, 0, false);
Inv.run("A3", 0.85, 10, 10, false);
Inv.run("A4", 0.85, 2, 9, false);
Inv.run("A5", 0.85, 8, 1, false);
System.out.println("== B. eta не задан: та же итерация при разных eta ==");
for (int i = 0; i <= 6; i++) { Inv.run("B", 0.82 + 0.01 * i, 5, 5, false); }
