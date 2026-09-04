package jp.essential.app.feature.downloader

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import jp.essential.app.R

@Composable
fun DownloaderScreen(
    initialUrl: String?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val engine = remember(context) { YtDlpDownloadEngine(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var mediaType by rememberSaveable { mutableStateOf(DownloadMediaType.Video) }
    var videoResolution by rememberSaveable { mutableStateOf(VideoResolution.P1080) }
    var frameRate by rememberSaveable { mutableStateOf(FrameRate.Fps60) }
    var videoFormat by rememberSaveable { mutableStateOf(VideoFormat.Mp4) }
    var audioQuality by rememberSaveable { mutableStateOf(AudioQuality.High) }
    var audioFormat by rememberSaveable { mutableStateOf(AudioFormat.Mp3) }
    var imageQuality by rememberSaveable { mutableStateOf(ImageQuality.P4K) }
    var imageFormat by rememberSaveable { mutableStateOf(ImageFormat.Png) }
    var candidates by remember { mutableStateOf<List<ImageCandidate>>(emptyList()) }
    var selectedCandidateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var state by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var pendingDownload by remember { mutableStateOf(false) }

    val isBusy = state is DownloadState.Preparing || state is DownloadState.Running
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingDownload) {
            pendingDownload = false
            startDownload(
                scope = scope,
                engine = engine,
                selection = DownloaderSelection(
                    url = url,
                    mediaType = mediaType,
                    videoResolution = videoResolution,
                    frameRate = frameRate,
                    videoFormat = videoFormat,
                    audioQuality = audioQuality,
                    audioFormat = audioFormat,
                    imageQuality = imageQuality,
                    imageFormat = imageFormat,
                    selectedImages = candidates.filter { it.id in selectedCandidateIds },
                ),
                onState = { state = it },
            )
        } else if (!granted) {
            pendingDownload = false
            state = DownloadState.Failed("Android 8で保存するにはストレージ権限が必要です")
        }
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) url = initialUrl
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            FeatureTopBar(
                title = "ダウンローダー",
                subtitle = "yt-dlp + FFmpeg・端末内処理",
                onBack = onBack,
            )
        }
        item {
            SafetyNotice()
        }
        item {
            MotionSurface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                enabled = !isBusy,
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("yt-dlpのアップデート", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "現在のバージョン確認と安定版への更新",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("設定へ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                shape = RoundedCornerShape(22.dp),
                label = { Text("公開URL") },
                placeholder = { Text("https://…") },
                singleLine = true,
                trailingIcon = {
                    Row {
                        androidx.compose.material3.IconButton(
                            enabled = !isBusy,
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                url = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            },
                        ) {
                            Icon(painterResource(R.drawable.ic_content_paste),
                                contentDescription = "クリップボードから貼り付け",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        androidx.compose.material3.IconButton(
                            enabled = !isBusy && url.isNotEmpty(),
                            onClick = { url = "" },
                        ) {
                            Icon(painterResource(R.drawable.ic_close),
                                contentDescription = "URLを削除")
                        }
                    }
                },
            )
        }
        item {
            LabeledOptions(
                label = "種類",
                values = DownloadMediaType.entries,
                selected = mediaType,
                text = DownloadMediaType::label,
                enabled = !isBusy,
                onSelected = {
                    mediaType = it
                    state = DownloadState.Idle
                },
            )
        }
        item {
            AnimatedContent(
                targetState = mediaType,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 5 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 5 })
                },
                label = "ダウンロード設定",
            ) { type ->
                when (type) {
                    DownloadMediaType.Video -> VideoOptions(
                        resolution = videoResolution,
                        onResolution = { videoResolution = it },
                        frameRate = frameRate,
                        onFrameRate = { frameRate = it },
                        format = videoFormat,
                        onFormat = { videoFormat = it },
                        enabled = !isBusy,
                    )
                    DownloadMediaType.Audio -> AudioOptions(
                        quality = audioQuality,
                        onQuality = { audioQuality = it },
                        format = audioFormat,
                        onFormat = { audioFormat = it },
                        enabled = !isBusy,
                    )
                    DownloadMediaType.Image -> ImageOptions(
                        quality = imageQuality,
                        onQuality = { imageQuality = it },
                        format = imageFormat,
                        onFormat = { imageFormat = it },
                        candidates = candidates,
                        selectedIds = selectedCandidateIds,
                        enabled = !isBusy,
                        onToggleCandidate = { candidate ->
                            selectedCandidateIds = if (candidate.id in selectedCandidateIds) {
                                selectedCandidateIds - candidate.id
                            } else {
                                selectedCandidateIds + candidate.id
                            }
                        },
                        onAnalyze = {
                            scope.launch {
                                state = DownloadState.Preparing("画像候補を解析しています")
                                engine.analyzeImages(url).fold(
                                    onSuccess = { result ->
                                        candidates = result
                                        selectedCandidateIds = result.take(1).mapTo(mutableSetOf(), ImageCandidate::id)
                                        state = DownloadState.Idle
                                    },
                                    onFailure = { error ->
                                        candidates = emptyList()
                                        selectedCandidateIds = emptySet()
                                        state = DownloadState.Failed(error.message ?: "画像候補を取得できませんでした")
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
        item {
            DownloadStatus(state)
        }
        item {
            Button(
                onClick = {
                    val selection = DownloaderSelection(
                        url = url,
                        mediaType = mediaType,
                        videoResolution = videoResolution,
                        frameRate = frameRate,
                        videoFormat = videoFormat,
                        audioQuality = audioQuality,
                        audioFormat = audioFormat,
                        imageQuality = imageQuality,
                        imageFormat = imageFormat,
                        selectedImages = candidates.filter { it.id in selectedCandidateIds },
                    )
                    if (
                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingDownload = true
                        storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        startDownload(scope, engine, selection) { state = it }
                    }
                },
                enabled = !isBusy && url.isNotBlank() &&
                    (mediaType != DownloadMediaType.Image || selectedCandidateIds.isNotEmpty()),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(21.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("処理中")
                } else {
                    Text("Download/Essentialへ保存")
                }
            }
        }
    }
}

private fun startDownload(
    scope: kotlinx.coroutines.CoroutineScope,
    engine: YtDlpDownloadEngine,
    selection: DownloaderSelection,
    onState: (DownloadState) -> Unit,
) {
    scope.launch {
        engine.download(selection, onState)
    }
}

@Composable
private fun VideoOptions(
    resolution: VideoResolution,
    onResolution: (VideoResolution) -> Unit,
    frameRate: FrameRate,
    onFrameRate: (FrameRate) -> Unit,
    format: VideoFormat,
    onFormat: (VideoFormat) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LabeledOptions("解像度", VideoResolution.entries, resolution, VideoResolution::label, enabled, onResolution)
        LabeledOptions("フレームレート", FrameRate.entries, frameRate, FrameRate::label, enabled, onFrameRate)
        LabeledOptions("形式", VideoFormat.entries, format, VideoFormat::label, enabled, onFormat)
    }
}

@Composable
private fun AudioOptions(
    quality: AudioQuality,
    onQuality: (AudioQuality) -> Unit,
    format: AudioFormat,
    onFormat: (AudioFormat) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("音質", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionRow(AudioQuality.entries.take(3), quality, AudioQuality::label, enabled, onQuality)
            OptionRow(AudioQuality.entries.drop(3), quality, AudioQuality::label, enabled, onQuality)
        }
        LabeledOptions("形式", AudioFormat.entries, format, AudioFormat::label, enabled, onFormat)
    }
}

@Composable
private fun ImageOptions(
    quality: ImageQuality,
    onQuality: (ImageQuality) -> Unit,
    format: ImageFormat,
    onFormat: (ImageFormat) -> Unit,
    candidates: List<ImageCandidate>,
    selectedIds: Set<String>,
    enabled: Boolean,
    onToggleCandidate: (ImageCandidate) -> Unit,
    onAnalyze: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LabeledOptions("最大画質", ImageQuality.entries, quality, ImageQuality::label, enabled, onQuality)
        LabeledOptions("形式", ImageFormat.entries, format, ImageFormat::label, enabled, onFormat)
        Button(
            onClick = onAnalyze,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(if (candidates.isEmpty()) "URLから画像候補を取得" else "画像候補を再取得")
        }
        AnimatedVisibility(candidates.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "保存する画像・複数選択可",
                    style = MaterialTheme.typography.titleMedium,
                )
                candidates.forEach { candidate ->
                    MotionSurface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        enabled = enabled,
                        onClick = { onToggleCandidate(candidate) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = candidate.id in selectedIds,
                                onCheckedChange = { onToggleCandidate(candidate) },
                                enabled = enabled,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    candidate.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    candidate.url,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> LabeledOptions(
    label: String,
    values: List<T>,
    selected: T,
    text: (T) -> String,
    enabled: Boolean,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        OptionRow(values, selected, text, enabled, onSelected)
    }
}

@Composable
private fun <T> OptionRow(
    values: List<T>,
    selected: T,
    text: (T) -> String,
    enabled: Boolean,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            val isSelected = value == selected
            MotionSurface(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape,
                enabled = enabled,
                onClick = { onSelected(value) },
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text(value),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadStatus(state: DownloadState) {
    AnimatedVisibility(state !is DownloadState.Idle) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                DownloadState.Idle -> Unit
                is DownloadState.Preparing -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                }
                is DownloadState.Running -> {
                    if (state.progress != null) {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                }
                is DownloadState.Completed -> {
                    Text("保存完了", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    state.savedNames.forEach { Text("・$it", style = MaterialTheme.typography.bodyMedium) }
                }
                is DownloadState.Failed -> {
                    Text("完了できませんでした", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SafetyNotice() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f), RoundedCornerShape(20.dp))
            .padding(15.dp),
    ) {
        Text(
            "本人が保存権限を持つ公開コンテンツ専用です。認証・Cookie・DRMなどの保護回避には対応しません。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun FeatureTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MotionSurface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            enabled = true,
            onClick = onBack,
            modifier = Modifier
                .size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("‹", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MotionSurface(
    shape: Shape,
    color: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 700f),
        label = "形状に沿う押下モーション",
    )
    Surface(
        color = color,
        contentColor = contentColor,
        shape = shape,
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
        content = content,
    )
}
