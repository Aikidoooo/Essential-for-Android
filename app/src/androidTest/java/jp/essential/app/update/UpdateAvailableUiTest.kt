package jp.essential.app.update

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UpdateAvailableUiTest {
    @get:Rule val compose = createComposeRule()
    private val release = AppRelease("0.5.2", "更新内容\n".repeat(200), "", 84_000_000, null)

    @Test fun longNotesAndLargeFontKeepActionsVisible() {
        var updates = 0
        var dismissals = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                EssentialTheme(darkTheme = true) {
                    UpdateAvailableDialog(release, "0.5.1", false, null, false, false, "", { dismissals++ }, { updates++ })
                }
            }
        }
        compose.onNode(hasText("アップデート") and hasClickAction()).assertIsDisplayed().performClick()
        compose.onNodeWithText("後で").assertIsDisplayed().performClick()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(instrumentation.targetContext.getExternalFilesDir(null), "update-ui-dark-large.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        assertEquals(1, updates)
        assertEquals(1, dismissals)
    }

    @Test fun downloadingDisablesActionsAndShowsProgress() {
        compose.setContent {
            EssentialTheme(darkTheme = false) {
                UpdateAvailableDialog(release, "0.5.1", true, 0.42f, false, false, "", {}, {})
            }
        }
        compose.onNodeWithText("ダウンロード中  42%").assertIsDisplayed()
        compose.onNodeWithText("準備中…").assertIsNotEnabled()
        compose.onNodeWithText("後で").assertIsNotEnabled()
    }

    @Test fun failedDownloadOffersRetry() {
        compose.setContent {
            EssentialTheme(darkTheme = true) {
                UpdateAvailableDialog(release, "0.5.1", false, null, false, true, "通信に失敗しました", {}, {})
            }
        }
        compose.onNodeWithText("通信に失敗しました").assertIsDisplayed()
        compose.onNodeWithText("再試行").assertIsEnabled()
    }

    @Test fun verifiedDownloadOffersInstall() {
        compose.setContent {
            EssentialTheme(darkTheme = false) {
                UpdateAvailableDialog(release, "0.5.1", false, null, true, false, "検証済みです", {}, {})
            }
        }
        compose.onNodeWithText("インストールへ進む").assertIsEnabled()
    }
}
