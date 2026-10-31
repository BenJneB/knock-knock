package be.ombres.conseil.mode.capitale

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import be.ombres.conseil.R
import be.ombres.conseil.core.Analyzer
import be.ombres.conseil.core.Prefs
import be.ombres.conseil.game.GuildIn
import be.ombres.conseil.map.MapReader
import be.ombres.conseil.mode.GameMode
import be.ombres.conseil.mode.ReadReport
import be.ombres.conseil.ocr.Screen
import be.ombres.conseil.ui.Bullets
import be.ombres.conseil.ui.Field
import be.ombres.conseil.ui.Hint
import be.ombres.conseil.ui.NumberedStep
import be.ombres.conseil.ui.Section

object CapitaleMode : GameMode {
    override val id = "capitale"
    override val title = "Défense de la capitale"

    override suspend fun read(context: Context, screen: Screen): ReadReport {
        val st = DcStore.load(context)
        val ours = Prefs.guild(context)
        val reading = MapReader.read(screen, ours, st.guilds.map { it.n })
        val r = dcMerge(st, reading, ours)
        DcStore.save(context, r.input)
        return ReadReport(r.summary, r.missing, r.notes)
    }

    @Composable
    override fun Settings(context: Context) {
        Hint("Pas de cartes d’exorcisme dans ce mode : le plan répartit vos tentatives de défi (1 gratuite et 10 payantes par joueur et par jour).")
    }

    @Composable
    override fun Council(context: Context, report: ReadReport?) {
        var st by remember { mutableStateOf(DcStore.load(context)) }
        fun upd(f: (DcInput) -> DcInput) { st = f(st); DcStore.save(context, st) }

        if (report != null) Section("Lecture", R.drawable.ic_eye, MaterialTheme.colorScheme.tertiaryContainer) {
            Text(report.summary)
            if (report.notes.isNotEmpty()) Bullets(report.notes)
            if (report.missing.isNotEmpty()) {
                Text("À vérifier ou saisir :", fontWeight = FontWeight.Bold)
                Bullets(report.missing)
            }
            if (Analyzer.hasLast(context)) OutlinedButton(onClick = { Analyzer.shareLast(context) }) { Text("Signaler une lecture ratée") }
        }

        when (val r = dcPlan(st)) {
            is DcErrors -> Section("Il manque des valeurs", R.drawable.ic_eye, MaterialTheme.colorScheme.tertiaryContainer) { Bullets(r.errs) }
            is DcOk -> {
                val v = dcVerdict(r)
                Section(v.title, R.drawable.ic_trophy, if (v.good) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer) {
                    v.lines.forEach { Text(it) }
                }
                Section("Le plan", R.drawable.ic_bubble) {
                    Column { r.steps.forEachIndexed { i, s -> NumberedStep(i + 1, s.t, s.d, s.c) } }
                    OutlinedButton(onClick = { copy(context, dcPlanText(r)) }) { Text("Copier le plan") }
                }
                Section("Défense", R.drawable.ic_shield_bubble) { Bullets(r.defense) }
            }
        }
        Inputs(st, ::upd)
    }

    private fun copy(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Plan de bataille", text))
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Plan copié : collez-le dans le chat de guilde.", Toast.LENGTH_SHORT).show()
    }

    @Composable
    private fun Inputs(st: DcInput, upd: ((DcInput) -> DcInput) -> Unit) {
        Section("Les trois guildes", R.drawable.ic_house) {
            Hint("Lues dans le bandeau du haut. Corrigez si besoin ; « Nous » = votre guilde.")
            st.guilds.forEachIndexed { i, g ->
                fun set(ng: GuildIn) = upd { s -> s.copy(guilds = s.guilds.toMutableList().also { it[i] = ng }) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field("Nom de la guilde", g.n, { set(g.copy(n = it)) }, Modifier.weight(1f), KeyboardType.Text)
                    RadioButton(selected = st.me == i, onClick = { upd { it.copy(me = i) } })
                    Text("Nous")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field("Âme", g.a, { set(g.copy(a = it)) }, Modifier.weight(1.4f), KeyboardType.Text)
                    Field("Prod. /s", g.r, { set(g.copy(r = it)) }, Modifier.weight(1f), KeyboardType.Decimal)
                    Field("Salles", g.s, { set(g.copy(s = it)) }, Modifier.weight(0.9f))
                }
            }
        }

        Section("Temps et tentatives", R.drawable.ic_hourglass) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Heures", st.th, { v -> upd { it.copy(th = v) } }, Modifier.weight(1f))
                Field("Minutes", st.tm, { v -> upd { it.copy(tm = v) } }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Tentatives à jouer", st.attempts, { v -> upd { it.copy(attempts = v) } }, Modifier.weight(1f))
                Field("Meilleur DMG d’un joueur", st.ourDmg, { v -> upd { it.copy(ourDmg = v) } }, Modifier.weight(1.3f), KeyboardType.Text)
            }
            Hint("Tentatives : 1 gratuite par joueur et par jour, jusqu’à 10 payantes. Une salle se prend en infligeant plus que son record de défense.")
        }

        Section("Salles à prendre", R.drawable.ic_house) {
            Hint("Salles des autres guildes (guilde + record de défense) et salles libres (efficacité /s, sans guilde). Repérées sur la carte du match.")
            if (st.rooms.isEmpty()) Hint("Aucune salle.")
            st.rooms.forEachIndexed { i, r ->
                fun set(nr: DcRoom) = upd { s -> s.copy(rooms = s.rooms.toMutableList().also { it[i] = nr }) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field("Salle", r.n, { set(r.copy(n = it)) }, Modifier.weight(1f), KeyboardType.Text)
                    IconButton(onClick = { upd { s -> s.copy(rooms = s.rooms.toMutableList().also { it.removeAt(i) }) } }) { Text("✕") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field("Guilde", r.owner, { set(r.copy(owner = it)) }, Modifier.weight(1f), KeyboardType.Text)
                    if (r.owner.isNotEmpty()) Field("Record DMG", r.dmg, { set(r.copy(dmg = it)) }, Modifier.weight(1f), KeyboardType.Text)
                    else Field("Efficacité /s", r.rate, { set(r.copy(rate = it)) }, Modifier.weight(1f), KeyboardType.Decimal)
                }
            }
            OutlinedButton(onClick = { upd { it.copy(rooms = it.rooms + DcRoom()) } }) { Text("Ajouter une salle") }
        }

        Section("Nos salles", R.drawable.ic_shield_bubble) {
            Hint("Leur record de défense sert à repérer celles qu’une autre guilde peut prendre.")
            if (st.ourRooms.isEmpty()) Hint("Aucune salle.")
            st.ourRooms.forEachIndexed { i, r ->
                fun set(nr: DcRoom) = upd { s -> s.copy(ourRooms = s.ourRooms.toMutableList().also { it[i] = nr }) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field("Salle", r.n, { set(r.copy(n = it)) }, Modifier.weight(1.3f), KeyboardType.Text)
                    Field("Record DMG", r.dmg, { set(r.copy(dmg = it)) }, Modifier.weight(1f), KeyboardType.Text)
                    IconButton(onClick = { upd { s -> s.copy(ourRooms = s.ourRooms.toMutableList().also { it.removeAt(i) }) } }) { Text("✕") }
                }
            }
            OutlinedButton(onClick = { upd { it.copy(ourRooms = it.ourRooms + DcRoom(owner = "nous")) } }, modifier = Modifier.fillMaxWidth()) { Text("Ajouter une salle") }
        }
    }
}
