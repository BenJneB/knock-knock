package be.ombres.conseil.ocr

import org.json.JSONArray
import org.json.JSONObject

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

private fun OcrLine.toJson() = JSONObject().put("t", text).put("b", JSONArray(listOf(left, top, right, bottom)))

private fun lineFromJson(o: JSONObject): OcrLine {
    val b = o.getJSONArray("b")
    return OcrLine(o.getString("t"), b.getInt(0), b.getInt(1), b.getInt(2), b.getInt(3))
}

private fun JSONArray.lines() = List(length()) { lineFromJson(getJSONObject(it)) }

fun OcrPage.toJson(): JSONObject = JSONObject()
    .put("width", width).put("height", height)
    .put("lines", JSONArray(lines.map { it.toJson() }))
    .put("elements", JSONArray(elements.map { it.toJson() }))

fun ocrPageFromJson(o: JSONObject) = OcrPage(
    o.getInt("width"), o.getInt("height"), o.getJSONArray("lines").lines(), o.getJSONArray("elements").lines(),
)
