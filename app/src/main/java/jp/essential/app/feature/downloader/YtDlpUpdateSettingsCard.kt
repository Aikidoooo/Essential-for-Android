package jp.essential.app.feature.downloader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private sealed interface UpdateUiState {
    data object LoadingVersion : UpdateUiState
    data class Ready(val version: String, val message: String? = null) : UpdateUiState
    data class Updating(val version: String) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

@Composable
fun YtDlpUpdateSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember(context) { YtDlpUpdateManager(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.LoadingVersion) }

    LaunchedEffect(manager) {
        manager.currentVersion().fold(
            onSuccess = { state = UpdateUiState.Ready(it) },
            onFailure = { state = UpdateUiState.Failed(it.message ?: "yt-dlpを準備できませんでした") },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                RoundedCornerShape(28.dp),
            )
            .padding(19.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ダウンローダー", style = MaterialTheme.typography.titleLarge)
        Text(
            "yt-dlpを安定版チャンネルから更新します。更新中はダウンロードを開始しないでください。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "yt-dlp更新状態",
        ) { current ->
            when (current) {
                UpdateUiState.LoadingVersion -> UpdateProgress("バージョンを確認しています")
                is UpdateUiState.Updating -> UpdateProgress("yt-dlpを更新しています")
                is UpdateUiState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "現在のバージョン  ${current.version}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    current.message?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is UpdateUiState.Failed -> Text(
                    current.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Button(
            onClick = {
                val version = (state as? UpdateUiState.Ready)?.version ?: "不明"
                scope.launch {
                    state = UpdateUiState.Updating(version)
                    manager.updateStable().fold(
                        onSuccess = { result ->
                            state = UpdateUiState.Ready(
                                version = result.currentVersion,
                                message = if (result.updated) {
                                    "${result.previousVersion} から更新しました"
                                } else {
                                    "すでに最新です"
                                },
                            )
                        },
                        onFailure = {
                            state = UpdateUiState.Failed(it.message ?: "yt-dlpを更新できませんでした")
                        },
                    )
                }
            },
            enabled = state is UpdateUiState.Ready || state is UpdateUiState.Failed,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(if (state is UpdateUiState.Failed) "もう一度試す" else "安定版へ更新")
        }
    }
}

@Composable
private fun UpdateProgress(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(strokeWidth = 2.dp)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
