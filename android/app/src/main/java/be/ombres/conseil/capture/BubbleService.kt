package be.ombres.conseil.capture

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.createBitmap
import be.ombres.conseil.CouncilActivity
import be.ombres.conseil.MainActivity
import be.ombres.conseil.R
import be.ombres.conseil.core.Analyzer
import be.ombres.conseil.core.Prefs
import be.ombres.conseil.mode.Brief
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Bulle flottante par-dessus le jeu. Un toucher : capture de l'écran (MediaProjection),
 * lecture du texte sur le téléphone, puis ouverture de l'écran de conseil.
 * Rien d'autre n'est lu : ni la mémoire du jeu, ni son trafic réseau, et aucune touche n'est simulée.
 */
class BubbleService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: ImageView? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val thread = HandlerThread("capture").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var latest: Image? = null // dernière image reçue, gardée ouverte (thread "capture")
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (projection != null) return START_NOT_STICKY

        // L'ordre compte : service au premier plan d'abord, projection ensuite (Android 14+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, notification())

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0

        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = mpm.getMediaProjection(code, data) ?: run { stopSelf(); return START_NOT_STICKY }
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { main.post { stopSelf() } }
        }, main)
        startDisplay(mp)
        showBubble()
        return START_NOT_STICKY
    }

    private fun startDisplay(mp: MediaProjection) {
        wm = getSystemService(WindowManager::class.java)
        val (w, h) = screenSize()
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        r.setOnImageAvailableListener({ ir ->
            val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            latest?.close()
            latest = img
        }, handler)
        reader = r
        display = mp.createVirtualDisplay(
            "conseil-capture", w, h, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler,
        )
    }

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            return b.width() to b.height()
        }
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        val size = (56 * resources.displayMetrics.density).toInt()
        val pad = size / 5
        val v = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble)
            setPadding(pad, pad, pad, pad)
            // Couleurs du jeu : violet de nuit, liseré doré.
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xEE2E1D45.toInt()); setStroke(4, 0xFFF2C14E.toInt()) }
            contentDescription = "Lire l’écran"
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = resources.displayMetrics.heightPixels / 3 }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    if (moved) { lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt(); wm.updateViewLayout(v, lp) }
                }

                MotionEvent.ACTION_UP -> if (!moved) capture()
            }
            true
        }
        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            // Certains téléphones (Xiaomi notamment) refusent la fenêtre malgré l'autorisation Android standard.
            Log.w("Conseil", "Bulle refusée par le téléphone", e)
            lastError = "Le téléphone a refusé d’afficher la bulle. Sur Xiaomi : Autorisations Xiaomi > fenêtres pop-up en arrière-plan et fenêtres flottantes."
            Toast.makeText(this, lastError, Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        lastError = null
        bubble = v
        applyAppVisible()
    }

    /** Sur nos propres écrans, la bulle ne sert à rien et cache le contenu. */
    private fun applyAppVisible() {
        if (!busy) bubble?.visibility = if (appScreens > 0) View.GONE else View.VISIBLE
    }

    private fun capture() {
        if (busy) return
        busy = true
        bubble?.visibility = View.INVISIBLE // la bulle ne doit pas cacher le jeu sur la capture
        handler.postDelayed({
            val bmp = latest?.let(::toBitmap)
            main.post {
                if (bmp == null) {
                    bubble?.visibility = View.VISIBLE; busy = false
                    Toast.makeText(this, "Capture impossible, réessayez.", Toast.LENGTH_SHORT).show()
                    return@post
                }
                bubble?.apply { visibility = View.VISIBLE; alpha = 0.5f } // lecture en cours
                analyze(bmp)
            }
        }, 350)
    }

    private fun analyze(bmp: Bitmap) = scope.launch {
        val report = try {
            Analyzer.read(this@BubbleService, bmp)
        } catch (e: Exception) {
            Log.w("Conseil", "Lecture impossible", e)
            null
        } finally {
            bmp.recycle()
            bubble?.alpha = 1f
            busy = false
            applyAppVisible()
        }
        if (report == null) {
            Toast.makeText(this@BubbleService, "Lecture impossible, réessayez.", Toast.LENGTH_SHORT).show()
            return@launch
        }
        Prefs.setLastReport(this@BubbleService, report)
        if (Prefs.miniPlan(this@BubbleService)) showBrief(report.summary, Prefs.mode(this@BubbleService).brief(this@BubbleService))
        else openCouncil()
    }

    private fun openCouncil() {
        startActivity(Intent(this, CouncilActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    /** Mini-plan par-dessus le jeu : verdict, premières actions, « Détails » (écran complet) et « Fermer ». */
    private fun showBrief(summary: String, brief: Brief) {
        hideBrief()
        val dp = resources.displayMetrics.density
        fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, (2 * dp).toInt(), 0, (2 * dp).toInt())
        }
        fun button(t: String, action: () -> Unit) = TextView(this).apply {
            text = t; textSize = 15f; setTextColor(0xFF2E1D45.toInt()); setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply { cornerRadius = 20 * dp; setColor(0xFFF2C14E.toInt()) }
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener { action() }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            background = GradientDrawable().apply { cornerRadius = 16 * dp; setColor(0xF02E1D45.toInt()); setStroke((2 * dp).toInt(), 0xFF3FB8C9.toInt()) }
            addView(text(brief.title, 18f, 0xFFF2C14E.toInt(), bold = true))
            addView(text(summary, 12f, 0xFFB6A9C6.toInt()))
            brief.lines.forEach { addView(text(it, 14f, 0xFFF1EAF8.toInt())) }
            addView(LinearLayout(this@BubbleService).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (8 * dp).toInt(), 0, 0)
                addView(button("Détails") { hideBrief(); openCouncil() })
                addView(android.widget.Space(this@BubbleService), LinearLayout.LayoutParams((12 * dp).toInt(), 1))
                addView(button("Fermer") { hideBrief() })
            })
        }
        val lp = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = (90 * dp).toInt() }
        wm.addView(card, lp)
        briefView = card
        main.postDelayed(closeBrief, 30_000) // se ferme seul après 30 s
    }

    private var briefView: View? = null
    private val closeBrief = Runnable { hideBrief() }

    private fun hideBrief() {
        main.removeCallbacks(closeBrief)
        briefView?.let { runCatching { wm.removeView(it) } }
        briefView = null
    }

    private fun toBitmap(img: Image): Bitmap {
        val plane = img.planes[0]
        val rowPx = plane.rowStride / plane.pixelStride
        val raw = createBitmap(rowPx, img.height)
        plane.buffer.rewind()
        raw.copyPixelsFromBuffer(plane.buffer)
        if (rowPx == img.width) return raw
        return Bitmap.createBitmap(raw, 0, 0, img.width, img.height).also { raw.recycle() }
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Bulle de lecture", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle("Conseil de guerre")
            .setContentText("Touchez la bulle pour lire l’écran du jeu.")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Arrêter", stop).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        hideBrief()
        bubble?.let { runCatching { wm.removeView(it) } }
        display?.release()
        projection?.stop()
        handler.post { latest?.close(); latest = null; reader?.close() }
        thread.quitSafely()
        running = false
        instance = null
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        instance = java.lang.ref.WeakReference(this)
    }

    companion object {
        private const val CHANNEL = "bulle"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"

        @Volatile var running = false
            private set

        /** Dernier échec d'affichage de la bulle, montré sur l'écran d'accueil ; null si tout va bien. */
        @Volatile var lastError: String? = null
            private set

        // Référence faible : le service ne doit pas être retenu en mémoire par un champ statique.
        private var instance: java.lang.ref.WeakReference<BubbleService>? = null
        private var appScreens = 0

        /** À appeler dans onStart (+1) et onStop (-1) de nos écrans : la bulle s'y cache. */
        fun appScreen(delta: Int) {
            appScreens = (appScreens + delta).coerceAtLeast(0)
            instance?.get()?.applyAppVisible()
        }

        fun start(c: Context, resultCode: Int, data: Intent) {
            c.startForegroundService(Intent(c, BubbleService::class.java).putExtra(EXTRA_CODE, resultCode).putExtra(EXTRA_DATA, data))
        }

        fun stop(c: Context) {
            c.startService(Intent(c, BubbleService::class.java).setAction(ACTION_STOP))
        }
    }
}
