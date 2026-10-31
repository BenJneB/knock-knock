package be.ombres.conseil.mode.zonesombre

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Port fidèle du moteur plan() de web/conseil-zone-sombre-chefs.html.
 * Les champs restent des chaînes, comme dans la version web, pour que les deux
 * versions interprètent les saisies de la même façon et donnent le même plan.
 * Toute modification ici doit être reportée dans la version JavaScript (et inversement).
 */

data class GuildIn(val n: String = "", val a: String = "", val r: String = "", val s: String = "")

data class RoomIn(val n: String = "", val sh: Boolean = false, val b: Boolean = true)

data class PlanInput(
    val guilds: List<GuildIn> = listOf(GuildIn(n = "s40-Ombres"), GuildIn(), GuildIn()),
    val me: Int = 0,
    val th: String = "",
    val tm: String = "",
    val round: String = "1", // "1", "2", "3" ou "f"
    val em: String = "",
    val sa: String = "",
    val gi: String = "",
    val ss: String = "",
    val today: String = "",
    val rooms: List<RoomIn> = emptyList(),
    val shield: Boolean = true,
    val free: String = "0",
    val freeV: String = "3",
    val sapeP: String = "25",
)

data class Guild(val n: String, val a: Double?, val r: Double?, val s: Int)

data class Step(val t: String, val d: String, val c: String)

sealed interface PlanResult

data class PlanErrors(val errs: List<String>) : PlanResult

data class PlanOk(
    val me: Guild,
    val rival: Guild,
    val T: Int,
    val room: Double,
    val margin0: Double,
    val margin: Double,
    val myR: Double,
    val enR: Double,
    val steps: List<Step>,
    val threats: List<String>,
    val keep: List<String>,
    val overtake: String?,
) : PlanResult

/* ---------- helpers (mêmes règles que parseNum / int / fmt / dur en JS) ---------- */

private val FLOAT_PREFIX = Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")
private val INT_PREFIX = Regex("^[+-]?\\d+")

/** Équivalent de parseFloat : lit le plus long préfixe numérique. */
private fun jsParseFloat(s: String): Double? = FLOAT_PREFIX.find(s)?.value?.toDoubleOrNull()

fun parseNum(v: String?): Double? {
    if (v == null) return null
    var s = v.trim().replace(Regex("\\s"), "").replaceFirst(",", ".").uppercase(Locale.ROOT)
    if (s.isEmpty()) return null
    var m = 1.0
    when {
        s.endsWith("K") -> { m = 1e3; s = s.dropLast(1) }
        s.endsWith("M") -> { m = 1e6; s = s.dropLast(1) }
        s.endsWith("B") -> { m = 1e9; s = s.dropLast(1) }
    }
    val x = jsParseFloat(s) ?: return null
    return if (x.isFinite()) x * m else null
}

fun int(v: String?): Int {
    val x = INT_PREFIX.find(v?.trimStart() ?: "")?.value?.toIntOrNull() ?: return 0
    return if (x > 0) x else 0
}

/** Number.prototype.toFixed de JS : arrondit la valeur binaire exacte (646.05 → "646.0"). */
private fun fixed(n: Double, d: Int): String {
    val s = BigDecimal(abs(n)).setScale(d, RoundingMode.HALF_UP).toPlainString()
    return if (n < 0) "-$s" else s
}

fun fmt(n: Double): String = when {
    !n.isFinite() -> "?"
    abs(n) >= 1e6 -> fixed(n / 1e6, 2) + "M"
    abs(n) >= 1e3 -> fixed(n / 1e3, 1) + "K"
    else -> jsRound(n).toString()
}

/** Math.round de JS : arrondi vers +∞ pour les demis. */
private fun jsRound(x: Double): Long = floor(x + 0.5).toLong()

fun dur(s: Double): String {
    if (!s.isFinite() || s < 0) return "jamais"
    val h = floor(s / 3600).toLong()
    val m = jsRound((s % 3600) / 60)
    return if (h != 0L) "$h h ${m.toString().padStart(2, '0')}" else "$m min"
}

/* ---------- moteur de stratégie ---------- */

private class Room(val i: Int, val n: String, val sh: Boolean, val b: Boolean, var used: Boolean = false)

private class Candidate(val r: Room?, val cost: LinkedHashMap<String, Int>, val gain: Double, val kind: String)

