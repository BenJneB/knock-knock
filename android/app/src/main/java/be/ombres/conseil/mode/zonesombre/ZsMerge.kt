package be.ombres.conseil.mode.zonesombre

import be.ombres.conseil.ocr.fold
import be.ombres.conseil.ocr.levenshtein

/** Ce qu'une capture a apporté : les valeurs mises à jour, et un résumé pour l'utilisateur. */
data class ZsMergeResult(val input: PlanInput, val summary: String, val missing: List<String>, val notes: List<String> = emptyList())

private fun key(name: String) = fold(name).filter { it.isLetterOrDigit() }

/** Vrai si [read] est vraisemblablement le nom [ours], mal lu ou non. */
fun sameGuild(read: String, ours: String): Boolean {
    val a = key(read)
    val b = key(ours)
    if (a.isEmpty() || b.isEmpty()) return false
    return a == b || levenshtein(a, b) <= 2
}

private val CARD_NAMES = mapOf("em" to "Esprit malveillant", "sa" to "Sape d’âme", "gi" to "Garde immaculée", "ss" to "Sanctuaire sacré")

/**
 * Reporte une lecture dans les valeurs mémorisées. Seuls les champs lus sont remplacés :
 * une valeur non lue garde sa valeur précédente, sauf si la guilde de la colonne a changé (nouveau match).
 */
fun merge(input: PlanInput, r: ZsReading, ourGuild: String): ZsMergeResult {
    var st = input
    val missing = mutableListOf<String>()
    val parts = mutableListOf<String>()
    val notes = mutableListOf<String>()

    if (r.guilds.size == 3) {
        // Sur le coffre, le bandeau est assombri et les noms souvent mal lus : seule la carte du match
        // peut signaler un nouveau match, et les noms déjà connus y sont gardés.
        val chest = r.kind == ZsKind.CHEST
        val newMatch = !chest && r.guilds.indices.any { i ->
            val n = r.guilds[i].name
            n != null && input.guilds.getOrNull(i)?.n?.let { it.isNotEmpty() && !sameGuild(n, it) } == true
        }
        val guilds = r.guilds.mapIndexedTo(mutableListOf()) { i, g ->
            val prev = if (newMatch) GuildIn() else input.guilds.getOrElse(i) { GuildIn() }
            if (g.name == null) missing.add("nom de la guilde ${i + 1}")
            val label = g.name ?: prev.n.ifEmpty { "guilde ${i + 1}" }
            if (g.soul == null) missing.add("Âme de $label")
            if (g.rate == null) missing.add("efficacité de $label")
            if (g.rooms == null && r.kind == ZsKind.MAP) missing.add("salles de $label")
            GuildIn(
                n = if (chest && prev.n.isNotEmpty()) prev.n else g.name ?: prev.n,
                a = g.soul ?: prev.a,
                r = g.rate ?: prev.r,
                s = g.rooms?.toString() ?: prev.s,
            )
        }
        var me = guilds.indexOfFirst { sameGuild(it.n, ourGuild) }
        if (me < 0) {
            // Nom illisible : si une seule colonne n'a pas de nom lu, c'est sans doute la nôtre.
            val unnamed = r.guilds.indices.filter { r.guilds[it].name == null }
            if (unnamed.size == 1) {
                me = unnamed[0]
                guilds[me] = guilds[me].copy(n = ourGuild)
                missing[missing.indexOf("nom de la guilde ${me + 1}")] = "votre guilde : supposée en colonne ${me + 1}"
            } else missing.add("votre guilde ($ourGuild) dans le bandeau")
        }
        st = st.copy(guilds = guilds, me = if (me >= 0) me else st.me)
        if (newMatch) st = st.copy(rooms = emptyList(), shield = true, free = "0")
        parts.add("scores")
    }

    if (r.hours != null && r.minutes != null) {
        st = st.copy(th = r.hours.toString(), tm = r.minutes.toString())
        parts.add("temps restant")
    } else if (r.kind != ZsKind.UNKNOWN) missing.add("temps restant")

    if (r.kind == ZsKind.CHEST) {
        r.cards["em"]?.let { st = st.copy(em = it.toString()) }
        r.cards["sa"]?.let { st = st.copy(sa = it.toString()) }
        r.cards["gi"]?.let { st = st.copy(gi = it.toString()) }
        r.cards["ss"]?.let { st = st.copy(ss = it.toString()) }
        CARD_NAMES.forEach { (k, n) -> if (r.cards[k] == null) missing.add("quantité de $n") }
        if (r.today != null) st = st.copy(today = r.today.toString()) else missing.add("cartes disponibles aujourd’hui")
        parts.add("cartes")
    }

    if (r.kind == ZsKind.MAP && r.guilds.size == 3) {
        if (r.rooms.isEmpty()) missing.add("salles sur la carte")
        else {
            st = mergeRooms(st, r, notes)
            parts.add("salles")
        }
    }

    val summary = when {
        r.kind == ZsKind.UNKNOWN -> "Écran non reconnu : ouvrez la carte du match ou le coffre de cartes, puis réessayez."
        parts.isEmpty() -> "Rien n’a pu être lu sur cet écran."
        r.kind == ZsKind.MAP -> "Carte du match lue : ${parts.joinToString(", ")}."
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

private fun short(name: String) = name.substringAfter('-').ifEmpty { name }

/** Salles lues sur la carte → salles du rival, bouclier de notre salle principale, salles libres. */
private fun mergeRooms(input: PlanInput, r: ZsReading, notes: MutableList<String>): PlanInput {
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
    }
    return st
}
