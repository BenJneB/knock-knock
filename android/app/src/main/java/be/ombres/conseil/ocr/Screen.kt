package be.ombres.conseil.ocr

import org.json.JSONObject

/** Traitement appliqué à une zone avant la lecture. */
enum class Filter {
    COLOR,
    GRAY,
    /** Ne garde que le texte blanc (seuil 170), en noir sur fond blanc. */
    WHITE170,
    /** Idem, seuil 200. */
    WHITE200,
}

/** Zone de la capture à relire, agrandie [scale] fois, en coordonnées de la capture. */
data class CropRequest(val left: Int, val top: Int, val right: Int, val bottom: Int, val scale: Int, val filter: Filter) {
    val key get() = "$left,$top,$right,$bottom,x$scale,$filter"
}

/** Accès aux pixels de la capture (couleur 0xRRGGBB), pour ce que le texte ne dit pas (bulle de bouclier…). */
interface Pixels {
    val width: Int
    val height: Int
    fun rgb(x: Int, y: Int): Int

    /** Part des pixels de la zone (un sur deux en x et en y) qui vérifient [test]. */
    fun fraction(left: Int, top: Int, right: Int, bottom: Int, test: (r: Int, g: Int, b: Int) -> Boolean): Double {
        var tot = 0
        var hit = 0
        for (y in maxOf(0, top) until minOf(height, bottom) step 2) for (x in maxOf(0, left) until minOf(width, right) step 2) {
            val c = rgb(x, y)
            tot++
            if (test((c shr 16) and 255, (c shr 8) and 255, c and 255)) hit++
        }
        return if (tot == 0) 0.0 else hit.toDouble() / tot
    }
}

/**
 * Une capture d'écran prête à être lue : le texte de la capture entière, plus la possibilité
 * de relire une zone agrandie. Les résultats de [ocr] sont ramenés en coordonnées de la capture.
 */
interface Screen {
    val page: OcrPage
    val pixels: Pixels
    suspend fun ocr(req: CropRequest): OcrPage
}

/** Ramène une page lue sur une zone agrandie en coordonnées de la capture. */
fun OcrPage.fromCrop(req: CropRequest, full: OcrPage): OcrPage {
    fun OcrLine.map() = OcrLine(text, req.left + left / req.scale, req.top + top / req.scale, req.left + right / req.scale, req.top + bottom / req.scale)
    return OcrPage(full.width, full.height, lines.map { it.map() }, elements.map { it.map() })
}

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

