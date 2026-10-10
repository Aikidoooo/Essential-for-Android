package jp.essential.app.feature.files

import android.graphics.Bitmap
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import jp.essential.app.ui.FeatureHeader
import jp.essential.app.ui.fixedHeader
import jp.essential.app.ui.liquidGlass
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class MediaEditorMode { Image, Trim, Mixer }

@Composable
internal fun MediaEditorScreen(file: ReferencedFile, mode: MediaEditorMode, onBack: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        MediaEditorContent(file, mode, onBack)
    }
}

@Composable
private fun MediaEditorContent(file: ReferencedFile, mode: MediaEditorMode, onBack: () -> Unit) {
    val context = LocalContext.current
    val engine = remember { MediaFileEngine(context.applicationContext) }
    val editor = remember { MediaEditEngine(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var upscaleMode by remember { mutableStateOf(UpscaleMode.Quality) }
    var peaks by remember { mutableStateOf(FloatArray(0)) }
    var range by remember { mutableStateOf(0f..1f) }
    var stems by remember { mutableStateOf<InstrumentStems?>(null) }
    var gains by remember { mutableStateOf(InstrumentStem.entries.associateWith { 1f }) }
    var instruments by remember { mutableFloatStateOf(1f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var preview by remember { mutableStateOf<StemPreviewPlayer?>(null) }
    val duration = file.durationMillis.coerceAtLeast(1L)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val topSafePadding = maxOf(32.dp, with(density) {
        maxOf(WindowInsets.safeDrawing.getTop(this), WindowInsets.statusBars.getTop(this), WindowInsets.displayCutout.getTop(this)).toDp()
    })
    BackHandler(onBack = onBack)

    fun report(label: String, value: Float?) { stage = label; progress = value }
    fun process(action: suspend () -> String) {
        if (busy) return
        scope.launch {
            busy = true; message = null; progress = null
            try { message = action() } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: OutOfMemoryError) { message = "端末のメモリが不足しました。高画質化（2倍）で再実行してください" }
            catch (error: Exception) { message = error.message ?: "処理に失敗しました" }
            finally { busy = false }
        }
    }
    LaunchedEffect(file.uri) {
        if (file.type != ReferencedMediaType.Image) {
            runCatching { editor.waveform(file) }.onSuccess { peaks = it }.onFailure { message = it.message }
        }
    }
    DisposableEffect(file.uri, mode) {
        if (mode == MediaEditorMode.Trim) {
            player = MediaPlayer()
            runCatching { player!!.apply {
                setDataSource(context, file.uri)
                setOnPreparedListener { prepared = true }
                setOnCompletionListener { playing = false }
                setOnErrorListener { _, _, _ -> playing = false; message = "音声を再生できませんでした"; true }
                prepareAsync()
            } }.onFailure { message = "音声を開けませんでした" }
        }
        onDispose { player?.release(); player = null; preview?.close(); stems?.close() }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { runCatching { player?.pause() }; preview?.pause(); playing = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(stems, gains, instruments) {
        preview?.gains = InstrumentStem.entries.map { (gains[it] ?: 1f) * if (it == InstrumentStem.Vocals) 1f else instruments }.toFloatArray()
    }
    LaunchedEffect(playing) {
        while (playing) {
            if (mode == MediaEditorMode.Trim) {
                position = player?.currentPosition?.toLong() ?: 0
                if (position >= (duration * range.endInclusive).toLong()) { player?.pause(); playing = false }
            } else {
                position = preview?.positionMillis ?: 0
                preview?.error?.let { message = it }
                if (preview?.playing != true) playing = false
            }
            delay(40)
        }
    }
    fun seekTo(millis: Long) {
        position = millis.coerceIn(0, duration)
        if (mode == MediaEditorMode.Trim && prepared) player?.seekTo(position.toInt()) else preview?.seekTo(position)
    }

    LazyColumn(Modifier.fillMaxSize().padding(top = topSafePadding)
        .consumeWindowInsets(PaddingValues(top = topSafePadding)).testTag("media-editor"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        fixedHeader { FeatureHeader(when (mode) { MediaEditorMode.Image -> "AI画像編集"; MediaEditorMode.Trim -> "音声切り取り"; MediaEditorMode.Mixer -> "ボーカル・楽器ミキサー" }, onBack) }
        item { MediaFilePreview(file, allowPlayback = false) }
        if (busy) item { MediaGlassCard {
            Text(stage + (progress?.let { " ${(it * 100).roundToInt()}%" } ?: ""))
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { progress!!.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        } }
        if (mode == MediaEditorMode.Image) {
            item { MediaGlassCard {
                Text("AI画像高画質化", style = MaterialTheme.typography.titleLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    UpscaleMode.entries.forEachIndexed { index, value ->
                        SegmentedButton(selected = value == upscaleMode, onClick = { if (!busy) upscaleMode = value }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(value.label) }
                    }
                }
                Text("${upscaleMode.scale}倍へ拡大・長辺2048px超の入力は先に縮小", style = MaterialTheme.typography.bodySmall)
                GlassMediaButton("高画質化して保存", !busy) { process {
                    val name = editor.upscale(file, upscaleMode, ::report)
                    "$name をDownload/Essentialへ保存しました"
                } }
            } }
            item { MediaGlassCard {
                Text("AI背景透過", style = MaterialTheme.typography.titleLarge)
                GlassMediaButton("背景を透明にして保存", !busy) { process {
                    report("背景透過モデルをダウンロード", null)
                    val name = engine.removeBackground(file) { value -> report(if (value == 1f) "背景透過を処理" else "背景透過モデルをダウンロード", if (value == 1f) null else value) }
                    "$name をDownload/Essentialへ保存しました"
                } }
            } }
        } else {
            item { MediaGlassCard {
                Text(if (mode == MediaEditorMode.Trim) "波形の両端を動かして範囲を選択" else "合成プレビュー", style = MaterialTheme.typography.titleMedium)
                WaveformTimeline(peaks, if (mode == MediaEditorMode.Trim) range else 0f..1f, position.toFloat() / duration,
                    editable = mode == MediaEditorMode.Trim, onRange = {
                        range = it
                        seekTo((duration * it.start).toLong())
                    })
                if (mode == MediaEditorMode.Trim) Text("${mediaTime((duration * range.start).toLong())}  —  ${mediaTime((duration * range.endInclusive).toLong())}")
                Text("${mediaTime(position)} / ${mediaTime(duration)}")
                Slider(value = position.toFloat() / duration, onValueChange = { seekTo((it * duration).toLong()) }, enabled = if (mode == MediaEditorMode.Trim) prepared else preview != null)
                GlassMediaButton(if (playing) "一時停止" else "再生", if (mode == MediaEditorMode.Trim) prepared else preview != null) {
                    if (playing) { player?.pause(); preview?.pause(); playing = false }
                    else {
                        if (mode == MediaEditorMode.Trim) {
                            if (position < duration * range.start || position >= duration * range.endInclusive) seekTo((duration * range.start).toLong())
                            player?.start()
                        } else preview?.play()
                        playing = true
                    }
                }
                if (mode == MediaEditorMode.Trim) GlassMediaButton("選択範囲を切り取って保存", !busy) { process {
                    report("音声を切り取り", null)
                    val name = engine.trimAudio(file, (duration * range.start).toLong(), (duration * range.endInclusive).toLong())
                    "$name をDownload/Essentialへ保存しました"
                } }
            } }
            if (mode == MediaEditorMode.Mixer) {
                item { MediaGlassCard {
                    Text("ボーカルと5種類の楽器", style = MaterialTheme.typography.titleLarge)
                    if (stems == null) GlassMediaButton("AIで分離する", !busy) { process {
                        val result = InstrumentSeparationEngine(context.applicationContext).separate(file, ::report)
                        stems = result
                        preview = StemPreviewPlayer(result, scope)
                        "分離が完了しました。音量を変えながら再生できます"
                    } }
                    if (stems != null) {
                        GainControl("楽器全体", instruments) { instruments = it }
                        val order = listOf(InstrumentStem.Vocals, InstrumentStem.Piano, InstrumentStem.Guitar, InstrumentStem.Bass, InstrumentStem.Drums, InstrumentStem.Other)
                        order.forEach { stem -> GainControl(stem.label, gains.getValue(stem)) { gains = gains + (stem to it) } }
                        GlassMediaButton(if (file.type == ReferencedMediaType.Video) "調整した音量で動画を保存" else "調整した音量で1つのWAVに合成", !busy) { process {
                            report("音声を合成", null)
                            val name = editor.mix(stems!!, gains, instruments, file)
                            "$name をDownload/Essentialへ保存しました"
                        } }
                    }
                } }
            }
        }
        if (message != null) item { MediaGlassCard {
            message?.let { Text(it) }
        } }
    }
}

@Composable
internal fun MediaGlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
internal fun GlassMediaButton(label: String, enabled: Boolean = true, action: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).liquidGlass(RoundedCornerShape(18.dp)).clickable(enabled = enabled, onClick = action).padding(12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .4f))
    }
}

@Composable
private fun GainControl(label: String, gain: Float, changed: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Text("${(gain * 100).roundToInt()}%") }
    Slider(gain, changed, valueRange = 0f..2f)
}

@Composable
internal fun MediaFilePreview(file: ReferencedFile, allowPlayback: Boolean = true) {
    val context = LocalContext.current
    var bitmap by remember(file.uri) { mutableStateOf<Bitmap?>(null) }
    var error by remember(file.uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(file.uri) {
        if (file.type != ReferencedMediaType.Audio) runCatching {
            withContext(Dispatchers.IO) {
                if (file.type == ReferencedMediaType.Image) loadMediaImage(context, file, 1024)
                else MediaFileEngine(context).previewFrame(file, 0)
            }
        }.onSuccess { bitmap = it }.onFailure { error = "プレビューを読み込めませんでした" }
    }
    DisposableEffect(file.uri) { onDispose { bitmap?.recycle() } }
    MediaGlassCard {
        bitmap?.let { image -> Image(image.asImageBitmap(), file.name, Modifier.fillMaxWidth().heightIn(max = 260.dp).aspectRatio(image.width.toFloat() / image.height), contentScale = ContentScale.Fit) }
        if (file.type == ReferencedMediaType.Audio) Text("♫", style = MaterialTheme.typography.displayLarge)
        Text(file.name, style = MaterialTheme.typography.titleMedium)
        if (file.type == ReferencedMediaType.Audio && allowPlayback) AudioFilePreview(file)
        error?.let { Text(it) }
    }
}

@Composable
private fun AudioFilePreview(file: ReferencedFile) {
    val context = LocalContext.current
    var peaks by remember { mutableStateOf(FloatArray(0)) }
    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember { MediaPlayer() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(file.uri) {
        runCatching { MediaEditEngine(context).waveform(file) }.onSuccess { peaks = it }.onFailure { error = it.message }
    }
    DisposableEffect(file.uri, lifecycle) {
        runCatching {
            player.setDataSource(context, file.uri)
            player.setOnPreparedListener { ready = true }
            player.setOnCompletionListener { playing = false }
            player.setOnErrorListener { _, _, _ -> playing = false; error = "音声を再生できませんでした"; true }
            player.prepareAsync()
        }.onFailure { error = "音声を開けませんでした" }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { runCatching { player.pause() }; playing = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.release() }
    }
    LaunchedEffect(playing) {
        while (playing) { position = player.currentPosition.toLong(); delay(50) }
    }
    WaveformTimeline(peaks, 0f..1f, position.toFloat() / file.durationMillis.coerceAtLeast(1), false) {}
    GlassMediaButton(if (playing) "プレビューを停止" else "音声をプレビュー", ready) {
        if (playing) player.pause() else player.start()
        playing = !playing
    }
    error?.let { Text(it) }
}

@Composable
internal fun WaveformTimeline(peaks: FloatArray, range: ClosedFloatingPointRange<Float>, position: Float, editable: Boolean, onRange: (ClosedFloatingPointRange<Float>) -> Unit) {
    val currentRange by rememberUpdatedState(range)
    val callback by rememberUpdatedState(onRange)
    val primary = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(140.dp).liquidGlass(RoundedCornerShape(18.dp)).testTag("waveform-timeline")
        .pointerInput(editable) {
            if (editable) {
                var startHandle = true
                detectDragGestures(onDragStart = { point ->
                    startHandle = abs(point.x - currentRange.start * size.width) < abs(point.x - currentRange.endInclusive * size.width)
                }) { change, _ ->
                    change.consume()
                    val value = (change.position.x / size.width).coerceIn(0f, 1f)
                    val selected = currentRange
                    callback(if (startHandle) value.coerceAtMost(selected.endInclusive - .005f)..selected.endInclusive else selected.start..value.coerceAtLeast(selected.start + .005f))
                }
            }
        }) {
        val start = range.start * size.width
        val end = range.endInclusive * size.width
        drawRect(primary.copy(alpha = .12f), Offset(start, 0f), androidx.compose.ui.geometry.Size(end - start, size.height))
        if (peaks.isNotEmpty()) peaks.forEachIndexed { index, peak ->
            val x = (index + .5f) / peaks.size * size.width
            val height = maxOf(3.dp.toPx(), peak * size.height * .75f)
            drawLine(if (x in start..end) primary else primary.copy(alpha = .22f), Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2), 2.dp.toPx())
        }
        if (editable) {
            drawRect(primary, Offset(start, 0f), androidx.compose.ui.geometry.Size(end - start, size.height), style = Stroke(2.dp.toPx()))
            listOf(start, end).forEach { x ->
                drawLine(primary, Offset(x.coerceIn(2.dp.toPx(), size.width - 2.dp.toPx()), 0f), Offset(x.coerceIn(2.dp.toPx(), size.width - 2.dp.toPx()), size.height), 5.dp.toPx())
            }
        }
        val cursor = position.coerceIn(0f, 1f) * size.width
        drawLine(Color.White, Offset(cursor, 0f), Offset(cursor, size.height), 2.dp.toPx())
    }
}

internal fun mediaTime(millis: Long): String = String.format(Locale.JAPAN, "%02d:%02d.%03d", millis / 60000, millis / 1000 % 60, millis % 1000)
