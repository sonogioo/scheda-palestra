package com.scheda.palestra

import android.content.Context
import java.io.File

/** Dove sei arrivato: allenamento, esercizio, serie già fatte dell'esercizio corrente. */
data class Progress(val workout: Int, val exercise: Int, val setsDone: Int) {

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
    }

    fun skip(w: Workout): Progress =
        if (isFinished(w)) this else copy(exercise = exercise + 1, setsDone = 0)

    val canUndo get() = setsDone > 0 || exercise > 0

    /** Valido solo se punta ancora a qualcosa di esistente (la scheda potrebbe essere cambiata). */
    fun isValidFor(workouts: List<Workout>): Boolean {
        val w = workouts.getOrNull(workout) ?: return false
        if (exercise > w.exercises.size) return false
        return exercise == w.exercises.size || setsDone < w.exercises[exercise].sets
    }
}

/** Salvataggio locale: testo della scheda in un file, posizione corrente nelle preferenze. */
class Store(private val context: Context) {
    private val file = File(context.filesDir, "scheda.txt")
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun loadText(): String =
        if (file.exists()) file.readText()
        else context.assets.open("scheda.txt").bufferedReader().use { it.readText() }

    fun saveText(text: String) = file.writeText(text)

    fun loadProgress(): Progress? =
        if (!prefs.contains("w")) null
        else Progress(prefs.getInt("w", 0), prefs.getInt("e", 0), prefs.getInt("s", 0))

    fun saveProgress(p: Progress?) {
        prefs.edit().apply {
            if (p == null) clear()
            else putInt("w", p.workout).putInt("e", p.exercise).putInt("s", p.setsDone)
        }.apply()
    }
}
