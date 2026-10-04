package jp.essential.app.feature.notificationlog

import android.app.Notification
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow

internal object NotificationLogConnection {
    val connected = MutableStateFlow(false)
}

/** 発信時刻も比較し、同じ本文の新しい通知は記録して、同一通知の更新だけ重複を抑える。 */
class NotificationLogListener : NotificationListenerService() {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val appNames = android.util.LruCache<String, String>(128)
    private val recent = object : LinkedHashMap<String, Triple<String, String, Long>>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Triple<String, String, Long>>?): Boolean = size > 512
    }

    override fun onListenerConnected() {
        NotificationLogConnection.connected.value = true
        NotificationLogCleanup.schedule(this)
        scope.launch { runCatching { NotificationLogStore.get(this@NotificationLogListener).prune() } }
    }

    override fun onListenerDisconnected() {
        NotificationLogConnection.connected.value = false
        requestRebind(ComponentName(this, NotificationLogListener::class.java))
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        val extras = notification.notification.extras
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty().take(4096)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
        val messages = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(
            notification.notification)?.messages.orEmpty() }.getOrDefault(emptyList())
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: messages.takeIf { it.isNotEmpty() }?.joinToString("\n") { message ->
                val sender = message.person?.name
                if (sender.isNullOrBlank()) message.text.toString() else "$sender: ${message.text}"
            }
            ?: lines ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()).orEmpty().take(16384)
        val receivedAt = System.currentTimeMillis()
        val sourceKey = notification.key
        val packageName = notification.packageName
        val postedAt = notification.notification.`when`
        scope.launch {
            val fingerprint = Triple(title, body, postedAt)
            if (recent[sourceKey] == fingerprint) return@launch
            runCatching {
                val appName = appNames.get(packageName) ?: runCatching { packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
                    .also { appNames.put(packageName, it) }
                NotificationLogStore.get(this@NotificationLogListener).record(packageName, appName, title, body, receivedAt)
                recent[sourceKey] = fingerprint
            }.onFailure { android.util.Log.e("NotificationLog", "通知ログの保存に失敗", it) }
        }
    }

    override fun onNotificationRemoved(notification: StatusBarNotification) {
        scope.launch { recent.remove(notification.key) }
    }

    override fun onDestroy() {
        NotificationLogConnection.connected.value = false
        scope.cancel()
        dispatcher.close()
        super.onDestroy()
    }
}

/** OSの省電力制御を尊重して定期削除し、表示時と受信時にも期限を検査する。 */
class NotificationLogCleanup : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: kotlinx.coroutines.Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch {
            val result = runCatching { NotificationLogStore.get(this@NotificationLogCleanup).prune() }
            jobFinished(params, result.isFailure)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { job?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val id = 74031
            if (scheduler.getPendingJob(id)?.intervalMillis != 6 * 60 * 60 * 1000L) {
                scheduler.schedule(JobInfo.Builder(id, ComponentName(context, NotificationLogCleanup::class.java))
                    .setPeriodic(6 * 60 * 60 * 1000L, 60 * 60 * 1000L).setPersisted(true).build())
            }
        }
    }
}
