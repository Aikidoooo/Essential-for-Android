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
import jp.essential.app.MainActivity
import jp.essential.app.R
import java.time.LocalDateTime
import java.time.ZoneId

private const val routineNotificationChannelId = "routine_reminders"

/** 繰り返しの次回分だけを登録し、編集・完了・削除時に旧アラームを置き換える。 */
internal object RoutineNotificationScheduler {
    fun schedule(context: Context, task: ScheduledTask) {
        cancel(context, task.id)
        val trigger = nextTaskReminder(task, LocalDateTime.now()) ?: return
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val occurrenceDate = if (task.legacyNotificationTime != null) trigger.toLocalDate()
            else trigger.plusMinutes(task.reminderMinutes.toLong()).toLocalDate()
        val intent = reminderIntent(context, task.id).putExtra("task_id", task.id)
            .putExtra("occurrence_key", occurrenceDate.toString())
        val pending = PendingIntent.getBroadcast(context, task.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // 正確なアラーム権限を要求せず、OSの省電力制約内で通知する。
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pending)
    }

    fun cancel(context: Context, taskId: String) {
        val pending = PendingIntent.getBroadcast(context, taskId.hashCode(), reminderIntent(context, taskId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
    }

    internal fun reminderIntent(context: Context, taskId: String) = Intent(context, RoutineNotificationReceiver::class.java)
        .setData(android.net.Uri.parse("essential://routine/$taskId"))
}

/** 起動・時刻変更時の再登録と、保存済み予定を確認した上での通知を行う。 */
internal class RoutineNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread({
            try {
                val store = TaskStore(context.applicationContext)
                if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                        Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
                    store.rescheduleAll()
                    return@Thread
                }
                val tasks = store.load()
                val id = intent.getStringExtra("task_id") ?: intent.data?.lastPathSegment
                val task = tasks.firstOrNull { it.id == id } ?: return@Thread
                if (!task.notificationEnabled) return@Thread
                val now = LocalDateTime.now()
                val occurrence = intent.getStringExtra("occurrence_key")?.let {
                    occurrenceStartingOn(task, java.time.LocalDate.parse(it))
                }
                val expectedTrigger = occurrence?.let(::taskReminderTime)
                if (occurrence != null && expectedTrigger != null && expectedTrigger <= now &&
                    occurrence.key !in task.completed && occurrence.end > now &&
                    (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                        "android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED)) {
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager?.createNotificationChannel(NotificationChannel(routineNotificationChannelId,
                        "タスクのリマインダー", NotificationManager.IMPORTANCE_DEFAULT))
                    val open = Intent(context, MainActivity::class.java).putExtra("open_feature", "routine")
                    val content = PendingIntent.getActivity(context, task.id.hashCode(), open,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val notification = NotificationCompat.Builder(context, routineNotificationChannelId)
                        .setSmallIcon(R.drawable.essential_icon).setContentTitle(task.title)
                        .setContentText(if (task.memo.isBlank()) "${task.xp} XP · ${reminderLabel(task.reminderMinutes)}" else task.memo)
                        .setContentIntent(content).setAutoCancel(true).build()
                    NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification)
                }
                store.rescheduleAll()
            } catch (_: Exception) {
                // 保存データが読めない場合は変更や通知をせず、画面のエラー表示へ委ねる。
            } finally { result.finish() }
        }, "task-reminders").start()
    }
}
