package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import org.junit.Rule
import org.junit.Test

class TabSwipeNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun swipesMoveBetweenThreeTabsAndRespectEdges() {
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            compose.onNodeWithContentDescription("ホーム").assertIsSelected()
            compose.onNodeWithTag("main-tabs").performTouchInput { swipeLeft() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("機能一覧").assertIsSelected()
            compose.onNodeWithText("機能一覧", substring = false).assertExists()
            compose.onNodeWithTag("main-tabs").performTouchInput { swipeLeft() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("プロフィール").assertIsSelected()
            compose.onNodeWithText("プロフィール編集").assertExists()
            compose.onNodeWithTag("main-tabs").performTouchInput { swipeLeft() }
            compose.onNodeWithContentDescription("プロフィール").assertIsSelected()
            compose.onNodeWithTag("main-tabs").performTouchInput { swipeRight() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("機能一覧").assertIsSelected()
            compose.onNodeWithTag("main-tabs").performTouchInput { swipeRight() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("ホーム").assertIsSelected()
            compose.onNodeWithTag("main-tabs").performTouchInput { swipeRight() }
            compose.onNodeWithContentDescription("ホーム").assertIsSelected()
            compose.onNodeWithContentDescription("プロフィール").performClick()
            compose.onNodeWithContentDescription("プロフィール").assertIsSelected()
        } finally { scenario.close() }
    }

    @Test fun verticalScrollShortDragAndWidgetReorderDoNotSwitchTabs() {
        val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val originalOrder = preferences.getString("home_widget_order", null)
        preferences.edit().putString("home_widget_order", "downloader,qr_scanner").commit()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            compose.onNodeWithTag("main-tabs").performTouchInput {
                swipe(Offset(width * 0.55f, height * 0.45f), Offset(width * 0.60f, height * 0.45f), 150)
            }
            compose.onNodeWithContentDescription("ホーム").assertIsSelected()
            compose.onNodeWithTag("main-tabs").performTouchInput { swipeUp() }
            compose.onNodeWithContentDescription("ホーム").assertIsSelected()
            compose.onNodeWithTag("main-tabs").performTouchInput { swipeDown() }
            compose.waitForIdle()
            val card = compose.onNodeWithText("スキャナー", substring = false, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val root = compose.onNodeWithTag("main-tabs").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("main-tabs").performTouchInput {
                down(card.center - root.topLeft)
                advanceEventTime(700)
                moveBy(Offset(-width * 0.45f, 0f), 250)
                up()
            }
            compose.onNodeWithContentDescription("ホーム").assertIsSelected()
            compose.waitForIdle()
            org.junit.Assert.assertEquals("scanner", preferences.getString("home_widget_order", "")?.split(',')?.first())
        } finally {
            scenario.close()
            preferences.edit().apply {
                if (originalOrder == null) remove("home_widget_order") else putString("home_widget_order", originalOrder)
            }.commit()
        }
    }

    @Test fun featureScreenDoesNotReceiveMainTabSwipes() {
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_FEATURE, "notification_log"))
        try {
            compose.waitUntil(10000) { compose.onAllNodesWithTag("notification-log-screen").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("notification-log-screen").performTouchInput { swipeLeft(); swipeRight() }
            compose.onNodeWithTag("notification-log-screen").assertExists()
            compose.onNodeWithTag("main-tabs").assertDoesNotExist()
        } finally { scenario.close() }
    }
}
