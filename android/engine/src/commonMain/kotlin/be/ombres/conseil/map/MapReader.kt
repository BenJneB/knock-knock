package be.ombres.conseil.map

import be.ombres.conseil.game.RoomType
import be.ombres.conseil.ocr.CropRequest
import be.ombres.conseil.ocr.Filter
import be.ombres.conseil.ocr.Hint
import be.ombres.conseil.ocr.OcrLine
import be.ombres.conseil.ocr.OcrPage
import be.ombres.conseil.ocr.Screen
import be.ombres.conseil.ocr.Votes
import be.ombres.conseil.ocr.digits
import be.ombres.conseil.ocr.fold
import be.ombres.conseil.ocr.levenshtein
import be.ombres.conseil.ocr.median

/*
 * Lecture des captures de la carte du match (bandeau, temps, salles) et du coffre de cartes,
 * commune aux modes qui se jouent sur cette carte (Zone sombre, Défense de la capitale).
 * 1. La capture entière est lue une fois : on y cherche des repères (« Âme », « Fin dans », « Quantité »…).
 * 2. Autour de chaque repère, la zone est relue plusieurs fois, agrandie et filtrée de façons différentes.
 * 3. Chaque valeur est choisie par vote entre ces lectures.
 * Les petits chiffres (salles, quantités) sont mal lus par ML Kit en taille réelle : d'où les filtres « texte blanc ».
 */

enum class ScreenKind { MAP, CHEST, UNKNOWN }

data class GuildRead(
    val name: String?,
    val soul: String?,
    val rate: String?,
    /** Salles verte / violette / rouge ; null quand un chiffre n'a pas pu être lu. */
    val roomSlots: List<Int?>,
) {
    val rooms: Int? get() = if (roomSlots.all { it != null }) roomSlots.sumOf { it!! } else null
}

/** Une salle occupée repérée sur la carte, par son étiquette (nom de guilde + DMG). */
data class RoomRead(
    /** Colonne du bandeau de la guilde qui l'occupe ; null si le nom n'a pas été reconnu. */
    val guild: Int?,
    val dmg: String?,
    val shield: Boolean,
    /** Montant affiché sous « Volé » quand un voleur (Sape d'âme) est dans la salle. */
    val stolen: String?,
    /** Position lisible : « haut gauche », « centre »… */
    val pos: String,
    val x: Int,
    val y: Int,
    /** Type d'après la couleur du sol ; null si incertain. */
    val type: RoomType? = null,
    /** Bonus d'efficacité « 50% » affiché près de l'étiquette. */
    val buff: Boolean = false,
)

/** Salle libre (« Efficacité en mode inactif X/s », production d'un joueur). */
data class FreeRoom(val rate: String?, val pos: String, val type: RoomType? = null, val buff: Boolean = false)

data class MapReading(
    val kind: ScreenKind,
    /** Les trois colonnes du bandeau, de gauche à droite ; vide si le bandeau n'est pas visible. */
    val guilds: List<GuildRead> = emptyList(),
    val hours: Int? = null,
    val minutes: Int? = null,
    /** Quantités lues dans le coffre : clés em, sa, gi, ss. */
    val cards: Map<String, Int?> = emptyMap(),
    val today: Int? = null,
    val rooms: List<RoomRead> = emptyList(),
    val free: List<FreeRoom> = emptyList(),
)

object MapReader {

    val CARD_KEYS = listOf("em", "sa", "gi", "ss")

    /** Cartes au départ de la phase (aucune nouvelle ensuite) : une quantité plus grande est une erreur de lecture. */
    val CARD_MAX = listOf(2, 10, 6, 6)

    // Position des chiffres de salles dans une colonne du bandeau (fraction de la largeur de colonne),
    // mesurée sur une capture 1080 px de large.
    private val ROOM_SLOTS = listOf(0.508, 0.675, 0.842)

    private val NAME = Regex("[sS5]\\d{1,3}\\s*-\\s*[A-Za-zÀ-ÿ0-9]+")
    private val SOUL = Regex("(\\d+(?:[.,]\\d+)?)\\s*([KMB])\\b")
    private val SOUL_NOSEP = Regex("(?<![\\d.,])(\\d{4,5})([KMB])\\b")
    private val RATE = Regex("(\\d+(?:[.,]\\d+)?)\\s*/\\s*[s5%$]")

