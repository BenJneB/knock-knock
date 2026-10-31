package be.ombres.conseil.mode.capitale

import be.ombres.conseil.game.GuildIn
import be.ombres.conseil.game.Step
import be.ombres.conseil.game.dur
import be.ombres.conseil.game.fmt
import be.ombres.conseil.game.int
import be.ombres.conseil.game.parseNum
import be.ombres.conseil.map.short
import be.ombres.conseil.ocr.fold

/*
 * Moteur de stratégie du mode Défense de la capitale (règles : captures/regle-capitale*.jpeg).
 * - Chaque jour, les 3 guildes du groupe sont classées au total d'Âme (récompenses et Trophées selon la place).
 * - Chaque joueur a 1 tentative de défi gratuite et 10 payantes par jour ; un défi en consomme une, gagné ou perdu.
 * - Prendre une salle : infliger plus de DMG que son record de défense (le DMG affiché sur l'étiquette,
 *   c'est-à-dire le meilleur DMG de ses occupants). Aller au lit dans une salle de sa guilde est gratuit.
 * - Chaque joueur présent dans une salle produit son efficacité : remplir les lits est le premier gain.
 * Pas de cartes d'exorcisme dans ce mode.
 */

/** Salle vue sur la carte : salle d'une autre guilde ([owner], record [dmg]) ou salle libre (owner vide, efficacité [rate]). */
data class DcRoom(val n: String = "", val owner: String = "", val dmg: String = "", val rate: String = "")

data class DcInput(
    val guilds: List<GuildIn> = listOf(GuildIn(n = "s40-Ombres"), GuildIn(), GuildIn()),
    val me: Int = 0,
    val th: String = "",
    val tm: String = "",
    /** Tentatives de défi que la guilde compte jouer (1 gratuite par joueur, jusqu'à 10 payantes). */
    val attempts: String = "1",
    /** Plus gros DMG qu'un joueur de la guilde peut infliger (par défaut : record de notre meilleure salle). */
    val ourDmg: String = "",
    /** Salles des autres guildes et salles libres. */
    val rooms: List<DcRoom> = emptyList(),
    /** Nos salles, avec leur record de défense. */
    val ourRooms: List<DcRoom> = emptyList(),
)

sealed interface DcResult

data class DcErrors(val errs: List<String>) : DcResult

data class DcRank(val name: String, val final: Double, val us: Boolean)

data class DcOk(
    val T: Int,
    /** Classement prévu au rythme actuel, puis avec le plan (du premier au dernier). */
    val before: List<DcRank>,
    val after: List<DcRank>,
    val steps: List<Step>,
    val defense: List<String>,
    val unused: Int,
) : DcResult {
    val rankBefore get() = before.indexOfFirst { it.us } + 1
    val rankAfter get() = after.indexOfFirst { it.us } + 1
}

private fun key(s: String) = fold(s).filter { it.isLetterOrDigit() }

/** Guilde désignée par [owner] (nom complet ou court, comme sur les étiquettes de salle). */
fun guildIndex(owner: String, guilds: List<GuildIn>): Int? {
    val k = key(owner)
    if (k.isEmpty()) return null
    return guilds.indices.firstOrNull { i -> key(guilds[i].n).let { it.isNotEmpty() && (it == k || it.endsWith(k) || k.endsWith(key(short(guilds[i].n)))) } }
}

private class Option(val room: DcRoom, val owner: Int?, val gain: Double, val loss: Double)

