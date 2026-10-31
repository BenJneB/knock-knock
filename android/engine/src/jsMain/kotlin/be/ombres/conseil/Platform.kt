package be.ombres.conseil

// Ce que la bibliothèque commune de Kotlin ne fournit pas : versions navigateur (page web).

actual fun nfdImpl(s: String): String = s.asDynamic().normalize("NFD") as String

actual fun fixedImpl(n: Double, d: Int): String = n.asDynamic().toFixed(d) as String
