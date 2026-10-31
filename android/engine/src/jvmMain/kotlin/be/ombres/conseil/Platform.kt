package be.ombres.conseil

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import kotlin.math.abs

// Ce que la bibliothèque commune de Kotlin ne fournit pas : versions JVM (Android et tests).

actual fun nfdImpl(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)

actual fun fixedImpl(n: Double, d: Int): String {
    val s = BigDecimal(abs(n)).setScale(d, RoundingMode.HALF_UP).toPlainString()
    return if (n < 0) "-$s" else s
}
