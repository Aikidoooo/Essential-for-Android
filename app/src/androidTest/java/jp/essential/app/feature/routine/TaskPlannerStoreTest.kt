package jp.essential.app.feature.routine

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.profile.AppProgressStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** 実ユーザーの保存領域やXPに触れず、予定の境界と移行・保存を検証する。 */
class TaskPlannerStoreTest {
    private val day = LocalDate.of(2026, 10, 14)
    private fun task(date: LocalDate = day, repeat: TaskRepeat = TaskRepeat.None) =
        ScheduledTask(UUID.randomUUID().toString(), "予定", "メモ", date.atTime(10, 0), date.atTime(11, 0), repeat = repeat)

    private fun withContext(test: (Context) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "planner_test_${UUID.randomUUID()}_"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        try { test(context) }
        finally { listOf("routine", "app_progress").forEach { base.deleteSharedPreferences(prefix + it) } }
    }

    @Test fun midnightAndMultiDayPlansHaveExclusiveEnds() {
        val overnight = task().copy(start = day.atTime(23, 0), end = day.plusDays(1).atTime(1, 0))
        assertEquals(1, occurrencesOn(overnight, day).size)
        assertEquals(1, occurrencesOn(overnight, day.plusDays(1)).size)
        assertTrue(occurrencesOn(overnight, day.plusDays(2)).isEmpty())
        val allDay = task().copy(start = day.atStartOfDay(), end = day.plusDays(1).atStartOfDay(), allDay = true)
        assertTrue(occurrencesOn(allDay, day.plusDays(1)).isEmpty())
        assertEquals("10/14 · 終日", taskPeriodLabel(TaskOccurrence(allDay, allDay.start, allDay.end)))
    }

    @Test fun repeatHandlesWeekdaysMonthEndsLeapYearsAndCustomIntervals() {
        assertFalse(occursOn(task(repeat = TaskRepeat.Weekdays), LocalDate.of(2026, 10, 17)))
        assertTrue(occursOn(task(repeat = TaskRepeat.Weekdays), LocalDate.of(2026, 10, 19)))
        assertFalse(occursOn(task(repeat = TaskRepeat.Weekly), day.plusDays(6)))
        assertTrue(occursOn(task(repeat = TaskRepeat.Weekly), day.plusDays(7)))
        val monthly = task(LocalDate.of(2026, 1, 31), TaskRepeat.Monthly)
        assertTrue(occursOn(monthly, LocalDate.of(2026, 2, 28)))
        assertFalse(occursOn(monthly, LocalDate.of(2026, 2, 27)))
        val yearly = task(LocalDate.of(2024, 2, 29), TaskRepeat.Yearly)
        assertTrue(occursOn(yearly, LocalDate.of(2025, 2, 28)))
        assertTrue(occursOn(yearly, LocalDate.of(2028, 2, 29)))
        val custom = task(repeat = TaskRepeat.Custom).copy(repeatDays = 3, repeatUntil = day.plusDays(6))
        assertTrue(occursOn(custom, day.plusDays(6)))
        assertFalse(occursOn(custom, day.plusDays(9)))
        assertEquals(day.plusDays(6), listOccurrence(custom, day.plusDays(10).atStartOfDay())!!.start.toLocalDate())
    }

    @Test fun filtersAreIndependentAndTasksAreSortedByDeadline() {
        val early = task().copy(title = "早い終了", end = day.atTime(10, 30))
        val late = task().copy(title = "遅い終了", end = day.atTime(15, 0))
        val done = task().copy(title = "完了", completed = setOf(day.toString()))
        val now = day.atTime(10, 45)
        val tasks = listOf(late, done, early)
        assertEquals(listOf("早い終了", "完了", "遅い終了"), taskList(tasks, TaskStatus.entries.toSet(), now).map { it.task.title })
        assertEquals(listOf("完了"), taskList(tasks, setOf(TaskStatus.Completed), now).map { it.task.title })
        assertEquals(2, taskList(tasks, setOf(TaskStatus.Pending, TaskStatus.Expired), now).size)
        assertTrue(taskList(tasks, emptySet(), now).isEmpty())
        withContext { context ->
            val store = TaskStore(context)
            store.saveFilter("calendar", setOf(TaskStatus.Pending))
            store.saveFilter("list", emptySet())
            assertEquals(setOf(TaskStatus.Pending), TaskStore(context).loadFilter("calendar"))
            assertTrue(TaskStore(context).loadFilter("list").isEmpty())
        }
    }

