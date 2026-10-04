package jp.essential.app.feature.schedule

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EssentialCalendarTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun rangeCanBeSelectedAndReopenedAcrossMonths() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        var selected = ""
        try {
            scenario.onActivity { activity -> activity.setContent {
                EssentialTheme(darkTheme = false) {
                    EssentialCalendar("2026/10/05", "2026/10/08", {}, { start, end -> selected = "$start-$end" })
                }
            } }
            compose.onNodeWithText("2026年10月").assertExists()
            compose.onNodeWithText("28", substring = false).performClick()
            compose.onNodeWithText("翌月").performClick()
            compose.onNodeWithText("3", substring = false).performClick()
            compose.onNodeWithText("決定").performClick()
            compose.runOnIdle { assertEquals("2026/10/28-2026/11/03", selected) }
        } finally { scenario.close() }
    }
}
