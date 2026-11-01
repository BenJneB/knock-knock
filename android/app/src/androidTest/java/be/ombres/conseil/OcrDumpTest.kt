package be.ombres.conseil

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import be.ombres.conseil.map.MapReader
import be.ombres.conseil.ocr.BitmapScreen
import be.ombres.conseil.ocr.RecordingScreen
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Lit chaque capture de captures/ avec ML Kit, comme le fait l'appli, et enregistre toutes les
 * lectures (capture entière + zones agrandies) en JSON. Les tests JVM du parseur rejouent ces
 * enregistrements (app/src/test/resources/ocr/). Voir tools/dump-ocr.sh.
 */
@RunWith(AndroidJUnit4::class)
class OcrDumpTest {
    @Test
    fun dumpCaptures() = runBlocking {
        val ins = InstrumentationRegistry.getInstrumentation()
        val assets = ins.context.assets
        val out = File(ins.targetContext.getExternalFilesDir(null), "ocr").apply { deleteRecursively(); mkdirs() }
        // Les captures des règles (regle-*) servent à lire les règles, pas de cas de test.
        val images = assets.list("")!!.filter { (it.endsWith(".jpeg") || it.endsWith(".jpg") || it.endsWith(".png")) && !it.startsWith("regle-") }
        for (name in images) {
            val bmp = assets.open(name).use { BitmapFactory.decodeStream(it) }
            val screen = RecordingScreen(BitmapScreen.of(bmp))
            val reading = MapReader.read(screen)
            val base = name.substringBeforeLast('.')
            File(out, "$base.json").writeText(screen.toJson().toString())
            File(out, "$base.txt").writeText(reading.toString())
        }
    }
}
