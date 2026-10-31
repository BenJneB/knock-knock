package be.ombres.conseil.mode.zonesombre

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Compare le port Kotlin à la version JavaScript sur les scénarios générés par
 * tools/golden-plan.mjs. Régénérer le fichier (node tools/zs-golden-plan.mjs) après toute modification de plan() côté web.
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

    @Test
    fun sameResultsAsJavaScript() {
        val text = javaClass.classLoader!!.getResource("zs-plan-golden.json").readText()
        val cases = JSONArray(text)
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
        }
    }
}
