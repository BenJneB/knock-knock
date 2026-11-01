// Script de la page capitale (assemblée par « ./gradlew :engine:webPage »). Code commun : ../commun/script.js.
// Fournis par le moteur (conseil) et par ../commun/script.js, inclus avant ce script dans la page assemblée.
/* global conseil, $, el, tag, load, save, drawGuilds, drawFields, roomKindHtml, readCapture, showErrors, planBox, listBox */
const STORE = "dc-chefs-v1";
const DEF = { guilds:[{n:"s40-Ombres",a:"",r:"",s:""},{n:"",a:"",r:"",s:""},{n:"",a:"",r:"",s:""}], me:0,
  th:"", tm:"", attempts:"1", ourDmg:"", rooms:[], ourRooms:[] };
let st = load();




/* ---------- guildes ---------- */

/* ---------- salles ---------- */
function drawRoomList(id, list, free){
  const box = $(id); box.innerHTML = "";
  if (!list.length) box.append(el("p", "hint", "Aucune salle."));
  list.forEach((r, i) => {
    const d = el("div", "room");
    const kind = roomKindHtml();
    d.innerHTML = free
      ? `<div><label>Salle</label><input type="text" placeholder="haut gauche" data-k="n"></div>
         <div><label>Guilde (vide = libre)</label><input type="text" placeholder="Noxia" data-k="owner"></div>
         <div><label>Record DMG ou /s</label><input type="text" placeholder="1.89B" data-k="val"></div>
         <button class="x" type="button" aria-label="Retirer la salle">×</button>${kind}`
      : `<div><label>Salle</label><input type="text" placeholder="centre" data-k="n"></div>
         <div><label>Record DMG</label><input type="text" placeholder="2.91B" data-k="dmg"></div>
         <div></div>
         <button class="x" type="button" aria-label="Retirer la salle">×</button>${kind}`;
    d.querySelectorAll("[data-k]").forEach(inp => {
      const k = inp.dataset.k;
      inp.value = k === "val" ? (r.owner ? r.dmg : r.rate) ?? "" : k === "buff" ? (r.buff ? "1" : "") : r[k] ?? "";
      inp.oninput = inp.onchange = () => {
        if (k === "val") { if (r.owner) { r.dmg = inp.value; r.rate = ""; } else { r.rate = inp.value; r.dmg = ""; } }
        else if (k === "buff") r.buff = inp.value === "1";
        else r[k] = inp.value;
        save();
      };
    });
    d.querySelector(".x").onclick = () => { list.splice(i, 1); save(); drawRoomList(id, list, free); };
    box.append(d);
  });
}
function drawRooms(){ drawRoomList("#rooms", st.rooms, true); drawRoomList("#ourRooms", st.ourRooms, false); }
$("#addRoom").onclick = () => { st.rooms.push({ n:"", owner:"", dmg:"", rate:"", type:"", buff:false }); save(); drawRooms(); };
$("#addOurRoom").onclick = () => { st.ourRooms.push({ n:"", owner:"nous", dmg:"", rate:"", type:"", buff:false }); save(); drawRooms(); };

/* ---------- champs simples ---------- */
const FIELDS = ["th","tm","attempts","ourDmg"];
function drawAll(){ drawGuilds(); drawRooms(); drawFields(FIELDS); }
drawAll();

/* ---------- lecture d'une capture (../commun/script.js) ---------- */
$("#shot").onchange = e => { const f = e.target.files[0]; if (f) readCapture(f, conseil.dcRead, () => { drawAll(); }); };

/* ---------- plan (moteur commun : conseil.dcPlanJs) ---------- */
$("#run").onclick = () => {
  const out = $("#out"); out.innerHTML = "";
  const r = conseil.dcPlanJs(st);
  if (showErrors(out, r.errs)) return;
  const v = el("div", "verdict" + (r.good ? " win" : ""));
  v.append(el("h2", null, r.title));
  r.lines.forEach(x => v.append(el("p", null, x)));
  out.append(v, planBox(r.steps, "", r.text), listBox("Défense", r.defense));
  out.scrollIntoView({ behavior: "smooth" });
};