    // Deux-points écrit \x3A dans les classes : sur Android (ICU), « [: … :] » est une syntaxe spéciale
    // et fait planter la compilation de l'expression (les tests JVM ne le voient pas).
    private val TIME_HM = Regex("(\\d{1,2})\\s*[\\x3A.]?\\s*h[\\s.,\\x3A]*(\\d{1,2})\\D{0,2}?m[i1l]?n")
    private val TIME_M = Regex("(\\d{1,2})\\D{0,2}?m[i1l]?n")
    private val TODAY = Regex("(\\d{1,2})\\s*/\\s*15")

    /**
     * [known] : noms de guilde déjà connus par colonne (dernière lecture), et [ourGuild] notre nom ;
     * servent à reconnaître les étiquettes des salles quand un nom du bandeau est illisible.
     */
    suspend fun read(screen: Screen, ourGuild: String? = null, known: List<String> = emptyList()): MapReading {
        val page = screen.page
        val chest = chestAnchors(page)
        val banner = readBanner(screen)
        val time = readTime(screen, banner?.ameTop, banner?.unit)
        val kind = when {
            chest.isNotEmpty() -> ScreenKind.CHEST
            banner != null -> ScreenKind.MAP
            else -> ScreenKind.UNKNOWN
        }
        var cards: Map<String, Int?> = emptyMap()
        var today: Int? = null
        if (kind == ScreenKind.CHEST) {
            cards = readCards(screen)
            today = readToday(screen)
        }
        var rooms: List<RoomRead> = emptyList()
        var free: List<FreeRoom> = emptyList()
        if (kind == ScreenKind.MAP && banner != null) {
            val map = readMap(screen, banner, ourGuild, known)
            rooms = map.first
            free = map.second
        }
        return MapReading(kind, banner?.guilds ?: emptyList(), time?.first, time?.second, cards, today, rooms, free)
    }

    /* ---------- bandeau du haut ---------- */

    private class Banner(val guilds: List<GuildRead>, val ameTop: Int, val unit: Int, val bottom: Int)

    private fun isAme(l: OcrLine): Boolean {
        val f = fold(l.text)
        return f.startsWith("ame") || f.startsWith("me:") || Regex("\\bame\\s*[\\x3A.]").containsMatchIn(f)
    }

    private suspend fun readBanner(screen: Screen): Banner? {
        val page = screen.page
        val ames = page.lines.filter { isAme(it) && it.top < page.height * 0.3 }
        if (ames.isEmpty()) return null
        val ameTop = median(ames.map { it.top })
        val u = median(ames.map { it.height }).coerceAtLeast(10)
        val w = page.width
        val top = ameTop - (4.5 * u).toInt()
        val bottom = ameTop + (2.6 * u).toInt()
        val stripTop = ameTop - 2 * u
        val stripBottom = ameTop + (0.3 * u).toInt()
        fun band(scale: Int, f: Filter) = CropRequest(0, top, w, bottom, scale, f)
        fun strip(scale: Int, f: Filter) = CropRequest(0, stripTop, w, stripBottom, scale, f, Hint.DIGITS)

        val textPages = listOf(page) + listOf(
            band(2, Filter.COLOR), band(2, Filter.GRAY), band(3, Filter.COLOR), band(3, Filter.GRAY),
            band(2, Filter.WHITE170), band(3, Filter.WHITE170),
        ).map { screen.ocr(it) }
        val roomPages = listOf(textPages[5], textPages[6]) + listOf(
            strip(2, Filter.WHITE200), strip(3, Filter.WHITE200), strip(2, Filter.WHITE170),
        ).map { screen.ocr(it) } + (0..2).flatMap { c ->
            // Une colonne à la fois, plus agrandie : aide pour les « 0 » isolés.
            listOf(Filter.WHITE200, Filter.WHITE170).map { f ->
                screen.ocr(CropRequest(c * w / 3, stripTop, (c + 1) * w / 3, stripBottom, 4, f, Hint.DIGITS))
            }
        }

        val cw = w / 3.0
        fun col(e: OcrLine) = (e.cx / cw).toInt().coerceIn(0, 2)
        val guilds = (0..2).map { c ->
            val name = Votes<String>()
            val soul = Votes<String>()
            val rate = Votes<String>()
            for (p in textPages) {
                for (e in p.elements + p.lines) {
                    if (col(e) != c || e.bottom < top || e.top > bottom) continue
                    NAME.find(e.text)?.let { name.add(normName(it.value)) }
                    val s = SOUL.find(e.text)
                    if (s != null) soul.add(s.groupValues[1].replace(',', '.') + s.groupValues[2])
                    else SOUL_NOSEP.find(e.text)?.let { m ->
                        // Point perdu à la lecture (« 72444K ») : les valeurs ont deux décimales.
                        val d = m.groupValues[1]
                        soul.add(d.dropLast(2) + "." + d.takeLast(2) + m.groupValues[2], 0.5)
                    }
                    RATE.find(e.text)?.let { rate.add(it.groupValues[1].replace(',', '.')) }
                }
            }
            val slots = ROOM_SLOTS.map { frac ->
                val x = c * cw + frac * cw
                val v = Votes<Int>()
                for (p in roomPages) {
                    for (e in p.elements) {
                        if (e.cy < stripTop || e.cy > stripBottom || kotlin.math.abs(e.cx - x) > 0.06 * cw) continue
                        v.add(digits(e.text))
                    }
                }
                v.best()
            }
            GuildRead(name.best(), soul.bestMerged(), rate.best(), slots)
        }
        return Banner(guilds, ameTop, u, bottom)
    }

