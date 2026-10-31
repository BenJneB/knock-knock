package be.ombres.conseil.core

import android.content.Context
import be.ombres.conseil.mode.GameMode
import be.ombres.conseil.mode.Modes
import be.ombres.conseil.mode.ReadReport
import org.json.JSONArray
import org.json.JSONObject

/** Réglages communs à tous les modes, stockés sur le téléphone. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("conseil", Context.MODE_PRIVATE)

    fun guild(c: Context): String = sp(c).getString("guild", null) ?: "s40-Ombres"
    fun setGuild(c: Context, v: String) = sp(c).edit().putString("guild", v).apply()

    fun mode(c: Context): GameMode = Modes.byId(sp(c).getString("mode", null))
    fun setMode(c: Context, m: GameMode) = sp(c).edit().putString("mode", m.id).apply()

    fun lastReport(c: Context): ReadReport? {
        val s = sp(c).getString("lastReport", null) ?: return null
        val o = JSONObject(s)
        fun list(k: String) = o.optJSONArray(k)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
        return ReadReport(o.getString("summary"), list("missing"), list("notes"))
    }

    fun setLastReport(c: Context, r: ReadReport?) = sp(c).edit().putString(
        "lastReport",
        r?.let { JSONObject().put("summary", it.summary).put("missing", JSONArray(it.missing)).put("notes", JSONArray(it.notes)).toString() },
    ).apply()
}