    @Test fun remindersHandleOffsetsCompletedOccurrencesAndLegacyTimes() {
        val daily = task(repeat = TaskRepeat.Daily).copy(notificationEnabled = true, reminderMinutes = 1440)
        assertEquals(day.atTime(10, 0), nextTaskReminder(daily, day.atTime(9, 0)))
        val completed = daily.copy(completed = setOf(day.plusDays(1).toString()))
        assertEquals(day.plusDays(1).atTime(10, 0), nextTaskReminder(completed, day.atTime(9, 0)))
        assertNull(nextTaskReminder(task().copy(notificationEnabled = true), day.atTime(12, 0)))
        val legacy = task(repeat = TaskRepeat.Daily).copy(notificationEnabled = true, legacyNotificationTime = LocalTime.of(9, 30))
        assertEquals(day.atTime(9, 30), nextTaskReminder(legacy, day.atTime(8, 0)))
    }

    @Test fun legacyTasksXpCompletionAndNotificationTimesArePreserved() = withContext { context ->
        val today = LocalDate.now()
        val old = JSONArray()
        RoutineCadence.entries.forEach { cadence -> old.put(JSONObject().put("id", cadence.name)
            .put("title", cadence.label).put("cadence", cadence.name).put("points", 5)
            .put("completedPeriod", routinePeriodKey(cadence, today)).put("notificationEnabled", true)
            .put("notificationHour", 9).put("notificationMinute", 30)
            .put("eventStartDate", today.toString()).put("eventStartTime", "10:00").put("eventDurationDays", 3)) }
        context.getSharedPreferences("routine", Context.MODE_PRIVATE).edit().putString("tasks", old.toString()).commit()
        context.getSharedPreferences("app_progress", Context.MODE_PRIVATE).edit()
            .putBoolean("routine_progress_migrated_v1", true).putInt("total_xp", 77).commit()
        val tasks = TaskStore(context).load()
        assertEquals(3, tasks.size)
        assertEquals(77, AppProgressStore(context).loadXp())
        assertTrue(tasks.all { it.completed.isNotEmpty() && it.legacyNotificationTime == LocalTime.of(9, 30) })
        assertEquals(today.plusDays(2), tasks.single { it.id == "Event" }.repeatUntil)
        assertEquals(LocalTime.of(10, 0), tasks.single { it.id == "Event" }.start.toLocalTime())
        assertEquals(today.atTime(10, 0), nextTaskReminder(tasks.single { it.id == "Event" }.copy(completed = emptySet()), today.atTime(9, 0)))
        assertEquals(today.plusDays(1).atTime(9, 30), nextTaskReminder(tasks.single { it.id == "Event" }, today.atTime(9, 0)))
        assertEquals(old.toString(), context.getSharedPreferences("routine", Context.MODE_PRIVATE).getString("tasks", null))
        assertEquals(tasks, TaskStore(context).load())
    }

    @Test fun concurrentCompletionAndRetryAwardXpOnlyOncePerOccurrence() = withContext { context ->
        val task = task(repeat = TaskRepeat.Daily).copy(xp = 300)
        val store = TaskStore(context)
        store.save(listOf(task))
        val pool = Executors.newFixedThreadPool(4)
        try { pool.invokeAll((1..8).map { Callable { TaskStore(context).complete(task.id, day.toString()) } }).forEach { it.get() } }
        finally { pool.shutdown() }
        assertEquals(300, AppProgressStore(context).loadXp())
        assertEquals(setOf(day.toString()), store.load().single().completed)
        // 完了保存の直前に中断した状態を再現しても、XP側の受取IDで再付与を防ぐ。
        store.save(listOf(task))
        store.complete(task.id, day.toString())
        assertEquals(300, AppProgressStore(context).loadXp())
        store.complete(task.id, day.plusDays(1).toString())
        assertEquals(600, AppProgressStore(context).loadXp())
    }

    @Test fun invalidDataNeverOverwritesSavedTasks() = withContext { context ->
        val preferences = context.getSharedPreferences("routine", Context.MODE_PRIVATE)
        preferences.edit().putString("tasks", "壊れたJSON").commit()
        assertTrue(runCatching { TaskStore(context).load() }.isFailure)
        assertEquals("壊れたJSON", preferences.getString("tasks", null))
        assertFalse(preferences.contains("scheduled_tasks_v2"))
        assertTrue(runCatching { task().copy(xp = 0) }.isFailure)
        assertTrue(runCatching { task().copy(xp = 301) }.isFailure)
        assertTrue(runCatching { task().copy(end = task().start) }.isFailure)
    }

    @Test fun editedOrDisabledRemindersReplaceAndCancelTheAlarm() = withContext { context ->
        val date = LocalDate.now().plusDays(1)
        val task = task(date).copy(notificationEnabled = true)
        try {
            RoutineNotificationScheduler.schedule(context, task)
            val pending = PendingIntent.getBroadcast(context, task.id.hashCode(), RoutineNotificationScheduler.reminderIntent(context, task.id),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            assertNotNull(pending)
            RoutineNotificationScheduler.schedule(context, task.copy(notificationEnabled = false))
            assertNull(PendingIntent.getBroadcast(context, task.id.hashCode(), RoutineNotificationScheduler.reminderIntent(context, task.id),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        } finally { RoutineNotificationScheduler.cancel(context, task.id) }
    }
}
