package be.ombres.conseil.mode.zonesombre

import be.ombres.conseil.map.*
import be.ombres.conseil.game.*
import kotlin.math.abs
import kotlin.math.floor

/** Une lecture de la carte, datée, gardée pour mesurer ce qui évolue d'une capture à l'autre. */
data class ZsSnapshot(
    /** Heure de la capture, en millisecondes. */
    val time: Long,
    /** Production (/s) et nombre de salles de chaque guilde, tels que saisis ou lus. */
    val rates: List<String>,
    val roomCounts: List<String>,
    val rooms: List<RoomRead>,
)

/** Part de la production d'une salle prise par un voleur, mesurée entre deux captures. */
data class SapeMeasure(val guild: Int, val pos: String, val share: Double, val minutes: Int)

/**
 * Mesure la part volée par chaque voleur vu sur les deux captures : le montant « Volé » augmente
 * de part × production de la salle chaque seconde. La production d'une salle est estimée comme
 * dans plan() : production de la guilde / nombre de salles.
 */
fun measureSape(prev: ZsSnapshot, cur: ZsSnapshot): List<SapeMeasure> {
    val seconds = (cur.time - prev.time) / 1000.0
    // Moins de 2 min : les montants affichés (3 chiffres) ne bougent pas assez ; plus de 2 h : la Sape est finie.
    if (seconds < 120 || seconds > 7200) return emptyList()
    return cur.rooms.mapNotNull { room ->
        val g = room.guild ?: return@mapNotNull null
        val now = parseNum(room.stolen) ?: return@mapNotNull null
        val before = prev.rooms.firstOrNull { it.guild == g && abs(it.x - room.x) < 60 && abs(it.y - room.y) < 60 }
            ?.let { parseNum(it.stolen) } ?: return@mapNotNull null
        val rate = parseNum(cur.rates.getOrNull(g)) ?: return@mapNotNull null
        val count = int(cur.roomCounts.getOrNull(g)).takeIf { it > 0 } ?: return@mapNotNull null
        val roomRate = rate / count
        val gained = now - before
        if (gained <= 0 || roomRate <= 0) return@mapNotNull null
        val share = gained / seconds / roomRate
        if (share > 1.0) return@mapNotNull null // incohérent (mauvaise lecture, autre voleur…)
        SapeMeasure(g, room.pos, share, (seconds / 60).toInt())
    }
}

/** Part volée à retenir (en %, arrondie) d'après les mesures ; null s'il n'y en a pas. */
fun measuredSapePercent(measures: List<SapeMeasure>): String? =
    if (measures.isEmpty()) null else jsNum(floor(measures.map { it.share }.average() * 100 + 0.5))
