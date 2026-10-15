package jp.essential.app.feature.routine

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.MainActivity
import jp.essential.app.profile.AppProgressStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** 実際の機能ルートで開閉し、既存データとXPを変更せずに表示を確認する。 */
class TaskPlannerLiveUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun mainActivityCalendarEditorAndDayTableKeepSavedXp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val before = AppProgressStore(context).loadXp()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("open_feature", "routine")).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("task-add").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("task-date-${LocalDate.now()}").assertIsDisplayed()
            capture(context, "task-live-home.png")
            compose.onNodeWithTag("task-home").performScrollToNode(hasTestTag("task-add"))
            compose.onNodeWithTag("task-add").performClick()
            capture(context, "task-live-editor.png")
            compose.onNodeWithContentDescription("キャンセル").performClick()
            compose.onNodeWithTag("task-home").performScrollToNode(hasTestTag("task-date-${LocalDate.now()}"))
            compose.onNodeWithTag("task-date-${LocalDate.now()}").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("task-day-ready").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("戻る").performClick()
            assertEquals(before, AppProgressStore(context).loadXp())
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.executeShellCommand("dumpsys gfxinfo jp.essential.app.debug").use { descriptor ->
                val text = java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
                java.io.File(context.getExternalFilesDir(null), "task-live-gfxinfo.txt").writeText(text)
            }
        }
    }

    private fun capture(context: Context, name: String) {
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val bitmap = if (name == "task-live-home.png") compose.onNodeWithTag("task-home").captureToImage().asAndroidBitmap()
            else InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
