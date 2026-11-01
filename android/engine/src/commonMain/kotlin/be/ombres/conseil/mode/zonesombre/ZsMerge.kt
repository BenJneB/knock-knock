package be.ombres.conseil.mode.zonesombre
import be.ombres.conseil.game.int
import be.ombres.conseil.game.jsNum
import be.ombres.conseil.game.parseNum
import be.ombres.conseil.map.MapReading
import be.ombres.conseil.map.ScreenKind
import be.ombres.conseil.map.mergeBanner
import be.ombres.conseil.map.roomCountChecks
import be.ombres.conseil.map.short

/** Ce qu'une capture a apporté : les valeurs mises à jour, et un résumé pour l'utilisateur. */
data class ZsMergeResult(val input: PlanInput, val summary: String, val missing: List<String>, val notes: List<String> = emptyList())

private val CARD_NAMES = mapOf("em" to "Esprit malveillant", "sa" to "Sape d’âme", "gi" to "Garde immaculée", "ss" to "Sanctuaire sacré")

/**
 * Reporte une lecture dans les valeurs mémorisées. Seuls les champs lus sont remplacés :
 * une valeur non lue garde sa valeur précédente, sauf si la guilde de la colonne a changé (nouveau match).
 */
fun merge(input: PlanInput, r: MapReading, ourGuild: String): ZsMergeResult {
    var st = input
    val missing = mutableListOf<String>()
    val parts = mutableListOf<String>()
    val notes = mutableListOf<String>()

    val banner = mergeBanner(st.guilds, st.me, r, ourGuild, missing, parts)
    st = st.copy(guilds = banner.guilds, me = banner.me)
    if (banner.newMatch) st = st.copy(rooms = emptyList(), shield = true, free = "0")
    if (banner.th != null && banner.tm != null) st = st.copy(th = banner.th, tm = banner.tm)

    if (r.kind == ScreenKind.CHEST) {
        r.cards["em"]?.let { st = st.copy(em = it.toString()) }
        r.cards["sa"]?.let { st = st.copy(sa = it.toString()) }
        r.cards["gi"]?.let { st = st.copy(gi = it.toString()) }
        r.cards["ss"]?.let { st = st.copy(ss = it.toString()) }
        CARD_NAMES.forEach { (k, n) -> if (r.cards[k] == null) missing.add("quantité de $n") }
        if (r.today != null) st = st.copy(today = r.today.toString()) else missing.add("cartes disponibles aujourd’hui")
        parts.add("cartes")
    }

    if (r.kind == ScreenKind.MAP && r.guilds.size == 3) {
        if (r.rooms.isEmpty()) missing.add("salles sur la carte")
        else {
            st = mergeRooms(st, r, notes)
            parts.add("salles")
            missing.addAll(roomCountChecks(st.guilds.map { it.n }, r))
        }
    }

    val summary = when {
        r.kind == ScreenKind.UNKNOWN -> "Écran non reconnu : ouvrez la carte du match ou le coffre de cartes, puis réessayez."
        parts.isEmpty() -> "Rien n’a pu être lu sur cet écran."
        r.kind == ScreenKind.MAP -> "Carte du match lue : ${parts.joinToString(", ")}."
        else -> "Coffre de cartes lu : ${parts.joinToString(", ")}."
    }
    return ZsMergeResult(st, summary, missing, notes)
}

/** Guilde que plan() choisira comme rival (même règle : la guilde adverse qui finit le plus haut). */
fun rivalIndex(st: PlanInput): Int? {
    val T = int(st.th) * 3600 + int(st.tm) * 60
    return st.guilds.indices
        .filter { it != st.me && parseNum(st.guilds[it].a) != null && parseNum(st.guilds[it].r) != null }
        .maxByOrNull { parseNum(st.guilds[it].a)!! + parseNum(st.guilds[it].r)!! * T }
}

/** Salles lues sur la carte → salles du rival, bouclier de notre salle principale, salles libres. */
private fun mergeRooms(input: PlanInput, r: MapReading, notes: MutableList<String>): PlanInput {
    var st = input
    val ours = r.rooms.filter { it.guild == st.me }
    val best = ours.mapNotNull { parseNum(it.dmg) }.maxOrNull()
    // Notre salle principale : celle qui a le plus gros DMG.
    val main = ours.maxByOrNull { parseNum(it.dmg) ?: -1.0 }
    if (main != null) st = st.copy(shield = main.shield)

    val rival = rivalIndex(st)
    if (rival != null) {
        val rooms = r.rooms.filter { it.guild == rival }.map { room ->
            val dmg = parseNum(room.dmg)
            val name = buildString {
                append(room.pos)
                if (room.dmg != null) append(" · DMG ${room.dmg}")
                if (room.stolen != null) append(" · voleur déjà là")
            }
            RoomIn(name, room.shield, b = dmg == null || best == null || dmg < best)
        }
        st = st.copy(rooms = rooms)
        val rn = short(st.guilds[rival].n)
        notes.add("Salles de $rn : ${rooms.size}, dont ${rooms.count { it.sh }} avec bouclier.")
        if (best != null) notes.add("« Battable » est estimé : DMG inférieur à celui de votre meilleure salle (${main?.dmg}). Corrigez si besoin.")
        else notes.add("Battable : DMG de vos salles non lu, toutes les salles du rival sont supposées battables. Corrigez si besoin.")
    }

    val canPlay = int(st.today) > 0 && int(st.ss) > 0
    for (room in ours.filter { it.stolen != null }) {
        notes.add(
            if (canPlay) "Voleur dans votre salle ${room.pos} (${room.stolen} volés) : un Sanctuaire sacré sur cette salle l’élimine."
            else "Voleur dans votre salle ${room.pos} (${room.stolen} volés)."
        )
    }
    if (main != null && !main.shield) notes.add("Votre salle principale (${main.pos}) n’a pas de bouclier.")

    st = st.copy(free = r.free.size.toString())
    if (r.free.isNotEmpty()) {
        val rates = r.free.mapNotNull { it.rate }
        notes.add("Salles libres : ${r.free.size}" + if (rates.isNotEmpty()) " (${rates.joinToString(", ") { "$it/s" }})." else ".")
        // Production d'une salle libre : la moyenne lue sur la carte remplace la valeur par défaut.
        val values = rates.mapNotNull { parseNum(it) }
        if (values.isNotEmpty()) st = st.copy(freeV = jsNum(kotlin.math.floor(values.average() * 10 + 0.5) / 10))
    }
    return st
}
