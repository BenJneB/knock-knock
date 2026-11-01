package be.ombres.conseil.map

import be.ombres.conseil.game.GuildIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoulRatesTest {
    // Captures situation (4 h 30) puis situation3 (2 h 42), soit 108 min plus tard.
    private val before = SoulSnapshot(0, listOf(GuildIn("s18-Noxia", "724.44K", "32"), GuildIn("s40-Ombres", "671.54K", "32"), GuildIn("s43-Nexus", "538.35K", "22")))
    private val after = SoulSnapshot(108 * 60_000L, listOf(GuildIn("s18-Noxia", "933.49K", "40"), GuildIn("s40-Ombres", "876.28K", "32"), GuildIn("s43-Nexus", "669.3K", "36")))

    @Test
    fun measuredRatesBetweenTwoCaptures() {
        val m = measureRates(before, after).associate { it.name to it.measured }
        assertEquals(209_050.0 / 6480, m["s18-Noxia"]!!, 1e-9) // 32,3/s
        assertEquals(204_740.0 / 6480, m["s40-Ombres"]!!, 1e-9) // 31,6/s
        assertEquals(130_950.0 / 6480, m["s43-Nexus"]!!, 1e-9) // 20,2/s
    }

    @Test
    fun notesWhenMeasuredDiffersFromShown() {
        val notes = rateNotes(measureRates(before, after))
        // Noxia affiche 40/s mais a gagné 32/s en moyenne ; Nexus affiche 36/s pour 20/s mesurés ; Ombres colle.
        assertEquals(2, notes.size)
        assertTrue(notes[0], notes[0].startsWith("Noxia a gagné moins que son efficacité affichée : 32/s mesurés sur 108 min (affiché 40/s)"))
        assertTrue(notes.none { it.startsWith("Ombres") })
    }

    @Test
    fun tooCloseOrNewMatchGivesNothing() {
        assertTrue(measureRates(before, after.copy(time = 60_000)).isEmpty())
        assertTrue(measureRates(after, before.copy(time = 108 * 60_000L * 2)).isEmpty()) // Âme en baisse
    }
}
