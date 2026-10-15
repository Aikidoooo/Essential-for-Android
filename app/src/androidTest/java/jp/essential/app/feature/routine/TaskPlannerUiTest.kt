package jp.essential.app.feature.routine

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.AppIconManager
import jp.essential.app.profile.AppProgressStore
import jp.essential.app.ui.AnimatedBackdrop
import jp.essential.app.ui.GlassBackdropScope
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

/** 独立した予定とXPで、新画面の主要な入力・遷移・フィルターを操作する。 */
class TaskPlannerUiTest {
    @get:Rule val compose = createComposeRule()
    private fun fixture(test: (Context, TaskStore) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "planner_ui_${UUID.randomUUID()}_"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        try {
            val store = TaskStore(context)
            val today = LocalDate.now()
            store.save(listOf(
                ScheduledTask("future", "未来タスク", "時間ごとの予定", today.plusDays(1).atTime(10, 0), today.plusDays(1).atTime(11, 0), xp = 100),
                ScheduledTask("expired", "期限切れタスク", "", today.minusDays(1).atTime(9, 0), today.minusDays(1).atTime(10, 0)),
                ScheduledTask("done", "完了タスク", "", today.atStartOfDay(), today.plusDays(1).atStartOfDay(),
                    allDay = true, completed = setOf(today.toString()))))
            compose.setContent {
                val activityContext = LocalContext.current
                val uiContext = remember(activityContext) {
                    object : ContextWrapper(activityContext) {
                        override fun getApplicationContext(): Context = this
                        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
                    }
                }
                CompositionLocalProvider(LocalContext provides uiContext) {
                    EssentialTheme(darkTheme = true) {
                        Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
                        GlassBackdropScope(Modifier.fillMaxSize(), background = {
                            AnimatedBackdrop(AppIconManager.defaultDark, motionEnabled = false)
                        }) { RoutineScreen {} }
                        }
                    }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("task-add").fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
            test(context, store)
        } finally { listOf("routine", "app_progress").forEach { base.deleteSharedPreferences(prefix + it) } }
    }

    @Test fun calendarDayTableAndIndependentMultiSelectWork() = fixture { context, store ->
        capture(context, "task-calendar-home.png")
        val date = LocalDate.now().plusDays(1)
        if (YearMonth.from(date) != YearMonth.now()) compose.onNodeWithContentDescription("次の月").performClick()
        compose.onNodeWithTag("task-date-$date").performClick()
        compose.onNodeWithText("24時間の予定").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("task-day-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("task-day-table").performScrollToNode(hasText("未来タスク"))
        compose.onNodeWithText("未来タスク").assertIsDisplayed()
        capture(context, "task-day-table.png")
        compose.onNodeWithContentDescription("戻る").performClick()
        compose.onNodeWithTag("calendar-multi-select").performClick()
        capture(context, "task-filter-before-click.png")
        compose.onNodeWithTag("task-filter-Completed").performClick()
        capture(context, "task-filter-after-click.png")
        compose.onNodeWithTag("task-filter-Expired").performClick()
        compose.onNodeWithText("決定").performClick()
        assertEquals(setOf(TaskStatus.Pending), store.loadFilter("calendar"))
        compose.onNodeWithTag("task-home").performScrollToNode(hasTestTag("list-multi-select"))
        compose.onNodeWithTag("list-multi-select").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onNodeWithTag("task-filter-Pending").performClick()
        compose.onNodeWithTag("task-filter-Expired").performClick()
        compose.onNodeWithText("決定").performClick()
        assertEquals(setOf(TaskStatus.Completed), store.loadFilter("list"))
        assertEquals(setOf(TaskStatus.Pending), store.loadFilter("calendar"))
        compose.onNodeWithTag("task-home").performScrollToNode(hasText("完了タスク"))
        compose.onNodeWithText("完了タスク").assertIsDisplayed()
        compose.onNodeWithText("未来タスク").assertDoesNotExist()
    }

    @Test fun taskEditorValidatesXpAndPersistsMemoTimeAndCompletion() = fixture { context, store ->
        compose.onNodeWithTag("task-home").performScrollToNode(hasTestTag("task-add"))
        compose.onNodeWithTag("task-add").performClick()
        compose.onNodeWithTag("task-title").performTextInput("追加タスク")
        compose.onNodeWithTag("task-memo").performScrollTo().performTextInput("確認用のメモ")
        compose.onNodeWithTag("task-xp").performScrollTo().performTextReplacement("301")
        compose.onNodeWithTag("task-save").assertIsNotEnabled()
        compose.onNodeWithTag("task-xp").performTextReplacement("300")
        compose.onNodeWithTag("task-xp").performImeAction()
        compose.onNodeWithTag("task-save").assertIsEnabled()
        capture(context, "task-editor-xp.png")
        compose.onNodeWithTag("task-title").performScrollTo()
        capture(context, "task-editor-top.png")
        compose.onNodeWithTag("task-start-date").performClick()
        capture(context, "task-editor-calendar.png")
        compose.onNode(hasTestTag("task-date-${LocalDate.now()}") and hasAnyAncestor(hasTestTag("task-editor"))).performClick()
        compose.onNodeWithTag("task-start-time").performClick()
        compose.onNodeWithTag("task-time-hours").performScrollToIndex(8)
        compose.waitForIdle()
        capture(context, "task-editor-time.png")
        compose.onNodeWithTag("task-end-time").performScrollTo().performClick()
        compose.onNodeWithTag("task-time-hours").performScrollToIndex(20)
        compose.waitForIdle()
        compose.onNodeWithTag("task-save").performClick()
        compose.waitUntil(10_000) { store.load().any { it.title == "追加タスク" } }
        val added = store.load().single { it.title == "追加タスク" }
        assertEquals(300, added.xp)
        assertEquals("確認用のメモ", added.memo)
        assertEquals(8, added.start.hour)
        val before = AppProgressStore(context).loadXp()
        compose.onNodeWithTag("task-home").performScrollToNode(hasText("追加タスク"))
        compose.onNodeWithContentDescription("追加タスクを完了").performClick()
        compose.waitUntil(10_000) { AppProgressStore(context).loadXp() == before + 300 }
        assertEquals(setOf(added.start.toLocalDate().toString()), store.load().single { it.id == added.id }.completed)
    }

    private fun capture(context: Context, name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
