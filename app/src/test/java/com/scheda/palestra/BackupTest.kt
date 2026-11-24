package com.scheda.palestra

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupTest {
    @Test
    fun roundTrip() {
        val d = LocalDate.of(2026, 11, 24)
        val json = Backup.encode("scheda", mapOf("panca piana" to 42.5f), mapOf("lunedì" to d)).toString()
        val b = Backup.decode(json)
        assertEquals("scheda", b.text)
        assertEquals(42.5f, b.weights["panca piana"])
        assertEquals(d, b.history["lunedì"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherFiles() {
        Backup.decode("""{"scheda": "x"}""")
    }
}
