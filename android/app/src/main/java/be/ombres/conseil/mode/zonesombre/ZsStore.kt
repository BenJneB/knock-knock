package be.ombres.conseil.mode.zonesombre

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Mémorise les valeurs du mode Zone sombre (scores, cartes, salles du rival, réglages). */
object ZsStore {
    private fun sp(c: Context) = c.getSharedPreferences("zonesombre", Context.MODE_PRIVATE)

    fun load(c: Context): PlanInput =
        sp(c).getString("input", null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() } ?: PlanInput()

    fun save(c: Context, st: PlanInput) = sp(c).edit().putString("input", toJson(st).toString()).apply()

    fun toJson(st: PlanInput): JSONObject = JSONObject()
        .put("guilds", JSONArray(st.guilds.map { JSONObject().put("n", it.n).put("a", it.a).put("r", it.r).put("s", it.s) }))
        .put("me", st.me).put("th", st.th).put("tm", st.tm).put("round", st.round)
        .put("em", st.em).put("sa", st.sa).put("gi", st.gi).put("ss", st.ss).put("today", st.today)
        .put("rooms", JSONArray(st.rooms.map { JSONObject().put("n", it.n).put("sh", it.sh).put("b", it.b) }))
        .put("shield", st.shield).put("free", st.free).put("freeV", st.freeV).put("sapeP", st.sapeP)

    fun fromJson(o: JSONObject): PlanInput {
        val d = PlanInput()
        val g = o.getJSONArray("guilds")
        val r = o.optJSONArray("rooms") ?: JSONArray()
        return PlanInput(
            guilds = List(g.length()) { g.getJSONObject(it).run { GuildIn(optString("n"), optString("a"), optString("r"), optString("s")) } },
            me = o.optInt("me", d.me),
            th = o.optString("th", d.th), tm = o.optString("tm", d.tm), round = o.optString("round", d.round),
            em = o.optString("em", d.em), sa = o.optString("sa", d.sa), gi = o.optString("gi", d.gi), ss = o.optString("ss", d.ss),
            today = o.optString("today", d.today),
            rooms = List(r.length()) { r.getJSONObject(it).run { RoomIn(optString("n"), optBoolean("sh"), optBoolean("b", true)) } },
            shield = o.optBoolean("shield", d.shield),
            free = o.optString("free", d.free), freeV = o.optString("freeV", d.freeV), sapeP = o.optString("sapeP", d.sapeP),
        )
    }
}
