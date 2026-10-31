package be.ombres.conseil.mode.capitale

import be.ombres.conseil.game.GuildIn
import be.ombres.conseil.map.GuildRead
import be.ombres.conseil.map.FreeRoom
import be.ombres.conseil.map.MapReading
import be.ombres.conseil.map.RoomRead
import be.ombres.conseil.map.ScreenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DcPlanTest {

    // Carte « situation3 » vue comme un match de Défense de la capitale.
    private val reading = MapReading(
        ScreenKind.MAP,
        listOf(
            GuildRead("s18-Noxia", "933.49K", "40", listOf(0, 4, 0)),
            GuildRead("s40-Ombres", "876.28K", "32", listOf(0, 0, 1)),
            GuildRead("s43-Nexus", "669.3K", "36", listOf(0, 2, 0)),
        ),
        hours = 2, minutes = 42,
        rooms = listOf(
            RoomRead(0, "1.89B", false, null, "milieu à gauche", 176, 1144),
            RoomRead(0, "881.11M", true, null, "bas à gauche", 176, 1498),
            RoomRead(2, "863.28M", false, "2.91K", "haut au centre", 512, 968),
            RoomRead(1, "2.91B", true, null, "milieu au centre", 512, 1285),
            RoomRead(2, "57.45B", false, null, "bas au centre", 515, 1683),
            RoomRead(0, "7.48B", false, null, "milieu à droite", 847, 1143),
            RoomRead(0, "355.18M", true, null, "bas à droite", 848, 1496),
        ),
        free = listOf(FreeRoom("2", "haut à gauche"), FreeRoom("3", "tout en bas à gauche"), FreeRoom(null, "tout en haut")),
    )

    @Test
    fun mergeFillsRoomsAndOurStrength() {
        val r = dcMerge(DcInput(), reading, "s40-Ombres")
        assertEquals(1, r.input.me)
        assertEquals("2.91B", r.input.ourDmg)
        assertEquals(6, r.input.rooms.count { it.owner.isNotEmpty() })
        assertEquals(listOf("2", "3"), r.input.rooms.filter { it.owner.isEmpty() }.map { it.rate })
        assertEquals(listOf(DcRoom("milieu au centre", "Ombres", "2.91B")), r.input.ourRooms)
        assertEquals("2" to "42", r.input.th to r.input.tm)
        assertTrue(r.missing.isEmpty())
    }

    @Test
    fun planTakesWeakRoomsOfTheNeighbourFirst() {
        val st = dcMerge(DcInput(), reading, "s40-Ombres").input.copy(attempts = "2")
        val r = dcPlan(st) as DcOk
        assertEquals(2, r.rankBefore) // Noxia devant, Nexus derrière
        // Salles prenables (record < 2.91B) : celles de Noxia (voisine du dessus) valent double.
        assertEquals(
            listOf("Remplir les lits de vos salles", "Défier milieu à gauche · DMG 1.89B (Noxia)", "Défier bas à gauche · DMG 881.11M (Noxia)"),
            r.steps.map { it.t },
        )
        assertEquals(1, r.rankAfter)
        // Les salles à 7.48B et 57.45B dépassent notre meilleur DMG : jamais proposées.
        assertTrue(r.steps.none { "7.48B" in it.t || "57.45B" in it.t })
        // Notre salle (2.91B) est menacée par Nexus (57.45B).
        assertTrue(r.defense.first().startsWith("Votre salle milieu au centre (record 2.91B) peut être prise par Nexus (meilleur record 57.45B)"))
        assertEquals("1re place", dcVerdict(r).title)
    }

    @Test
    fun noAttemptMeansOnlyFreeActions() {
        val st = dcMerge(DcInput(), reading, "s40-Ombres").input.copy(attempts = "0")
        val r = dcPlan(st) as DcOk
        assertEquals(listOf("Remplir les lits de vos salles"), r.steps.map { it.t })
        assertEquals(r.rankBefore, r.rankAfter)
    }

    @Test
    fun missingValuesAreReported() {
        val r = dcPlan(DcInput(guilds = listOf(GuildIn("s40-Ombres"), GuildIn(), GuildIn()))) as DcErrors
        assertEquals(3, r.errs.size)
    }
}
