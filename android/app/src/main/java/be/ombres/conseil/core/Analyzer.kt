package be.ombres.conseil.core

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import be.ombres.conseil.mode.ReadReport
import be.ombres.conseil.ocr.BitmapScreen
import be.ombres.conseil.ocr.RecordingScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lecture d'une capture par le mode actif. La capture, tout ce que ML Kit a lu et le résultat sont gardés :
 * en cas de lecture ratée, l'utilisateur les partage, et ils deviennent un cas de test
 * (tools/import-lecture.sh les range dans captures/ et dans les tests).
 */
object Analyzer {
    private fun dir(c: Context) = File(c.cacheDir, "derniere-lecture")

    suspend fun read(context: Context, bmp: Bitmap): ReadReport {
        val name = "lecture-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val d = dir(context)
        withContext(Dispatchers.IO) {
            d.deleteRecursively()
            d.mkdirs()
            File(d, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val screen = RecordingScreen(BitmapScreen.of(bmp))
        val report = Prefs.mode(context).read(context, screen)
        withContext(Dispatchers.IO) {
            File(d, "$name.json").writeText(screen.toJson().toString())
            File(d, "$name.txt").writeText(
                (listOf(report.summary) + report.notes + report.missing.map { "À vérifier : $it" }).joinToString("\n")
            )
        }
        return report
    }

    fun hasLast(context: Context) = dir(context).listFiles()?.any { it.extension == "png" } == true

    /** Partage la dernière lecture (capture + texte lu) pour signaler une erreur. */
    fun shareLast(context: Context) {
        val files = dir(context).listFiles()?.sortedBy { it.name } ?: return
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) })
        if (uris.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Conseil de guerre : lecture ratée")
            putExtra(Intent.EXTRA_TEXT, "Lecture ratée (capture, texte lu et résultat en pièces jointes). Ce qui est faux :\n")
            clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Signaler la lecture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
