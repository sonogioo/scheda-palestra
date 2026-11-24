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

    private val w3 = Workout(
        "B", null,
        listOf(Exercise(null, "A", 1, "10", ""), Exercise(null, "B", 2, "10", ""), Exercise(null, "C", 1, "10", "")),
    )

    @Test
    fun postponeMovesExerciseToTheEnd() {
        var p = Progress(0, 0, 0).postpone(w3)
        assertEquals(listOf(1, 2, 0), p.order)
        assertEquals("B", p.current(w3)?.name)
        assertEquals("C", p.nextExercise(w3)?.name)
        repeat(3) { p = p.advance(w3) } // B x2, C x1
        assertEquals("A", p.current(w3)?.name)
        assertTrue(p.isDone(w3, 2))
        assertFalse(p.isDone(w3, 0))
        assertFalse(p.canPostpone(w3)) // ultimo rimasto
        assertEquals(Progress(0, 1, 0, order = listOf(1, 2, 0)), p.undo(w3)) // torna a C
        p = p.advance(w3)
        assertTrue(p.isFinished(w3))
    }

    @Test
    fun cannotPostponeAfterStarting() {
        val p = Progress(0, 1, 1)
        assertFalse(p.canPostpone(w3))
        assertEquals(p, p.postpone(w3))
    }

}