    private fun normName(s: String): String {
        val m = Regex("[sS5](\\d{1,3})\\s*-\\s*(.+)").matchEntire(s) ?: return s
        return "s${m.groupValues[1]}-${m.groupValues[2]}"
    }

    /* ---------- salles sur la carte ---------- */

    private val DMG = Regex("dm[g6]\\s*([\\x3A.;&,]?)\\s*([0-9OSA]+(?:[.,][0-9OSA]+)?)\\s*([KMB])?", RegexOption.IGNORE_CASE)
    private val FREE_RATE = Regex("(\\d+(?:[.,]\\d+)?)\\s*/\\s*[s5]")

    /** Bleu clair de la bulle de bouclier (le sol de la carte est violet : g < r). */
    private fun cyan(r: Int, g: Int, b: Int) = g > r + 30 && b > r + 50 && b >= g - 15

    private class Label(val left: Int, val top: Int, val bottom: Int, val text: String)

    private suspend fun readMap(screen: Screen, banner: Banner, ourGuild: String?, known: List<String>): Pair<List<RoomRead>, List<FreeRoom>> {
        val page = screen.page
        val w = page.width
        val k = w / 1080.0 // mesures faites sur une capture de 1080 px de large
        fun d(v: Int) = (v * k).toInt()
        val mapTop = banner.bottom
        val mapBottom = (page.height * 0.86).toInt()
        val pages = listOf(page, screen.ocr(CropRequest(0, mapTop, w, mapBottom, 2, Filter.COLOR)))
        val lines = pages.flatMap { p -> p.lines.filter { it.top in mapTop..mapBottom } }

        // Étiquettes : la ligne « DMG : … », alignée à gauche sous le nom de la guilde.
        val labels = mutableListOf<Label>()
        for (l in lines.filter { DMG.containsMatchIn(it.text) || fold(it.text).startsWith("dmg") }) {
            // Deux étiquettes sont toujours bien plus éloignées que ça : au-delà, c'est la même lue deux fois.
            if (labels.none { kotlin.math.abs(it.left - l.left) < d(90) && kotlin.math.abs(it.top - l.top) < d(25) }) labels.add(Label(l.left, l.top, l.bottom, l.text))
        }
        val unread = banner.guilds.count { it.name == null }
        val names = banner.guilds.mapIndexed { i, g ->
            g.name ?: known.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: ourGuild.takeIf { unread == 1 }
        }
        val suffixes = names.map { n -> n?.substringAfter('-')?.let { key(it) }?.takeIf { it.isNotEmpty() } }

        val labelled = labels.map { lab ->
            // Nom de la guilde : juste au-dessus de la ligne DMG.
            val nameLine = lines.filter { it.bottom in (lab.top - d(40))..(lab.top + d(4)) && it.right > lab.left && it.left < lab.left + d(120) }
            val guild = suffixes.indices.firstOrNull { i ->
                val sfx = suffixes[i] ?: return@firstOrNull false
                nameLine.any { n -> key(n.text).let { t -> t.contains(sfx) || (t.length >= sfx.length && levenshtein(t.takeLast(sfx.length), sfx) <= 1) } }
            }
            val dmg = readDmg(screen, lab, ::d)
            // Corps de la salle, d'après la position de l'étiquette.
            val l = lab.left - d(75)
            val r = lab.left + d(135)
            val t = lab.top - d(35)
            val b = lab.top + d(212)
            val sides = listOf(
                screen.pixels.fraction(l - d(50), t + d(60), l - d(5), b, ::cyan),
                screen.pixels.fraction(r + d(5), t + d(60), r + d(50), b, ::cyan),
            )
            val shield = sides.average() >= 0.12
            // Voleur : « Volé » et le montant volé, au coin bas gauche de la salle. Sous une bulle de bouclier,
            // seul le montant est lisible : un montant en K/M à cet endroit suffit.
            val stolenVotes = Votes<String>()
            lines.filter { kotlin.math.abs(it.left - l) < d(90) && kotlin.math.abs(it.top - b) < d(90) }.forEach { a ->
                SOUL.find(a.text)?.let { m -> stolenVotes.add(m.groupValues[1].replace(',', '.') + m.groupValues[2]) }
            }
            val stolen = stolenVotes.best()
            val cx = lab.left + d(30)
            val cy = lab.top + d(90)
            val type = floorType(screen, cx, lab.top + d(150), d(40))
            // Bonus : badge « 50% » au bout de l'étiquette, juste sous elle.
            val buff = lines.any { BUFF.containsMatchIn(it.text) && it.left in lab.left + d(80)..lab.left + d(280) && it.top in lab.top - d(10)..lab.top + d(70) }
            RoomRead(guild, dmg, shield, stolen, position(cx, cy, w, mapTop, mapBottom), cx, cy, type, buff)
        }

        // Étiquette sans nom lisible : si une seule guilde a moins de salles vues que de salles annoncées
        // dans le bandeau, c'est la sienne.
        val announced = banner.guilds.map { it.rooms }
        val missing = announced.indices.filter { i -> announced[i] != null && labelled.count { it.guild == i } < announced[i]!! }
        val rooms = if (missing.size == 1) labelled.map { if (it.guild == null) it.copy(guild = missing[0]) else it } else labelled

        // Salles libres : « Efficacité en mode inactif », avec la production juste en dessous.
        val freeLines = mutableListOf<OcrLine>()
        for (l in lines.filter { fold(it.text).let { f -> f.contains("en mode") || f.contains("inactif") || f.contains("inastif") } }) {
            if (freeLines.none { kotlin.math.abs(it.cx - l.cx) < d(60) && kotlin.math.abs(it.cy - l.cy) < d(40) }) freeLines.add(l)
        }
        val free = freeLines.map { f ->
            val rate = lines.firstOrNull { it.top in f.top..f.bottom + d(45) && kotlin.math.abs(it.cx - f.cx) < d(60) && FREE_RATE.containsMatchIn(it.text) }
                ?.let { FREE_RATE.find(it.text)!!.groupValues[1].replace(',', '.') }
            val type = floorType(screen, f.cx, f.bottom + d(130), d(40))
            val buff = lines.any { BUFF.containsMatchIn(it.text) && it.cx in f.left - d(20)..f.right + d(120) && it.top in f.bottom..f.bottom + d(90) }
            FreeRoom(rate, position(f.cx, f.cy + d(100), w, mapTop, mapBottom), type, buff)
        }
        return rooms to free
    }