fun plan(st: PlanInput): PlanResult {
    val G = st.guilds.map { g -> Guild(g.n.ifEmpty { "?" }, parseNum(g.a), parseNum(g.r), int(g.s)) }
    val me = G[st.me]
    val T = int(st.th) * 3600 + int(st.tm) * 60
    val errs = mutableListOf<String>()
    if (me.a == null || me.r == null) errs.add("Remplis l’Âme et la production de votre guilde.")
    val others = G.filterIndexed { i, g -> i != st.me && g.a != null && g.r != null }
    if (others.isEmpty()) errs.add("Remplis au moins une guilde adverse (Âme et production).")
    if (T == 0) errs.add("Indique le temps restant.")
    if (errs.isNotEmpty()) return PlanErrors(errs)

    fun fin(g: Guild) = g.a!! + g.r!! * T
    val rival = others.sortedByDescending { fin(it) }[0]
    val rivalR = rival.r!!
    val room = if (rival.s != 0) rivalR / rival.s
    else rivalR / max(1, if (st.rooms.isEmpty()) 4 else st.rooms.size)
    val freeV = parseNum(st.freeV) ?: 3.0
    val p = (parseNum(st.sapeP) ?: 25.0) / 100
    val finale = st.round == "f"

    var myR = me.r!!
    var enR = rivalR
    val margin0 = fin(me) - fin(rival)
    var margin = margin0
    val stock = linkedMapOf("em" to int(st.em), "sa" to int(st.sa), "gi" to int(st.gi), "ss" to int(st.ss))
    var budget = min(int(st.today), stock.values.sum())
    val steps = mutableListOf<Step>()
    val threats = mutableListOf<String>()
    val keep = mutableListOf<String>()
    fun use(c: String) { stock[c] = stock[c]!! - 1; budget-- }
    val safety = room * 3600 // marge : une salle rivale pendant 1 h

    // 1. Défense de la salle principale
    var reserveGI = false
    if (!st.shield) {
        if (stock["gi"]!! > 0 && budget > 0) {
            use("gi")
            steps.add(Step("Mettre un bouclier sur notre salle principale", "Sans bouclier, le rival peut la vider avec un Esprit malveillant ou y placer un voleur. C’est votre source de production : à protéger avant tout.", "1 Garde immaculée"))
        } else threats.add("Votre salle principale n’a pas de bouclier et vous ne pouvez pas en remettre aujourd’hui : gardez des joueurs prêts à la reprendre si elle est vidée.")
    } else if (stock["gi"]!! > 0 && budget > 1) { reserveGI = true; budget-- }

    // 2. Candidats offensifs
    val rooms = st.rooms.mapIndexed { i, r -> Room(i, r.n.ifEmpty { "salle ${i + 1}" }, r.sh, r.b) }
    val take = 2 * room * T
    val sapeGain = 2 * p * room * min(7200, T)
    fun need() = (if (finale) safety * 2 else safety) - margin
    var freeLeft = int(st.free)

    fun cost(vararg kv: Pair<String, Int>) = linkedMapOf(*kv)
    fun candidates(): List<Candidate> {
        val c = mutableListOf<Candidate>()
        rooms.filter { !it.used }.forEach { r ->
            if (r.b && !r.sh) c.add(Candidate(r, cost(), take, "combat"))
            if (r.b && r.sh) c.add(Candidate(r, cost("ss" to 1), take, "ss-combat"))
            if (!r.b && !r.sh) c.add(Candidate(r, cost("em" to 1), take, "em"))
            if (!r.b && r.sh) c.add(Candidate(r, cost("ss" to 1, "em" to 1), take, "ss-em"))
            if (!r.sh) c.add(Candidate(r, cost("sa" to 1), sapeGain, "sape"))
        }
        if (freeLeft > 0) c.add(Candidate(null, cost(), freeV * T, "free"))
        return c.filter { x -> x.cost.all { (k, v) -> stock[k]!! >= v } && x.cost.values.sum() <= budget }
    }
    fun score(x: Candidate): Double {
        val n = x.cost.values.sum()
        return if (n == 0) 1e15 + x.gain else x.gain / n
    }

    while (need() > 0) {
        val c = candidates().sortedByDescending { score(it) }.firstOrNull() ?: break
        c.cost.forEach { (k, v) -> repeat(v) { use(k) } }
        margin += c.gain
        c.r?.used = true
        if (c.kind == "free") { freeLeft--; myR += freeV; steps.add(Step("Occuper une salle libre", "Production gratuite : +${jsNum(freeV)}/s pour vous.", "")); continue }
        val n = c.r!!.n
        if (c.kind == "sape") { steps.add(Step("Sape d’âme sur $n", "Le voleur prend une partie de leur production pendant 2 h, que vous récupérez.", "1 Sape d’âme")); continue }
        myR += room; enR -= room
        when (c.kind) {
            "combat" -> steps.add(Step("Attaquer $n en combat", "Salle battable sans bouclier : aucune carte nécessaire. Vous gagnez sa production et le rival la perd.", ""))
            "ss-combat" -> steps.add(Step("Retirer le bouclier de $n, puis l’attaquer", "Le Sanctuaire sacré supprime leur bouclier, vos joueurs prennent ensuite la salle en combat.", "1 Sanctuaire sacré"))
            "em" -> steps.add(Step("Vider $n et l’occuper tout de suite", "Trop solide pour un combat : l’Esprit malveillant la vide. Prévenez vos joueurs avant, pour qu’ils l’occupent avant le rival.", "1 Esprit malveillant"))
            "ss-em" -> steps.add(Step("Retirer le bouclier de $n, la vider et l’occuper", "Sanctuaire sacré pour faire tomber le bouclier, puis Esprit malveillant. Vos joueurs doivent être prêts à entrer aussitôt.", "1 Sanctuaire sacré + 1 Esprit malveillant"))
        }
    }

    // 3. En finale, les cartes restantes ne servent plus à rien : on vide les Sapes
    if (finale) {
        rooms.filter { !it.used && !it.sh }.forEach { r ->
            if (stock["sa"]!! > 0 && budget > 0) { use("sa"); r.used = true; margin += sapeGain; steps.add(Step("Sape d’âme sur ${r.n}", "C’est la finale : les cartes non jouées seront perdues, autant les utiliser.", "1 Sape d’âme")) }
        }
    }

    if (reserveGI) { budget++; keep.add("1 Garde immaculée en réserve aujourd’hui : si le rival fait tomber votre bouclier, remettez-le aussitôt.") }
    // (plus aucune carte jouable aujourd’hui : inutile de proposer un Sanctuaire sacré)
    if (stock["ss"]!! > 0 && budget > 0) threats.add("Si votre production baisse sans raison, le rival a peut-être posé un voleur : un Sanctuaire sacré sur votre salle l’élimine.")
    threats.add("Le rival peut faire tomber votre bouclier (Sanctuaire sacré) puis vider votre salle (Esprit malveillant). Surveillez la carte et gardez des joueurs prêts.")
    if (rooms.isEmpty() && margin0 < 0) threats.add(0, "Ajoute les salles du rival pour obtenir un plan d’attaque précis.")

    val left = "${stock["em"]} Esprit malveillant, ${stock["sa"]} Sape d’âme, ${stock["gi"]} Garde immaculée, ${stock["ss"]} Sanctuaire sacré"
    if (!finale) keep.add("Il vous restera : $left. Ces cartes ne se renouvellent pas pendant la phase éliminatoire.")

    val diff = myR - enR
    val gap = rival.a!! - me.a!!
    val overtake = if (gap > 0 && diff > 0) dur(gap / diff) else null
    return PlanOk(me, rival, T, room, margin0, margin, myR, enR, steps, threats, keep, overtake)
}

