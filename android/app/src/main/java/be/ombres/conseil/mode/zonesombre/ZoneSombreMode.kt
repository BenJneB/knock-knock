package be.ombres.conseil.mode.zonesombre

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
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
import be.ombres.conseil.core.Reminders
import be.ombres.conseil.ui.Art
import be.ombres.conseil.mode.GameMode
import be.ombres.conseil.mode.ReadReport
import be.ombres.conseil.ocr.Screen
import java.util.Date
import be.ombres.conseil.ui.Bullets
import be.ombres.conseil.ui.Choice
import be.ombres.conseil.ui.Field
import be.ombres.conseil.ui.Hint
import be.ombres.conseil.ui.Labeled
import be.ombres.conseil.ui.NumberedStep
import be.ombres.conseil.ui.Section

object ZoneSombreMode : GameMode {
    override val id = "zone-sombre"
    override val title = "Affrontement Zone sombre"

    override suspend fun read(context: Context, screen: Screen): ReadReport {
        val st = ZsStore.load(context)
        val reading = ZsReader.read(screen, Prefs.guild(context), st.guilds.map { it.n })
        val r = merge(st, reading, Prefs.guild(context))
        var input = r.input
        val notes = r.notes.toMutableList()
        if (reading.kind == ZsKind.MAP && reading.rooms.isNotEmpty()) {
            // Part volée par les voleurs : mesurée entre cette lecture et la précédente.
            val snap = ZsSnapshot(System.currentTimeMillis(), input.guilds.map { it.r }, input.guilds.map { it.s }, reading.rooms)
            ZsStore.loadSnapshot(context)?.let { prev ->
                val measures = measureSape(prev, snap)
                measures.forEach { m ->
                    val who = input.guilds.getOrNull(m.guild)?.n?.substringAfter('-') ?: "?"
                    notes.add("Voleur sur $who (${m.pos}) : environ ${jsNum(kotlin.math.floor(m.share * 100 + 0.5))} % de la production de la salle, mesuré sur ${m.minutes} min.")
                }
                measuredSapePercent(measures)?.let { p ->
                    notes.add("Part volée par une Sape : $p % (mesurée), au lieu de ${input.sapeP} %.")
                    input = input.copy(sapeP = p)
                }
            }
            ZsStore.saveSnapshot(context, snap)
        }
        ZsStore.save(context, input)
        return ReadReport(r.summary, r.missing, notes)
    }

    @Composable
    override fun Settings(context: Context) {
        var st by remember { mutableStateOf(ZsStore.load(context)) }
        fun upd(f: (PlanInput) -> PlanInput) { st = f(st); ZsStore.save(context, st) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Part volée par une Sape (%)", st.sapeP, { v -> upd { it.copy(sapeP = v) } }, Modifier.weight(1f), KeyboardType.Decimal)
            Field("Prod. d’une salle libre /s", st.freeV, { v -> upd { it.copy(freeV = v) } }, Modifier.weight(1f), KeyboardType.Decimal)
        }
        Hint("La part volée par une Sape n’est pas indiquée par le jeu : 25 % est une estimation. La production d’une salle libre est mise à jour à chaque lecture de la carte.")
    }

    @Composable
    override fun Council(context: Context, report: ReadReport?) {
        var st by remember { mutableStateOf(ZsStore.load(context)) }
        fun upd(f: (PlanInput) -> PlanInput) { st = f(st); ZsStore.save(context, st) }

        if (report != null) Section("Lecture", R.drawable.ic_eye, MaterialTheme.colorScheme.tertiaryContainer) {
            Text(report.summary)
            if (report.notes.isNotEmpty()) Bullets(report.notes)
            if (report.missing.isNotEmpty()) {
                Text("À vérifier ou saisir :", fontWeight = FontWeight.Bold)
                Bullets(report.missing)
            }
            if (Analyzer.hasLast(context)) OutlinedButton(onClick = { Analyzer.shareLast(context) }) { Text("Signaler une lecture ratée") }
        }

        PlanView(context, plan(st))
        Inputs(st, ::upd)
    }

    @Composable
    private fun PlanView(context: Context, r: PlanResult) {
        if (r is PlanErrors) {
            Section("Il manque des valeurs", R.drawable.ic_eye, MaterialTheme.colorScheme.tertiaryContainer) { Bullets(r.errs) }
            return
        }
        r as PlanOk
        val v = verdict(r)
        Section(v.title, R.drawable.ic_trophy, if (v.win) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer) {
            v.lines.forEach { Text(it) }
            Hint(v.hint)
        }
        Section("Le plan", R.drawable.ic_bubble) {
            if (r.steps.isEmpty()) Text(emptyPlanText(r))
            else Column {
                r.steps.forEachIndexed { i, s ->
                    NumberedStep(i + 1, s.t, s.d, s.c, if ("Sape" in s.c) ({ SapeReminder(context, s.t) }) else null)
                }
            }
            OutlinedButton(onClick = { copyPlan(context, r) }) { Text("Copier le plan") }
        }
        if (r.keep.isNotEmpty()) Section("Cartes", R.drawable.ic_cards) { Bullets(r.keep) }
        if (r.threats.isNotEmpty()) Section("À surveiller", R.drawable.ic_eye) { Bullets(r.threats) }
    }

