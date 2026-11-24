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
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.mutableIntStateOf
import java.time.LocalDate

/** Avvisa la UI che l'allenamento è cambiato fuori dall'app (pulsante della notifica, sveglia). */
object Live {
    val changes = mutableIntStateOf(0)
    fun bump() { changes.intValue++ }
}

/**
 * Notifica dell'allenamento in corso, anche a telefono bloccato:
 * - esercizio, serie, peso e pulsante "Serie fatta" (o "Inizia serie" durante il recupero);
 * - durante il recupero, conto alla rovescia;
 * - allo scadere del recupero, sveglia di sistema: suono, vibrazione e avviso "Vai con la serie N".
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
    const val ACTION_PRIMARY = "com.scheda.palestra.PRIMARY"
    const val ACTION_OPEN_TRAIN = "com.scheda.palestra.OPEN_TRAIN"

    private var lastFired = 0L

    /** Aggiorna notifica e sveglia in base all'allenamento: da chiamare a ogni cambiamento. */
    fun sync(context: Context, w: Workout?, p: Progress?, weightKg: Float) {
        val ex = if (w != null && p != null) p.current(w) else null
        if (w == null || p == null || ex == null) {
            cancel(context)
            return
        }
        ensureChannels(context)
        val now = System.currentTimeMillis()
        val resting = p.isResting(now)
        val set = "Serie ${p.setsDone + 1} di ${ex.sets}"
        val kg = if (weightKg > 0f) " · ${formatKg(weightKg)}" else ""
        val isLastSet = p.nextExercise(w) == null && p.setsDone == ex.sets - 1
        val buttonLabel = when {
            resting -> "Inizia serie ${p.setsDone + 1}"
            isLastSet -> "Fine allenamento"
            else -> "Serie fatta"
        }

        if (canNotify(context)) {
            val b = Notification.Builder(context, CH_TIMER)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(ex.name)
                .setContentText(if (resting) "Recupero · poi ${set.lowercase()}$kg" else "$set · ${ex.repsLabel}$kg")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(if (resting) Notification.CATEGORY_STOPWATCH else Notification.CATEGORY_WORKOUT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(openTrain(context))
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_stat_timer), buttonLabel, primaryIntent(context),
                    ).build()
                )
            if (resting) {
                b.setWhen(p.restUntil).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            } else {
                b.setShowWhen(false)
            }
            context.getSystemService(NotificationManager::class.java).notify(ID_TIMER, b.build())
        }

        val am = context.getSystemService(AlarmManager::class.java)
        if (resting) {
            val pi = alarmIntent(context, p.restUntil)
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.restUntil, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.restUntil, pi)
            }
        } else {
            am.cancel(alarmIntent(context, 0))
        }
    }

    /** Allenamento chiuso o finito: via sveglia e notifica (l'avviso di fine recupero resta). */
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, 0))
        context.getSystemService(NotificationManager::class.java).cancel(ID_TIMER)
    }

    /** Fine recupero: suono + vibrazione + notifica. Ignorato se già fatto per questo recupero. */
    fun fire(context: Context, restUntil: Long, text: String) {
        synchronized(this) {
            if (restUntil == lastFired) return
            lastFired = restUntil
        }
        ensureChannels(context)
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
                .setContentIntent(openTrain(context))
                .build()
            context.getSystemService(NotificationManager::class.java).notify(ID_DONE, n)
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
                NotificationChannel(CH_TIMER, "Allenamento in corso", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Esercizio, serie e conto alla rovescia del recupero"
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

    private fun openTrain(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_TRAIN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun primaryIntent(context: Context) = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, WorkoutActionReceiver::class.java).setAction(ACTION_PRIMARY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun alarmIntent(context: Context, restUntil: Long) = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, RestAlarmReceiver::class.java).putExtra(EXTRA_UNTIL, restUntil),
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

    // ------------------------------------------------ Eventi fuori dall'app

    /** Legge allenamento e posizione salvati; null se non c'è nulla in corso. */
    private fun loadCurrent(store: Store): Pair<Workout, Progress>? {
        val workouts = SchedaParser.parse(store.loadText())
        val p = store.loadProgress()?.takeIf { it.isValidFor(workouts) } ?: return null
        return workouts[p.workout] to p
    }

    private fun save(context: Context, store: Store, w: Workout, p: Progress) {
        store.saveProgress(p)
        Live.bump()
        sync(context, w, p, p.current(w)?.let { store.weight(it.name) } ?: 0f)
    }

    /** Sveglia di fine recupero. */
    internal fun onAlarm(context: Context, intent: Intent) {
        val until = intent.getLongExtra(EXTRA_UNTIL, 0)
        // Sveglia arrivata con molto ritardo (telefono spento, ecc.): niente avviso.
        if (until == 0L || System.currentTimeMillis() - until > 60_000) return
        val store = Store(context)
        val (w, p) = loadCurrent(store) ?: return
        if (p.restUntil != until) return
        fire(context, until, restEndText(w, p))
        save(context, store, w, p.noRest())
    }

    /** Pulsante della notifica: come il pulsante grande dell'app. */
    internal fun onPrimary(context: Context) {
        val store = Store(context)
        val (w, p) = loadCurrent(store) ?: return cancel(context)
        if (p.isFinished(w)) return cancel(context)
        clearDone(context)
        val now = System.currentTimeMillis()
        val next = if (p.isResting(now)) p.noRest() else p.completeSet(w, now)
        if (next.isFinished(w)) store.markDone(w, LocalDate.now())
        save(context, store, w, next)
    }
}

/** Testo dell'avviso di fine recupero: quale serie viene adesso. */
internal fun restEndText(w: Workout, p: Progress): String {
    val ex = p.current(w) ?: return ""
    return "Vai: serie ${p.setsDone + 1} di ${ex.sets} · ${ex.name}"
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestAlarm.onAlarm(context, intent)
}

class WorkoutActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RestAlarm.ACTION_PRIMARY) RestAlarm.onPrimary(context)
    }
}
