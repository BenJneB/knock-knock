package be.ombres.conseil.game

import kotlin.math.abs
import kotlin.math.floor

/*
 * Outils communs aux modes de jeu : lecture des nombres saisis ou lus (« 724.44K »), mise en forme.
 * Mêmes règles que les fonctions JavaScript d'origine de la page web (parseFloat, toFixed, Math.round).
 */

/** Une guilde telle que saisie ou lue dans le bandeau : nom, Âme, production /s, nombre de salles. */
data class GuildIn(val n: String = "", val a: String = "", val r: String = "", val s: String = "")

/** Une étape du plan : titre, explication, coût (« 1 Sape d’âme », « 1 tentative »… ; vide si gratuit). */
data class Step(val t: String, val d: String, val c: String)

private val FLOAT_PREFIX = Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")
private val INT_PREFIX = Regex("^[+-]?\\d+")

/** Équivalent de parseFloat : lit le plus long préfixe numérique. */
private fun jsParseFloat(s: String): Double? = FLOAT_PREFIX.find(s)?.value?.toDoubleOrNull()

fun parseNum(v: String?): Double? {
    if (v == null) return null
    var s = v.trim().replace(Regex("\\s"), "").replaceFirst(",", ".").uppercase()
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
fun fixed(n: Double, d: Int) = be.ombres.conseil.fixedImpl(n, d)

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

/** Affichage d'un nombre comme String(n) en JS (3.0 → "3", 2.5 → "2.5"). */
fun jsNum(x: Double): String =
    if (x == floor(x) && abs(x) < 1e15) x.toLong().toString() else x.toString()
