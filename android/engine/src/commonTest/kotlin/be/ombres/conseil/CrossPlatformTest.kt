package be.ombres.conseil

import be.ombres.conseil.game.GuildIn
import be.ombres.conseil.game.RoomType
import be.ombres.conseil.game.fixed
import be.ombres.conseil.game.fmt
import be.ombres.conseil.game.parseNum
import be.ombres.conseil.map.MapReader
import be.ombres.conseil.map.SoulSnapshot
import be.ombres.conseil.map.measureRates
import be.ombres.conseil.mode.capitale.DcInput
import be.ombres.conseil.mode.capitale.DcOk
import be.ombres.conseil.mode.capitale.DcRoom
import be.ombres.conseil.mode.capitale.dcPlan
import be.ombres.conseil.mode.zonesombre.PlanInput
import be.ombres.conseil.mode.zonesombre.PlanOk
import be.ombres.conseil.mode.zonesombre.RoomIn
import be.ombres.conseil.mode.zonesombre.plan
import be.ombres.conseil.mode.zonesombre.planText
import be.ombres.conseil.ocr.Votes
import be.ombres.conseil.ocr.digits
import be.ombres.conseil.ocr.fold
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exécutés sur la JVM (appli Android) et en JavaScript (page web) : mêmes entrées, mêmes résultats attendus.
 * Les expressions régulières, l'arrondi et la mise en forme des nombres diffèrent d'une plateforme à l'autre.
 */
class CrossPlatformTest {

    @Test
    fun numbers() {
        assertEquals(724_440.0, parseNum("724.44K"))
        assertEquals(671_540.0, parseNum("671,54K"))
        assertEquals(null, parseNum("abc"))
        assertEquals("646.0", fixed(646.05, 1)) // valeur binaire exacte, comme Number.toFixed
        assertEquals("1.32M", fmt(1_318_000.0))
        assertEquals("24", fmt(24.0))
    }

    @Test
    fun text() {
        assertEquals("ame efficacite", fold("Âme Efficacité"))
        assertEquals(0, digits("()"))
        assertEquals(1, digits("L"))
        assertEquals(null, digits("abc"))
    }

    @Test
    fun timeAndDmg() {
        assertEquals(4 to 30, MapReader.parseTime("Fin dans:4h 30 min"))
        assertEquals(2 to 42, MapReader.parseTime("Fin dans :2:h 42 min"))
        assertEquals(2 to 42, MapReader.parseTime("Fin dans:2:h, 42 min"))
        assertEquals(0 to 45, MapReader.parseTime("Fin dans : 45 min"))
        assertEquals(null, MapReader.parseTime("Fin dans3 h3mi"))
        assertEquals("2.91B", MapReader.parseDmg(listOf("DMG&2.91B")))
        assertEquals("2.91B", MapReader.parseDmg(listOf("DMG82.91B")))
        assertEquals("1.41B", MapReader.parseDmg(listOf("DMG: 141B")))
        assertEquals("0", MapReader.parseDmg(listOf("DMG : 0")))
    }

    @Test
    fun votes() {
        val v = Votes<String>()
        v.add("28K"); v.add("876.28K"); v.add("28K")
        assertEquals("28K", v.best())
        assertEquals("876.28K", v.bestMerged())
    }

    @Test
    fun zoneSombrePlan() {
        val st = PlanInput(
            guilds = listOf(GuildIn("s18-Noxia", "890.78K", "21", "2"), GuildIn("s40-Ombres", "828.91K", "36", "2"), GuildIn("s43-Nexus", "640.46K", "30", "2")),
            me = 1, th = "3", tm = "4", sa = "5", ss = "3", today = "2",
            rooms = listOf(RoomIn("bas à gauche", sh = true, b = true), RoomIn("bas à droite", sh = false, b = false)),
        )
        val r = plan(st) as PlanOk
        assertEquals("s18-Noxia", r.rival.n)
        assertEquals(
            "Conseil de guerre — Zone sombre (fin dans 3 h 04)\n" +
                "En tête : Au rythme actuel, vous finissez devant s18-Noxia avec 103.7K d’avance.\n" +
                "Rien à attaquer : tenez vos positions et économisez vos cartes.\n" +
                "• Il vous restera : 0 Esprit malveillant, 5 Sape d’âme, 0 Garde immaculée, 3 Sanctuaire sacré. Ces cartes ne se renouvellent pas pendant la phase éliminatoire.",
            planText(r),
        )
    }

    /** Zone sombre : la valeur d'une salle suit son type et son bonus (salle suprême = 4 lits × 8/s = 32/s). */
    @Test
    fun zoneSombreRoomValues() {
        val st = PlanInput(
            guilds = listOf(GuildIn("s18-Noxia", "900K", "40", "2"), GuildIn("s40-Ombres", "800K", "30", "2"), GuildIn("s43-Nexus", "500K", "20", "2")),
            me = 1, th = "2", tm = "0",
            rooms = listOf(
                RoomIn("basique", sh = false, b = true, type = "BASIC"),
                RoomIn("suprême", sh = false, b = true, type = "SUPREME"),
            ),
        )
        val r = plan(st) as PlanOk
        assertEquals("Attaquer suprême en combat", r.steps.first().t)
        assertEquals(30.0 + 32.0, r.myR) // notre production gagne celle de la salle suprême
        assertEquals(40.0 - 32.0, r.enR)
    }

    @Test
    fun capitalePlan() {
        val st = DcInput(
            guilds = listOf(GuildIn("s43-Nexus", "864.6K", "50", "3"), GuildIn("s40-Ombres", "417.72K", "24", "2"), GuildIn("s42-44", "229.8K", "18", "1")),
            me = 1, th = "5", tm = "10", attempts = "1", ourDmg = "2.02B",
            rooms = listOf(
                DcRoom("bas à droite", "Nexus", "506.19M", type = "INTERMEDIATE", buff = true),
                DcRoom("milieu à gauche", "44", "791.65M", type = "INTERMEDIATE", buff = true),
            ),
            ourRooms = listOf(DcRoom("haut", "Ombres", "2.02B", type = "INTERMEDIATE"), DcRoom("droite", "Ombres", "355.06M", type = "INTERMEDIATE")),
        )
        val r = dcPlan(st) as DcOk
        assertEquals(listOf("Remplir les lits de vos salles", "Défier bas à droite (Nexus)"), r.steps.map { it.t })
        assertEquals(18.0, RoomType.INTERMEDIATE.full(buff = true))
    }

    @Test
    fun measuredRates() {
        val m = measureRates(
            SoulSnapshot(0, listOf(GuildIn("s18-Noxia", "724.44K", "32"))),
            SoulSnapshot(6_480_000, listOf(GuildIn("s18-Noxia", "933.49K", "40"))),
        )
        assertEquals(209_050.0 / 6480, m.single().measured, 1e-9)
    }
}
