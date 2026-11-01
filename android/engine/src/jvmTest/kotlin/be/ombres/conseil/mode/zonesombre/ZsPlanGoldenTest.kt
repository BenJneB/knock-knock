package be.ombres.conseil.mode.zonesombre

import be.ombres.conseil.game.GuildIn
import be.ombres.conseil.game.Step
import be.ombres.conseil.game.jsNum
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Non-régression du moteur : 400 scénarios dont les résultats ont été produits par l'ancien moteur JavaScript
 * de la page web. Le même fichier vérifie la version compilée en JavaScript (tools/check-web-engine.mjs).
 * Un changement voulu de stratégie doit mettre à jour ce fichier : CONSEIL_REGENERER=1 ./gradlew :engine:jvmTest
 * le réécrit avec les résultats actuels (relire ensuite les différences avec git diff).
 */
class ZsPlanGoldenTest {

    private fun strings(a: JSONArray) = List(a.length()) { a.getString(it) }

    private fun input(o: JSONObject) = PlanInput(
        guilds = o.getJSONArray("guilds").let { a ->
            List(a.length()) { a.getJSONObject(it).run { GuildIn(getString("n"), getString("a"), getString("r"), getString("s")) } }
        },
        me = o.getInt("me"),
        th = o.getString("th"), tm = o.getString("tm"), round = o.getString("round"),
        em = o.getString("em"), sa = o.getString("sa"), gi = o.getString("gi"), ss = o.getString("ss"),
        today = o.getString("today"),
        rooms = o.getJSONArray("rooms").let { a ->
            List(a.length()) { a.getJSONObject(it).run { RoomIn(getString("n"), getString("sh") == "1", getString("b") == "1") } }
        },
        shield = o.getString("shield") != "0",
        free = o.getString("free"), freeV = o.getString("freeV"), sapeP = o.getString("sapeP"),
    )

    /** Résultat attendu d'un cas, clés dans l'ordre du fichier. */
    private fun out(r: PlanResult): Map<String, Any?> = when (r) {
        is PlanErrors -> linkedMapOf("errs" to r.errs)

        is PlanOk -> {
            val v = verdict(r)
            linkedMapOf(
                "rival" to r.rival.n, "T" to r.T, "room" to r.room, "margin0" to r.margin0, "margin" to r.margin,
                "myR" to r.myR, "enR" to r.enR, "steps" to r.steps.map { linkedMapOf("t" to it.t, "d" to it.d, "c" to it.c) },
                "threats" to r.threats, "keep" to r.keep, "overtake" to r.overtake,
                "title" to v.title, "lines" to v.lines, "hint" to v.hint, "text" to planText(r),
            )
        }
    }

    /** JSON indenté d'un espace, comme le fichier d'origine (JSON.stringify(x, null, 1)) ; garde l'ordre des clés de [st]. */
    private fun json(v: Any?, ind: String = ""): String {
        val next = "$ind "
        fun str(s: String) = buildString {
            append('"')
            for (ch in s) {
                when {
                    ch == '"' || ch == '\\' -> append('\\').append(ch)
                    ch == '\n' -> append("\\n")
                    ch < ' ' -> append("\\u%04x".format(ch.code))
                    else -> append(ch)
                }
            }
            append('"')
        }
        fun obj(entries: List<Pair<String, Any?>>) = if (entries.isEmpty()) "{}"
        else entries.joinToString(",\n", "{\n", "\n$ind}") { (k, x) -> "$next${str(k)}: ${json(x, next)}" }
        fun arr(items: List<Any?>) = if (items.isEmpty()) "[]" else items.joinToString(",\n", "[\n", "\n$ind]") { "$next${json(it, next)}" }
        return when (v) {
            null, JSONObject.NULL -> "null"

            is String -> str(v)

            is Boolean -> v.toString()

            is Int -> v.toString()

            is Double -> jsNum(v).replace("E-", "e-").replace("E", "e+")

            // notation de JavaScript
            is Map<*, *> -> obj(v.entries.map { it.key as String to it.value })

            is JSONObject -> obj(v.keys().asSequence().toList().sortedBy { ST_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: 99 }.map { it to v.get(it) })

            is List<*> -> arr(v)

            is JSONArray -> arr(List(v.length()) { v.get(it) })

            else -> error("type inattendu : $v")
        }
    }

    @Test
    fun sameResultsAsJavaScript() {
        val text = javaClass.classLoader!!.getResource("zs-plan-golden.json").readText()
        val cases = JSONArray(text)
        if (System.getenv("CONSEIL_REGENERER") == "1") {
            val all = List(cases.length()) { k ->
                val st = cases.getJSONObject(k).getJSONObject("st")
                linkedMapOf("st" to st, "out" to out(plan(input(st))))
            }
            File("src/jvmTest/resources/zs-plan-golden.json").writeText(json(all))
            return
        }
        for (k in 0 until cases.length()) {
            val c = cases.getJSONObject(k)
            val exp = c.getJSONObject("out")
            val r = plan(input(c.getJSONObject("st")))
            val tag = "cas $k"
            if (exp.has("errs")) {
                assertEquals(tag, strings(exp.getJSONArray("errs")), (r as PlanErrors).errs)
                continue
            }
            r as PlanOk
            assertEquals(tag, exp.getString("rival"), r.rival.n)
            assertEquals(tag, exp.getInt("T"), r.T)
            assertEquals(tag, exp.getDouble("room"), r.room, 0.0)
            assertEquals(tag, exp.getDouble("margin0"), r.margin0, 0.0)
            assertEquals(tag, exp.getDouble("margin"), r.margin, 0.0)
            assertEquals(tag, exp.getDouble("myR"), r.myR, 0.0)
            assertEquals(tag, exp.getDouble("enR"), r.enR, 0.0)
            val steps = exp.getJSONArray("steps")
            assertEquals(tag, List(steps.length()) { steps.getJSONObject(it).run { Step(getString("t"), getString("d"), getString("c")) } }, r.steps)
            assertEquals(tag, strings(exp.getJSONArray("threats")), r.threats)
            assertEquals(tag, strings(exp.getJSONArray("keep")), r.keep)
            assertEquals(tag, if (exp.isNull("overtake")) null else exp.getString("overtake"), r.overtake)
            val v = verdict(r)
            assertEquals(tag, exp.getString("title"), v.title)
            assertEquals(tag, strings(exp.getJSONArray("lines")), v.lines)
            assertEquals(tag, exp.getString("hint"), v.hint)
            assertEquals(tag, exp.getString("text"), planText(r))
        }
    }

    private companion object {
        val ST_ORDER = listOf(
            "guilds", "n", "a", "r", "s", "me", "th", "tm", "round", "em", "sa", "gi", "ss", "today", "rooms", "sh", "b",
            "shield", "free", "freeV", "sapeP",
        )
    }
}
