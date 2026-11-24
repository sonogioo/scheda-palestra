package com.scheda.palestra

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) = rule.onRoot().captureRoboImage("build/shots/$name.png")

    private fun settle() = rule.waitForIdle()

    private fun tap(text: String) {
        rule.onNodeWithText(text).performClick(); settle()
    }

    private fun tapTag(tag: String) {
        rule.onNodeWithTag(tag).performClick(); settle()
    }


    @Test
    fun screens() {
        settle()
        shot("1_home_oggi")

        tap("Lun")
        shot("2_home_lunedi")

        tapTag("start_0")
        tap("+"); tap("+"); tap("+"); tap("+") // 10 kg
        shot("3_train_peso")

        tapTag("cta") // Serie fatta → recupero
        shot("4_recupero")

        tapTag("cta") // Inizia serie 2
        shot("5_serie2")

        tap("Esci")
        tap("Ven")
        tapTag("start_3")
        repeat(60) {
            if (rule.onAllNodesWithTag("cta").fetchSemanticsNodes().isEmpty()) return@repeat
            tapTag("cta")
        }
        shot("6_fine")

        tap("Torna alle schede")
        shot("7_home_settimana")
    }
}
