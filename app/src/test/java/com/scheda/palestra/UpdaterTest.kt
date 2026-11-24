package com.scheda.palestra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(Updater.compareVersions("1.10", "1.9") > 0)
        assertTrue(Updater.compareVersions("1.1", "1.0") > 0)
        assertTrue(Updater.compareVersions("1.0", "1.1") < 0)
        assertEquals(0, Updater.compareVersions("1.1", "1.1.0"))
        assertTrue(Updater.compareVersions("2.0", "1.99") > 0)
    }
}
