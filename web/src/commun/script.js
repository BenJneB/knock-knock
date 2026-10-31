/*
 * Code commun aux pages web (assemblées par « ./gradlew :engine:webPage » dans android/) :
 * petits utilitaires d'affichage et lecture d'une capture dans le navigateur avec Tesseract.js.
 * Le lecteur et le moteur sont ceux de l'appli (module android/engine, intégré dans chaque page).
 */
const $ = s => document.querySelector(s);

// Pastille de coût, avec l'icône des cartes citées (« 1 Sanctuaire sacré + 1 Esprit malveillant »).
function tag(text){
  const t = el("span", "tag");
  [["Sanctuaire", "sanctuary"], ["Esprit", "spirit"], ["Sape", "thief"], ["Garde", "guard"]].forEach(([w, id]) => {
    if (text.includes(w)) t.insertAdjacentHTML("beforeend", `<svg class="ic" viewBox="0 0 24 24" aria-hidden="true"><use href="#i-${id}"/></svg>`);
  });
  t.append(text); return t;
}
function el(t, c, x){ const e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; }

/* ---------- mémorisation (STORE, DEF et st sont définis par la page) ---------- */
function load(){ try { const s = JSON.parse(localStorage.getItem(STORE)); if (s && s.guilds) return Object.assign(structuredClone(DEF), s); } catch {} return structuredClone(DEF); }
function save(){ try { localStorage.setItem(STORE, JSON.stringify(st)); } catch {} }

/* ---------- les trois guildes ---------- */
function drawGuilds(){
  const box = $("#guilds"); box.innerHTML = "";
  st.guilds.forEach((g, i) => {
    const d = el("div", "guild" + (st.me === i ? " me" : ""));
    d.innerHTML = `
      <div class="guild-head">
        <input type="text" placeholder="Nom de la guilde" aria-label="Nom de la guilde ${i+1}" data-k="n">
        <label class="me-pick"><input type="radio" name="me" ${st.me === i ? "checked" : ""}> Nous</label>
      </div>
      <div class="g3">
        <div><label>Âme</label><input type="text" inputmode="decimal" placeholder="724.44K" data-k="a"></div>
        <div><label>Prod. /s</label><input type="number" inputmode="decimal" data-k="r"></div>
        <div><label>Salles</label><input type="number" inputmode="numeric" min="0" data-k="s"></div>
      </div>`;
    d.querySelectorAll("[data-k]").forEach(inp => { inp.value = g[inp.dataset.k] ?? ""; inp.oninput = () => { g[inp.dataset.k] = inp.value; save(); }; });
    d.querySelector("input[type=radio]").onchange = () => { st.me = i; save(); drawGuilds(); };
    box.append(d);
  });
}

/* ---------- champs simples : identifiants des champs liés à st ---------- */
function drawFields(keys){
  keys.forEach(k => {
    const e = $("#" + k); e.value = st[k] ?? ""; e.oninput = e.onchange = () => { st[k] = e.value; save(); };
  });
}

$("#reset").onclick = () => { if (!confirm("Effacer toutes les valeurs ?")) return; st = structuredClone(DEF); save(); location.reload(); };

/* ---------- affichage du plan ---------- */
/** Affiche les valeurs manquantes ; vrai s'il y en a. */
function showErrors(out, errs){
  if (!errs) return false;
  const e = el("div", "err"); errs.forEach(x => e.append(el("div", null, x))); out.append(e);
  out.scrollIntoView({ behavior: "smooth" });
  return true;
}
/** Bloc « Le plan » : étapes numérotées (ou [emptyText]) et bouton pour copier [text]. */
function planBox(steps, emptyText, text){
  const b = el("div", "box"); b.append(el("h3", null, "Le plan"));
  if (steps.length) {
    const ol = el("ol", "steps");
    steps.forEach(s => { const li = el("li"); li.append(el("strong", null, s.t), el("span", null, s.d)); if (s.c) { li.append(document.createElement("br"), tag(s.c)); } ol.append(li); });
    b.append(ol);
  } else b.append(el("p", null, emptyText));
  const copy = el("button", "ghost", "Copier le plan");
  copy.type = "button";
  copy.onclick = async () => {
    try { await navigator.clipboard.writeText(text); copy.textContent = "Plan copié"; }
    catch { copy.textContent = "Copie impossible"; }
    setTimeout(() => { copy.textContent = "Copier le plan"; }, 2000);
  };
  b.append(copy);
  return b;
}
/** Bloc titré avec une liste à puces. */
function listBox(title, items){
  const x = el("div", "box"); x.append(el("h3", null, title));
  const ul = el("ul", "plain"); items.forEach(i => ul.append(el("li", null, i))); x.append(ul);
  return x;
}