    // « 50% », parfois lu « 502 » ou « 505 ».
    private val BUFF = Regex("(?<![\\d.,])50\\s?[%25](?![\\d.,])")

    /**
     * Type d'une salle d'après la couleur moyenne de son sol (zone de ±[half] autour de x, y) :
     * suprême rosé (rouge nettement au-dessus du bleu), basique gris (vert au moins égal au rouge), intermédiaire violet.
     */
    private fun floorType(screen: Screen, x: Int, y: Int, half: Int): RoomType? {
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        val px = screen.pixels
        for (yy in (y - half).coerceAtLeast(0) until (y + half).coerceAtMost(px.height) step 3) {
            for (xx in (x - half).coerceAtLeast(0) until (x + half).coerceAtMost(px.width) step 3) {
                val c = px.rgb(xx, yy)
                r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255; n++
            }
        }
        if (n == 0) return null
        val (ar, ag, ab) = Triple(r / n, g / n, b / n)
        return when {
            ar > ab + 20 && ar > 150 -> RoomType.SUPREME
            ag >= ar - 2 -> RoomType.BASIC
            ab > ar + 5 -> RoomType.INTERMEDIATE
            else -> null
        }
    }

    private suspend fun readDmg(screen: Screen, lab: Label, d: (Int) -> Int): String? {
        val variants = listOf(3 to Filter.COLOR, 3 to Filter.GRAY, 4 to Filter.COLOR, 2 to Filter.COLOR, 2 to Filter.GRAY, 5 to Filter.COLOR)
        val texts = listOf(lab.text) + variants.flatMap { (scale, f) ->
            screen.ocr(CropRequest(lab.left - d(10), lab.top - d(8), lab.left + d(190), lab.bottom + d(8), scale, f, Hint.DMG)).lines.map { it.text }
        }
        return parseDmg(texts)
    }

