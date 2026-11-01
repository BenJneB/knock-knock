package be.ombres.conseil

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import be.ombres.conseil.capture.BubbleService
import be.ombres.conseil.core.Analyzer
import be.ombres.conseil.core.Prefs
import be.ombres.conseil.mode.Modes
import be.ombres.conseil.ui.Choice
import be.ombres.conseil.ui.ConseilTheme
import be.ombres.conseil.ui.Field
import be.ombres.conseil.ui.GameHeader
import be.ombres.conseil.ui.Hint
import be.ombres.conseil.ui.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Accueil : réglages, démarrage de la bulle, lecture d'une capture enregistrée. */
class MainActivity : ComponentActivity() {

    private var bubbleOn by mutableStateOf(false)
    private var reading by mutableStateOf(false)

    private val projection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        if (res.resultCode == RESULT_OK && data != null) {
            BubbleService.start(this, res.resultCode, data)
            bubbleOn = true
            moveTaskToBack(true) // retour au jeu
        } else Toast.makeText(this, "Capture d’écran refusée : la bulle ne peut pas lire le jeu.", Toast.LENGTH_LONG).show()
    }

    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { askProjection() }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::readImage) }

    override fun onStart() {
        super.onStart()
        BubbleService.appScreen(+1)
    }

    override fun onStop() {
        BubbleService.appScreen(-1)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ConseilTheme { Home() } }
    }

    override fun onResume() {
        super.onResume()
        bubbleOn = BubbleService.running
    }

    private fun startBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Autorisez « Afficher par-dessus les autres applis », puis revenez.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
            return
        }
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else askProjection()
    }

    private fun askProjection() {
        projection.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }

    private fun readImage(uri: Uri) {
        reading = true
        lifecycleScope.launch {
            val report = runCatching {
                val bmp = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) } }
                Analyzer.read(this@MainActivity, bmp)
            }.getOrNull()
            reading = false
            if (report == null) { Toast.makeText(this@MainActivity, "Image illisible.", Toast.LENGTH_SHORT).show(); return@launch }
            Prefs.setLastReport(this@MainActivity, report)
            startActivity(Intent(this@MainActivity, CouncilActivity::class.java))
        }
    }

    @Composable
    private fun Home() {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GameHeader("Knock Knock: Room Defense — lit l’écran du jeu et propose le plan de bataille, sans connexion.")

                Section("Bulle de lecture", R.drawable.ic_bubble) {
                    Hint("La bulle flotte par-dessus le jeu. Touchez-la sur la carte du match ou dans le coffre de cartes : l’écran est lu et le plan s’affiche.")
                    if (!bubbleOn) Button(onClick = ::startBubble, modifier = Modifier.fillMaxWidth()) { Text("Démarrer la bulle") }
                    else OutlinedButton(onClick = { BubbleService.stop(this@MainActivity); bubbleOn = false }, modifier = Modifier.fillMaxWidth()) { Text("Arrêter la bulle") }
                    OutlinedButton(onClick = { pickImage.launch("image/*") }, enabled = !reading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (reading) "Lecture en cours…" else "Lire une capture enregistrée")
                    }
                    OutlinedButton(onClick = { Prefs.setLastReport(this@MainActivity, null); startActivity(Intent(this@MainActivity, CouncilActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ouvrir le conseil")
                    }
                }

                Section("Réglages", R.drawable.ic_tune) {
                    var guild by remember { mutableStateOf(Prefs.guild(this@MainActivity)) }
                    Field("Nom de notre guilde", guild, { guild = it; Prefs.setGuild(this@MainActivity, it) }, Modifier.fillMaxWidth(), KeyboardType.Text)
                    var mode by remember { mutableStateOf(Prefs.mode(this@MainActivity)) }
                    Text("Mode de jeu", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Modes.all.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { mode = m; Prefs.setMode(this@MainActivity, m) }) {
                            RadioButton(selected = m == mode, onClick = { mode = m; Prefs.setMode(this@MainActivity, m) })
                            Text(m.title)
                        }
                    }
                    key(mode.id) { mode.Settings(this@MainActivity) }
                    var mini by remember { mutableStateOf(Prefs.miniPlan(this@MainActivity)) }
                    Text("Après une lecture par la bulle", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Choice(listOf(true to "Mini-plan sur le jeu", false to "Écran complet"), mini) { mini = it; Prefs.setMiniPlan(this@MainActivity, it) }
                }
            }
        }
    }
}