fun dcPlan(st: DcInput): DcResult {
    val names = st.guilds.map { it.n.ifEmpty { "?" } }
    val souls = st.guilds.map { parseNum(it.a) }
    val rates = st.guilds.map { parseNum(it.r) }
    val T = int(st.th) * 3600 + int(st.tm) * 60
    val errs = mutableListOf<String>()
    if (souls[st.me] == null || rates[st.me] == null) errs.add("Remplis l’Âme et la production de votre guilde.")
    val others = st.guilds.indices.filter { it != st.me && souls[it] != null && rates[it] != null }
    if (others.isEmpty()) errs.add("Remplis au moins une guilde adverse (Âme et production).")
    if (T == 0) errs.add("Indique le temps restant.")
    if (errs.isNotEmpty()) return DcErrors(errs)

    val playing = (listOf(st.me) + others)
    val final = playing.associateWith { souls[it]!! + rates[it]!! * T }.toMutableMap()
    fun ranking() = playing.sortedByDescending { final[it] }.map { DcRank(names[it], final[it]!!, it == st.me) }
    val before = ranking()

    // Guilde à viser : celle juste devant nous (pour monter), ou la deuxième si nous sommes premiers
    // (pour garder la place). Lui prendre une salle compte double : notre gain et sa perte.
    val order = playing.sortedByDescending { final[it] }
    val pos = order.indexOf(st.me)
    val target = order.getOrNull(pos - 1) ?: order.getOrNull(pos + 1)

    fun roomValue(g: Int): Double {
        val count = int(st.guilds[g].s).takeIf { it > 0 } ?: st.rooms.count { guildIndex(it.owner, st.guilds) == g }.coerceAtLeast(1)
        return rates[g]!! / count
    }
    val ourDmg = parseNum(st.ourDmg)
    val options = st.rooms.mapNotNull { room ->
        if (room.owner.isEmpty()) {
            val rate = parseNum(room.rate) ?: return@mapNotNull null
            Option(room, null, rate * T, 0.0)
        } else {
            val g = guildIndex(room.owner, st.guilds) ?: return@mapNotNull null
            if (g == st.me || rates[g] == null) return@mapNotNull null
            val dmg = parseNum(room.dmg)
            if (ourDmg != null && dmg != null && dmg >= ourDmg) return@mapNotNull null // record trop haut
            val v = roomValue(g) * T
            Option(room, g, v, if (g == target) v else 0.0)
        }
    }.sortedByDescending { it.gain + it.loss }

    val steps = mutableListOf(
        Step(
            "Remplir les lits de vos salles",
            "Aller au lit dans une salle de votre guilde ne coûte aucune tentative, et chaque joueur présent produit l’efficacité de la salle. Les joueurs sans salle d’abord.",
            "",
        )
    )
    val budget = int(st.attempts)
    val chosen = options.take(budget)
    for (o in chosen) {
        final[st.me] = final[st.me]!! + o.gain
        if (o.owner != null) final[o.owner] = final[o.owner]!! - roomValue(o.owner) * T
        if (o.owner == null) {
            steps.add(Step("Occuper la salle libre ${o.room.n}", "Un défi suffit (${o.room.rate}/s par joueur) ; d’autres joueurs peuvent ensuite y aller au lit sans tentative.", "1 tentative"))
        } else {
            val who = short(names[o.owner])
            val need = if (o.room.dmg.isNotEmpty()) "Record de défense ${o.room.dmg} : envoyez un joueur qui inflige plus de ${o.room.dmg}. " else ""
            val effect = if (o.loss > 0) "Vous gagnez sa production et $who la perd." else "Vous gagnez sa production."
            steps.add(Step("Défier ${o.room.n} ($who)", need + effect, "1 tentative"))
        }
    }

    // Défense : le record d'une salle est le meilleur DMG de ses occupants.
    val defense = mutableListOf<String>()
    val enemyBest = st.rooms.filter { it.owner.isNotEmpty() }
        .groupBy { guildIndex(it.owner, st.guilds) }
        .filterKeys { it != null && it != st.me }
        .mapNotNull { (g, rs) -> rs.mapNotNull { parseNum(it.dmg) }.maxOrNull()?.let { g!! to it } }
    for (room in st.ourRooms) {
        val dmg = parseNum(room.dmg) ?: continue
        val threat = enemyBest.filter { it.second > dmg }.maxByOrNull { it.second } ?: continue
        defense.add("Votre salle ${room.n} (record ${room.dmg}) peut être prise par ${short(names[threat.first])} (meilleur record ${fmtDmg(threat.second)}) : placez-y votre joueur le plus fort, son DMG deviendra le record.")
    }
    defense.add("Le record de défense d’une salle est le meilleur DMG de ses occupants : gardez vos joueurs les plus forts dans vos meilleures salles.")
    val unused = (budget - chosen.size).coerceAtLeast(0)
    if (unused > 0 && options.size < budget) defense.add("$unused tentative(s) sans cible utile : inutile de jouer les tentatives payantes.")
    if (ourDmg == null && st.rooms.any { it.owner.isNotEmpty() }) defense.add("Indiquez le meilleur DMG de vos joueurs pour écarter les salles impossibles à prendre.")

    return DcOk(T, before, ranking(), steps, defense, unused)
}

/** DMG tel qu'affiché dans le jeu (2.91B, 863.23M). */
fun fmtDmg(v: Double): String = when {
    v >= 1e9 -> be.ombres.conseil.game.fixed(v / 1e9, 2) + "B"
    v >= 1e6 -> be.ombres.conseil.game.fixed(v / 1e6, 2) + "M"
    else -> fmt(v)
}

private fun place(n: Int) = if (n == 1) "1re" else "${n}e"

data class DcVerdict(val good: Boolean, val title: String, val lines: List<String>)

fun dcVerdict(r: DcOk): DcVerdict {
    fun list(rs: List<DcRank>) = rs.mapIndexed { i, g -> "${place(i + 1)} ${g.name} (${fmt(g.final)})" }.joinToString(", ")
    val lines = mutableListOf("Au rythme actuel : ${list(r.before)}.")
    if (r.steps.size > 1) lines.add(
        if (r.rankAfter < r.rankBefore) "Avec le plan, vous passez ${place(r.rankAfter)} : ${list(r.after)}."
        else "Avec le plan : ${list(r.after)}."
    )
    val above = r.after.getOrNull(r.rankAfter - 2)
    val us = r.after[r.rankAfter - 1]
    if (above != null && r.T > 0) {
        val perSecond = (above.final - us.final) / r.T
        lines.add("Il manque ${fmt(above.final - us.final)} pour dépasser ${above.name}, soit ${fmt(perSecond)}/s de plus jusqu’à la fin (${dur(r.T.toDouble())}).")
    }
    return DcVerdict(r.rankAfter == 1, "${place(r.rankAfter)} place", lines)
}

/** Plan en quelques lignes, à coller dans le chat de guilde. */
fun dcPlanText(r: DcOk): String {
    val v = dcVerdict(r)
    val out = mutableListOf("Conseil de guerre — Défense de la capitale (fin dans ${r.T / 3600} h ${(r.T % 3600 / 60).toString().padStart(2, '0')})", "${v.title} : ${v.lines.joinToString(" ")}", "Plan :")
    r.steps.forEachIndexed { i, s -> out.add("${i + 1}. ${s.t}${if (s.c.isNotEmpty()) " (${s.c})" else ""}") }
    return out.joinToString("\n")
}
