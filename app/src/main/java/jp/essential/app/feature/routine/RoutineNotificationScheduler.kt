package jp.essential.app.feature.routine

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import jp.essential.app.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private const val routineNotificationChannelId = "routine_reminders"

/** 日課の通知を端末のアラームへ登録し、設定変更時は以前の登録を解除する。 */
internal object RoutineNotificationScheduler {
    private const val intervalDay = 24L * 60L * 60L * 1000L
    private const val intervalWeek = 7L * intervalDay

    fun schedule(context: Context, task: RoutineTask) {
        cancel(context, task.id)
        if (!task.notificationEnabled) return
        val trigger = nextTrigger(task) ?: return
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(context, task, PendingIntent.FLAG_UPDATE_CURRENT)
        val interval = if (task.cadence == RoutineCadence.Weekly) intervalWeek else intervalDay
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger, interval, pendingIntent)
    }

    fun cancel(context: Context, taskId: String) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, RoutineNotificationReceiver::class.java).setData(android.net.Uri.parse("essential://routine/$taskId"))
        PendingIntent.getBroadcast(context, taskId.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or immutableFlag)?.let {
            alarm.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(context: Context, task: RoutineTask, flags: Int): PendingIntent {
        val intent = Intent(context, RoutineNotificationReceiver::class.java)
            .setData(android.net.Uri.parse("essential://routine/${task.id}"))
            .putExtra("title", task.title)
            .putExtra("emoji", task.emoji)
            .putExtra("cadence", task.cadence.name)
            .putExtra("eventStartDate", task.eventStartDate)
            .putExtra("eventDurationDays", task.eventDurationDays)
        return PendingIntent.getBroadcast(context, task.id.hashCode(), intent, flags or immutableFlag)
    }

    private fun nextTrigger(task: RoutineTask): Long? {
        val now = LocalDateTime.now()
        val time = LocalTime.of(task.notificationHour.coerceIn(0, 23), task.notificationMinute.coerceIn(0, 59))
        val candidate = when (task.cadence) {
            RoutineCadence.Daily -> nextDaily(now, time)
            RoutineCadence.Weekly -> nextWeekly(now, time)
            RoutineCadence.Event -> nextEvent(task, now, time)
        } ?: return null
        return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun nextDaily(now: LocalDateTime, time: LocalTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun nextWeekly(now: LocalDateTime, time: LocalTime): LocalDateTime {
        val daysUntilMonday = (8 - now.toLocalDate().dayOfWeek.value) % 7
        var candidate = now.toLocalDate().plusDays(daysUntilMonday.toLong()).atTime(time)
        if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1)
        return candidate
    }

    private fun nextEvent(task: RoutineTask, now: LocalDateTime, time: LocalTime): LocalDateTime? {
        val start = runCatching { LocalDate.parse(task.eventStartDate.orEmpty()) }.getOrNull() ?: return null
        val end = start.plusDays(task.eventDurationDays.coerceAtLeast(1) - 1L)
        if (now.toLocalDate().isAfter(end)) return null
        val firstDate = if (now.toLocalDate().isBefore(start)) start else now.toLocalDate()
        val eventStartTime = runCatching { LocalTime.parse(task.eventStartTime.orEmpty()) }.getOrNull() ?: LocalTime.MIN
        // 開催初日の通知がイベント開始時刻より前に飛ばないよう、初日だけ境界をそろえる。
        val firstTime = if (firstDate == start) maxOf(time, eventStartTime) else time
        var candidate = firstDate.atTime(firstTime)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.takeIf { !it.toLocalDate().isAfter(end) }
    }

    private val immutableFlag: Int
        get() = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
}

/** スケジュールされた日課通知を表示するBroadcastReceiver。 */
internal class RoutineNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager?.createNotificationChannel(
                NotificationChannel(routineNotificationChannelId, "日課の通知", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "日課・イベントミッションの設定時刻に通知します"
                },
            )
        }
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "日課" }
        val emoji = intent.getStringExtra("emoji").orEmpty()
        if (intent.getStringExtra("cadence") == RoutineCadence.Event.name) {
            val start = runCatching { LocalDate.parse(intent.getStringExtra("eventStartDate").orEmpty()) }.getOrNull()
            val duration = intent.getIntExtra("eventDurationDays", 1).coerceAtLeast(1)
            val today = LocalDate.now()
            if (start == null || today !in start..start.plusDays(duration - 1L)) return
        }
        val notification = NotificationCompat.Builder(context, routineNotificationChannelId)
            .setSmallIcon(R.drawable.essential_icon)
            .setContentTitle("$emoji 日課の時間")
            .setContentText(title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(title.hashCode(), notification) }
    }
}
