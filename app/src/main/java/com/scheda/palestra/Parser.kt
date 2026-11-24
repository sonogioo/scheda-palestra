package com.scheda.palestra

data class Exercise(
    val group: String?,
    val name: String,
    val sets: Int,
    /** "10", "8-10" oppure "F" (cedimento). */
    val reps: String,
    /** Testo dopo serie×ripetizioni, es. "80kg rec 90". */
    val note: String,
) {
    val toFailure: Boolean get() = reps.equals("F", ignoreCase = true) || reps.equals("max", ignoreCase = true)
    val repsLabel: String get() = if (toFailure) "a cedimento" else "$reps ripetizioni"
}

data class Workout(
    val title: String,
    /** Riga subito sotto il titolo, es. "Lunedì". */
    val subtitle: String?,
    val exercises: List<Exercise>,
) {
    val displayName: String get() = subtitle ?: title

    /** Gruppi muscolari in ordine, senza ripetizioni: "Petto, Spalle, Tricipiti". */
    val groupsLabel: String get() = exercises.mapNotNull { it.group }.distinct().joinToString(", ")

    val totalSets: Int get() = exercises.sumOf { it.sets }
}

/**
 * Trasforma il testo della scheda (così come scritto nelle note) in allenamenti.
 *
 * Regole:
 *  - i simboli di elenco iniziali (•, -, *, ·) e le righe vuote vengono ignorati;
 *  - "Nome 3x10", "Nome 3xF", "Nome 4x8-10 80kg" → esercizio;
 *  - una riga che termina con ":" → gruppo muscolare (es. "Petto:");
 *  - qualunque altra riga apre un nuovo allenamento; se l'allenamento corrente
 *    non ha ancora esercizi, diventa invece il suo sottotitolo (es. "Lunedì").
 */
object SchedaParser {
    private val bullet = Regex("""^[\s•\-*·▪◦–]+""")
    private val exerciseRe = Regex(
        """^(.+?)\s+(\d+)\s*[xX×]\s*(\d+(?:\s*[-/]\s*\d+)?|[Ff]|max|MAX|Max)\b\s*(.*)$"""
    )

    fun parse(text: String): List<Workout> {
        val result = mutableListOf<Workout>()
        var title: String? = null
        var subtitle: String? = null
        var group: String? = null
        var exercises = mutableListOf<Exercise>()

        fun flush() {
            val t = title
            if (t != null || exercises.isNotEmpty()) {
                result += Workout(t ?: "Allenamento ${result.size + 1}", subtitle, exercises)
            }
            title = null; subtitle = null; group = null
            exercises = mutableListOf()
        }

        for (raw in text.lines()) {
            val line = raw.replace(bullet, "").trim()
            if (line.isEmpty()) continue

            val m = exerciseRe.matchEntire(line)
            when {
                m != null -> {
                    val (name, sets, reps, note) = m.destructured
                    exercises += Exercise(
                        group = group,
                        name = name.trim(),
                        sets = sets.toInt().coerceIn(1, 99),
                        reps = reps.replace(" ", "").let { if (it.equals("f", true)) "F" else it },
                        note = note.trim(),
                    )
                }
                line.endsWith(":") -> group = line.dropLast(1).trim()
                title != null && exercises.isEmpty() && subtitle == null -> subtitle = line
                else -> {
                    flush()
                    title = line
                }
            }
        }
        flush()
        return result.filter { it.exercises.isNotEmpty() }
    }
}
