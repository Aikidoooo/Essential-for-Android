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

/** 長押しの時間を待たない短いドラッグでも、確定とキャンセルが働くことを検証する。 */
class ImmediateNavigationDragTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun dragStartsWithoutLongPressAndTapStillWorks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            val bar = compose.onNodeWithTag("liquid-glass-navigation")
            bar.performTouchInput {
                down(Offset(width / 6f, height / 2f))
                moveTo(Offset(width * 5f / 6f, height / 2f), 100)
                up()
            }
            compose.onNodeWithContentDescription("プロフィール").assertIsSelected()
            bar.performTouchInput {
                down(Offset(width * 5f / 6f, height / 2f))
                moveTo(Offset(width / 6f, height / 2f), 100)
                cancel()
            }
            compose.onNodeWithContentDescription("プロフィール").assertIsSelected()
            compose.onNodeWithContentDescription("ホーム").performClick().assertIsSelected()
        } finally { scenario.close() }
    }
}
