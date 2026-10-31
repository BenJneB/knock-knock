package be.ombres.conseil.map

import be.ombres.conseil.ocr.Pixels
import be.ombres.conseil.ocr.RecordedScreen
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Rejoue les lectures ML Kit enregistrées sur les captures de captures/ (tools/dump-ocr.sh),
 * avec les pixels des captures elles-mêmes (détection des boucliers).
 */
class MapReaderTest {

    private fun read(name: String, ourGuild: String? = null) = runBlocking {
        val json = JSONObject(javaClass.classLoader!!.getResource("ocr/$name.json").readText())
        val file = listOf("jpeg", "jpg", "png").map { File("../../captures/$name.$it") }.first { it.exists() }
        val img = ImageIO.read(file)
        val pixels = object : Pixels {
            override val width = img.width
            override val height = img.height
            override fun rgb(x: Int, y: Int) = img.getRGB(x, y) and 0xFFFFFF
        }
        MapReader.read(RecordedScreen(json, pixels), ourGuild)
    }

    /** Salle attendue : guilde (colonne du bandeau), DMG, bouclier, montant volé. */
    private fun rooms(r: MapReading) = r.rooms.map { listOf(it.guild, it.dmg, it.shield, it.stolen) }.sortedBy { it.toString() }

    @Test
    fun situation() {
        val r = read("situation")
        assertEquals(ScreenKind.MAP, r.kind)
        assertEquals(
            listOf(
                GuildRead("s18-Noxia", "724.44K", "32", listOf(0, 4, 0)),
                GuildRead("s40-Ombres", "671.54K", "32", listOf(0, 0, 1)),
                GuildRead("s43-Nexus", "538.35K", "22", listOf(0, 2, 0)),
            ),
            r.guilds,
        )
        assertEquals(listOf(4, 1, 2), r.guilds.map { it.rooms })
        assertEquals(4 to 30, r.hours to r.minutes)
        assertEquals(
            listOf(
                listOf(0, "2.39B", false, null),
                listOf(0, "200.63M", false, null),
                listOf(0, "42.15B", false, "5.25K"),
                listOf(0, "9.45B", false, null),
                listOf(1, "1.41B", true, null),
                listOf(2, "429.56M", false, null),
                listOf(2, "57.45B", false, "37.9K"),
            ).sortedBy { it.toString() },
            rooms(r),
        )
        // La 3e salle libre est en partie cachée par le bouton « Classement » : production non lue.
        assertEquals(listOf("2", "3", null, "3"), r.free.map { it.rate })
    }

    @Test
    fun situation2() {
        val r = read("situation2")
        assertEquals(ScreenKind.MAP, r.kind)
        assertEquals(listOf("890.78K", "828.91K", "640.46K"), r.guilds.map { it.soul })
        assertEquals(listOf("21", "36", "30"), r.guilds.map { it.rate })
        assertEquals(listOf(2, 2, 2), r.guilds.map { it.rooms })
        assertEquals(3 to 4, r.hours to r.minutes)
        assertEquals(
            listOf(
                listOf(0, "0", true, "22.89K"), // bouclier, et le voleur n'est lisible que par son montant
                listOf(0, "3.02B", false, null),
                listOf(1, "2.91B", true, null),
                listOf(1, "192.63M", false, null),
                listOf(2, "863.28M", false, null), // 863.23M à l'écran : 3 rouge sur jaune lu comme un 8
                listOf(2, "57.45B", false, "21.06K"),
            ).sortedBy { it.toString() },
            rooms(r),
        )
        assertEquals(listOf("4", "3", "4", "2"), r.free.map { it.rate })
    }

