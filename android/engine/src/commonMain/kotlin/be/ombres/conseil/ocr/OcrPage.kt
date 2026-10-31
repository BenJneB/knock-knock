package be.ombres.conseil.ocr

/** Un morceau de texte reconnu et sa position en pixels dans la capture. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val cx get() = (left + right) / 2
    val cy get() = (top + bottom) / 2
    val height get() = bottom - top
}

/**
 * Texte reconnu sur une capture, indépendant de ML Kit pour pouvoir tester le parseur sur la JVM.
 * [lines] : lignes ML Kit ; [elements] : mots ML Kit (plus fins, utiles quand une ligne regroupe deux colonnes).
 */
data class OcrPage(val width: Int, val height: Int, val lines: List<OcrLine>, val elements: List<OcrLine>)
