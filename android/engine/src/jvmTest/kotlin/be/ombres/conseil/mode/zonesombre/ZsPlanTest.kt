package be.ombres.conseil.mode.zonesombre

import be.ombres.conseil.game.GuildIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZsPlanTest {

    private val base = PlanInput(
        guilds = listOf(GuildIn("s40-Ombres", "100K", "10", "1"), GuildIn("s1-Rival", "500K", "50", "4"), GuildIn("s2-Autre", "0", "0", "0")),
        th = "2", em = "1", sa = "0", gi = "0", ss = "1", today = "15", shield = true,
    )

    /**
     * Un seul Esprit : le meilleur gain par carte (vider B, intermédiaire) empêcherait de vider A (suprême, avec bouclier),
     * qui rapporte bien plus au total. L'ancien choix glouton jouait B.
     */
    @Test
    fun bestCombinationRatherThanBestCardFirst() {
        val st = base.copy(rooms = listOf(RoomIn("A", sh = true, b = false, type = "SUPREME"), RoomIn("B", sh = false, b = false, type = "INTERMEDIATE")))
        val r = plan(st) as PlanOk
        assertEquals(listOf("Retirer le bouclier de A, la vider et l’occuper"), r.steps.map { it.t })
    }

    /** À marge égale, le plan garde les Esprits malveillants (les plus rares) et joue un Sanctuaire. */
    @Test
    fun sparesRareCards() {
        val st = base.copy(
            guilds = listOf(GuildIn("s40-Ombres", "500K", "30", "1"), GuildIn("s1-Rival", "500K", "30", "2"), GuildIn("s2-Autre", "0", "0", "0")),
            rooms = listOf(RoomIn("A", sh = true, b = true, type = "INTERMEDIATE"), RoomIn("B", sh = false, b = false, type = "INTERMEDIATE")),
        )
        val r = plan(st) as PlanOk
        assertEquals(listOf("Retirer le bouclier de A, puis l’attaquer"), r.steps.map { it.t })
    }

    /** La troisième guilde, presque à égalité avec le rival, finirait devant nous même après le plan : c'est signalé. */
    @Test
    fun warnsAboutTheOtherGuild() {
        val st = base.copy(
            guilds = listOf(GuildIn("s40-Ombres", "100K", "10", "1"), GuildIn("s1-Rival", "300K", "30", "3"), GuildIn("s2-Autre", "290K", "30", "3")),
            rooms = listOf(RoomIn("A", sh = false, b = true)),
        )
        val r = plan(st) as PlanOk
        assertEquals("s1-Rival", r.rival.n)
        assertTrue(r.threats.any { it.startsWith("s2-Autre finirait aussi devant vous") })
    }
}
