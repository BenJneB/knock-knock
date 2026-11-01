package be.ombres.conseil.mode.capitale

import android.content.Context
import androidx.core.content.edit
import be.ombres.conseil.game.GuildIn
import org.json.JSONArray
import org.json.JSONObject

/** Mémorise les valeurs du mode Défense de la capitale. */
object DcStore {
    private fun sp(c: Context) = c.getSharedPreferences("capitale", Context.MODE_PRIVATE)

    fun load(c: Context): DcInput =
        sp(c).getString("input", null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() } ?: DcInput()

    fun save(c: Context, st: DcInput) = sp(c).edit { putString("input", toJson(st).toString()) }

    private fun rooms(rs: List<DcRoom>) = JSONArray(rs.map { JSONObject().put("n", it.n).put("owner", it.owner).put("dmg", it.dmg).put("rate", it.rate).put("type", it.type).put("buff", it.buff) })

    private fun rooms(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) {
        a.getJSONObject(it).run { DcRoom(optString("n"), optString("owner"), optString("dmg"), optString("rate"), optString("type"), optBoolean("buff")) }
    }

    fun toJson(st: DcInput): JSONObject = JSONObject()
        .put("guilds", JSONArray(st.guilds.map { JSONObject().put("n", it.n).put("a", it.a).put("r", it.r).put("s", it.s) }))
        .put("me", st.me).put("th", st.th).put("tm", st.tm).put("attempts", st.attempts).put("ourDmg", st.ourDmg)
        .put("rooms", rooms(st.rooms)).put("ourRooms", rooms(st.ourRooms))

    fun fromJson(o: JSONObject): DcInput {
        val d = DcInput()
        val g = o.getJSONArray("guilds")
        return DcInput(
            guilds = List(g.length()) { g.getJSONObject(it).run { GuildIn(optString("n"), optString("a"), optString("r"), optString("s")) } },
            me = o.optInt("me", d.me), th = o.optString("th"), tm = o.optString("tm"),
            attempts = o.optString("attempts", d.attempts), ourDmg = o.optString("ourDmg"),
            rooms = rooms(o.optJSONArray("rooms")), ourRooms = rooms(o.optJSONArray("ourRooms")),
        )
    }
}
