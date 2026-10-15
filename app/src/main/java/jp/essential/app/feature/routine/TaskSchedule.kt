package jp.essential.app.feature.routine

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal enum class TaskRepeat(val label: String) {
    None("しない"), Daily("毎日"), Weekdays("月曜日から金曜日"), Weekly("毎週"),
    Monthly("毎月"), Yearly("毎年"), Custom("カスタム")
}

internal enum class TaskStatus(val label: String) {
    Pending("未完了"), Completed("完了"), Expired("期限切れ")
}

internal data class ScheduledTask(
    val id: String,
    val title: String,
    val memo: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val repeat: TaskRepeat = TaskRepeat.None,
    val repeatDays: Int = 1,
    val repeatUntil: LocalDate? = null,
    val notificationEnabled: Boolean = false,
    val reminderMinutes: Int = 0,
    val xp: Int = 30,
    val completed: Set<String> = emptySet(),
    val legacyNotificationTime: LocalTime? = null,
    val legacyFirstReminderNotBefore: LocalDateTime? = null,
) {
    init {
        require(title.isNotBlank() && title.length <= 200)
        require(end.isAfter(start))
        require(xp in 1..300 && repeatDays in 1..365 && reminderMinutes in 0..10080)
        require(repeatUntil == null || repeatUntil >= start.toLocalDate())
    }
}

internal data class TaskOccurrence(val task: ScheduledTask, val start: LocalDateTime, val end: LocalDateTime) {
    val key: String get() = start.toLocalDate().toString()
    val rewardId: String get() = "${task.id}:$key"
    fun status(now: LocalDateTime): TaskStatus = when {
        key in task.completed -> TaskStatus.Completed
        !end.isAfter(now) -> TaskStatus.Expired
        else -> TaskStatus.Pending
    }
}

/** 月末と閏日は、その月に存在する最後の日へ合わせる。 */
internal fun occursOn(task: ScheduledTask, date: LocalDate): Boolean {
    val anchor = task.start.toLocalDate()
    if (date < anchor || task.repeatUntil?.let { date > it } == true) return false
    return when (task.repeat) {
        TaskRepeat.None -> date == anchor
        TaskRepeat.Daily -> true
        TaskRepeat.Weekdays -> date.dayOfWeek.value <= 5
        TaskRepeat.Weekly -> date.dayOfWeek == anchor.dayOfWeek
        TaskRepeat.Monthly -> date.dayOfMonth == minOf(anchor.dayOfMonth, YearMonth.from(date).lengthOfMonth())
        TaskRepeat.Yearly -> date.month == anchor.month &&
            date.dayOfMonth == minOf(anchor.dayOfMonth, YearMonth.from(date).lengthOfMonth())
        TaskRepeat.Custom -> ChronoUnit.DAYS.between(anchor, date) % task.repeatDays == 0L
    }
}

internal fun occurrenceStartingOn(task: ScheduledTask, date: LocalDate): TaskOccurrence? {
    if (!occursOn(task, date)) return null
    val start = date.atTime(task.start.toLocalTime())
    return TaskOccurrence(task, start, start.plus(Duration.between(task.start, task.end)))
}

/** 日をまたぐ予定は、終了時刻を含まない区間として各日に表示する。 */
internal fun occurrencesOn(task: ScheduledTask, date: LocalDate): List<TaskOccurrence> {
    val dayStart = date.atStartOfDay()
    val dayEnd = date.plusDays(1).atStartOfDay()
    if (task.repeat == TaskRepeat.None) {
        return if (task.start < dayEnd && task.end > dayStart) listOf(TaskOccurrence(task, task.start, task.end)) else emptyList()
    }
    val durationDays = Duration.between(task.start, task.end).toDays() + 1
    val first = maxOf(task.start.toLocalDate(), date.minusDays(durationDays))
    return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= date }
        .mapNotNull { occurrenceStartingOn(task, it) }
        .filter { it.start < dayEnd && it.end > dayStart }.toList()
}

/** 一覧は今日の予定を優先し、その日の予定がなければ次の発生日を選ぶ。 */
internal fun listOccurrence(task: ScheduledTask, now: LocalDateTime): TaskOccurrence? {
    if (task.repeat == TaskRepeat.None) return TaskOccurrence(task, task.start, task.end)
    val today = occurrencesOn(task, now.toLocalDate())
    if (today.isNotEmpty()) return today.minBy { it.end }
    nextOccurrence(task, now.toLocalDate())?.let { return it }
    val until = task.repeatUntil ?: return null
    var last = until
    while (last >= task.start.toLocalDate() && last >= until.minusYears(1)) {
        occurrenceStartingOn(task, last)?.let { return it }
        last = last.minusDays(1)
    }
    return null
}

internal fun taskList(tasks: List<ScheduledTask>, statuses: Set<TaskStatus>, now: LocalDateTime): List<TaskOccurrence> =
    tasks.mapNotNull { listOccurrence(it, now) }.filter { it.status(now) in statuses }
        .sortedWith(compareBy<TaskOccurrence> { it.end }.thenBy { it.start }.thenBy { it.task.id })

internal fun nextOccurrence(task: ScheduledTask, from: LocalDate): TaskOccurrence? {
    var date = maxOf(from, task.start.toLocalDate())
    // 全ての繰り返し周期がこの範囲内で次の発生日へ到達する。
    val limit = date.plusYears(2)
    while (date <= limit && task.repeatUntil?.let { date <= it } != false) {
        occurrenceStartingOn(task, date)?.let { return it }
        if (task.repeat == TaskRepeat.None && date >= task.start.toLocalDate()) return null
        date = date.plusDays(1)
    }
    return null
}

/** 通知の先行時間と完了状態を含め、未来の一回だけをアラームへ登録する。 */
internal fun nextTaskReminder(task: ScheduledTask, now: LocalDateTime): LocalDateTime? {
    if (!task.notificationEnabled) return null
    var date = maxOf(task.start.toLocalDate(), now.plusMinutes(task.reminderMinutes.toLong()).toLocalDate())
    val limit = date.plusYears(2)
    while (date <= limit) {
        val occurrence = nextOccurrence(task, date) ?: return null
        val trigger = taskReminderTime(occurrence)
        if (trigger > now && occurrence.key !in task.completed) return trigger
        if (task.repeat == TaskRepeat.None) return null
        date = occurrence.start.toLocalDate().plusDays(1)
    }
    return null
}

internal fun taskReminderTime(occurrence: TaskOccurrence): LocalDateTime {
    val task = occurrence.task
    return task.legacyNotificationTime?.let { time ->
        val trigger = occurrence.start.toLocalDate().atTime(time)
        task.legacyFirstReminderNotBefore?.takeIf { it.toLocalDate() == occurrence.start.toLocalDate() }
            ?.let { maxOf(trigger, it) } ?: trigger
    } ?: occurrence.start.minusMinutes(task.reminderMinutes.toLong())
}
