package be.ombres.conseil.ocr

/** Minuscules sans accents, pour comparer du texte lu de façon approximative. */
fun fold(s: String): String =
    nfd(s).filter { it.code !in 0x300..0x36F }.lowercase()

/** Décomposition Unicode (NFD) : « é » devient « e » + accent, que [fold] retire. */
private fun nfd(s: String) = be.ombres.conseil.nfdImpl(s)

/** Lit un petit nombre isolé, en corrigeant les confusions classiques (O→0, l→1…). */
fun digits(token: String): Int? {
    val t = token.trim().trim('.', ',', ':', ';', '\'', '"')
    if (t.isEmpty() || t.length > 3) return null
    val fixed = when (t) {
        "()", "(", ")", "O", "o", "D", "Q" -> "0"
        "l", "I", "L", "|", "!", "i" -> "1"
        "Z", "z" -> "2"
        "S", "s" -> "5"
        else -> t
    }
    return if (fixed.all { it.isDigit() }) fixed.toIntOrNull() else null
}

/** Distance d'édition, pour reconnaître un nom de guilde mal lu. */
fun levenshtein(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        prev = cur
    }
    return prev[b.length]
}

/** Vote pondéré entre plusieurs lectures d'une même valeur ; en cas d'égalité, la première vue gagne. */
class Votes<T> {
    private val score = LinkedHashMap<T, Double>()
    fun add(v: T?, weight: Double = 1.0) { if (v != null) score[v] = (score[v] ?: 0.0) + weight }
    fun best(): T? = score.entries.maxByOrNull { it.value }?.key

    /**
     * Comme [best], mais une valeur « absorbée » par une autre lui apporte ses voix ; par défaut, celle qui n'en est
     * que la fin (« 28K » pour « 876.28K », nombre coupé en deux mots à la lecture).
     */
    fun bestMerged(absorbs: (long: String, short: String) -> Boolean = { long, short -> long.endsWith(short) }): T? {
        val merged = score.mapValues { (k, v) ->
            v + score.filterKeys { o -> o != k && absorbs(k.toString(), o.toString()) }.values.sum()
        }
        return merged.entries.maxByOrNull { it.value }?.key
    }
}

fun median(xs: List<Int>): Int = xs.sorted()[xs.size / 2]
