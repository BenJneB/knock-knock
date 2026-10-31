package be.ombres.conseil.core

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import be.ombres.conseil.CouncilActivity
import be.ombres.conseil.R

/**
 * Rappels locaux (ex. fin d'une Sape d'âme, 2 h après l'avoir jouée), par l'alarme du système.
 * L'alarme n'est pas exacte à la minute près (pas de permission spéciale) : elle sonne dans les 10 minutes.
 */
object Reminders {
    private const val CHANNEL = "rappels"

    /** Programme un rappel dans [delayMs] ; renvoie l'heure prévue (ms). */
    fun schedule(context: Context, id: Int, title: String, text: String, delayMs: Long): Long {
        val at = System.currentTimeMillis() + delayMs
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("id", id).putExtra("title", title).putExtra("text", text)
        val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Fenêtre de 10 min (le minimum sans permission d'alarme exacte) ; sans elle, Android peut décaler d'une heure.
        context.getSystemService(AlarmManager::class.java).setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60 * 1000L, pi)
        return at
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun show(context: Context, id: Int, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Rappels de cartes", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, 0, Intent(context, CouncilActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            id,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_bubble)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.show(context, intent.getIntExtra("id", 0), intent.getStringExtra("title") ?: "", intent.getStringExtra("text") ?: "")
    }
}
