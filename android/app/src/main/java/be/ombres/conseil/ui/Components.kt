package be.ombres.conseil.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.ombres.conseil.R

/** En-tête façon panneau du jeu : fond violet de nuit, liseré cyan, titre doré, porte à heurtoir. */
@Composable
fun GameHeader(subtitle: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(Brush.verticalGradient(listOf(Game.Night, Game.NightDeep)), RoundedCornerShape(16.dp))
            .border(2.dp, Game.Cyan, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_door), null, Modifier.size(52.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text("Conseil de guerre", style = MaterialTheme.typography.headlineSmall, color = Game.Gold, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color(0xFFD9CCE8), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Icône colorée (cartes, bouclier, flamme…), dessinée telle quelle. */
@Composable
fun Art(@DrawableRes id: Int, size: Int = 22, modifier: Modifier = Modifier) {
    Image(painterResource(id), null, modifier.size(size.dp))
}

@Composable
fun Section(
    title: String,
    @DrawableRes icon: Int? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = color,
        shape = RoundedCornerShape(16.dp),
        border = if (color == MaterialTheme.colorScheme.surface) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp).size(22.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Number,
    leading: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        leadingIcon = leading,
        modifier = modifier,
    )
}

/** Choix entre quelques options, sous forme de pastilles. */
@Composable
fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) -> FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) }) }
    }
}

@Composable
fun Labeled(label: String, @DrawableRes icon: Int? = null, content: @Composable RowScope.() -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Art(icon, 18, Modifier.padding(end = 4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Icônes des cartes d'exorcisme citées dans un texte (« 1 Sanctuaire sacré + 1 Esprit malveillant »). */
fun cardIcons(text: String): List<Int> = listOfNotNull(
    R.drawable.ic_card_sanctuary.takeIf { "Sanctuaire" in text },
    R.drawable.ic_card_spirit.takeIf { "Esprit" in text },
    R.drawable.ic_card_thief.takeIf { "Sape" in text },
    R.drawable.ic_card_guard.takeIf { "Garde" in text },
)

@Composable
fun NumberedStep(n: Int, title: String, detail: String, tag: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.padding(bottom = 12.dp)) {
        Box(
            Modifier.size(30.dp).background(Game.Night, CircleShape).border(2.dp, Game.Gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$n", color = Game.Gold, fontWeight = FontWeight.ExtraBold) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (tag.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50), modifier = Modifier.padding(top = 4.dp)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    cardIcons(tag).forEach { Art(it, 18, Modifier.padding(end = 4.dp)) }
                    Text(tag, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            action?.invoke()
        }
    }
}

@Composable
fun Bullets(items: List<String>) {
    items.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
}