    /** Vote entre plusieurs lectures d'une même ligne « DMG : … ». */
    fun parseDmg(texts: List<String>): String? {
        val votes = Votes<String>()
        for (text in texts) {
            val m = DMG.find(text) ?: continue
            var num = m.groupValues[2].uppercase().replace('O', '0').replace('S', '5').replace('A', '4').replace(',', '.')
            val unit = m.groupValues[3].uppercase()
            var weight = 1.0
            // « DMG : » affiche toujours un deux-points ; sans séparateur, un 8 collé à DMG est sans doute
            // ce deux-points mal lu (« DMG82.91B » pour 2.91B).
            if (m.groupValues[1].isEmpty() && num.startsWith("8") && num.length > 1 && !num.startsWith("8.")) {
                votes.add(num + unit, 0.4)
                num = num.drop(1)
                weight = 0.6
            }
            when {
                unit.isEmpty() -> if (num == "0") votes.add("0", weight)

                num.contains('.') -> votes.add(num + unit, weight)

                // Point perdu à la lecture (« 141B ») : les valeurs ont deux décimales.
                num.length >= 3 -> votes.add(num.dropLast(2) + "." + num.takeLast(2) + unit, 0.5 * weight)

                else -> votes.add(num + unit, 0.5 * weight)
            }
        }
        return votes.bestWithDroppedOnes()
    }

    /**
     * Le « 1 », trait fin, disparaît souvent dans les zones agrandies (« 79.65M » pour 791.65M) :
     * une valeur qui ne diffère d'une autre que par un « 1 » manquant lui apporte ses voix.
     */
    private fun Votes<String>.bestWithDroppedOnes(): String? = bestMerged { long, short ->
        long.length == short.length + 1 && long.indices.any { i -> long[i] == '1' && long.removeRange(i, i + 1) == short }
    }

    private fun position(x: Int, y: Int, w: Int, top: Int, bottom: Int): String {
        val col = when { x < w * 0.38 -> "gauche"; x > w * 0.62 -> "droite"; else -> "" }
        val rows = listOf("tout en haut", "haut", "milieu", "bas", "tout en bas")
        val row = rows[((y - top) * rows.size / (bottom - top).coerceAtLeast(1)).coerceIn(0, rows.size - 1)]
        return if (col.isEmpty()) "$row au centre" else "$row à $col"
    }

    private fun key(s: String) = fold(s).filter { it.isLetterOrDigit() }

    /* ---------- temps restant ---------- */

    private suspend fun readTime(screen: Screen, ameTop: Int?, u: Int?): Pair<Int, Int>? {
        val page = screen.page
        val anchor = page.lines.firstOrNull { fold(it.text).let { f -> f.startsWith("fin d") || f.startsWith("ein d") || f.contains("in dans") } }
        val box = when {
            anchor != null -> intArrayOf(anchor.left - 20, anchor.top - 15, anchor.right + 40, anchor.bottom + 15)
            ameTop != null && u != null -> intArrayOf((page.width * 0.2).toInt(), ameTop + (3.5 * u).toInt(), (page.width * 0.75).toInt(), ameTop + 6 * u)
            else -> return null
        }
        val votes = Votes<Pair<Int, Int>>()
        for ((scale, f) in listOf(2 to Filter.GRAY, 3 to Filter.GRAY, 2 to Filter.COLOR, 3 to Filter.COLOR)) {
            val p = screen.ocr(CropRequest(box[0], box[1], box[2], box[3], scale, f))
            val t = parseTime(p.lines.joinToString(" ") { it.text })
            // « 42 min » seul peut être « 2 h 42 min » dont les heures sont mal lues : il compte moins.
            votes.add(t, if (t?.first == 0) 0.4 else 1.0)
        }
        return votes.best()
    }

