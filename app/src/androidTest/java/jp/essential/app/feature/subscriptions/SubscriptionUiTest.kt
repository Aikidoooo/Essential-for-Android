package jp.essential.app.feature.subscriptions

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SubscriptionUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun savesReopensEditsAndDeletesSubscription() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("subscriptions", Context.MODE_PRIVATE)
        val original = preferences.getString("entries", null)
        preferences.edit().putString("entries", "[]").commit()
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_FEATURE, "subscriptions")
        var scenario = ActivityScenario.launch<MainActivity>(intent)
        try {
            compose.onNodeWithText("サブスクを追加").performClick()
            compose.onNodeWithText("サービス名").performTextReplacement("検証用月額")
            compose.onNodeWithText("月額（円）").performTextReplacement("980")
            compose.onNodeWithText("毎月の決済日（1〜31）").performTextReplacement("31")
            compose.onNodeWithText("開始日（yyyy-MM-dd）").performTextReplacement("2026-01-01")
            compose.onNodeWithText("保存", substring = false).performClick()
            compose.waitUntil(10000) { SubscriptionStore(context).load().size == 1 }
            assertEquals(980L, SubscriptionStore(context).load().single().yen)
            scenario.close()
            scenario = ActivityScenario.launch(intent)
            compose.onNodeWithText("¥980", substring = false).assertExists()
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("検証用月額"))
            compose.onNodeWithText("検証用月額", substring = false).assertIsDisplayed()
            compose.onNodeWithText("編集", substring = false).performScrollTo().performClick()
            compose.onNodeWithText("月額（円）").performTextReplacement("1200")
            compose.onNodeWithText("保存", substring = false).performClick()
            compose.waitUntil(10000) { SubscriptionStore(context).load().single().yen == 1200L }
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("削除", substring = false))
            compose.onNodeWithText("削除", substring = false).performClick()
            compose.onNodeWithText("サブスクを削除").assertExists()
            compose.waitForIdle()
            android.os.SystemClock.sleep(350)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                java.io.File(context.filesDir, "subscription-delete-app.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.onNodeWithText("キャンセル").performClick()
            assertEquals(1, SubscriptionStore(context).load().size)
            compose.onNodeWithText("削除", substring = false).performClick()
            compose.onNodeWithText("記録を削除", substring = false).performClick()
            compose.waitUntil(10000) { SubscriptionStore(context).load().isEmpty() }
        } finally {
            scenario.close()
            preferences.edit().apply { if (original == null) remove("entries") else putString("entries", original) }.commit()
        }
    }
}
