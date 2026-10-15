package jp.essential.app.feature.routine

import android.content.Context
import jp.essential.app.profile.AppLevel
import jp.essential.app.profile.AppProgressStore
import jp.essential.app.profile.calculateAppLevel
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.WeekFields

internal enum class RoutineCadence(val label: String, val pointRange: IntRange) {
    Daily("デイリー", 1..5),
    Weekly("ウィークリー", 5..15),
    Event("イベントミッション", 1..30),
}

internal data class RoutineTask(
    val id: String,
    val title: String,
    val emoji: String,
    val cadence: RoutineCadence,
    val points: Int,
    val completedPeriod: String? = null,
    val notificationEnabled: Boolean = false,
    val notificationHour: Int = 9,
    val notificationMinute: Int = 0,
    val eventStartDate: String? = null,
    val eventStartTime: String? = null,
    val eventDurationDays: Int = 1,
)

/** 既存の日課テストと互換性を保ちながら、アプリレベル計算へ移行する。 */
internal typealias RoutineLevel = AppLevel
internal fun requiredRoutineXp(level: Int): Int = jp.essential.app.profile.requiredAppXp(level)
internal fun calculateRoutineLevel(totalPoints: Int): RoutineLevel = calculateAppLevel(totalPoints)

/** 達成状態をデイリーは日付、ウィークリーはISO週単位で区切る。 */
internal fun routinePeriodKey(cadence: RoutineCadence, date: LocalDate = LocalDate.now()): String =
    when (cadence) {
        RoutineCadence.Daily -> "D:$date"
        RoutineCadence.Weekly -> {
            val weekFields = WeekFields.ISO
            "W:${date.get(weekFields.weekBasedYear())}-${date.get(weekFields.weekOfWeekBasedYear())}"
        }
        RoutineCadence.Event -> "E:$date"
    }

/** イベント日課が指定期間内かどうかを判定する。日課と週課は常に有効とする。 */
internal fun isRoutineTaskActive(
    task: RoutineTask,
    date: LocalDate = LocalDate.now(),
    time: LocalTime = LocalTime.now(),
): Boolean {
    if (task.cadence != RoutineCadence.Event) return true
    val start = runCatching { LocalDate.parse(task.eventStartDate.orEmpty()) }.getOrNull() ?: return false
    val end = start.plusDays(task.eventDurationDays.coerceAtLeast(1) - 1L)
    if (date !in start..end) return false
    if (date == start) {
        val startTime = runCatching { LocalTime.parse(task.eventStartTime.orEmpty()) }.getOrNull()
        if (startTime != null && time.isBefore(startTime)) return false
    }
    return true
}

/** 日課の種類ごとに、達成済み状態を一日単位で保存するキーを返す。 */
internal fun routineTaskPeriodKey(task: RoutineTask, date: LocalDate = LocalDate.now()): String =
    if (task.cadence == RoutineCadence.Event) routinePeriodKey(RoutineCadence.Event, date) else routinePeriodKey(task.cadence, date)

internal class LegacyRoutineStore(context: Context) {
    private val preferences = context.getSharedPreferences("routine", Context.MODE_PRIVATE)
    private val progressStore = AppProgressStore(context)

    fun loadTasks(): List<RoutineTask> = runCatching {
        val array = JSONArray(preferences.getString("tasks", "[]"))
        buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val cadence = runCatching { RoutineCadence.valueOf(item.getString("cadence")) }
                    .getOrDefault(RoutineCadence.Daily)
                add(
                    RoutineTask(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        emoji = item.optString("emoji", "✨"),
                        cadence = cadence,
                        points = item.optInt("points", cadence.pointRange.first)
                            .coerceIn(cadence.pointRange),
                        completedPeriod = item.optString("completedPeriod").takeIf { it.isNotBlank() },
                        notificationEnabled = item.optBoolean("notificationEnabled", false),
                        notificationHour = item.optInt("notificationHour", 9).coerceIn(0, 23),
                        notificationMinute = item.optInt("notificationMinute", 0).coerceIn(0, 59),
                        eventStartDate = item.optString("eventStartDate").takeIf { it.isNotBlank() },
                        eventStartTime = item.optString("eventStartTime").takeIf { it.isNotBlank() },
                        eventDurationDays = item.optInt("eventDurationDays", 1).coerceIn(1, 365),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun loadTotalPoints(): Int = progressStore.loadXp()

    fun save(tasks: List<RoutineTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("emoji", task.emoji)
                    .put("cadence", task.cadence.name)
                    .put("points", task.points)
                    .put("completedPeriod", task.completedPeriod.orEmpty())
                    .put("notificationEnabled", task.notificationEnabled)
                    .put("notificationHour", task.notificationHour)
                    .put("notificationMinute", task.notificationMinute)
                    .put("eventStartDate", task.eventStartDate.orEmpty())
                    .put("eventStartTime", task.eventStartTime.orEmpty())
                    .put("eventDurationDays", task.eventDurationDays),
            )
        }
        preferences.edit().putString("tasks", array.toString()).apply()
    }
}
