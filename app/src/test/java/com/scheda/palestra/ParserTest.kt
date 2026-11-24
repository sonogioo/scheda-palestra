package com.scheda.palestra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ParserTest {

    private val scheda = File("src/main/assets/scheda.txt").readText()

    @Test
    fun parsesTheFourDays() {
        val w = SchedaParser.parse(scheda)
        assertEquals(listOf("Lunedì", "Martedì", "Giovedì", "Venerdì"), w.map { it.displayName })
        assertEquals(listOf(7, 8, 6, 5), w.map { it.exercises.size })
        assertEquals("Monday Upper Body Focus", w[0].title)
    }

    @Test
    fun parsesExerciseDetails() {
        val giovedi = SchedaParser.parse(scheda)[2]
        val pectoral = giovedi.exercises[1]
        assertEquals("Pectoral Machine (o croci ai cavi dal basso)", pectoral.name)
        assertEquals("Petto", pectoral.group)
        assertEquals(3, pectoral.sets)
        assertEquals("10", pectoral.reps)

        val shoulder = giovedi.exercises[2]
        assertEquals("Spalle", shoulder.group)
        assertTrue(shoulder.toFailure)
        assertEquals("8", giovedi.exercises[3].reps)
    }

    @Test
    fun handlesBlankLinesBetweenEveryRow() {
        val spaced = scheda.lines().joinToString("\n\n")
        assertEquals(listOf(7, 8, 6, 5), SchedaParser.parse(spaced).map { it.exercises.size })
    }

    @Test
    fun parsesRangesAndNotes() {
        val w = SchedaParser.parse("Giorno A\n- Squat 4x8-10 80kg rec 90\n- Plank 3xmax")
        val squat = w[0].exercises[0]
        assertEquals("8-10", squat.reps)
        assertEquals("80kg rec 90", squat.note)
        assertTrue(w[0].exercises[1].toFailure)
    }

    @Test
    fun exercisesWithoutTitleGetDefaultName() {
        val w = SchedaParser.parse("Panca 3x10")
        assertEquals("Allenamento 1", w[0].displayName)
    }

    @Test
    fun summarizesGroupsAndSets() {
        val lunedi = SchedaParser.parse(scheda)[0]
        assertEquals("Petto, Spalle, Tricipiti", lunedi.groupsLabel)
        assertEquals(21, lunedi.totalSets)
    }
}
