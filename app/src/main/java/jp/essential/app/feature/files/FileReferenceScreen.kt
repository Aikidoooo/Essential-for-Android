package jp.essential.app.feature.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch
import jp.essential.app.ui.progressiveItem

@Composable
fun FileReferenceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val engine = remember(context) { MediaFileEngine(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var referencedFile by remember { mutableStateOf<ReferencedFile?>(null) }
    var targetMegabytes by remember { mutableIntStateOf(20) }
    var framePosition by remember { mutableFloatStateOf(0f) }
    var trimStart by remember { mutableFloatStateOf(0f) }
    var trimEnd by remember { mutableFloatStateOf(1f) }
    var gifPreset by remember { mutableStateOf(GifPreset.Standard) }
    var processingLabel by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    fun process(label: String, action: suspend () -> String) {
        if (processingLabel != null) return
        scope.launch {
            processingLabel = label
            resultMessage = null
            runCatching { action() }
                .onSuccess { resultMessage = "$it をDownload/Essentialへ保存しました" }
                .onFailure { resultMessage = it.message ?: "$label に失敗しました" }
            processingLabel = null
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                engine.inspect(uri)
            }.onSuccess {
                referencedFile = it
                framePosition = 0f
                trimStart = 0f
                trimEnd = 1f
                resultMessage = null
            }.onFailure { resultMessage = it.message ?: "ファイルを参照できませんでした" }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        var motionIndex = 0
        progressiveItem(motionIndex++) { FileTopBar(onBack) }
        progressiveItem(motionIndex++) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.74f),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("画像・動画・音声を端末内で加工", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "参照元は変更せず、処理結果を新しいファイルとして保存します。",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Button(
                        onClick = { picker.launch(arrayOf("image/*", "video/*", "audio/*")) },
                        enabled = processingLabel == null,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text(if (referencedFile == null) "ファイルを参照" else "別のファイルを参照") }
                }
            }
        }
        progressiveItem(motionIndex++) {
            AnimatedContent(referencedFile, label = "参照ファイル") { file ->
                if (file == null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Text("まだファイルが選択されていません", modifier = Modifier.padding(20.dp))
                    }
                } else {
                    FileSummary(file)
                }
            }
        }
        referencedFile?.let { file ->
            progressiveItem(motionIndex++) { SectionTitle("圧縮", "目標以下を目指して新しいファイルを生成") }
            progressiveItem(motionIndex++) {
                OptionButtons(
                    values = listOf(20, 50, 100, 500),
                    selected = targetMegabytes,
                    label = { "${it}MB以下" },
                    onSelected = { targetMegabytes = it },
                )
            }
            progressiveItem(motionIndex++) {
                ToolCard {
                    Text("自由設定　${targetMegabytes}MB以下", fontWeight = FontWeight.Bold)
                    Slider(
                        value = targetMegabytes.toFloat(),
                        onValueChange = { targetMegabytes = it.toInt().coerceIn(1, 500) },
                        valueRange = 1f..500f,
                    )
                    ActionButton("${targetMegabytes}MB以下へ圧縮", processingLabel) {
                        process("圧縮") { engine.compress(file, targetMegabytes) }
                    }
                }
            }
            when (file.type) {
                ReferencedMediaType.Image -> item {
                    ToolCard {
                        SectionTitle("AI背景透過", "前景を認識し、背景が透明なPNGへ")
                        Text(
                            "初回はGoogle Play開発者サービスがAIモデルを取得するため時間がかかる場合があります。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ActionButton("背景を透明にする", processingLabel) {
                            process("背景透過") { engine.removeBackground(file) }
                        }
                    }
                }
                ReferencedMediaType.Video -> {
                    progressiveItem(motionIndex++) {
                        ToolCard {
                            SectionTitle("フレーム切り取り", "時刻または1フレーム単位でPNGへ")
                            val positionMillis = (file.durationMillis * framePosition).toLong()
                            val fps = file.frameRate.takeIf { it > 0f } ?: 30f
                            Text("${formatTime(positionMillis)}　約${(positionMillis / 1000f * fps).toLong()}フレーム")
                            Slider(value = framePosition, onValueChange = { framePosition = it }, valueRange = 0f..1f)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { framePosition = (framePosition - (1000f / fps) / file.durationMillis.coerceAtLeast(1)).coerceAtLeast(0f) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("−1フレーム") }
                                OutlinedButton(
                                    onClick = { framePosition = (framePosition + (1000f / fps) / file.durationMillis.coerceAtLeast(1)).coerceAtMost(1f) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("＋1フレーム") }
                            }
                            ActionButton("このフレームを写真にする", processingLabel) {
                                process("フレーム切り取り") { engine.extractFrame(file, positionMillis) }
                            }
                        }
                    }
                    progressiveItem(motionIndex++) {
                        ToolCard {
                            SectionTitle("音声ファイル化", "動画の音声を高速にMP3へ変換")
                            ActionButton("MP3へ変換", processingLabel) {
                                process("MP3変換") { engine.convertVideoToMp3(file) }
                            }
                        }
                    }
                    progressiveItem(motionIndex++) {
                        ToolCard {
                            SectionTitle("GIF化", "用途に合わせて画質と滑らかさを選択")
                            OptionButtons(GifPreset.entries, gifPreset, GifPreset::label) { gifPreset = it }
                            ActionButton("${gifPreset.label}GIFを作成", processingLabel) {
                                process("GIF化") { engine.makeGif(file, gifPreset) }
                            }
                        }
                    }
                }
                ReferencedMediaType.Audio -> item {
                    ToolCard {
                        SectionTitle("音声ファイル切り取り", "開始地点と終了地点を指定")
                        val startMillis = (file.durationMillis * trimStart).toLong()
                        val endMillis = (file.durationMillis * trimEnd).toLong()
                        Text("開始 ${formatTime(startMillis)}")
                        Slider(
                            value = trimStart,
                            onValueChange = { trimStart = it.coerceAtMost(trimEnd - 0.001f) },
                            valueRange = 0f..1f,
                        )
                        Text("終了 ${formatTime(endMillis)}")
                        Slider(
                            value = trimEnd,
                            onValueChange = { trimEnd = it.coerceAtLeast(trimStart + 0.001f) },
                            valueRange = 0f..1f,
                        )
                        ActionButton("指定範囲を切り取る", processingLabel) {
                            process("音声切り取り") { engine.trimAudio(file, startMillis, endMillis) }
                        }
                    }
                }
            }
        }
        progressiveItem(motionIndex++) {
            AnimatedVisibility(processingLabel != null || resultMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (processingLabel != null) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(processingLabel?.let { "$it を処理しています" } ?: resultMessage.orEmpty())
                    }
                }
            }
        }
    }
}

@Composable
private fun FileSummary(file: ReferencedFile) {
    ToolCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(file.type.label.take(1), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${file.type.label}・${formatBytes(file.sizeBytes)}" +
                        if (file.durationMillis > 0) "・${formatTime(file.durationMillis)}" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> OptionButtons(values: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { value ->
                    if (value == selected) {
                        Button(onClick = { onSelected(value) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Text(label(value), maxLines = 1)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelected(value) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Text(label(value), maxLines = 1)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, processingLabel: String?, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = processingLabel == null,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(18.dp),
    ) { Text(label) }
}

@Composable
private fun FileTopBar(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            modifier = Modifier.size(46.dp).clickable(onClick = onBack),
        ) { Box(contentAlignment = Alignment.Center) { Text("‹", style = MaterialTheme.typography.headlineMedium) } }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("ファイル参照", style = MaterialTheme.typography.headlineMedium)
            Text("圧縮・変換・切り取り・AI背景透過", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    return String.format(Locale.JAPAN, "%02d:%02d.%03d", totalSeconds / 60, totalSeconds % 60, millis % 1000)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.JAPAN, "%.2fGB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.JAPAN, "%.1fMB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.JAPAN, "%.1fKB", bytes / 1024.0)
}
