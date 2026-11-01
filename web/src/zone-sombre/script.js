// Script de la page zone-sombre (assemblée par « ./gradlew :engine:webPage »). Code commun : ../commun/script.js.
const STORE = "zs-chefs-v1";
const DEF = { guilds:[{n:"s40-Ombres",a:"",r:"",s:""},{n:"",a:"",r:"",s:""},{n:"",a:"",r:"",s:""}], me:0,
  th:"", tm:"", round:"1", em:"", sa:"", gi:"", ss:"", today:"", rooms:[], shield:"1", free:"0", freeV:"3", sapeP:"25" };
let st = load();


/* ---------- guilds ---------- */

/* ---------- rooms ---------- */
function drawRooms(){
  const box = $("#rooms"); box.innerHTML = "";
  if (!st.rooms.length) { const p = document.createElement("p"); p.className = "hint"; p.textContent = "Aucune salle ajoutée."; box.append(p); }
  st.rooms.forEach((r, i) => {
    const d = document.createElement("div"); d.className = "room";
    d.innerHTML = `
      <div><label>Salle</label><input type="text" placeholder="haut gauche" data-k="n"></div>
      <div><label>Bouclier</label><select data-k="sh"><option value="0">Non</option><option value="1">Oui</option></select></div>
      <div><label>Battable</label><select data-k="b"><option value="1">Oui</option><option value="0">Non</option></select></div>
      <button class="x" type="button" aria-label="Retirer la salle">×</button>${roomKindHtml()}`;
    d.querySelectorAll("[data-k]").forEach(inp => { inp.value = r[inp.dataset.k] ?? ""; inp.oninput = inp.onchange = () => { r[inp.dataset.k] = inp.value; save(); }; });
    d.querySelector(".x").onclick = () => { st.rooms.splice(i, 1); save(); drawRooms(); };
    box.append(d);
  });
}
$("#addRoom").onclick = () => { st.rooms.push({ n:"", sh:"0", b:"1", type:"", buff:"" }); save(); drawRooms(); };

/* ---------- simple fields ---------- */
const FIELDS = ["th","tm","round","em","sa","gi","ss","today","shield","free","freeV","sapeP"];
drawFields(FIELDS);

drawGuilds(); drawRooms();

/* ---------- strategy engine ----------
 * Le moteur est dans engine.js : c'est le même code que l'appli Android (android/engine),
 * compilé en JavaScript (./gradlew :engine:copyWebEngine). Ne pas le modifier ici.
 */
const plan = () => conseil.zsPlan(st);

/* ---------- lecture d'une capture (../commun/script.js) ---------- */
$("#shot").onchange = e => { const f = e.target.files[0]; if (f) readCapture(f, conseil.zsRead, () => { drawGuilds(); drawRooms(); drawFields(FIELDS); }); };

/* ---------- render ---------- */
$("#run").onclick = () => {
  const out = $("#out"); out.innerHTML = "";
  const r = plan();
  if (showErrors(out, r.errs)) return;
  const vd = r.verdict;
  const v = el("div", "verdict" + (vd.win ? " win" : ""));
  v.append(el("h2", null, vd.title));
  vd.lines.forEach(x => v.append(el("p", null, x)));
  v.append(el("p", "hint", vd.hint));
  out.append(v, planBox(r.steps, r.emptyText, r.text));
  [["Cartes", r.keep], ["À surveiller", r.threats]].forEach(([t, list]) => { if (list.length) out.append(listBox(t, list)); });
  out.scrollIntoView({ behavior: "smooth" });
};
