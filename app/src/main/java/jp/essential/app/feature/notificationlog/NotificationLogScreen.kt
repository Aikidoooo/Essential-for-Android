package jp.essential.app.feature.notificationlog

import android.content.ComponentName
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.Alignment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.essential.app.ui.GlassBackButton
import jp.essential.app.ui.ProgressiveWidget
import jp.essential.app.ui.progressiveItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun notificationAccessEnabled(context: android.content.Context): Boolean {
    val component = ComponentName(context, NotificationLogListener::class.java)
    return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
}

@Composable
internal fun NotificationLogScreen(storeOverride: NotificationLogStore? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val store = remember(storeOverride) { storeOverride ?: NotificationLogStore.get(context) }
    val revision by store.revision.collectAsState()
    val connected by NotificationLogConnection.connected.collectAsState()
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(notificationAccessEnabled(context)) }
    var keptOnly by rememberSaveable { mutableStateOf(false) }
    var limit by rememberSaveable { mutableIntStateOf(100) }
    var entries by remember { mutableStateOf(emptyList<NotificationLogEntry>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableLongStateOf(0) }
    var deletion by remember { mutableStateOf<NotificationLogEntry?>(null) }
    var releaseKeep by remember { mutableStateOf<NotificationLogEntry?>(null) }
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss") }
    val xiaomiDevice = remember { jp.essential.app.device.DeviceOptimizer.current().isXiaomiFamily }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = notificationAccessEnabled(context)
                refresh += 1
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // 設定画面からの復帰だけでなく、表示中の許可変更もすぐに反映する。
    DisposableEffect(context.contentResolver) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                allowed = notificationAccessEnabled(context)
            }
        }
        resolver.registerContentObserver(Settings.Secure.getUriFor("enabled_notification_listeners"), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { NotificationLogCleanup.schedule(context) }
        while (true) { delay(60_000); refresh += 1 }
    }
    LaunchedEffect(revision, keptOnly, limit, refresh) {
        loading = true
        try {
            entries = withContext(Dispatchers.IO) { store.list(keptOnly, limit) }
            error = null
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            error = "ログを読み込めませんでした。もう一度お試しください。"
        } finally { loading = false }
    }
    fun change(action: () -> Unit) {
        scope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = "変更を保存できませんでした。もう一度お試しください。"
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("notification-log-screen"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        progressiveItem(0, "notification-header") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassBackButton(onBack)
                NotificationLogLogo()
            }
            Spacer(Modifier.height(18.dp))
            Text("通知ログ", style = MaterialTheme.typography.headlineLarge)
            Text("届いた通知を、秒単位で振り返る", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (allowed) Text(if (connected) "通知を記録しています" else "通知サービスの接続待ち",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!allowed) progressiveItem(1, "notification-access") {
            LogGlassCard {
                Text(when {
                    !allowed -> "通知へのアクセスを許可"
                    connected -> "通知を記録しています"
                    else -> "許可済み・通知サービスの接続待ち"
                }, style = MaterialTheme.typography.titleMedium)
                Text("許可後に届いた通知を、この端末だけに保存します。通知を消してもログは残ります。", style = MaterialTheme.typography.bodyMedium)
                Text("通常のログは受信から3日後に自動削除。keepすると残せます。OSが伏せる内容は記録できません。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("省電力制御などでバックグラウンド削除が遅れる場合も、画面表示時に期限切れを削除します。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        .onFailure { error = "通知アクセスの設定を開けませんでした。端末の設定から許可してください。" }
                }) { Text(if (allowed) "通知アクセスの設定" else "許可する") }
            }
        }
        progressiveItem(2, "notification-filter") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = !keptOnly, onClick = { keptOnly = false; limit = 100 }, label = { Text("すべて") })
                FilterChip(selected = keptOnly, onClick = { keptOnly = true; limit = 100 }, label = { Text("keep") })
            }
            Text("通常のログは3日間保存。keepすると自動削除されません。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("ログの削除は通知欄の通知には影響しません。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (xiaomiDevice) {
                Text("HyperOSで記録が途切れる場合は、自動起動とアプリのバッテリー設定を確認してください。設定変更は任意です。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    if (!jp.essential.app.device.openHyperOsBackgroundSettings(context)) {
                        error = "端末の設定からEssentialの自動起動・バッテリー設定を確認してください。"
                    }
                }) { Text("HyperOSのバックグラウンド設定") }
            }
        }
        error?.let { message -> item("notification-error") {
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { refresh += 1 }) { Text("再読み込み") }
        } }
        if (loading && entries.isEmpty()) item("notification-loading") { CircularProgressIndicator() }
        else if (entries.isEmpty()) progressiveItem(3, "notification-empty") {
            LogGlassCard { Text(if (keptOnly) "keepしたログはありません" else "まだ通知ログはありません") }
        }
        itemsIndexed(entries.take(limit), key = { _, entry -> entry.id }) { index, entry ->
            ProgressiveWidget(index + 3) {
                LogGlassCard(Modifier.testTag("notification-entry-${entry.id}")) {
                    Text(entry.appName, style = MaterialTheme.typography.titleMedium)
                    Text(formatter.format(Instant.ofEpochMilli(entry.receivedAt).atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (entry.title.isNotBlank()) Text(entry.title, style = MaterialTheme.typography.titleSmall)
                    Text(entry.body.ifBlank { "通知に表示できる本文がありません" }, style = MaterialTheme.typography.bodyMedium)
                    Text(entry.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = entry.kept, onClick = {
                            if (entry.kept && entry.receivedAt <= System.currentTimeMillis() - LOG_RETENTION_MILLIS) releaseKeep = entry
                            else change { store.keep(entry.id, !entry.kept) }
                        }, label = { Text(if (entry.kept) "keep中" else "keepする") })
                        TextButton(onClick = { deletion = entry }) { Text("削除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        if (entries.size > limit) item("notification-more") {
            OutlinedButton(onClick = { limit += 100 }, modifier = Modifier.fillMaxWidth()) { Text("さらに表示") }
        }
    }
    deletion?.let { entry ->
        AlertDialog(onDismissRequest = { deletion = null }, title = { Text("このログを削除しますか？") },
            text = { Text("${entry.appName}のログを削除します。元に戻せません。") },
            confirmButton = { TextButton(onClick = { deletion = null; change { store.delete(entry.id) } }) { Text("削除する") } },
            dismissButton = { TextButton(onClick = { deletion = null }) { Text("キャンセル") } })
    }
    releaseKeep?.let { entry ->
        AlertDialog(onDismissRequest = { releaseKeep = null }, title = { Text("keepを解除しますか？") },
            text = { Text("受信から3日を過ぎているため、解除するとこのログはすぐに削除されます。") },
            confirmButton = { TextButton(onClick = { releaseKeep = null; change { store.keep(entry.id, false) } }) { Text("解除して削除") } },
            dismissButton = { TextButton(onClick = { releaseKeep = null }) { Text("キャンセル") } })
    }
}

@Composable
private fun LogGlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Surface(modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        tonalElevation = 2.dp, shadowElevation = 2.dp) {
        Column(Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
