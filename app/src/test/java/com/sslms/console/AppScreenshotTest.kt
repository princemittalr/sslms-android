package com.sslms.console

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.sslms.console.data.DemoApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app in demo mode and saves a screenshot of each screen to
 * app/build/outputs/roborazzi. Run: ./gradlew recordRoborazziDebug
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-night-xxhdpi")
class AppScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val out = "build/outputs/roborazzi"

    @Before fun setUp() { DemoApi.simulateLatency = false }

    private fun settle() { rule.mainClock.advanceTimeBy(1500); rule.waitForIdle() }

    @Test
    fun walkThroughApp() {
        settle()
        rule.onRoot().captureRoboImage("$out/01_login.png")

        // Sign in with demo data
        rule.onNodeWithText("Demo data").performClick()
        rule.onNodeWithText("Password").performTextInput("admin123")
        rule.onAllNodesWithText("Sign in").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Nodes online").fetchSemanticsNodes().isNotEmpty() }
        settle()
        rule.onRoot().captureRoboImage("$out/02_overview.png")

        rule.onAllNodesWithText("Devices").onFirst().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("NODE001").fetchSemanticsNodes().isNotEmpty() }
        settle()
        rule.onRoot().captureRoboImage("$out/03_devices.png")

        rule.onNodeWithText("NODE003").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Live reading").fetchSemanticsNodes().isNotEmpty() }
        settle()
        rule.onRoot().captureRoboImage("$out/04_device_detail.png")
        rule.onNodeWithContentDescription("Back").performClick()
        settle()

        rule.onNodeWithText("Add device", useUnmergedTree = true).performClick()
        settle()
        rule.onRoot().captureRoboImage("$out/06_add_device.png")
        rule.onNodeWithContentDescription("Cancel").performClick()
        settle()

        rule.onAllNodesWithText("Faults").onFirst().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Lamp Failure").fetchSemanticsNodes().isNotEmpty() }
        settle()
        rule.onRoot().captureRoboImage("$out/07_faults.png")

        rule.onAllNodesWithText("Reports").onFirst().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Run report").fetchSemanticsNodes().isNotEmpty() }
        settle()
        rule.onRoot().captureRoboImage("$out/08_reports.png")

        rule.onAllNodesWithText("Settings").onFirst().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Hardware sync schedule").fetchSemanticsNodes().isNotEmpty() }
        settle()
        rule.onRoot().captureRoboImage("$out/09_settings.png")
    }
}