/* ---------- lecture d'une capture ----------
 * Même lecteur que l'appli Android ; seul le moteur de lecture du texte change : Tesseract.js ici, ML Kit sur le
 * téléphone. La page lui fournit la capture, les zones agrandies (mêmes filtres que l'appli) et les pixels.
 */
// Réglages de Tesseract selon ce que contient la zone (indiqué par le lecteur commun). Un moteur par réglage :
// Tesseract garde la liste de caractères autorisés d'un appel à l'autre.
const OCR_MODES = {
  TEXT:   { tessedit_pageseg_mode: "3" },
  DMG:    { tessedit_pageseg_mode: "7",  tessedit_char_whitelist: "DMG:0123456789.,KMB" },
  DIGITS: { tessedit_pageseg_mode: "11", tessedit_char_whitelist: "0123456789" },
};
const ocrWorkers = {};
async function recognize(canvas, left, top, scale, hint = "TEXT"){
  const mode = OCR_MODES[hint] ? hint : "TEXT";
  if (!ocrWorkers[mode]) ocrWorkers[mode] = Tesseract.createWorker("fra").then(async w => { await w.setParameters(OCR_MODES[mode]); return w; });
  const { data } = await (await ocrWorkers[mode]).recognize(canvas);
  // Coordonnées ramenées à la capture d'origine.
  const map = x => ({ t: x.text.trim(), b: [left + x.bbox.x0 / scale, top + x.bbox.y0 / scale, left + x.bbox.x1 / scale, top + x.bbox.y1 / scale].map(Math.floor) });
  return { lines: data.lines.map(map).filter(x => x.t), elements: data.words.map(map).filter(x => x.t) };
}
function zone(img, l, t, r, b, scale, filter){
  const c = document.createElement("canvas"); c.width = (r - l) * scale; c.height = (b - t) * scale;
  const g = c.getContext("2d", { willReadFrequently: true });
  g.imageSmoothingQuality = "high";
  if (filter === "GRAY") g.filter = "grayscale(1)";
  g.drawImage(img, l, t, r - l, b - t, 0, 0, c.width, c.height);
  const thr = filter === "WHITE170" ? 170 : filter === "WHITE200" ? 200 : 0;
  if (thr) { // ne garder que le texte blanc, en noir sur fond blanc
    const d = g.getImageData(0, 0, c.width, c.height), p = d.data;
    for (let i = 0; i < p.length; i += 4) { const v = Math.min(p[i], p[i + 1], p[i + 2]) >= thr ? 0 : 255; p[i] = p[i + 1] = p[i + 2] = v; }
    g.putImageData(d, 0, 0);
  }
  return c;
}
/**
 * Lit une capture avec le lecteur commun : [read] est la fonction du moteur (conseil.zsRead, conseil.dcRead),
 * [refresh] redessine la page après le report des valeurs lues dans st.
 */
async function readCapture(file, read, refresh){
  const out = $("#readOut"); out.innerHTML = "";
  const status = el("p", "hint", "Lecture de la capture…"); out.append(status);
  try {
    const img = await createImageBitmap(file);
    const w = img.width, h = img.height;
    const full = zone(img, 0, 0, w, h, 1, "COLOR");
    const px = full.getContext("2d").getImageData(0, 0, w, h).data;
    const page = await recognize(full, 0, 0, 1);
    let n = 0;
    const screen = {
      width: w, height: h, lines: page.lines, elements: page.elements,
      rgb: (x, y) => { const i = (y * w + x) * 4; return (px[i] << 16) | (px[i + 1] << 8) | px[i + 2]; },
      ocr: req => {
        const l = Math.max(0, Math.min(w - 1, req.left)), t = Math.max(0, Math.min(h - 1, req.top));
        const r = Math.max(l + 1, Math.min(w, req.right)), b = Math.max(t + 1, Math.min(h, req.bottom));
        status.textContent = `Lecture de la capture… (zone ${++n})`;
        return recognize(zone(img, l, t, r, b, req.scale, req.filter), l, t, req.scale, req.hint);
      },
    };
    const ours = (st.guilds[st.me] && st.guilds[st.me].n) || "s40-Ombres";
    const res = await read(screen, st, ours);
    st = Object.assign(st, res.st); save(); refresh();
    out.innerHTML = "";
    const box = el("div", "err");
    box.append(el("strong", null, res.summary));
    const list = [...res.notes, ...res.missing.map(m => `À vérifier : ${m}`)];
    if (list.length) { const ul = el("ul", "plain"); list.forEach(x => ul.append(el("li", null, x))); box.append(ul); }
    out.append(box);
    if (res.kind !== "UNKNOWN") $("#run").click();
  } catch (e) {
    out.innerHTML = ""; out.append(el("div", "err", "Lecture impossible : " + e.message));
  }
}
