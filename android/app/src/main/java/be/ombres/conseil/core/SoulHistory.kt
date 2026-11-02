package be.ombres.conseil.core

import android.content.Context
import androidx.core.content.edit
import be.ombres.conseil.game.GuildIn
import be.ombres.conseil.map.SoulSnapshot
import be.ombres.conseil.map.measureRates
import be.ombres.conseil.map.rateNotes
import org.json.JSONArray
import org.json.JSONObject

/** Garde l'Âme lue à chaque capture (par mode) pour mesurer la vraie production à la capture suivante. */
object SoulHistory {
    private fun sp(c: Context) = c.getSharedPreferences("ames", Context.MODE_PRIVATE)

    /** Remarques sur la production mesurée depuis la lecture précédente ; mémorise la lecture actuelle. */
    fun notes(context: Context, modeId: String, guilds: List<GuildIn>): List<String> {
        val cur = SoulSnapshot(System.currentTimeMillis(), guilds)
        val prev = sp(context).getString(modeId, null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }
        if (guilds.any { it.n.isNotEmpty() && it.a.isNotEmpty() }) sp(context).edit { putString(modeId, toJson(cur).toString()) }
        return if (prev == null) emptyList() else rateNotes(measureRates(prev, cur))
    }

    private fun toJson(s: SoulSnapshot) = JSONObject().put("time", s.time)
        .put("guilds", JSONArray(s.guilds.map { JSONObject().put("n", it.n).put("a", it.a).put("r", it.r) }))

    private fun fromJson(o: JSONObject): SoulSnapshot {
        val g = o.getJSONArray("guilds")
        return SoulSnapshot(o.getLong("time"), List(g.length()) { g.getJSONObject(it).run { GuildIn(optString("n"), optString("a"), optString("r")) } })
    }
}
