package be.ombres.conseil.mode.capitale

import be.ombres.conseil.game.parseNum
import be.ombres.conseil.map.MapReading
import be.ombres.conseil.map.ScreenKind
import be.ombres.conseil.map.mergeBanner
import be.ombres.conseil.map.roomCountChecks
import be.ombres.conseil.map.short

/** Ce qu'une capture a apporté au mode Défense de la capitale. */
data class DcMergeResult(val input: DcInput, val summary: String, val missing: List<String>, val notes: List<String>)

/** Reporte une lecture de la carte du match : bandeau, temps, salles (adverses, libres, les nôtres). */
fun dcMerge(input: DcInput, r: MapReading, ourGuild: String): DcMergeResult {
    val missing = mutableListOf<String>()
    val parts = mutableListOf<String>()
    val notes = mutableListOf<String>()
    val banner = mergeBanner(input.guilds, input.me, r, ourGuild, missing, parts)
    var st = input.copy(guilds = banner.guilds, me = banner.me)
    if (banner.newMatch) st = st.copy(rooms = emptyList(), ourRooms = emptyList())
    if (banner.th != null && banner.tm != null) st = st.copy(th = banner.th, tm = banner.tm)

    if (r.kind == ScreenKind.MAP && r.guilds.size == 3) {
        if (r.rooms.isEmpty() && r.free.isEmpty()) missing.add("salles sur la carte")
        else {
            val label = { pos: String, dmg: String? -> if (dmg != null) "$pos · DMG $dmg" else pos }
            val enemy = r.rooms.filter { it.guild != null && it.guild != st.me }
                .map { DcRoom(label(it.pos, it.dmg), short(st.guilds[it.guild!!].n), it.dmg ?: "") }
            val free = r.free.map { DcRoom(it.pos, "", "", it.rate ?: "") }
            val ours = r.rooms.filter { it.guild == st.me }.map { DcRoom(it.pos, short(st.guilds[st.me].n), it.dmg ?: "") }
            st = st.copy(rooms = enemy + free.filter { it.rate.isNotEmpty() }, ourRooms = ours)
            parts.add("salles")
            // Sans aucune étiquette lue, comparer aux salles annoncées n'apprend rien (lecture incomplète).
            if (r.rooms.isNotEmpty()) missing.addAll(roomCountChecks(st.guilds.map { it.n }, r))
            else missing.add("salles des guildes (étiquettes non lues) : à saisir")
            notes.add("Salles adverses : ${enemy.size} ; salles libres : ${free.size}${if (free.any { it.rate.isEmpty() }) " (production illisible pour certaines)" else ""}.")
            // Le record d'une salle est le meilleur DMG de ses occupants : notre meilleur record donne notre force.
            val best = ours.maxByOrNull { parseNum(it.dmg) ?: -1.0 }?.dmg?.takeIf { it.isNotEmpty() }
            if (best != null) {
                st = st.copy(ourDmg = best)
                notes.add("Meilleur DMG de vos joueurs estimé à $best (record de votre meilleure salle). Corrigez si un joueur frappe plus fort.")
            }
        }
    }

    val summary = when {
        r.kind == ScreenKind.UNKNOWN -> "Écran non reconnu : ouvrez la carte du match, puis réessayez."
        parts.isEmpty() -> "Rien n’a pu être lu sur cet écran."
        else -> "Carte du match lue : ${parts.joinToString(", ")}."
    }
    return DcMergeResult(st, summary, missing, notes)
}