    @Test
    fun situation3() {
        val r = read("situation3")
        assertEquals(listOf("933.49K", "876.28K", "669.3K"), r.guilds.map { it.soul })
        assertEquals(listOf("40", "32", "36"), r.guilds.map { it.rate })
        assertEquals(listOf(4, 1, 2), r.guilds.map { it.rooms })
        assertEquals(2 to 42, r.hours to r.minutes) // lu « 2:h 42 min »
        assertEquals(
            listOf(
                listOf(0, "1.89B", false, null),
                listOf(0, "7.48B", false, null),
                listOf(0, "881.11M", true, null),
                listOf(0, "355.18M", true, null),
                listOf(1, "2.91B", true, null),
                listOf(2, "863.28M", false, "2.91K"),
                listOf(2, "57.45B", false, null),
            ).sortedBy { it.toString() },
            rooms(r),
        )
        // 6 salles libres à l'écran ; celle du haut à droite, sous « Serveurs participants », n'est pas vue.
        assertEquals(listOf("2", "3", "3", "2", null), r.free.map { it.rate })
    }

    /** Même carte que situation3, capturée par la bulle par-dessus une visionneuse photo (signalée depuis l'appli). */
    @Test
    fun bubbleCaptureOfSituation3() {
        val r = read("lecture-20261031-230804", ourGuild = "s40-Ombres")
        // Notre nom est caché par l'heure de la visionneuse : nos salles sont reconnues grâce au nom réglé.
        assertEquals(listOf("s18-Noxia", null, "s43-Nexus"), r.guilds.map { it.name })
        assertEquals(2 to 42, r.hours to r.minutes)
        assertEquals(
            listOf(
                listOf(0, "1.89B", false, null),
                listOf(0, "7.48B", false, null),
                listOf(0, "881.11M", true, null),
                listOf(0, "355.18M", true, null),
                listOf(1, "2.91B", true, null), // « DMG&2.91B » : deux-points lu comme « & »
                listOf(2, "863.28M", false, "2.91K"),
                listOf(2, "57.45B", false, null),
            ).sortedBy { it.toString() },
            rooms(r),
        )
    }

    @Test
    fun chest() {
        val r = read("cartes")
        assertEquals(ScreenKind.CHEST, r.kind)
        assertEquals(mapOf("em" to 1, "sa" to 5, "gi" to 1, "ss" to 4), r.cards)
        assertEquals(3, r.today)
        assertEquals(3 to 31, r.hours to r.minutes)
        // Le bandeau est assombri par la fenêtre du coffre : scores lus, salles non.
        assertEquals(listOf("795.52K", "776.62K", "590.78K"), r.guilds.map { it.soul })
        assertEquals(listOf("30", "32", "22"), r.guilds.map { it.rate })
        assertEquals(listOf(null, null, null), r.guilds.map { it.rooms })
    }

    @Test
    fun chest2() {
        val r = read("cartes2")
        assertEquals(ScreenKind.CHEST, r.kind)
        assertEquals(mapOf("em" to 0, "sa" to 5, "gi" to 0, "ss" to 3), r.cards)
        assertEquals(0, r.today)
        assertEquals(listOf("890.78K", "828.91K", "640.46K"), r.guilds.map { it.soul })
    }

    @Test
    fun parseTime() {
        assertEquals(4 to 30, MapReader.parseTime("Fin dans:4h 30 min"))
        assertEquals(3 to 31, MapReader.parseTime("Fin dans:3 h31Nmin participants"))
        assertEquals(0 to 45, MapReader.parseTime("Fin dans : 45 min"))
        assertEquals(null, MapReader.parseTime("Fin dans3 h3mi"))
        assertEquals(2 to 42, MapReader.parseTime("Fin dans :2:h 42 min"))
        assertEquals(2 to 42, MapReader.parseTime("Fin dans:2:h, 42 min")) // Tesseract (version web)
        assertEquals(2 to 42, MapReader.parseTime("Fin dans :2:h.42 min partiéipanttes:"))
    }

    @Test
    fun dmgSeparatorMisreads() {
        // Le deux-points de « DMG : » lu comme « & » ou comme « 8 » : la valeur reste 2.91B.
        assertEquals(listOf("2.91B", "2.91B", "863.23M"), listOf("DMG&2.91B", "DMG82.91B", "DMG:863.23M").map { MapReader.parseDmg(listOf(it)) })
        assertEquals("0", MapReader.parseDmg(listOf("DMG : 0")))
    }
}
