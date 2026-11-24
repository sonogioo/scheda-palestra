package com.scheda.palestra

import android.content.Context
import java.io.File
import java.time.LocalDate

/**
 * Dove sei arrivato: allenamento, esercizio, serie già fatte dell'esercizio corrente.
 * [startedAt] è l'ora d'inizio (per il riepilogo); [restUntil]/[restTotal] il recupero in corso (ms).
 */
data class Progress(
    val workout: Int,
    val exercise: Int,
    val setsDone: Int,
    val startedAt: Long = 0,
    val restUntil: Long = 0,
    val restTotal: Long = 0,
) {

    fun isFinished(w: Workout) = exercise >= w.exercises.size

    /** "Serie fatta": avanza di una serie, e all'ultima passa all'esercizio dopo. */
    fun advance(w: Workout): Progress {
        if (isFinished(w)) return this
        val done = setsDone + 1
        return if (done >= w.exercises[exercise].sets) copy(exercise = exercise + 1, setsDone = 0)
        else copy(setsDone = done)
    }

    /** Annulla l'ultima serie segnata, anche tornando all'esercizio precedente. */
    fun undo(w: Workout): Progress = when {
        setsDone > 0 -> copy(setsDone = setsDone - 1)
        exercise > 0 -> copy(exercise = exercise - 1, setsDone = w.exercises[exercise - 1].sets - 1)
        else -> this
    }.noRest()

    fun skip(w: Workout): Progress =
        (if (isFinished(w)) this else copy(exercise = exercise + 1, setsDone = 0)).noRest()

    /** Dopo "Serie fatta": avanza e, se l'allenamento non è finito, parte il recupero. */
    fun completeSet(w: Workout, now: Long): Progress {
        val restSeconds = w.exercises.getOrNull(exercise)?.restSeconds ?: Rest.DEFAULT_SECONDS
        val next = advance(w)
        return if (next.isFinished(w)) next.noRest()
        else next.copy(restUntil = now + restSeconds * 1000L, restTotal = restSeconds * 1000L)
    }

    fun isResting(now: Long) = restUntil > now

    /** Allunga o accorcia il recupero (mai sotto i 5 secondi rimanenti). */
    fun adjustRest(deltaMs: Long, now: Long): Progress {
        if (!isResting(now)) return this
        val until = maxOf(now + 5_000, restUntil + deltaMs)
        return copy(restUntil = until, restTotal = maxOf(restTotal + (until - restUntil), until - now))
    }

    fun noRest() = copy(restUntil = 0, restTotal = 0)

    val canUndo get() = setsDone > 0 || exercise > 0

    /** Valido solo se punta ancora a qualcosa di esistente (la scheda potrebbe essere cambiata). */
    fun isValidFor(workouts: List<Workout>): Boolean {
        val w = workouts.getOrNull(workout) ?: return false
        if (exercise > w.exercises.size) return false
        return exercise == w.exercises.size || setsDone < w.exercises[exercise].sets
    }
}

/** Salvataggio locale: scheda in un file; posizione, pesi e storico nelle preferenze. */
class Store(private val context: Context) {
    private val file = File(context.filesDir, "scheda.txt")
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    private val weights = context.getSharedPreferences("weights", Context.MODE_PRIVATE)
    private val history = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun loadText(): String =
        if (file.exists()) file.readText()
        else context.assets.open("scheda.txt").bufferedReader().use { it.readText() }

    fun saveText(text: String) = file.writeText(text)

    fun loadProgress(): Progress? =
        if (!prefs.contains("w")) null
        else Progress(
            prefs.getInt("w", 0), prefs.getInt("e", 0), prefs.getInt("s", 0),
            prefs.getLong("t0", 0), prefs.getLong("ru", 0), prefs.getLong("rt", 0),
        )

    fun saveProgress(p: Progress?) {
        prefs.edit().apply {
            if (p == null) clear()
            else putInt("w", p.workout).putInt("e", p.exercise).putInt("s", p.setsDone)
                .putLong("t0", p.startedAt).putLong("ru", p.restUntil).putLong("rt", p.restTotal)
        }.apply()
    }

    /** Ultimo peso usato per un esercizio, in kg (0 = non impostato). */
    fun weight(exercise: String): Float = weights.getFloat(exercise.lowercase(), 0f)

    fun saveWeight(exercise: String, kg: Float) = weights.edit().putFloat(exercise.lowercase(), kg).apply()

    fun allWeights(): Map<String, Float> =
        weights.all.mapNotNull { (k, v) -> (v as? Float)?.let { k to it } }.toMap()

    /** Data dell'ultimo completamento per ogni allenamento (chiave: nome mostrato, minuscolo). */
    fun history(): Map<String, LocalDate> =
        history.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to LocalDate.ofEpochDay(it) } }.toMap()

    fun markDone(workout: Workout, date: LocalDate) =
        history.edit().putLong(workout.displayName.lowercase(), date.toEpochDay()).apply()
}

/** Recupero tra le serie: letto dalla nota dell'esercizio ("rec 90", "rec 1:30", "rec 2'"), altrimenti 90 s. */
object Rest {
    const val DEFAULT_SECONDS = 90

    private val re = Regex("""(?:recupero|rec|rest)\.?\s*(\d+)(?:\s*[:.]\s*(\d{1,2}))?\s*('|min|m\b|"|sec|s\b)?""", RegexOption.IGNORE_CASE)

    fun parse(text: String): Int? {
        val m = re.find(text) ?: return null
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2]
        val unit = m.groupValues[3].lowercase()
        val seconds = when {
            b.isNotEmpty() -> a * 60 + b.toInt()
            unit in setOf("'", "min", "m") -> a * 60
            unit in setOf("\"", "sec", "s") -> a
            a <= 5 -> a * 60 // "rec 2" = 2 minuti
            else -> a
        }
        return seconds.coerceIn(5, 900)
    }

    fun format(ms: Long): String {
        val s = ((ms + 999) / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }
}

fun formatKg(kg: Float): String =
    if (kg % 1f == 0f) "${kg.toInt()} kg" else "%.1f kg".format(java.util.Locale.ITALIAN, kg)
