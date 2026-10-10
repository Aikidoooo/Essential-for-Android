package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.MainActivity
import org.junit.Rule
import org.junit.Test

class TabSwipeNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    /** 遷移途中でも表示領域を固定し、安全域の付け替えによる斜め移動を防ぐ。 */
    @Test fun tabTransitionKeepsViewportTopAndHeightFixed() {
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            compose.waitForIdle()
            val initial = compose.onNodeWithTag("main-tabs").fetchSemanticsNode().boundsInRoot
            compose.mainClock.autoAdvance = false
            listOf("機能一覧", "ホーム", "プロフィール", "機能一覧").forEach { destination ->
                compose.onNodeWithContentDescription(destination).performClick()
                compose.mainClock.advanceTimeBy(96)
                val during = compose.onNodeWithTag("main-tabs").fetchSemanticsNode().boundsInRoot
                org.junit.Assert.assertEquals("遷移中も上端が固定", initial.top, during.top, 0.5f)
                org.junit.Assert.assertEquals("遷移中も高さが固定", initial.height, during.height, 0.5f)
                compose.mainClock.advanceTimeBy(1200)
            }
        } finally {
            compose.mainClock.autoAdvance = true
            scenario.close()
        }
    }

    @Test fun longPressNavigationFollowsFingerAndCommitsOrCancels() {
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            val bar = compose.onNodeWithTag("liquid-glass-navigation")
            bar.performTouchInput {
                down(Offset(width / 6f, height / 2f))
                advanceEventTime(700)
                moveTo(Offset(width * 5f / 6f, height / 2f), 350)
            }
            compose.waitForIdle()
            // OSの起動画面が退場したあと、指を離す前のガラスの膨らみを撮影する。
            Thread.sleep(1000)
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null), "liquid-navigation-drag.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            bar.performTouchInput {
                up()
            }
            compose.onNodeWithContentDescription("プロフィール").assertIsSelected()
            bar.performTouchInput {
                down(Offset(width * 5f / 6f, height / 2f))
                advanceEventTime(700)
                moveTo(Offset(width / 6f, height / 2f), 350)
                cancel()
            }
            compose.onNodeWithContentDescription("プロフィール").assertIsSelected()
            bar.performTouchInput {
                down(Offset(width * 5f / 6f, height / 2f))
                advanceEventTime(700)
                moveTo(Offset(-30f, height / 2f), 350)
                up()
            }
            compose.onNodeWithContentDescription("ホーム").assertIsSelected()
            compose.onNodeWithContentDescription("機能一覧").performClick().assertIsSelected()
        } finally { scenario.close() }
    }

    @Test fun swipesMoveBetweenThreeTabsAndRespectEdges() {
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            compose.onNodeWithContentDescription("ホーム").assertIsSelected()
            compose.onNodeWithTag("main-tabs").performTouchInput { swipeLeft() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("機能一覧").assertIsSelected()
            compose.onAllNodesWithText("機能一覧", substring = false).onFirst().assertExists()
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