    fun parseTime(text: String): Pair<Int, Int>? {
        val t = fold(text).substringAfter("dans", fold(text))
        TIME_HM.find(t)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        TIME_M.find(t)?.let { return 0 to it.groupValues[1].toInt() }
        return null
    }

    /* ---------- coffre de cartes ---------- */

    private fun chestAnchors(page: OcrPage) = page.lines.filter {
        val f = fold(it.text)
        f.contains("quantit") || f.contains("uantit") || f.contains("disponible") || f.contains("aujourd")
    }

    private fun isQuantity(l: OcrLine) = fold(l.text).let { it.contains("quant") || it.contains("uantit") }

    private suspend fun readCards(screen: Screen): Map<String, Int?> {
        val page = screen.page
        val qs = page.lines.filter(::isQuantity)
        if (qs.isEmpty()) return emptyMap()
        val qTop = median(qs.map { it.top })
        val qh = median(qs.map { it.height }).coerceAtLeast(10)
        val top = qTop - qh / 2
        val bottom = qTop + (2.6 * qh).toInt()
        val centers = if (qs.size == 4) qs.sortedBy { it.left }.map { it.cx.toDouble() }
        else listOf(0.207, 0.409, 0.606, 0.81).map { it * page.width }
        val half = (centers[1] - centers[0]) / 2

        val votes = List(4) { Votes<Int>() }
        val variants = listOf(
            2 to Filter.WHITE170, 2 to Filter.COLOR, 3 to Filter.COLOR, 3 to Filter.GRAY, 2 to Filter.GRAY, 2 to Filter.WHITE200,
        )
        // Bande entière, puis chaque carte seule, plus agrandie (chiffre en partie masqué par le cadre).
        val crops = variants.map { (scale, f) -> CropRequest(0, top, page.width, bottom, scale, f) } +
            centers.flatMap { cx ->
                listOf(Filter.WHITE170, Filter.COLOR).map { f -> CropRequest((cx - half).toInt(), top, (cx + half).toInt(), bottom, 4, f, Hint.DIGITS) }
            }
        for (req in crops) {
            val p = screen.ocr(req)
            for (e in p.elements) {
                if (e.top < qTop + qh / 2 || !e.text.trim().all { it.isDigit() }) continue
                val k = centers.indices.minBy { kotlin.math.abs(centers[it] - e.cx) }
                if (kotlin.math.abs(centers[k] - e.cx) > half) continue
                votes[k].add(e.text.trim().toIntOrNull()?.takeIf { it <= CARD_MAX[k] })
            }
        }
        return CARD_KEYS.indices.associate { CARD_KEYS[it] to votes[it].best() }
    }

    private suspend fun readToday(screen: Screen): Int? {
        val page = screen.page
        val anchors = page.lines.filter { fold(it.text).let { f -> f.contains("disponible") || f.contains("aujourd") } }
        if (anchors.isEmpty()) return null
        val votes = Votes<Int>()
        anchors.forEach { a -> TODAY.find(a.text)?.let { votes.add(it.groupValues[1].toInt()) } }
        val l = anchors.minOf { it.left } - 20
        val t = anchors.minOf { it.top } - 10
        val r = anchors.maxOf { it.right } + 80
        val b = anchors.maxOf { it.bottom } + 10
        for ((scale, f) in listOf(2 to Filter.COLOR, 2 to Filter.GRAY, 3 to Filter.COLOR, 3 to Filter.GRAY)) {
            val p = screen.ocr(CropRequest(l, t, r, b, scale, f))
            p.lines.forEach { line -> TODAY.find(line.text)?.let { votes.add(it.groupValues[1].toInt()) } }
        }
        return votes.best()
    }
}
