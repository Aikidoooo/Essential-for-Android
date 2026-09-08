package jp.essential.app.update

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.essential.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class AppUpdateModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("app_update", 0)
    private val repository = GitHubUpdateRepository(application)
    var startupCheck by mutableStateOf(preferences.getBoolean("startup_check", true)); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf<Float?>(null); private set
    var message by mutableStateOf(""); private set
    var release by mutableStateOf<AppRelease?>(null); private set
    var showDialog by mutableStateOf(false)
    var downloaded by mutableStateOf(false); private set
    var downloadFailed by mutableStateOf(false); private set
    private var started = false

    fun onStart() {
        if (started) return
        started = true
        if (startupCheck && BuildConfig.UPDATE_REPOSITORY.isNotBlank()) check(manual = false)
    }
    fun changeStartupCheck(value: Boolean) {
        startupCheck = value
        preferences.edit().putBoolean("startup_check", value).apply()
    }
    fun check(manual: Boolean = true) {
        if (busy) return
        busy = true
        message = "更新を確認しています"
        viewModelScope.launch {
            try {
                release = repository.latest()
                downloaded = false
                downloadFailed = false
                message = if (release == null) "最新版を使用しています" else "新しいバージョンがあります"
                showDialog = release != null
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message = if (manual) error.message ?: "確認できませんでした。再試行してください" else "起動時の更新確認に失敗しました。設定から再試行できます"
            } finally { busy = false }
        }
    }
    fun download() {
        val target = release ?: return
        if (busy) return
        busy = true
        downloadFailed = false
        progress = 0f
        message = "APKをダウンロードしています"
        viewModelScope.launch {
            try {
                repository.download(target) { value -> viewModelScope.launch { progress = value } }
                downloaded = true
                message = "検証済みです。インストールへ進めます"
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                downloadFailed = true
                message = error.message ?: "ダウンロードに失敗しました。再試行してください"
            } finally { busy = false; progress = null }
        }
    }

    fun beginInstallation(): Boolean {
        if (!repository.apk.isFile) {
            downloaded = false
            downloadFailed = true
            message = "更新APKを再取得してください"
            return false
        }
        // PackageInstallerへ渡した後に元APKを削除するため、戻った場合は再取得できる状態にする。
        downloaded = false
        return true
    }
}

@Composable
internal fun AppUpdateHost(model: AppUpdateModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(model) { model.onStart() }
    val release = model.release
    if (model.showDialog && release != null) {
        UpdateAvailableDialog(
            release = release,
            currentVersion = BuildConfig.VERSION_NAME,
            busy = model.busy,
            progress = model.progress,
            downloaded = model.downloaded,
            failed = model.downloadFailed,
            message = model.message,
            onDismiss = { model.showDialog = false },
            onUpdate = {
                if (model.downloaded && model.beginInstallation()) context.startActivity(Intent(context, UpdateInstallActivity::class.java))
                else model.download()
            },
        )
    }
}

@Composable
internal fun AppUpdateSettingsCard(model: AppUpdateModel = viewModel()) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("アプリのアップデート", style = MaterialTheme.typography.titleLarge)
            Text("現在 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
            Text(if (BuildConfig.UPDATE_REPOSITORY.isBlank()) "GitHub配信先は未設定です" else "GitHub: ${BuildConfig.UPDATE_REPOSITORY}")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("起動時にアップデートを確認", modifier = Modifier.weight(1f))
                Switch(checked = model.startupCheck, onCheckedChange = model::changeStartupCheck)
            }
            Button(enabled = !model.busy, onClick = { model.check() }, modifier = Modifier.fillMaxWidth()) { Text("アップデートを確認") }
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (model.message.isNotBlank()) Text(model.message, style = MaterialTheme.typography.bodyMedium)
            Text("ダウンロードとインストールは確認後に開始します", style = MaterialTheme.typography.bodySmall)
        }
    }
}
