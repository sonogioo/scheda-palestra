package com.scheda.palestra

import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    @Test
    fun screens() {
        shot("1_home_oggi")
        rule.onNodeWithText("Lun").performClick()
        rule.waitForIdle()
        shot("2_home_lunedi")
        rule.onNodeWithTag("start_0").performClick()
        shot("3_train_start")
        rule.onNodeWithText("Serie fatta").performClick()
        shot("4_train_set2")
        rule.onNodeWithText("Esci").performClick()
        rule.waitForIdle()
        shot("5_home_dopo_esci")
    }
}
