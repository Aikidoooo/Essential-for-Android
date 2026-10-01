package jp.essential.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 更新直後に自分の更新用一時ファイルだけを回収し、ユーザーの保存データは残す。 */
class UpdateReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                GitHubUpdateRepository(context.applicationContext).cleanupAfterPackageReplacement()
            } catch (error: Exception) {
                Log.w("UpdateReplacedReceiver", "更新用コピーの回収は次回起動時に再試行します", error)
            } finally {
                pending.finish()
            }
        }
    }
}
