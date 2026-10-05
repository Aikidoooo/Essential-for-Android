package jp.essential.app.feature.subscriptions

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 削除対象の表示、キャンセル、確定と処理中の連打防止を確認する。 */
class SubscriptionDeleteDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cancelAndDeleteAreDistinctAndSavingDisablesActions() {
        var deleted = 0
        var cancelled = 0
        val saving = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                SubscriptionDeleteDialog(Subscription("test", "テスト動画", 980, 15, LocalDate.now()),
                    saving.value, null, { cancelled++ }, { deleted++; saving.value = true })
            }
        }
        compose.onNodeWithText("テスト動画").assertExists()
        compose.onNodeWithText("¥980／月").assertExists()
        compose.onNodeWithText("毎月15日に決済").assertExists()
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(context.filesDir, "subscription-delete-glass.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText("キャンセル").performClick()
        compose.runOnIdle { assertEquals(1, cancelled); assertEquals(0, deleted) }
        compose.onNodeWithText("記録を削除").performClick()
        compose.runOnIdle { assertEquals(1, deleted) }
        compose.onNodeWithText("削除中…").assertIsNotEnabled()
        compose.onNodeWithText("キャンセル").assertIsNotEnabled()
    }
}
