package com.scheda.palestra

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
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Recupero anche a telefono bloccato o con l'app in secondo piano:
 * - notifica con il conto alla rovescia mentre recuperi;
 * - sveglia di sistema allo scadere: suono, vibrazione e notifica "Vai con la serie N".
 *
 * L'avviso parte una sola volta per ogni recupero, sia che arrivi prima la sveglia
 * sia che arrivi prima il conto alla rovescia dentro l'app.
 */
object RestAlarm {
    private const val CH_TIMER = "rest_timer"
    private const val CH_DONE = "rest_done"
    private const val ID_TIMER = 1
    private const val ID_DONE = 2
    private const val EXTRA_UNTIL = "until"
    private const val EXTRA_TEXT = "text"

    private var lastFired = 0L

    fun schedule(context: Context, restUntil: Long, title: String, text: String) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(ID_DONE)
        if (canNotify(context)) {
            val n = Notification.Builder(context, CH_TIMER)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(title)
                .setContentText(text)
                .setWhen(restUntil)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(openApp(context))
                .setTimeoutAfter((restUntil - System.currentTimeMillis()).coerceAtLeast(1_000))
                .build()
            nm.notify(ID_TIMER, n)
        }

        val am = context.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(context, restUntil, text)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, restUntil, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, restUntil, pi)
        }
    }

    /** Recupero annullato o saltato: via sveglia e conto alla rovescia (l'avviso finale resta). */
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, 0, ""))
        context.getSystemService(NotificationManager::class.java).cancel(ID_TIMER)
    }

    /** Fine recupero: suono + vibrazione + notifica. Ignorato se già fatto per questo recupero. */
    fun fire(context: Context, restUntil: Long, text: String) {
        synchronized(this) {
            if (restUntil == lastFired) return
            lastFired = restUntil
        }
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(ID_TIMER)
        vibrate(context)
        playSound(context)
        if (canNotify(context)) {
            val n = Notification.Builder(context, CH_DONE)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("Recupero finito")
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .setContentIntent(openApp(context))
                .build()
            nm.notify(ID_DONE, n)
        }
    }

    fun clearDone(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(ID_DONE)

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_TIMER) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_TIMER, "Timer di recupero", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Conto alla rovescia durante il recupero"
                    setShowBadge(false)
                }
            )
        }
        if (nm.getNotificationChannel(CH_DONE) == null) {
            nm.createNotificationChannel(
                // Suono e vibrazione li gestisce l'app (così non si sentono due volte).
                NotificationChannel(CH_DONE, "Fine recupero", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Avviso quando il recupero è finito"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun alarmIntent(context: Context, restUntil: Long, text: String) = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, RestAlarmReceiver::class.java)
            .putExtra(EXTRA_UNTIL, restUntil)
            .putExtra(EXTRA_TEXT, text),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Vibrazione "da sveglia": passa anche se le vibrazioni al tocco sono spente. */
    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 800), -1)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    /** Suono di notifica del telefono (rispetta la modalità silenzioso). */
    private fun playSound(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        } catch (_: Exception) {
            // senza suono resta la vibrazione
        }
    }

    internal fun onAlarm(context: Context, intent: Intent) {
        val until = intent.getLongExtra(EXTRA_UNTIL, 0)
        // Sveglia arrivata con molto ritardo (telefono spento, ecc.): niente avviso.
        if (until == 0L || System.currentTimeMillis() - until > 60_000) return
        fire(context, until, intent.getStringExtra(EXTRA_TEXT).orEmpty())
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestAlarm.onAlarm(context, intent)
}
