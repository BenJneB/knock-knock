package be.ombres.conseil.mode.zonesombre

import be.ombres.conseil.game.GuildIn
import be.ombres.conseil.map.FreeRoom
import be.ombres.conseil.map.GuildRead
import be.ombres.conseil.map.MapReading
import be.ombres.conseil.map.RoomRead
import be.ombres.conseil.map.ScreenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZsMergeTest {

    private val map = MapReading(
        ScreenKind.MAP,
        listOf(
            GuildRead("s18-Noxia", "724.44K", "32", listOf(0, 4, 0)),
            GuildRead("s40-0mbres", "671.54K", "32", listOf(0, 0, 1)),
            GuildRead("s43-Nexus", "538.35K", null, listOf(0, null, 0)),
        ),
        hours = 4, minutes = 30,
    )

    @Test
    fun mapFillsScoresAndFindsUs() {
        val r = merge(PlanInput(), map, "s40-Ombres")
        assertEquals(1, r.input.me)
        assertEquals(GuildIn("s18-Noxia", "724.44K", "32", "4"), r.input.guilds[0])
        assertEquals(GuildIn("s43-Nexus", "538.35K", "", ""), r.input.guilds[2])
        assertEquals("4" to "30", r.input.th to r.input.tm)
        assertEquals(listOf("efficacité de s43-Nexus", "salles de s43-Nexus", "salles sur la carte"), r.missing)
    }

    @Test
    fun unreadValuesKeepPreviousOnesInSameMatch() {
        val first = merge(PlanInput(), map, "s40-Ombres").input
        val chest = MapReading(
            ScreenKind.CHEST,
            listOf(
                GuildRead("s18-Noxia", "795.52K", "30", listOf(null, null, null)),
                GuildRead("s40-Ombres", "776.62K", "32", listOf(null, null, null)),
                GuildRead("s43-Nexus", "590.78K", "22", listOf(null, null, null)),
            ),
            hours = 3, minutes = 31, cards = mapOf("em" to 1, "sa" to 5, "gi" to null, "ss" to 4), today = 3,
        )
        val r = merge(first.copy(gi = "6", rooms = listOf(RoomIn("centre"))), chest, "s40-Ombres")
        assertEquals("4", r.input.guilds[0].s)
        assertEquals("795.52K", r.input.guilds[0].a)
        assertEquals(listOf("1", "5", "6", "4", "3"), with(r.input) { listOf(em, sa, gi, ss, today) })
        assertEquals(1, r.input.rooms.size)
        assertEquals(listOf("quantité de Garde immaculée"), r.missing)
    }

    @Test
    fun unreadNameIsAssumedToBeOursWhenAlone() {
        val noName = map.copy(guilds = map.guilds.mapIndexed { i, g -> if (i == 1) g.copy(name = null) else g })
        val r = merge(PlanInput(), noName, "s40-Ombres")
        assertEquals(1, r.input.me)
        assertEquals("s40-Ombres", r.input.guilds[1].n)
        assertEquals("votre guilde : supposée en colonne 2", r.missing.first())
    }

    @Test
    fun newMatchClearsRivalRooms() {
        val first = merge(PlanInput(), map, "s40-Ombres").input.copy(rooms = listOf(RoomIn("centre")))
        val other = map.copy(guilds = listOf(GuildRead("s7-Abc", "1K", "5", listOf(0, 0, 0))) + map.guilds.drop(1))
        val r = merge(first, other, "s40-Ombres")
        assertTrue(r.input.rooms.isEmpty())
        assertEquals("", r.input.guilds[2].r)
    }

    // Carte « situation2 » : Noxia est le rival, notre salle principale (2.91B) a un bouclier.
    private val map2 = MapReading(
        ScreenKind.MAP,
        listOf(
            GuildRead("s18-Noxia", "890.78K", "21", listOf(1, 1, 0)),
            GuildRead("s40-Ombres", "828.91K", "36", listOf(0, 1, 1)),
            GuildRead("s43-Nexus", "640.46K", "30", listOf(0, 2, 0)),
        ),
        hours = 3, minutes = 4,
        rooms = listOf(
            RoomRead(0, "0", true, "22.89K", "bas à gauche", 200, 1405),
            RoomRead(2, "863.28M", false, null, "haut au centre", 536, 877),
            RoomRead(1, "2.91B", true, null, "milieu au centre", 535, 1191),
            RoomRead(2, "57.45B", false, "21.06K", "bas au centre", 536, 1591),
            RoomRead(1, "192.63M", false, null, "haut à droite", 873, 1046),
            RoomRead(0, "3.02B", false, null, "tout en bas à droite", 848, 1795),
        ),
        free = listOf(FreeRoom("4", "a"), FreeRoom("3", "b"), FreeRoom("4", "c"), FreeRoom("2", "d")),
    )

    @Test
    fun roomsOfTheRivalComeFromTheMap() {
        val r = merge(PlanInput(rooms = listOf(RoomIn("ancienne"))), map2, "s40-Ombres")
        assertEquals(0, rivalIndex(r.input))
        assertEquals(
            listOf(
                RoomIn("bas à gauche · DMG 0 · voleur déjà là", sh = true, b = true),
                RoomIn("tout en bas à droite · DMG 3.02B", sh = false, b = false), // plus fort que notre meilleure salle
            ),
            r.input.rooms,
        )
        assertTrue(r.input.shield)
        assertEquals("4", r.input.free)
        assertEquals("3.3", r.input.freeV) // moyenne de 4, 3, 4 et 2 /s lus sur la carte
        assertEquals("Carte du match lue : scores, temps restant, salles.", r.summary)
        assertTrue(r.notes.contains("Salles libres : 4 (4/s, 3/s, 4/s, 2/s)."))
        assertTrue(r.notes.contains("« Battable » est estimé : DMG inférieur à celui de votre meilleure salle (2.91B). Corrigez si besoin."))
    }

    @Test
    fun thiefInOurRoomSuggestsSanctuaireOnlyIfPlayableToday() {
        val stolen = map2.copy(rooms = map2.rooms.map { if (it.dmg == "2.91B") it.copy(stolen = "3K") else it })
        val withCards = merge(PlanInput(ss = "3", today = "2"), stolen, "s40-Ombres")
        assertTrue(withCards.notes.contains("Voleur dans votre salle milieu au centre (3K volés) : un Sanctuaire sacré sur cette salle l’élimine."))
        val noneToday = merge(PlanInput(ss = "3", today = "0"), stolen, "s40-Ombres")
        assertTrue(noneToday.notes.contains("Voleur dans votre salle milieu au centre (3K volés)."))
    }

    @Test
    fun misreadNameOnChestDoesNotStartANewMatch() {
        val first = merge(PlanInput(), map2, "s40-Ombres").input
        val chest = MapReading(
            ScreenKind.CHEST,
            listOf(
                GuildRead("s18-Noxia", "900K", "21", listOf(null, null, null)),
                GuildRead("s48-HMes", "830K", "36", listOf(null, null, null)),
                GuildRead("s43-Nexus", "650K", "30", listOf(null, null, null)),
            ),
            cards = mapOf("em" to 0, "sa" to 5, "gi" to 0, "ss" to 3), today = 0,
        )
        val r = merge(first, chest, "s40-Ombres").input
        assertEquals(first.rooms, r.rooms)
        assertEquals("s40-Ombres", r.guilds[1].n)
        assertEquals("830K", r.guilds[1].a)
        assertEquals(1, r.me)
    }

    @Test
    fun announcedRoomsMissingFromTheMapAreReported() {
        val oneHidden = map2.copy(rooms = map2.rooms.filter { it.dmg != "3.02B" })
        val r = merge(PlanInput(), oneHidden, "s40-Ombres")
        assertTrue(r.missing.contains("Noxia : 2 salles annoncées, 1 vue sur la carte. Faites défiler la carte pour les voir toutes, puis relisez."))
        assertTrue(merge(PlanInput(), map2, "s40-Ombres").missing.none { "annoncées" in it })
    }

    @Test
    fun stolenShareIsMeasuredBetweenTwoCaptures() {
        val nexusTop = RoomRead(2, "863.23M", false, "2.91K", "haut au centre", 512, 968)
        val prev = ZsSnapshot(0, listOf("40", "32", "36"), listOf("4", "1", "2"), listOf(nexusTop))
        // 10 min plus tard, 1.62K de plus : 1620 / 600 s = 2.7/s, pour une salle à 36 / 2 = 18/s → 15 %.
        val cur = prev.copy(time = 600_000, rooms = listOf(nexusTop.copy(stolen = "4.53K", x = 515)))
        val m = measureSape(prev, cur)
        assertEquals(1, m.size)
        assertEquals(0.15, m[0].share, 1e-9)
        assertEquals("15", measuredSapePercent(m))
        assertTrue(measureSape(prev, cur.copy(time = 60_000)).isEmpty()) // trop rapproché
    }
}
