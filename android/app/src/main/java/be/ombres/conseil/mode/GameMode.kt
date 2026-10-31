package be.ombres.conseil.mode

import android.content.Context
import androidx.compose.runtime.Composable
import be.ombres.conseil.mode.zonesombre.ZoneSombreMode
import be.ombres.conseil.ocr.Screen

/**
 * Un mode de jeu (Zone sombre, Défense de la capitale…) : ses règles, sa lecture d'écran,
 * son moteur de stratégie et son écran de conseil. La bulle, la capture et la lecture du texte
 * sont communes à tous les modes.
 */
interface GameMode {
    val id: String
    val title: String

    /** Lit une capture et mémorise ce qui a été lu. */
    suspend fun read(context: Context, screen: Screen): ReadReport

    /** Écran de conseil : valeurs lues (modifiables) et plan. */
    @Composable
    fun Council(context: Context, report: ReadReport?)

    /** Réglages propres au mode, affichés sur l'écran d'accueil. */
    @Composable
    fun Settings(context: Context)
}

/** Résumé d'une lecture : ce qui a été lu, ce qui a été repéré, et ce qu'il faut vérifier ou saisir à la main. */
data class ReadReport(val summary: String, val missing: List<String>, val notes: List<String> = emptyList())

object Modes {
    val all: List<GameMode> = listOf(ZoneSombreMode)
    fun byId(id: String?): GameMode = all.firstOrNull { it.id == id } ?: all.first()
}
