package jp.essential.app.design

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.essential.app.AppIconManager
import jp.essential.app.AppIconOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** OSの省電力を尊重するアラームと起動時の検査で、期限切れを復元する。 */
internal object AppDesignExpiry {
    @Synchronized fun reconcile(context: Context) {
        val store = AppDesignAccessStore(context)
        store.refresh()
        val effective = AppIconManager.load(context)
        if (context.getSharedPreferences("appearance", 0).getString("app_icon_style", null) != effective.id)
            AppIconManager.apply(context, effective)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 74033, Intent(context, AppDesignExpiryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(pending)
        store.nextBoundary()?.let { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it * 1000, pending) }
    }
}

class AppDesignExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try { AppDesignExpiry.reconcile(context.applicationContext) }
            catch (failure: Exception) { android.util.Log.e("AppDesign", "デザインの期限確認に失敗", failure) }
            finally { pending.finish() }
        }.start()
    }
}

/** 前面では次の境界で更新し、復帰・受取・通常デザイン選択にも追従する。 */
@Composable
internal fun AppDesignExpiryEffect(onChanged: (AppIconOption) -> Unit) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val callback by rememberUpdatedState(onChanged)
    var revision by remember { mutableIntStateOf(0) }
    val store = remember(context) { AppDesignAccessStore(context) }
    val appearance = remember(context) { context.getSharedPreferences("appearance", 0) }
    DisposableEffect(lifecycle, store) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        // 自分が更新するlast_seenではループさせず、利用権と選択の変更にだけ反応する。
        val accessListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "grants") revision++ }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        store.preferences.registerOnSharedPreferenceChangeListener(accessListener)
        appearance.registerOnSharedPreferenceChangeListener(listener)
        store.updatePreferences.registerOnSharedPreferenceChangeListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            store.preferences.unregisterOnSharedPreferenceChangeListener(accessListener)
            appearance.unregisterOnSharedPreferenceChangeListener(listener)
            store.updatePreferences.unregisterOnSharedPreferenceChangeListener(listener)
            lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(revision) {
        try {
            val effective = withContext(Dispatchers.IO) { AppDesignExpiry.reconcile(context); AppIconManager.load(context) }
            callback(effective)
            val next = store.nextBoundary()
            if (next != null) { delay((next * 1000 - System.currentTimeMillis()).coerceAtLeast(1)); revision++ }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            android.util.Log.e("AppDesign", "デザインの期限確認に失敗", failure)
        }
    }
}
