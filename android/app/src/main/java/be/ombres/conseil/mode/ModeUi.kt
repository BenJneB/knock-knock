package be.ombres.conseil.mode

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import be.ombres.conseil.R
import be.ombres.conseil.core.Analyzer
import be.ombres.conseil.game.GuildIn
import be.ombres.conseil.game.RoomType
import be.ombres.conseil.ui.Art
import be.ombres.conseil.ui.Bullets
import be.ombres.conseil.ui.Choice
import be.ombres.conseil.ui.Field
import be.ombres.conseil.ui.Hint
import be.ombres.conseil.ui.Section

/* Éléments d'écran communs aux modes de jeu. */

/** Bloc « Lecture » : ce que la dernière capture a apporté, ce qu'il faut vérifier, et le signalement d'une erreur. */
@Composable
fun ReadReportSection(context: Context, report: ReadReport) {
    Section("Lecture", R.drawable.ic_eye, MaterialTheme.colorScheme.tertiaryContainer) {
        Text(report.summary)
        if (report.notes.isNotEmpty()) Bullets(report.notes)
        if (report.missing.isNotEmpty()) {
            Text("À vérifier ou saisir :", fontWeight = FontWeight.Bold)
            Bullets(report.missing)
        }
        if (Analyzer.hasLast(context)) OutlinedButton(onClick = { Analyzer.shareLast(context) }) { Text("Signaler une lecture ratée") }
    }
}

/** Les trois guildes du bandeau, modifiables ; « Nous » désigne notre guilde. */
@Composable
fun GuildsSection(guilds: List<GuildIn>, me: Int, onGuild: (Int, GuildIn) -> Unit, onMe: (Int) -> Unit) {
    Section("Les trois guildes", R.drawable.ic_house) {
        Hint("Lues dans le bandeau du haut. Corrigez si besoin ; « Nous » = votre guilde.")
        guilds.forEachIndexed { i, g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field("Nom de la guilde", g.n, { onGuild(i, g.copy(n = it)) }, Modifier.weight(1f), KeyboardType.Text)
                RadioButton(selected = me == i, onClick = { onMe(i) })
                Text("Nous")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Âme", g.a, { onGuild(i, g.copy(a = it)) }, Modifier.weight(1.4f), KeyboardType.Text) { Art(R.drawable.ic_flame, size = 20) }
                Field("Prod. /s", g.r, { onGuild(i, g.copy(r = it)) }, Modifier.weight(1f), KeyboardType.Decimal)
                Field("Salles", g.s, { onGuild(i, g.copy(s = it)) }, Modifier.weight(0.9f))
            }
        }
    }
}

/** Copie le plan en texte, à coller dans le chat de guilde. */
fun copyPlan(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Plan de bataille", text))
    // Depuis Android 13, le système confirme lui-même la copie.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Plan copié : collez-le dans le chat de guilde.", Toast.LENGTH_SHORT).show()
}

/** Type de salle (lits, production d'un joueur) et bonus « 50% » : donnent la production de la salle. */
@Composable
fun RoomKind(type: String, buff: Boolean, onChange: (String, Boolean) -> Unit) {
    Choice(listOf("" to "?") + RoomType.entries.map { it.name to it.label }, type) { onChange(it, buff) }
    Choice(listOf(false to "Sans bonus", true to "Bonus 50%"), buff) { onChange(type, it) }
}
