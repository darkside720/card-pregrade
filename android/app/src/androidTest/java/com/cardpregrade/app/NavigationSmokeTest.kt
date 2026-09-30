package com.cardpregrade.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardpregrade.app.camera.FakeCameraSource
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks every Phase 2 screen through the real NavHost. */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun clickText(text: String) = rule.onNodeWithText(text).performScrollTo().performClick()
    private fun assertScreen(tag: String) = rule.onNodeWithTag(tag).assertIsDisplayed()
    private fun back() = rule.onNodeWithContentDescription("Back").performClick()

    @Test
    fun homeShowsProductIdentityAndDisclaimer() {
        assertScreen("screen_home")
        rule.onNodeWithText("TCG Card Pre-Grading / Visual Inspection tool").assertIsDisplayed()
        rule.onNodeWithText("not official PSA, BGS, CGC", substring = true).performScrollTo().assertIsDisplayed()
    }

    @After
    fun tearDown() = TestContainer.reset()

    @Test
    fun newScanThroughCaptureAndAnalysisToDemoResult() {
        TestContainer.install(FakeCameraSource())
        clickText("New scan")
        assertScreen("screen_new_scan")
        clickText("Start guided capture")

        rule.waitForTag("screen_capture")
        rule.waitForText("Step 1 of 4")
        repeat(4) { rule.captureAndAccept() }
        rule.onNodeWithText("Continue to analysis").performScrollTo().assertIsEnabled()
        clickText("Continue to analysis")

        assertScreen("screen_analysis")
        rule.onNodeWithText("No analysis was run").assertIsDisplayed()
        rule.waitForText("4 photo(s) are stored")
        clickText("View demo result layout")

        assertScreen("screen_results")
        rule.onNodeWithTag("demo_banner").assertIsDisplayed()
        rule.onNodeWithText("DEMO / MOCK ANALYSIS").assertIsDisplayed()
        rule.onNodeWithText("Lugia 149/147").assertIsDisplayed()
    }

    @Test
    fun savedScansAndBack() {
        clickText("Saved scans")
        assertScreen("screen_saved")
        back()
        assertScreen("screen_home")
    }

    @Test
    fun demoResultOpensZoomForDefect() {
        clickText("View demo result")
        assertScreen("screen_results")
        clickText("Possible whitening")
        rule.onNodeWithText("Close").assertIsDisplayed().performClick()
    }

    @Test
    fun settingsEnablesDeveloperCalibration() {
        clickText("Settings & About")
        assertScreen("screen_settings")
        rule.onNodeWithTag("developer_mode_switch").performScrollTo().performClick()
        clickText("Open calibration screen")
        assertScreen("screen_calibration")
        rule.onNodeWithText("Corner crops").performScrollTo().assertIsDisplayed()
    }
}
