package com.scheda.palestra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    private val w = Workout(
        "A", null,
        listOf(
            Exercise(null, "Panca", 2, "10", ""),
            Exercise(null, "Croci", 3, "F", ""),
        ),
    )

    @Test
    fun advancesThroughSetsAndExercises() {
        var p = Progress(0, 0, 0)
        p = p.advance(w); assertEquals(Progress(0, 0, 1), p)
        p = p.advance(w); assertEquals(Progress(0, 1, 0), p)
        repeat(3) { p = p.advance(w) }
        assertTrue(p.isFinished(w))
        assertEquals(p, p.advance(w))
    }

    @Test
    fun undoGoesBackAcrossExercises() {
        assertEquals(Progress(0, 0, 1), Progress(0, 1, 0).undo(w))
        assertEquals(Progress(0, 1, 2), Progress(0, 2, 0).undo(w))
        assertFalse(Progress(0, 0, 0).canUndo)
    }

    @Test
    fun validatesAgainstChangedScheda() {
        assertTrue(Progress(0, 1, 2).isValidFor(listOf(w)))
        assertFalse(Progress(1, 0, 0).isValidFor(listOf(w)))
        assertFalse(Progress(0, 0, 5).isValidFor(listOf(w)))
    }
}
