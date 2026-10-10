package jp.essential.app.feature.files

import jp.essential.app.ui.fixedHeader
import jp.essential.app.ui.FeatureHeader
import jp.essential.app.ui.GlassFeatureTitle

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import jp.essential.app.ui.liquidGlass
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import jp.essential.app.ui.progressiveItem
import jp.essential.app.ui.EssentialBubblySlider
import jp.essential.app.ui.EssentialMediaPickerContract
import android.widget.VideoView
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.view.ViewOutlineProvider

@Composable
fun FileReferenceScreen(initialFile: ReferencedFile? = null, onBack: () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        FileReferenceContent(initialFile, onBack)
    }
}

@Composable
private fun FileReferenceContent(initialFile: ReferencedFile?, onBack: () -> Unit) {
    val context = LocalContext.current
    val engine = remember(context) { MediaFileEngine(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var referencedFile by remember { mutableStateOf<ReferencedFile?>(initialFile) }
    var frameEditorFile by remember { mutableStateOf<ReferencedFile?>(null) }
    var targetMegabytes by remember { mutableIntStateOf(20) }

    var gifPreset by remember { mutableStateOf(GifPreset.Standard) }
    var processingLabel by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    var editorMode by remember { mutableStateOf<MediaEditorMode?>(null) }
    if (editorMode != null && referencedFile != null) {
        MediaEditorScreen(referencedFile!!, editorMode!!, onBack = { editorMode = null })
        return
    }
    frameEditorFile?.let { file ->
        FrameExtractionScreen(
            file = file,
            engine = engine,
            onBack = { frameEditorFile = null },
        )
        return
    }

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

    val picker = rememberLauncherForActivityResult(EssentialMediaPickerContract()) { uri ->
        if (uri != null) scope.launch {
            try {
                referencedFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // 永続権限を返すプロバイダーの場合だけ、次回以降の読込権限を維持する。
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    engine.inspect(uri)
                }
                resultMessage = null
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { resultMessage = error.message ?: "ファイルを参照できませんでした" }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        var motionIndex = 0
        fixedHeader { FileTopBar(onBack) }
        referencedFile?.let { file ->
            progressiveItem(motionIndex++) { MediaFilePreview(file) }
        }
        progressiveItem(motionIndex++) {
            Box(Modifier.fillMaxWidth().height(190.dp).liquidGlass(RoundedCornerShape(24.dp))
                .drawWithCache {
                    val stroke = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())))
                    onDrawWithContent {
                        drawContent()
                        drawRoundRect(Color.White.copy(alpha = .35f), cornerRadius = CornerRadius(24.dp.toPx()), style = stroke)
                    }
                }.clickable(enabled = processingLabel == null) { picker.launch(arrayOf("image/*", "video/*", "audio/*")) }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(58.dp).liquidGlass(CircleShape), contentAlignment = Alignment.Center) { Text("↑", style = MaterialTheme.typography.headlineLarge) }
                    Text(if (referencedFile == null) "ファイルを参照" else "別のファイルを参照", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        referencedFile?.let { file -> progressiveItem(motionIndex++) { FileSummary(file) } }
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
                    EssentialBubblySlider(
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
                        SectionTitle("AI画像編集", "背景透過・3モデルの高画質化")
                        GlassMediaButton("AI画像編集を開く", processingLabel == null) { editorMode = MediaEditorMode.Image }
                    }
                }
                ReferencedMediaType.Video -> {
                    progressiveItem(motionIndex++) {
                        ToolCard {
                            SectionTitle("ボーカル・楽器ミキサー", "動画の音声を6種類へ分離して合成")
                            GlassMediaButton("ボーカル・楽器ミキサーを開く", processingLabel == null) { editorMode = MediaEditorMode.Mixer }
                        }
                    }
                    progressiveItem(motionIndex++) {
                        ToolCard {
                            SectionTitle("フレーム切り取り", "動画を見ながら専用画面で時刻を選択")
                            Text(
                                "プレビュー、再生、一コマ送りを一つの画面で操作できます。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ActionButton("フレーム切り取りを開く", processingLabel) {
                                frameEditorFile = file
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
                        SectionTitle("音声編集", "波形で切り取り・再生しながら音量を調整")
                        GlassMediaButton("音声切り取りを開く", processingLabel == null) { editorMode = MediaEditorMode.Trim }
                        GlassMediaButton("ボーカル・楽器ミキサーを開く", processingLabel == null) { editorMode = MediaEditorMode.Mixer }
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
private fun FrameExtractionScreen(
    file: ReferencedFile,
    engine: MediaFileEngine,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var positionMillis by rememberSaveable(file.uri.toString()) { mutableLongStateOf(0L) }
    var prepared by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var playing by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    val durationMillis = file.durationMillis.coerceAtLeast(1L)
    val fps = file.frameRate.takeIf { it > 0f } ?: 30f
    val frameDurationMillis = (1000f / fps).toLong().coerceAtLeast(1L)

    BackHandler(onBack = onBack)
    DisposableEffect(Unit) {
        onDispose {
            videoView?.stopPlayback()
            previewBitmap?.recycle()
        }
    }
    LaunchedEffect(playing, prepared) {
        while (playing && prepared) {
            videoView?.let { view ->
                positionMillis = view.currentPosition.toLong().coerceIn(0L, durationMillis)
                if (!view.isPlaying) playing = false
            }
            delay(50L)
        }
    }
    LaunchedEffect(positionMillis, playing) {
        if (playing) return@LaunchedEffect
        delay(90L)
        runCatching { engine.previewFrame(file, positionMillis) }
            .onSuccess { frame ->
                val previous = previewBitmap
                previewBitmap = frame
                delay(20L)
                previous?.takeIf { it !== frame }?.recycle()
            }
    }

    fun seekTo(targetMillis: Long) {
        val target = targetMillis.coerceIn(0L, durationMillis)
        positionMillis = target
        videoView?.seekTo(target.toInt())
    }

    val controlsReady = prepared || previewBitmap != null
    // 実際に描画したフレーム寸法を優先し、メタデータと表示方向が異なる動画にも追従する。
    val previewWidth = previewBitmap?.width?.takeIf { it > 0 } ?: file.widthPixels.takeIf { it > 0 } ?: 16
    val previewHeight = previewBitmap?.height?.takeIf { it > 0 } ?: file.heightPixels.takeIf { it > 0 } ?: 9
    val targetPreviewAspectRatio = adaptivePreviewAspectRatio(previewWidth, previewHeight)
    val previewAspectRatio by animateFloatAsState(
        targetValue = targetPreviewAspectRatio,
        animationSpec = tween(360),
        label = "動画縦横比",
    )
    val portraitVideo = isPortraitPreview(targetPreviewAspectRatio)
    val previewShape = RoundedCornerShape(if (portraitVideo) 34.dp else 28.dp)
    val mediaShape = RoundedCornerShape(if (portraitVideo) 28.dp else 21.dp)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        var motionIndex = 0
        fixedHeader { FrameTopBar(onBack) }
        progressiveItem(motionIndex++) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val widthFraction by animateFloatAsState(
                    targetValue = if (portraitVideo) 0.66f else 1f,
                    animationSpec = tween(360),
                    label = "動画プレビュー幅",
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(widthFraction)
                        .aspectRatio(previewAspectRatio)
                        .clip(previewShape)
                        .semantics {
                            contentDescription = if (portraitVideo) {
                                "角丸の縦動画プレビュー ${previewWidth}×${previewHeight}"
                            } else {
                                "角丸の横動画プレビュー ${previewWidth}×${previewHeight}"
                            }
                        },
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = previewShape,
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(8.dp).clip(mediaShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        AndroidView(
                            factory = { context ->
                                VideoView(context).apply {
                                    videoView = this
                                    background = GradientDrawable().apply {
                                        setColor(android.graphics.Color.BLACK)
                                        cornerRadius = 28f * context.resources.displayMetrics.density
                                    }
                                    outlineProvider = ViewOutlineProvider.BACKGROUND
                                    clipToOutline = true
                                    setVideoURI(file.uri)
                                    setOnPreparedListener { player ->
                                        player.isLooping = false
                                        prepared = true
                                        playbackError = false
                                        seekTo(positionMillis.toInt())
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        playbackError = true
                                        prepared = false
                                        playing = false
                                        true
                                    }
                                    setOnCompletionListener {
                                        playing = false
                                        positionMillis = durationMillis
                                    }
                                }
                            },
                            update = { view ->
                                if (!playing && abs(view.currentPosition.toLong() - positionMillis) > 80L) {
                                    view.seekTo(positionMillis.toInt())
                                }
                            },
                            modifier = Modifier.fillMaxSize().clip(mediaShape),
                        )
                        if (!playing) {
                            previewBitmap?.let { frame ->
                                Image(
                                    bitmap = frame.asImageBitmap(),
                                    contentDescription = "選択中の動画フレーム",
                                    modifier = Modifier.fillMaxSize().clip(mediaShape),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                        if (!controlsReady) CircularProgressIndicator()
                    }
                }
            }
        }
        progressiveItem(motionIndex++) {
            ToolCard {
                val playColor by animateColorAsState(
                    targetValue = if (playing) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    animationSpec = tween(320),
                    label = "再生ボタン色",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(formatTime(positionMillis), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "約${(positionMillis / 1000f * fps).toLong()}フレーム / ${formatTime(durationMillis)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        modifier = Modifier.size(58.dp).clickable(enabled = prepared) {
                            videoView?.let { view ->
                                if (playing) view.pause() else view.start()
                                playing = !playing
                            }
                        },
                        color = playColor.copy(alpha = 0.9f),
                        shape = CircleShape,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (playing) "Ⅱ" else "▶", fontWeight = FontWeight.Black)
                        }
                    }
                }
                EssentialBubblySlider(
                    value = positionMillis.toFloat(),
                    onValueChange = { seekTo(it.toLong()) },
                    valueRange = 0f..durationMillis.toFloat(),
                    enabled = controlsReady,
                )
                AnimatedVisibility(playbackError) {
                    Text(
                        "この動画は端末の標準再生に非対応です。フレーム画像でプレビューしています。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { seekTo(positionMillis - frameDurationMillis) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        enabled = controlsReady,
                    ) { Text("−1フレーム") }
                    OutlinedButton(
                        onClick = { seekTo(positionMillis + frameDurationMillis) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        enabled = controlsReady,
                    ) { Text("＋1フレーム") }
                }
            }
        }
        progressiveItem(motionIndex++) {
            ToolCard {
                SectionTitle("この瞬間を保存", "選択中の時刻を高品質PNGとして書き出します")
                Button(
                    onClick = {
                        if (processing) return@Button
                        videoView?.pause()
                        playing = false
                        scope.launch {
                            processing = true
                            resultMessage = null
                            runCatching { engine.extractFrame(file, positionMillis) }
                                .onSuccess { resultMessage = "$it をDownload/Essentialへ保存しました" }
                                .onFailure { resultMessage = it.message ?: "フレーム切り取りに失敗しました" }
                            processing = false
                        }
                    },
                    enabled = controlsReady && !processing,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    if (processing) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (processing) "PNGを作成中" else "このフレームを写真にする")
                }
                AnimatedVisibility(resultMessage != null) {
                    Text(
                        resultMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FrameTopBar(onBack: () -> Unit) {
    FeatureHeader("フレーム切り取り", onBack)
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
    MediaGlassCard(content)
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
    FeatureHeader("ファイル参照", onBack)
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
