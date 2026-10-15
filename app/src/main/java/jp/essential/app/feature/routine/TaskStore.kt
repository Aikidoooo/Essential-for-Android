package jp.essential.app.feature.routine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.WeekFields
import jp.essential.app.profile.AppProgressStore

/** 旧日課を残したまま、新しい予定・完了・フィルターを端末へ保存する。 */
internal class TaskStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("routine", Context.MODE_PRIVATE)
    private val progress = AppProgressStore(context)

    fun load(): List<ScheduledTask> = synchronized(lock) {
        if (!preferences.contains("scheduled_tasks_v2")) {
            val legacy = preferences.getString("tasks", "[]").orEmpty()
            val expected = JSONArray(legacy).length()
            val oldTasks = LegacyRoutineStore(context).loadTasks()
            check(oldTasks.size == expected) { "以前のタスクを読み取れませんでした。保存データは保持しています。" }
            val migrated = oldTasks.map(::migrateTask)
            check(preferences.edit().putString("tasks_before_planner_v2", legacy)
                .putString("scheduled_tasks_v2", encode(migrated)).commit()) { "タスクを保存できませんでした" }
        }
        val array = JSONArray(preferences.getString("scheduled_tasks_v2", "[]"))
        (0 until array.length()).map { decode(array.getJSONObject(it)) }
    }

    fun save(tasks: List<ScheduledTask>) = synchronized(lock) {
        check(tasks.map { it.id }.distinct().size == tasks.size)
        val previous = if (preferences.contains("scheduled_tasks_v2")) load() else emptyList()
        check(preferences.edit().putString("scheduled_tasks_v2", encode(tasks)).commit()) { "タスクを保存できませんでした" }
        previous.filter { old -> tasks.none { it.id == old.id } }.forEach { RoutineNotificationScheduler.cancel(context, it.id) }
        tasks.forEach { RoutineNotificationScheduler.schedule(context, it) }
    }

    fun rescheduleAll() = synchronized(lock) {
        load().forEach { RoutineNotificationScheduler.schedule(context, it) }
    }

    /** 同じ予定の完了を連打しても、XPの受取IDを共通ストアで一度だけ確定する。 */
    fun complete(taskId: String, occurrenceKey: String): List<ScheduledTask> = synchronized(lock) {
        val tasks = load()
        val task = tasks.firstOrNull { it.id == taskId } ?: return@synchronized tasks
        if (occurrenceKey in task.completed) return@synchronized tasks
        val date = LocalDate.parse(occurrenceKey)
        val occurrence = occurrenceStartingOn(task, date) ?: return@synchronized tasks
        progress.awardTaskXp(occurrence.rewardId, task.xp)
        val updated = tasks.map { if (it.id == task.id) it.copy(completed = it.completed + occurrenceKey) else it }
        save(updated)
        updated
    }

    fun loadFilter(name: String): Set<TaskStatus> = preferences.getStringSet("task_filter_$name", null)
        ?.mapNotNull { runCatching { TaskStatus.valueOf(it) }.getOrNull() }?.toSet()
        ?: TaskStatus.entries.toSet()

    fun saveFilter(name: String, statuses: Set<TaskStatus>) {
        preferences.edit().putStringSet("task_filter_$name", statuses.map { it.name }.toSet()).apply()
    }

    private fun encode(tasks: List<ScheduledTask>): String = JSONArray().apply {
        tasks.forEach { task -> put(JSONObject().put("id", task.id).put("title", task.title)
            .put("memo", task.memo).put("start", task.start.toString()).put("end", task.end.toString())
            .put("allDay", task.allDay).put("repeat", task.repeat.name).put("repeatDays", task.repeatDays)
            .put("repeatUntil", task.repeatUntil?.toString()).put("notificationEnabled", task.notificationEnabled)
            .put("reminderMinutes", task.reminderMinutes).put("xp", task.xp)
            .put("legacyNotificationTime", task.legacyNotificationTime?.toString())
            .put("legacyFirstReminderNotBefore", task.legacyFirstReminderNotBefore?.toString())
            .put("completed", JSONArray(task.completed.toList()))) }
    }.toString()

    private fun decode(item: JSONObject): ScheduledTask {
        val completed = item.optJSONArray("completed") ?: JSONArray()
        return ScheduledTask(item.getString("id"), item.getString("title"), item.optString("memo"),
            LocalDateTime.parse(item.getString("start")), LocalDateTime.parse(item.getString("end")),
            allDay = item.optBoolean("allDay"), repeat = TaskRepeat.valueOf(item.getString("repeat")),
            repeatDays = item.optInt("repeatDays", 1),
            repeatUntil = item.optString("repeatUntil").takeIf { it.isNotBlank() && it != "null" }?.let(LocalDate::parse),
            notificationEnabled = item.optBoolean("notificationEnabled"),
            reminderMinutes = item.optInt("reminderMinutes"), xp = item.getInt("xp"),
            completed = (0 until completed.length()).map { completed.getString(it) }.toSet(),
            legacyNotificationTime = item.optString("legacyNotificationTime")
                .takeIf { it.isNotBlank() && it != "null" }?.let(LocalTime::parse),
            legacyFirstReminderNotBefore = item.optString("legacyFirstReminderNotBefore")
                .takeIf { it.isNotBlank() && it != "null" }?.let(LocalDateTime::parse))
    }

    private fun migrateTask(task: RoutineTask): ScheduledTask {
        val today = LocalDate.now()
        val eventDate = task.eventStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val anchor = when (task.cadence) {
            RoutineCadence.Weekly -> today.minusDays(today.dayOfWeek.value - 1L)
            RoutineCadence.Event -> eventDate ?: today
            else -> today
        }
        val completed = task.completedPeriod?.let { key ->
            when {
                key.startsWith("D:") || key.startsWith("E:") -> key.substring(2)
                key.startsWith("W:") -> runCatching {
                    val values = key.substring(2).split("-")
                    LocalDate.of(values[0].toInt(), 1, 4)
                        .with(WeekFields.ISO.weekOfWeekBasedYear(), values[1].toLong())
                        .with(java.time.temporal.ChronoField.DAY_OF_WEEK, 1).toString()
                }.getOrNull()
                else -> null
            }
        }?.let { setOf(it) }.orEmpty()
        val duration = if (task.cadence == RoutineCadence.Weekly) 7L else 1L
        val startTime = if (task.cadence == RoutineCadence.Event) task.eventStartTime?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        } ?: LocalTime.MIN else LocalTime.MIN
        return ScheduledTask(task.id, task.title, "", anchor.atTime(startTime), anchor.plusDays(duration).atStartOfDay(),
            allDay = startTime == LocalTime.MIN, repeat = if (task.cadence == RoutineCadence.Weekly) TaskRepeat.Weekly else TaskRepeat.Daily,
            repeatUntil = if (task.cadence == RoutineCadence.Event) anchor.plusDays(task.eventDurationDays - 1L) else null,
            notificationEnabled = task.notificationEnabled,
            reminderMinutes = 0, xp = task.points.coerceIn(1, 300), completed = completed,
            legacyNotificationTime = if (task.notificationEnabled) LocalTime.of(task.notificationHour, task.notificationMinute) else null,
            legacyFirstReminderNotBefore = if (task.cadence == RoutineCadence.Event) anchor.atTime(startTime) else null)
    }

    private companion object { val lock = Any() }
}
