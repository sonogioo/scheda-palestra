package com.scheda.palestra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RestTest {
    @Test
    fun parsesRestFromNotes() {
        assertEquals(90, Rest.parse("80kg rec 90"))
        assertEquals(60, Rest.parse("rec 60s"))
        assertEquals(90, Rest.parse("rec 1:30"))
        assertEquals(120, Rest.parse("rec 2'"))
        assertEquals(120, Rest.parse("recupero 2 min"))
        assertEquals(180, Rest.parse("rec 3"))
        assertNull(Rest.parse("80kg"))
        assertNull(Rest.parse(""))
    }

    @Test
    fun formatsCountdown() {
        assertEquals("1:30", Rest.format(90_000))
        assertEquals("0:05", Rest.format(4_200))
        assertEquals("0:00", Rest.format(-10))
    }

    private val w = Workout(
        "A", null,
        listOf(Exercise(null, "Panca", 2, "10", "rec 60"), Exercise(null, "Croci", 1, "F", "")),
    )

    @Test
    fun completingASetStartsRest() {
        val p = Progress(0, 0, 0).completeSet(w, now = 1_000)
        assertEquals(1, p.setsDone)
        assertEquals(61_000, p.restUntil)
        assertTrue(p.isResting(30_000))
        assertFalse(p.isResting(61_000))
        // L'esercizio senza nota usa il recupero di base.
        val q = Progress(0, 1, 0).completeSet(
            w.copy(exercises = w.exercises + Exercise(null, "Plank", 2, "F", "")), now = 0,
        )
        assertEquals(Rest.DEFAULT_SECONDS * 1000L, q.restTotal)
    }

    @Test
    fun lastSetDoesNotRest() {
        val p = Progress(0, 1, 0).completeSet(w, now = 0)
        assertTrue(p.isFinished(w))
        assertEquals(0, p.restUntil)
    }

    @Test
    fun adjustRestKeepsAtLeastFiveSeconds() {
        val p = Progress(0, 0, 1, restUntil = 20_000, restTotal = 60_000)
        assertEquals(35_000, p.adjustRest(15_000, now = 10_000).restUntil)
        assertEquals(15_000, p.adjustRest(-60_000, now = 10_000).restUntil)
    }

    @Test
    fun undoAndSkipStopRest() {
        val p = Progress(0, 0, 1, restUntil = 99_000, restTotal = 60_000)
        assertEquals(0, p.undo(w).restUntil)
        assertEquals(0, p.skip(w).restUntil)
    }

    @Test
    fun weekCheck() {
        val tue = LocalDate.of(2026, 11, 24)
        assertTrue(Days.isThisWeek(LocalDate.of(2026, 11, 23), tue)) // lunedì
        assertTrue(Days.isThisWeek(tue, tue))
        assertFalse(Days.isThisWeek(LocalDate.of(2026, 11, 22), tue)) // domenica scorsa
        assertFalse(Days.isThisWeek(null, tue))
    }

    @Test
    fun formatsWeights() {
        assertEquals("40 kg", formatKg(40f))
        assertEquals("42,5 kg", formatKg(42.5f))
    }
}
