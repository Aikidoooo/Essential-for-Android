package jp.essential.app.update

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import jp.essential.app.ui.theme.EssentialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 非公開のインストール画面。設定復帰とOSの確認要求を同じ画面で処理する。 */
class UpdateInstallActivity : ComponentActivity() {
    private var message by mutableStateOf("インストールを準備しています")
    private var waitingPermission = false
    private var preparing = false
    private val preferences get() = getSharedPreferences("app_update", 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = getSharedPreferences("appearance", 0).getBoolean("dark_theme", androidx.compose.foundation.isSystemInDarkTheme())
            EssentialTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
                        Text("Essentialの更新", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(20.dp))
                        Text(message)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { begin() }, enabled = !preparing) { Text("続ける・再試行") }
                        TextButton(onClick = { finish() }) { Text("閉じる") }
                    }
                }
            }
        }
        if (intent.action == RESULT_ACTION) result(intent)
        else if (savedInstanceState == null) begin()
        else message = "続けるボタンから再試行できます"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == RESULT_ACTION) result(intent)
    }

    override fun onResume() {
        super.onResume()
        if (waitingPermission) {
            waitingPermission = false
            if (packageManager.canRequestPackageInstalls()) begin()
            else message = "許可されていません。続けるボタンで設定を開けます"
        }
    }

    private fun begin() {
        if (preparing) return
        if (!packageManager.canRequestPackageInstalls()) {
            waitingPermission = true
            message = "設定で「この提供元のアプリを許可」を有効にしてください"
            try { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
            catch (_: Exception) { waitingPermission = false; message = "設定アプリから不明なアプリのインストールを許可してください" }
            return
        }
        preparing = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val repository = GitHubUpdateRepository(this@UpdateInstallActivity)
                    repository.validateApk(repository.apk)
                    val installer = packageManager.packageInstaller
                    // 前回の中断セッションのみ回収する。完了した更新はOSが処理する。
                    installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
                    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                        setAppPackageName(packageName)
                        setSize(repository.apk.length())
                        // 自己更新でも、権限・OS・更新所有者によって無確認更新の条件が変わるため確認を必須にする。
                        if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                    }
                    val id = installer.createSession(params)
                    preferences.edit().putInt("install_session", id).commit()
                    try {
                        installer.openSession(id).use { session ->
                            session.openWrite("base.apk", 0, repository.apk.length()).use { output ->
                                repository.apk.inputStream().use { it.copyTo(output) }
                                session.fsync(output)
                            }
                            val callback = Intent(this@UpdateInstallActivity, UpdateInstallActivity::class.java)
                                .setAction(RESULT_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                            val options = android.app.ActivityOptions.makeBasic()
                            if (Build.VERSION.SDK_INT >= 35) {
                                // 明示した非公開ActivityへのOS結果通知に限って起動を許可する。
                                options.setPendingIntentCreatorBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                            }
                            session.commit(PendingIntent.getActivity(this@UpdateInstallActivity, id, callback, flags, options.toBundle()).intentSender)
                        }
                    } catch (error: Exception) { installer.abandonSession(id); throw error }
                }
                message = "Androidのインストール確認を待っています"
            } catch (error: Exception) {
                message = error.message ?: "インストールを開始できませんでした"
            } finally { preparing = false }
        }
    }

    @Suppress("DEPRECATION")
    private fun result(intent: Intent) {
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (id < 0 || id != preferences.getInt("install_session", -2)) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmation != null) {
                    try { startActivity(confirmation); message = "Androidの確認画面で操作してください" }
                    catch (_: Exception) { message = "確認画面を開けませんでした。再試行してください" }
                } else message = "確認要求が不正です。再試行してください"
            }
            PackageInstaller.STATUS_SUCCESS -> {
                GitHubUpdateRepository(this).apk.delete()
                preferences.edit().remove("install_session").apply()
                message = "更新が完了しました"
            }
            else -> {
                preferences.edit().remove("install_session").apply()
                message = "更新が中止または失敗しました。再試行できます（${intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)}）"
            }
        }
    }

    companion object { private const val RESULT_ACTION = "jp.essential.app.UPDATE_INSTALL_RESULT" }
}
