package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 一覧の下まで移動しても、機能名と戻る操作へ手が届くことを確認する。 */
class FeatureHeaderUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun headersRemainVisibleWhileContentScrollsAndBackStillWorks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val routes = listOf("schedule" to "行程表ジェネレーター", "downloader" to "ダウンローダー",
            "files" to "ファイル参照", "routine" to "日課", "mini_game" to "ミニゲーム",
            "notification_log" to "通知ログ", "mannaka" to "まんなか")
        for ((route, title) in routes) {
            val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_FEATURE, route))
            try {
                compose.waitUntil(15000) { compose.onAllNodesWithTag("fixed-feature-header").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(title, substring = false).assertIsDisplayed()
                val before = compose.onNodeWithTag("fixed-feature-header").fetchSemanticsNode().boundsInRoot
                repeat(3) { compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() } }
                compose.onNodeWithText(title, substring = false).assertIsDisplayed()
                val after = compose.onNodeWithTag("fixed-feature-header").fetchSemanticsNode().boundsInRoot
                assertTrue("$title の見出しが上端に残る", after.top <= before.top + 1f)
                compose.onNodeWithContentDescription(if (route == "mannaka") "ホームに戻る" else "戻る").assertIsDisplayed().performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithTag("fixed-feature-header").fetchSemanticsNodes().isEmpty() }
            } finally { scenario.close() }
        }
    }
}
