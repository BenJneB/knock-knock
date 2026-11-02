package be.ombres.conseil.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Lecture du texte avec ML Kit (modèle latin embarqué : aucune connexion nécessaire). */
object OcrEngine {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): OcrPage = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val lines = mutableListOf<OcrLine>()
                val elements = mutableListOf<OcrLine>()
                for (block in text.textBlocks) {
                    for (line in block.lines) {
                        line.boundingBox?.let { lines.add(OcrLine(line.text, it.left, it.top, it.right, it.bottom)) }
                        for (e in line.elements) e.boundingBox?.let { elements.add(OcrLine(e.text, it.left, it.top, it.right, it.bottom)) }
                    }
                }
                cont.resume(OcrPage(bitmap.width, bitmap.height, lines, elements))
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
