package com.scheda.palestra

import java.time.DayOfWeek
import java.time.LocalDate

/** Riconosce il giorno della settimana dal nome dell'allenamento (italiano o inglese). */
object Days {
    private val names = mapOf(
        DayOfWeek.MONDAY to listOf("lunedì", "lunedi", "monday"),
        DayOfWeek.TUESDAY to listOf("martedì", "martedi", "tuesday"),
        DayOfWeek.WEDNESDAY to listOf("mercoledì", "mercoledi", "wednesday"),
        DayOfWeek.THURSDAY to listOf("giovedì", "giovedi", "thursday"),
        DayOfWeek.FRIDAY to listOf("venerdì", "venerdi", "friday"),
        DayOfWeek.SATURDAY to listOf("sabato", "saturday"),
        DayOfWeek.SUNDAY to listOf("domenica", "sunday"),
    )

    fun dayOf(w: Workout): DayOfWeek? {
        val text = "${w.subtitle.orEmpty()} ${w.title}".lowercase()
        return names.entries.firstOrNull { (_, words) -> words.any { it in text } }?.key
    }

    /**
     * Pagina da mostrare all'apertura: l'allenamento di oggi; se oggi è riposo,
     * il prossimo in calendario; se nessun allenamento ha un giorno, il primo.
     */
    fun indexFor(workouts: List<Workout>, today: DayOfWeek): Int {
        val withDay = workouts.mapIndexedNotNull { i, w -> dayOf(w)?.let { i to it } }
        if (withDay.isEmpty()) return 0
        return withDay.minBy { (_, d) -> Math.floorMod(d.value - today.value, 7) }.first
    }

    fun isToday(w: Workout, today: DayOfWeek) = dayOf(w) == today

    /** Vero se [date] cade nella settimana di [today] (da lunedì a oggi). */
    fun isThisWeek(date: LocalDate?, today: LocalDate): Boolean {
        if (date == null) return false
        val monday = today.with(DayOfWeek.MONDAY)
        return !date.isBefore(monday) && !date.isAfter(today)
    }
}