/** Affichage d'un nombre comme String(n) en JS (3.0 → "3", 2.5 → "2.5"). */
fun jsNum(x: Double): String =
    if (x == floor(x) && abs(x) < 1e15) x.toLong().toString() else x.toString()

/* ---------- textes du verdict (repris du rendu de la version web) ---------- */

data class Verdict(val win: Boolean, val title: String, val lines: List<String>, val hint: String)

fun verdict(r: PlanOk): Verdict {
    val win0 = r.margin0 > 0
    val win = r.margin > 0
    val title = if (win0) "En tête" else if (win) "Rattrapable" else "Très difficile"
    val lines = mutableListOf(
        if (win0) "Au rythme actuel, vous finissez devant ${r.rival.n} avec ${fmt(r.margin0)} d’avance."
        else "Au rythme actuel, ${r.rival.n} finit devant avec ${fmt(-r.margin0)} d’avance."
    )
    if (r.steps.isNotEmpty()) lines.add(
        if (win) "Avec le plan : vous finissez devant avec environ ${fmt(r.margin)} d’avance (${fixed(r.myR, 1)}/s contre ${fixed(r.enR, 1)}/s)${if (r.overtake != null) ", et vous repassez devant dans ${r.overtake}" else ""}."
        else "Même avec le plan, il manquerait environ ${fmt(-r.margin)}. Il faut prendre plus de salles que prévu ou trouver d’autres cibles battables."
    )
    return Verdict(win, title, lines, "Estimation : une salle du rival vaut environ ${fixed(r.room, 1)}/s.")
}

fun emptyPlanText(r: PlanOk): String =
    if (r.margin0 > 0) "Rien à attaquer : tenez vos positions et économisez vos cartes."
    else "Aucune action possible avec les salles et cartes indiquées."
