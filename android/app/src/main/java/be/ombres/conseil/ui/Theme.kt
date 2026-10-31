package be.ombres.conseil.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Couleurs inspirées du jeu : violet de la carte, cyan des cadres, or des titres, maisons verte/violette/rouge. */
object Game {
    val Night = Color(0xFF2E1D45)
    val NightDeep = Color(0xFF1B1226)
    val Cyan = Color(0xFF3FB8C9)
    val Gold = Color(0xFFF2C14E)
    val HouseGreen = Color(0xFF3DB37A)
    val HouseViolet = Color(0xFF9B4FC9)
    val HouseRed = Color(0xFFC8463C)
}

// Même palette que la version web (web/conseil-zone-sombre-chefs.html).
private val Light = lightColorScheme(
    primary = Color(0xFF17808C), onPrimary = Color.White,
    // « primaryContainer » : verdict difficile / mises en garde (orangé).
    primaryContainer = Color(0xFFF8DCC6), onPrimaryContainer = Color(0xFF2A1A12),
    secondary = Color(0xFF2E9E6B), onSecondary = Color.White,
    // « secondaryContainer » : verdict favorable (vert d'eau).
    secondaryContainer = Color(0xFFD2F0EE), onSecondaryContainer = Color(0xFF0E2B2E),
    tertiaryContainer = Color(0xFFFBEBC6), onTertiaryContainer = Color(0xFF2E2410),
    background = Color(0xFFEDE8F3), onBackground = Color(0xFF221A2E),
    surface = Color(0xFFFBF9FD), onSurface = Color(0xFF221A2E),
    surfaceVariant = Color(0xFFFBF9FD), onSurfaceVariant = Color(0xFF665B74),
    outline = Color(0xFFD6CDE1), outlineVariant = Color(0xFFD6CDE1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4FD1DC), onPrimary = Color(0xFF0E2B2E),
    primaryContainer = Color(0xFF4A2C1C), onPrimaryContainer = Color(0xFFFBE9DC),
    secondary = Color(0xFF5FD39A), onSecondary = Color(0xFF0E2B1E),
    secondaryContainer = Color(0xFF15393A), onSecondaryContainer = Color(0xFFDDF6F4),
    tertiaryContainer = Color(0xFF3E3214), onTertiaryContainer = Color(0xFFFBEFD0),
    background = Game.NightDeep, onBackground = Color(0xFFF1EAF8),
    surface = Color(0xFF261A35), onSurface = Color(0xFFF1EAF8),
    surfaceVariant = Color(0xFF261A35), onSurfaceVariant = Color(0xFFB6A9C6),
    outline = Color(0xFF3E2F52), outlineVariant = Color(0xFF3E2F52),
)

@Composable
fun ConseilTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