    @Composable
    private fun Inputs(st: PlanInput, upd: ((PlanInput) -> PlanInput) -> Unit) {
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
                    Field("Âme", g.a, { set(g.copy(a = it)) }, Modifier.weight(1.4f), KeyboardType.Text) { Art(R.drawable.ic_flame, 20) }
                    Field("Prod. /s", g.r, { set(g.copy(r = it)) }, Modifier.weight(1f), KeyboardType.Decimal)
                    Field("Salles", g.s, { set(g.copy(s = it)) }, Modifier.weight(0.9f))
                }
            }
        }

        Section("Temps et tour", R.drawable.ic_hourglass) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Heures", st.th, { v -> upd { it.copy(th = v) } }, Modifier.weight(1f))
                Field("Minutes", st.tm, { v -> upd { it.copy(tm = v) } }, Modifier.weight(1f))
            }
            Choice(listOf("1" to "Jour 1", "2" to "Jour 2", "3" to "Jour 3", "f" to "Finale"), st.round) { v -> upd { it.copy(round = v) } }
        }

        Section("Nos cartes", R.drawable.ic_cards) {
            Hint("Lues dans le coffre de cartes et mémorisées.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CardField("Esprit", R.drawable.ic_card_spirit, st.em, { v -> upd { it.copy(em = v) } }, Modifier.weight(1f))
                CardField("Sape", R.drawable.ic_card_thief, st.sa, { v -> upd { it.copy(sa = v) } }, Modifier.weight(1f))
                CardField("Garde", R.drawable.ic_card_guard, st.gi, { v -> upd { it.copy(gi = v) } }, Modifier.weight(1f))
                CardField("Sanct.", R.drawable.ic_card_sanctuary, st.ss, { v -> upd { it.copy(ss = v) } }, Modifier.weight(1f))
            }
            Field("Cartes jouables aujourd’hui", st.today, { v -> upd { it.copy(today = v) } }, Modifier.fillMaxWidth())
        }

        Section("Salles du rival", R.drawable.ic_house) {
            Hint("Repérées sur la carte du match (bouclier, voleur). « Battable » : vos joueurs peuvent la prendre en combat — estimé d’après les DMG, à corriger si besoin.")
            if (st.rooms.isEmpty()) Hint("Aucune salle ajoutée.")
            st.rooms.forEachIndexed { i, r ->
                fun set(nr: RoomIn) = upd { s -> s.copy(rooms = s.rooms.toMutableList().also { it[i] = nr }) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (r.sh) Art(R.drawable.ic_shield_bubble, 28, Modifier.padding(end = 6.dp))
                    Field("Salle", r.n, { set(r.copy(n = it)) }, Modifier.weight(1f), KeyboardType.Text)
                    IconButton(onClick = { upd { s -> s.copy(rooms = s.rooms.toMutableList().also { it.removeAt(i) }) } }) { Text("✕") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Labeled("Bouclier", R.drawable.ic_shield_bubble) { Choice(listOf(false to "Non", true to "Oui"), r.sh) { set(r.copy(sh = it)) } }
                    Labeled("Battable") { Choice(listOf(true to "Oui", false to "Non"), r.b) { set(r.copy(b = it)) } }
                }
            }
            OutlinedButton(onClick = { upd { it.copy(rooms = it.rooms + RoomIn()) } }) { Text("Ajouter une salle") }
        }

        Section("Notre défense", R.drawable.ic_card_guard) {
            Labeled("Bouclier sur notre salle principale", R.drawable.ic_shield_bubble) { Choice(listOf(true to "Oui", false to "Non"), st.shield) { v -> upd { it.copy(shield = v) } } }
            Field("Salles libres prenables", st.free, { v -> upd { it.copy(free = v) } }, Modifier.fillMaxWidth())
        }
    }

    @Composable
    private fun CardField(label: String, @DrawableRes icon: Int, value: String, onChange: (String) -> Unit, modifier: Modifier) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Art(icon, 34)
            Field(label, value, onChange, Modifier.fillMaxWidth())
        }
    }

    /** Copie le plan en texte, à coller dans le chat de guilde. */
    private fun copyPlan(context: Context, r: PlanOk) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Plan de bataille", planText(r)))
        // Depuis Android 13, le système confirme lui-même la copie.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Plan copié : collez-le dans le chat de guilde.", Toast.LENGTH_SHORT).show()
    }

    /** Une Sape dure 2 h : rappel à la fin, pour décider de la suite. */
    @Composable
    private fun SapeReminder(context: Context, step: String) {
        var at by remember(step) { mutableStateOf<Long?>(null) }
        val time = at
        if (time != null) {
            Hint("Rappel prévu vers ${DateFormat.getTimeFormat(context).format(Date(time))}.")
            return
        }
        TextButton(onClick = {
            if (!Reminders.canNotify(context)) {
                Toast.makeText(context, "Autorisez les notifications de l’appli pour recevoir le rappel.", Toast.LENGTH_LONG).show()
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                return@TextButton
            }
            at = Reminders.schedule(
                context, step.hashCode(), "Fin de la Sape d’âme",
                "${step.removePrefix("Sape d’âme sur ")} : le voleur part. Relisez la carte pour la suite du plan.",
                2 * 60 * 60 * 1000L,
            )
        }) { Text("Jouée : rappel dans 2 h") }
    }
}
