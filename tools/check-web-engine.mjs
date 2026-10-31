// Vérifie que le moteur compilé en JavaScript (intégré dans la page web, issu du module android/engine) donne
// exactement les résultats attendus sur les scénarios de référence, comme les tests JVM (ZsPlanGoldenTest).
// Usage : node tools/check-web-engine.mjs   (après ./gradlew :engine:webPage dans android/)
import { createRequire } from "node:module";
import fs from "node:fs";
const require = createRequire(import.meta.url);
// Moteur compilé (le même que celui intégré dans la page web).
const conseil = require("../android/engine/build/kotlin-webpack/js/productionExecutable/engine.js");
const cases = JSON.parse(fs.readFileSync(new URL("../android/engine/src/jvmTest/resources/zs-plan-golden.json", import.meta.url), "utf8"));

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
let bad = 0;
cases.forEach(({ st, out }, k) => {
  const r = conseil.zsPlan(st);
  const got = r.errs ? { errs: [...r.errs] } : {
    rival: r.rival.n, T: r.T, room: r.room, margin0: r.margin0, margin: r.margin, myR: r.myR, enR: r.enR,
    steps: [...r.steps].map(s => ({ t: s.t, d: s.d, c: s.c })), threats: [...r.threats], keep: [...r.keep], overtake: r.overtake ?? null,
    title: r.verdict.title, lines: [...r.verdict.lines], hint: r.verdict.hint, text: r.text,
  };
  for (const key of Object.keys(out)) if (!same(got[key], out[key])) {
    if (bad++ < 5) console.log(`cas ${k}, ${key} :\n  attendu ${JSON.stringify(out[key])}\n  obtenu  ${JSON.stringify(got[key])}`);
  }
});
console.log(bad ? `${bad} différence(s) sur ${cases.length} cas` : `${cases.length} cas identiques`);
process.exit(bad ? 1 : 0);
