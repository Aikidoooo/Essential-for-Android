package jp.essential.app.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** HyperOSの利用者向け設定を開き、機種差がある場合は標準のアプリ情報へ戻る。 */
internal fun openHyperOsBackgroundSettings(context: Context): Boolean {
    val candidates = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
    return candidates.any { intent ->
        runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
