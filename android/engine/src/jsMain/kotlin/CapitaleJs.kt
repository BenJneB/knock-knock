// Point d'entrée de la page web Défense de la capitale (web/src/capitale/) : même moteur que l'appli.
@file:OptIn(ExperimentalJsExport::class)

import be.ombres.conseil.game.GuildIn
import be.ombres.conseil.map.MapReader
import be.ombres.conseil.mode.capitale.DcErrors
import be.ombres.conseil.mode.capitale.DcInput
import be.ombres.conseil.mode.capitale.DcOk
import be.ombres.conseil.mode.capitale.DcRoom
import be.ombres.conseil.mode.capitale.dcMerge
import be.ombres.conseil.mode.capitale.dcPlan
import be.ombres.conseil.mode.capitale.dcPlanText
import be.ombres.conseil.mode.capitale.dcVerdict
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.Promise

private fun dcRooms(a: dynamic): List<DcRoom> =
    if (a == null || a == undefined) emptyList()
    else (a as Array<dynamic>).map { DcRoom(str(it.n), str(it.owner), str(it.dmg), str(it.rate), str(it.type), it.buff == true) }

private fun dcInput(st: dynamic) = DcInput(
    guilds = (st.guilds as Array<dynamic>).map { GuildIn(str(it.n), str(it.a), str(it.r), str(it.s)) },
    me = (st.me as Number).toInt(), th = str(st.th), tm = str(st.tm),
    attempts = str(st.attempts), ourDmg = str(st.ourDmg),
    rooms = dcRooms(st.rooms), ourRooms = dcRooms(st.ourRooms),
)

private fun room(r: DcRoom): dynamic = obj("n" to r.n, "owner" to r.owner, "dmg" to r.dmg, "rate" to r.rate, "type" to r.type, "buff" to r.buff)

private fun dcState(st: DcInput): dynamic = obj(
    "guilds" to st.guilds.map { obj("n" to it.n, "a" to it.a, "r" to it.r, "s" to it.s) }.toTypedArray(),
    "me" to st.me, "th" to st.th, "tm" to st.tm, "attempts" to st.attempts, "ourDmg" to st.ourDmg,
    "rooms" to st.rooms.map { room(it) }.toTypedArray(),
    "ourRooms" to st.ourRooms.map { room(it) }.toTypedArray(),
)

/** Plan du mode Défense de la capitale : { errs } ou { title, good, lines, steps, defense, text }. */
@JsExport
fun dcPlanJs(st: dynamic): dynamic = when (val r = dcPlan(dcInput(st))) {
    is DcErrors -> obj("errs" to r.errs.toTypedArray())

    is DcOk -> {
        val v = dcVerdict(r)
        obj(
            "title" to v.title, "good" to v.good, "lines" to v.lines.toTypedArray(),
            "steps" to r.steps.map { obj("t" to it.t, "d" to it.d, "c" to it.c) }.toTypedArray(),
            "defense" to r.defense.toTypedArray(), "text" to dcPlanText(r),
        )
    }
}

/** Lit une capture de la carte et la reporte dans l'état de la page : { st, kind, summary, notes, missing }. */
@OptIn(DelicateCoroutinesApi::class)
@JsExport
fun dcRead(screen: JsScreen, st: dynamic, ourGuild: String): Promise<dynamic> = GlobalScope.promise {
    val input = dcInput(st)
    val reading = MapReader.read(WebScreen(screen), ourGuild, input.guilds.map { it.n })
    val r = dcMerge(input, reading, ourGuild)
    obj(
        "st" to dcState(r.input), "kind" to reading.kind.name, "summary" to r.summary,
        "notes" to r.notes.toTypedArray(), "missing" to r.missing.toTypedArray(),
    )
}
