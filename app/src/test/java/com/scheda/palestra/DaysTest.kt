package com.scheda.palestra

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.DayOfWeek

class DaysTest {
    private val w = SchedaParser.parse(File("src/main/assets/scheda.txt").readText())

    @Test
    fun opensOnTodaysWorkout() {
        assertEquals(0, Days.indexFor(w, DayOfWeek.MONDAY))
        assertEquals(1, Days.indexFor(w, DayOfWeek.TUESDAY))
        assertEquals(2, Days.indexFor(w, DayOfWeek.THURSDAY))
        assertEquals(3, Days.indexFor(w, DayOfWeek.FRIDAY))
    }

    @Test
    fun restDayOpensOnNextWorkout() {
        assertEquals(2, Days.indexFor(w, DayOfWeek.WEDNESDAY)) // → giovedì
        assertEquals(0, Days.indexFor(w, DayOfWeek.SATURDAY)) // → lunedì
        assertEquals(0, Days.indexFor(w, DayOfWeek.SUNDAY))
    }

    @Test
    fun workoutsWithoutDayOpenOnFirst() {
        val plain = SchedaParser.parse("Giorno A\nPanca 3x10\n\nGiorno B\nSquat 3x10")
        assertEquals(0, Days.indexFor(plain, DayOfWeek.TUESDAY))
    }
}
