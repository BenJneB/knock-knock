package be.ombres.conseil.map

import be.ombres.conseil.game.GuildIn
import be.ombres.conseil.game.fmt
import be.ombres.conseil.game.parseNum
import kotlin.math.abs

/*
 * Production mesurée : entre deux captures, l'Âme gagnée par chaque guilde divisée par le temps écoulé.
 * L'efficacité affichée est instantanée ; la mesure dit ce que la guilde gagne vraiment (salles prises ou perdues,
 * joueurs qui arrivent ou partent, voleurs…).
 */

/** Âme et efficacité des guildes, lues à un instant donné (millisecondes). */
data class SoulSnapshot(val time: Long, val guilds: List<GuildIn>)

data class MeasuredRate(val name: String, val measured: Double, val shown: Double?, val minutes: Int)

/** Vitesse de gain de chaque guilde présente dans les deux lectures (de 2 min à 3 h d'écart). */
fun measureRates(prev: SoulSnapshot, cur: SoulSnapshot): List<MeasuredRate> {
    val seconds = (cur.time - prev.time) / 1000.0
    if (seconds < 120 || seconds > 3 * 3600) return emptyList()
    return cur.guilds.mapNotNull { g ->
        val now = parseNum(g.a) ?: return@mapNotNull null
        val before = prev.guilds.firstOrNull { it.n.isNotEmpty() && sameGuild(it.n, g.n) }?.let { parseNum(it.a) } ?: return@mapNotNull null
        val gained = now - before
        if (gained < 0) return@mapNotNull null // nouveau match ou mauvaise lecture
        MeasuredRate(g.n, gained / seconds, parseNum(g.r), (seconds / 60).toInt())
    }
}

/** Remarques pour les guildes dont la production mesurée s'écarte de plus de 15 % de l'efficacité affichée. */
fun rateNotes(rates: List<MeasuredRate>): List<String> = rates.mapNotNull { m ->
    val shown = m.shown ?: return@mapNotNull null
    if (shown <= 0 || abs(m.measured - shown) / shown <= 0.15) return@mapNotNull null
    val trend = if (m.measured > shown) "plus" else "moins"
    "${short(m.name)} a gagné $trend que son efficacité affichée : ${fmt(m.measured)}/s mesurés sur ${m.minutes} min (affiché ${fmt(shown)}/s)."
}
