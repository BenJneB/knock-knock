package be.ombres.conseil

/** Décomposition Unicode NFD. */
expect fun nfdImpl(s: String): String

/** Number.prototype.toFixed de JS : arrondi de la valeur binaire exacte. */
expect fun fixedImpl(n: Double, d: Int): String
