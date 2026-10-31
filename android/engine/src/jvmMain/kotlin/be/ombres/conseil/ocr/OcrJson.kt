package be.ombres.conseil.ocr

import org.json.JSONArray
import org.json.JSONObject

/*
 * Enregistrement des lectures en JSON (org.json, fourni par Android ; ajouté pour les tests JVM) :
 * l'appli garde chaque lecture, les tests JVM les rejouent.
 */

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

/** Enregistre tout ce qui a été lu, pour rejouer la lecture dans les tests JVM. */
class RecordingScreen(private val inner: Screen) : Screen {
    override val page get() = inner.page
    override val pixels get() = inner.pixels
    private val crops = LinkedHashMap<String, OcrPage>()

    override suspend fun ocr(req: CropRequest): OcrPage = inner.ocr(req).also { crops[req.key] = it }

    fun toJson(): JSONObject = JSONObject()
        .put("page", page.toJson())
        .put("crops", JSONObject().apply { crops.forEach { (k, v) -> put(k, v.toJson()) } })
}

/** Rejoue un enregistrement de [RecordingScreen] ; les pixels viennent de la capture d'origine. */
class RecordedScreen(json: JSONObject, override val pixels: Pixels) : Screen {
    override val page = ocrPageFromJson(json.getJSONObject("page"))
    private val crops = json.getJSONObject("crops")

    override suspend fun ocr(req: CropRequest): OcrPage =
        if (crops.has(req.key)) ocrPageFromJson(crops.getJSONObject(req.key))
        else error("Zone non enregistrée : ${req.key} (relancer tools/dump-ocr.sh)")
}
