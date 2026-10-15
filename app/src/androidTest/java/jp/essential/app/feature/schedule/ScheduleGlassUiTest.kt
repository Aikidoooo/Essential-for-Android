package jp.essential.app.feature.schedule

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

/** ガラスの置換後も入力、参加者の追加、日付選択、行程追加が操作できることを確認する。 */
class ScheduleGlassUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun formAndCalendarKeepWorkingWithGlass() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("open_feature", "schedule")).use {
            compose.onNodeWithText("行程表ジェネレーター").assertExists()
            compose.onNode(hasSetTextAction() and hasText("題名")).performScrollTo().performTextInput("京都旅行")
            compose.onNode(hasSetTextAction() and hasText("目的")).performTextInput("観光")
            compose.onNodeWithText("カレンダー", substring = false).performScrollTo().performClick()
            compose.onNodeWithText("開始日、終了日の順に選択").assertExists()
            compose.waitForIdle()
            android.os.SystemClock.sleep(300)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { image ->
                File(context.getExternalFilesDir(null), "schedule-calendar-single-background.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
            }
            compose.onNodeWithText("キャンセル").performClick()
            capture(context, "schedule-glass-top.png")
            compose.onNode(hasSetTextAction() and hasText("参加者 1")).performScrollTo().performTextInput("参加者A")
            compose.onNode(hasSetTextAction() and hasText("参加者 2")).assertExists()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("行程を追加"))
            compose.onNodeWithText("行程を追加").performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("行程 2"))
            compose.onNodeWithText("行程 2").assertExists()
            capture(context, "schedule-glass-entry.png")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("PDF表"))
            compose.onNodeWithText("PDF表").assertIsEnabled()
            compose.onNodeWithText("文章", substring = false).assertIsEnabled()
            compose.onNodeWithText("縦型画像").assertIsEnabled()
        }
    }
    private fun capture(context: Context, name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
