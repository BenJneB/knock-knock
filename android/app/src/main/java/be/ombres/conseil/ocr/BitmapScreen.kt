package be.ombres.conseil.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** [Screen] sur une vraie capture : découpe, agrandit et filtre la zone, puis la passe dans ML Kit. */
class BitmapScreen private constructor(private val bitmap: Bitmap, override val page: OcrPage) : Screen {

    override val pixels: Pixels = object : Pixels {
        private val px = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        override val width = bitmap.width
        override val height = bitmap.height
        override fun rgb(x: Int, y: Int) = px[y * width + x] and 0xFFFFFF
    }

    // Zones lues en parallèle, mais pas toutes à la fois : une zone agrandie pèse jusqu'à ~10 Mo.
    override suspend fun ocr(req: CropRequest): OcrPage = limit.withPermit {
        val (out, clamped) = withContext(Dispatchers.Default) { prepare(req) }
        OcrEngine.recognize(out).fromCrop(clamped, page).also { out.recycle() }
    }

    /** Découpe, agrandit et filtre la zone (hors du fil principal). */
    private fun prepare(req: CropRequest): Pair<Bitmap, CropRequest> {
        val l = req.left.coerceIn(0, bitmap.width - 1)
        val t = req.top.coerceIn(0, bitmap.height - 1)
        val r = req.right.coerceIn(l + 1, bitmap.width)
        val b = req.bottom.coerceIn(t + 1, bitmap.height)
        val w = (r - l) * req.scale
        val h = (b - t) * req.scale
        val out = createBitmap(w, h)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (req.filter == Filter.GRAY) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        Canvas(out).drawBitmap(bitmap, Rect(l, t, r, b), Rect(0, 0, w, h), paint)
        val thr = when (req.filter) { Filter.WHITE170 -> 170; Filter.WHITE200 -> 200; else -> 0 }
        if (thr > 0) keepWhite(out, thr)
        return out to req.copy(left = l, top = t, right = r, bottom = b)
    }

    private fun keepWhite(bmp: Bitmap, thr: Int) {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (i in px.indices) {
            val p = px[i]
            val m = minOf((p shr 16) and 255, (p shr 8) and 255, p and 255)
            px[i] = if (m >= thr) 0xFF000000.toInt() else -1
        }
        bmp.setPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    }

    companion object {
        private val limit = Semaphore(4)

        suspend fun of(bitmap: Bitmap) = BitmapScreen(bitmap, OcrEngine.recognize(bitmap))
    }
}
