package be.ombres.conseil

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.ombres.conseil.core.Prefs
import be.ombres.conseil.ui.ConseilTheme
import be.ombres.conseil.ui.GameHeader

/** Écran de conseil du mode actif : valeurs lues, plan, cartes à garder, menaces. */
class CouncilActivity : ComponentActivity() {
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        show()
    }

    // Nouvelle capture pendant que l'écran est ouvert : on recharge les valeurs.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        show()
    }

    private fun show() {
        val gen = ++generation
        val mode = Prefs.mode(this)
        val report = Prefs.lastReport(this)
        setContent {
            ConseilTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    key(gen) {
                        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            GameHeader(mode.title)
                            mode.Council(this@CouncilActivity, report)
                        }
                    }
                }
            }
        }
    }
}
