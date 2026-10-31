// Point d'entrée de la page web (web/conseil-zone-sombre-chefs.html) : même moteur que l'appli Android.
// Hors de tout paquet, pour que les fonctions soient accessibles directement : conseil.zsPlan(st).
@file:OptIn(ExperimentalJsExport::class)

import be.ombres.conseil.game.GuildIn
import be.ombres.conseil.map.MapReader
import be.ombres.conseil.mode.zonesombre.merge
import be.ombres.conseil.ocr.CropRequest
import be.ombres.conseil.ocr.OcrLine
import be.ombres.conseil.ocr.OcrPage
import be.ombres.conseil.ocr.Pixels
import be.ombres.conseil.ocr.Screen
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlin.js.Promise
import be.ombres.conseil.mode.zonesombre.PlanErrors
import be.ombres.conseil.mode.zonesombre.PlanInput
import be.ombres.conseil.mode.zonesombre.PlanOk
import be.ombres.conseil.mode.zonesombre.RoomIn
import be.ombres.conseil.mode.zonesombre.emptyPlanText
import be.ombres.conseil.mode.zonesombre.plan
import be.ombres.conseil.mode.zonesombre.planText
import be.ombres.conseil.mode.zonesombre.verdict

internal fun str(v: dynamic): String = if (v == null || v == undefined) "" else v.toString()

internal fun obj(vararg kv: Pair<String, Any?>): dynamic {
    val o: dynamic = js("({})")
    kv.forEach { (k, v) -> o[k] = v }
    return o
}

/** L'état de la page (mêmes champs que PlanInput, valeurs telles que saisies) → PlanInput. */
private fun input(st: dynamic): PlanInput {
    val guilds = (st.guilds as Array<dynamic>).map { GuildIn(str(it.n), str(it.a), str(it.r), str(it.s)) }
    val rooms = (st.rooms as Array<dynamic>).map { RoomIn(str(it.n), str(it.sh) == "1", str(it.b) == "1") }
    return PlanInput(
        guilds = guilds, me = (st.me as Number).toInt(),
        th = str(st.th), tm = str(st.tm), round = str(st.round),
        em = str(st.em), sa = str(st.sa), gi = str(st.gi), ss = str(st.ss), today = str(st.today),
        rooms = rooms, shield = str(st.shield) != "0",
        free = str(st.free), freeV = str(st.freeV), sapeP = str(st.sapeP),
    )
}

/**
 * Calcule le plan. Renvoie { errs } si des valeurs manquent, sinon le résultat de plan()
 * avec, en plus, verdict, texte vide et texte à partager.
 */
@JsExport
fun zsPlan(st: dynamic): dynamic = when (val r = plan(input(st))) {
    is PlanErrors -> obj("errs" to r.errs.toTypedArray())
    is PlanOk -> {
        val v = verdict(r)
        obj(
            "rival" to obj("n" to r.rival.n),
            "T" to r.T, "room" to r.room, "margin0" to r.margin0, "margin" to r.margin, "myR" to r.myR, "enR" to r.enR,
            "steps" to r.steps.map { obj("t" to it.t, "d" to it.d, "c" to it.c) }.toTypedArray(),
            "threats" to r.threats.toTypedArray(), "keep" to r.keep.toTypedArray(), "overtake" to r.overtake,
            "verdict" to obj("win" to v.win, "title" to v.title, "lines" to v.lines.toTypedArray(), "hint" to v.hint),
            "emptyText" to emptyPlanText(r),
            "text" to planText(r),
        )
    }
}

/* ---------- lecture d'une capture dans le navigateur ---------- */

/**
 * Capture fournie par la page : texte lu sur la capture entière, relecture d'une zone agrandie
 * (promesse ; coordonnées déjà ramenées à la capture) et pixels. Lignes : { t: texte, b: [gauche, haut, droite, bas] }.
 */
external interface JsScreen {
    val width: Int
    val height: Int
    val lines: Array<dynamic>
    val elements: Array<dynamic>
    fun ocr(req: dynamic): Promise<dynamic>
    fun rgb(x: Int, y: Int): Int
}

private fun ocrLines(a: Array<dynamic>) = a.map {
    val b = it.b
    OcrLine(str(it.t), (b[0] as Number).toInt(), (b[1] as Number).toInt(), (b[2] as Number).toInt(), (b[3] as Number).toInt())
}

internal class WebScreen(private val js: JsScreen) : Screen {
    override val page = OcrPage(js.width, js.height, ocrLines(js.lines), ocrLines(js.elements))
    override val pixels = object : Pixels {
        override val width = js.width
        override val height = js.height
        override fun rgb(x: Int, y: Int) = js.rgb(x, y)
    }

    override suspend fun ocr(req: CropRequest): OcrPage {
        val r = js.ocr(
            obj("left" to req.left, "top" to req.top, "right" to req.right, "bottom" to req.bottom, "scale" to req.scale, "filter" to req.filter.name, "hint" to req.hint.name)
        ).await()
        return OcrPage(js.width, js.height, ocrLines(r.lines as Array<dynamic>), ocrLines(r.elements as Array<dynamic>))
    }
}

/** PlanInput → état de la page (mêmes conventions que la saisie : "1"/"0" pour oui/non). */
private fun webState(st: PlanInput): dynamic = obj(
    "guilds" to st.guilds.map { obj("n" to it.n, "a" to it.a, "r" to it.r, "s" to it.s) }.toTypedArray(),
    "me" to st.me, "th" to st.th, "tm" to st.tm, "round" to st.round,
    "em" to st.em, "sa" to st.sa, "gi" to st.gi, "ss" to st.ss, "today" to st.today,
    "rooms" to st.rooms.map { obj("n" to it.n, "sh" to if (it.sh) "1" else "0", "b" to if (it.b) "1" else "0") }.toTypedArray(),
    "shield" to if (st.shield) "1" else "0", "free" to st.free, "freeV" to st.freeV, "sapeP" to st.sapeP,
)

/**
 * Lit une capture (carte du match ou coffre) et reporte ce qui a été lu dans l'état de la page,
 * exactement comme l'appli Android. Résultat : { st, kind, summary, notes, missing }.
 */
@OptIn(DelicateCoroutinesApi::class)
@JsExport
fun zsRead(screen: JsScreen, st: dynamic, ourGuild: String): Promise<dynamic> = GlobalScope.promise {
    val input = input(st)
    val reading = MapReader.read(WebScreen(screen), ourGuild, input.guilds.map { it.n })
    val r = merge(input, reading, ourGuild)
    obj(
        "st" to webState(r.input), "kind" to reading.kind.name, "summary" to r.summary,
        "notes" to r.notes.toTypedArray(), "missing" to r.missing.toTypedArray(),
    )
}
