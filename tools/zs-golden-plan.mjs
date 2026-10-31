// Génère des scénarios aléatoires, les passe dans plan() de la version web,
// et écrit entrées + sorties dans android/app/src/test/resources/zs-plan-golden.json.
// Le test Kotlin ZsPlanGoldenTest vérifie que le port donne exactement le même résultat.
// Usage : node tools/zs-golden-plan.mjs
import fs from "node:fs";
const html = fs.readFileSync(new URL("../web/conseil-zone-sombre-chefs.html", import.meta.url), "utf8");
const script = html.split("<script>")[1].split("</script>")[0];
const engine = script.slice(script.indexOf("/* ---------- helpers"), script.indexOf("/* ---------- render"));
const make = new Function("st", engine.replace(/function el\(.*\n/, "") + "\nreturn { plan, verdict, planText };");

let seed = 12345;
const rnd = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
const pick = a => a[Math.floor(rnd() * a.length)];
const n = (lo, hi) => String(Math.floor(lo + rnd() * (hi - lo + 1)));
const soul = () => pick(["", "683.16K", "724.44K", "1.2M", "538350", "671,54K", " 12k ", String((rnd() * 900).toFixed(2)) + "K", "abc"]);
const rate = () => pick(["", "32", "22", "30", "8.5", "0", n(1, 80), n(1, 80)]);

const cases = [];
for (let k = 0; k < 400; k++) {
  const st = {
    guilds: [0, 1, 2].map(i => ({ n: pick(["s18-Noxia", "s40-Ombres", "s43-Nexus", ""]), a: k % 7 ? soul().replace("abc", "") || "500K" : soul(), r: k % 9 ? rate() || "20" : rate(), s: pick(["", "0", n(1, 6)]) })),
    me: Math.floor(rnd() * 3),
    th: pick(["", "0", n(0, 12)]), tm: pick(["", n(0, 59)]),
    round: pick(["1", "2", "3", "f"]),
    em: n(0, 2), sa: n(0, 10), gi: n(0, 6), ss: n(0, 6), today: pick(["", n(0, 15), n(0, 15)]),
    rooms: Array.from({ length: Math.floor(rnd() * 6) }, () => ({ n: pick(["", "haut gauche", "centre", "bas"]), sh: pick(["0", "1"]), b: pick(["0", "1"]) })),
    shield: pick(["0", "1"]), free: pick(["0", n(0, 3)]), freeV: pick(["3", "2", "", "2.5"]), sapeP: pick(["25", "40", ""]),
  };
  const { plan, verdict, planText } = make(st);
  const r = plan();
  let out;
  if (r.errs) out = { errs: r.errs };
  else {
    const v = verdict(r);
    out = { rival: r.rival.n, T: r.T, room: r.room, margin0: r.margin0, margin: r.margin, myR: r.myR, enR: r.enR,
      steps: r.steps, threats: r.threats, keep: r.keep, overtake: r.overtake,
      title: v.title, lines: v.lines, hint: v.hint, text: planText(r) };
  }
  cases.push({ st, out });
}
fs.writeFileSync(new URL("../android/app/src/test/resources/zs-plan-golden.json", import.meta.url), JSON.stringify(cases, null, 1));
const ok = cases.filter(c => !c.out.errs);
console.log(`${cases.length} cas, ${ok.length} avec plan, ${ok.filter(c => c.out.steps.length).length} avec étapes`);
