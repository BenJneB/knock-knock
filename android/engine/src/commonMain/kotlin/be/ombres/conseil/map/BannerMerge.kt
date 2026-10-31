package be.ombres.conseil.map

import be.ombres.conseil.game.GuildIn
import be.ombres.conseil.ocr.fold
import be.ombres.conseil.ocr.levenshtein

/*
 * Report du bandeau (guildes) et du temps restant d'une lecture, commun aux modes joués sur la carte du match.
 * Seuls les champs lus sont remplacés : une valeur non lue garde sa valeur précédente, sauf si la guilde
 * de la colonne a changé (nouveau match).
 */

private fun key(name: String) = fold(name).filter { it.isLetterOrDigit() }

/** Vrai si [read] est vraisemblablement le nom [ours], mal lu ou non. */
fun sameGuild(read: String, ours: String): Boolean {
    val a = key(read)
    val b = key(ours)
    if (a.isEmpty() || b.isEmpty()) return false
    return a == b || levenshtein(a, b) <= 2
}

/** Nom court d'une guilde : « s18-Noxia » → « Noxia ». */
fun short(name: String) = name.substringAfter('-').ifEmpty { name }

fun plural(n: Int, one: String, many: String) = "$n ${if (n > 1) many else one}"

/** Guildes et colonne « Nous » après la lecture ; [newMatch] : les guildes ont changé. */
data class BannerMerge(val guilds: List<GuildIn>, val me: Int, val newMatch: Boolean, val th: String?, val tm: String?)

/** Reporte le bandeau et le temps ; ce qui n'a pas pu être lu est ajouté à [missing], ce qui l'a été à [parts]. */
fun mergeBanner(
    guilds: List<GuildIn>,
    me: Int,
    r: MapReading,
    ourGuild: String,
    missing: MutableList<String>,
    parts: MutableList<String>,
): BannerMerge {
    var out = guilds
    var newMe = me
    var newMatch = false
    if (r.guilds.size == 3) {
        // Sur le coffre, le bandeau est assombri et les noms souvent mal lus : seule la carte du match
        // peut signaler un nouveau match, et les noms déjà connus y sont gardés.
        val chest = r.kind == ScreenKind.CHEST
        newMatch = !chest && r.guilds.indices.any { i ->
            val n = r.guilds[i].name
            n != null && guilds.getOrNull(i)?.n?.let { it.isNotEmpty() && !sameGuild(n, it) } == true
        }
        val read = r.guilds.mapIndexedTo(mutableListOf()) { i, g ->
            val prev = if (newMatch) GuildIn() else guilds.getOrElse(i) { GuildIn() }
            if (g.name == null) missing.add("nom de la guilde ${i + 1}")
            val label = g.name ?: prev.n.ifEmpty { "guilde ${i + 1}" }
            if (g.soul == null) missing.add("Âme de $label")
            if (g.rate == null) missing.add("efficacité de $label")
            if (g.rooms == null && r.kind == ScreenKind.MAP) missing.add("salles de $label")
            GuildIn(
                n = if (chest && prev.n.isNotEmpty()) prev.n else g.name ?: prev.n,
                a = g.soul ?: prev.a,
                r = g.rate ?: prev.r,
                s = g.rooms?.toString() ?: prev.s,
            )
        }
        var found = read.indexOfFirst { sameGuild(it.n, ourGuild) }
        if (found < 0) {
            // Nom illisible : si une seule colonne n'a pas de nom lu, c'est sans doute la nôtre.
            val unnamed = r.guilds.indices.filter { r.guilds[it].name == null }
            if (unnamed.size == 1) {
                found = unnamed[0]
                read[found] = read[found].copy(n = ourGuild)
                missing[missing.indexOf("nom de la guilde ${found + 1}")] = "votre guilde : supposée en colonne ${found + 1}"
            } else missing.add("votre guilde ($ourGuild) dans le bandeau")
        }
        out = read
        if (found >= 0) newMe = found
        parts.add("scores")
    }
    var th: String? = null
    var tm: String? = null
    if (r.hours != null && r.minutes != null) {
        th = r.hours.toString()
        tm = r.minutes.toString()
        parts.add("temps restant")
    } else if (r.kind != ScreenKind.UNKNOWN) missing.add("temps restant")
    return BannerMerge(out, newMe, newMatch, th, tm)
}

/**
 * Compare les salles annoncées dans le bandeau à celles vues sur la carte : une salle hors de l'écran
 * (carte défilée) ou cachée par un élément du jeu fausserait le plan sans que rien ne le signale.
 */
fun roomCountChecks(names: List<String>, r: MapReading): List<String> = r.guilds.indices.mapNotNull { i ->
    val announced = r.guilds[i].rooms ?: return@mapNotNull null
    val seen = r.rooms.count { it.guild == i }
    val name = short(names.getOrNull(i) ?: "").ifEmpty { "guilde ${i + 1}" }
    when {
        seen < announced -> "$name : ${plural(announced, "salle annoncée", "salles annoncées")}, ${plural(seen, "vue", "vues")} sur la carte. Faites défiler la carte pour les voir toutes, puis relisez."
        seen > announced -> "$name : ${plural(seen, "salle vue", "salles vues")} pour ${plural(announced, "annoncée", "annoncées")}. Vérifiez les salles du rival."
        else -> null
    }
}
