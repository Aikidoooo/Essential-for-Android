package jp.essential.app.feature.textscan

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import jp.essential.app.feature.scanner.scannerSafeArea
import jp.essential.app.ui.GlassBackButton
import jp.essential.app.ui.GlassBackdropScope
import jp.essential.app.ui.GlassFeatureTitle
import jp.essential.app.ui.liquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

private val regionsSaver = listSaver<List<ScanRegion>, ArrayList<Float>>(
    save = { regions -> regions.map { region -> ArrayList(region.points.flatMap { listOf(it.x, it.y) }) } },
    restore = { values -> values.map { values -> ScanRegion(values.chunked(2).map { ScanPoint(it[0], it[1]) }) } },
)

@Composable
internal fun TextScanSelectionScreen(uri: Uri, busy: Boolean, error: String?,
    onBack: () -> Unit, onRead: (Bitmap, List<ScanRegion>) -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failure by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        try { bitmap = withContext(Dispatchers.IO) { decodeScanPhoto(context, uri) } }
        catch (cause: Exception) {
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            failure = true
        }
    }
    if (bitmap != null) ScanRegionEditor(bitmap!!, uri.toString(), busy, error, onBack, onRead)
    else Surface(Modifier.fillMaxSize(), color = Color(0xFF101623), contentColor = Color.White) {
        BackHandler(onBack = onBack)
        Column(Modifier.fillMaxSize().scannerSafeArea().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            GlassBackButton(onClick = onBack)
            if (failure) Text("写真を開けませんでした。別の写真を選んでください。")
            else { CircularProgressIndicator(); Text("写真を準備しています") }
        }
    }
}

@Composable
internal fun ScanRegionEditor(photo: Bitmap, photoKey: String, busy: Boolean, error: String?,
    onBack: () -> Unit, onRead: (Bitmap, List<ScanRegion>) -> Unit) {
    var regions by rememberSaveable(photoKey, stateSaver = regionsSaver) { mutableStateOf(emptyList<ScanRegion>()) }
    var stroke by remember(photoKey) { mutableStateOf(emptyList<ScanPoint>()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var warning by remember { mutableStateOf<String?>(null) }
    val tint = Color(0xFF8DDEFA)
    val image = remember(photo) { photo.asImageBitmap() }
    val scale = min(viewport.width.toFloat() / photo.width, viewport.height.toFloat() / photo.height)
    val width = photo.width * scale
    val height = photo.height * scale
    val bounds = Rect((viewport.width - width) / 2, (viewport.height - height) / 2,
        (viewport.width + width) / 2, (viewport.height + height) / 2)
    fun normalized(point: Offset) = ScanPoint(((point.x - bounds.left) / bounds.width).coerceIn(0f, 1f),
        ((point.y - bounds.top) / bounds.height).coerceIn(0f, 1f))
    BackHandler { if (!busy) onBack() }
    GlassBackdropScope(Modifier.fillMaxSize(), background = {
        Box(Modifier.fillMaxSize().background(Color(0xFF101623)))
    }) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize().scannerSafeArea().padding(18.dp).testTag("text-scan-selection"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassBackButton(onClick = { if (!busy) onBack() })
                    GlassFeatureTitle("読み取り範囲", Modifier.weight(1f))
                }
                Text("読み取りたい部分をなぞって囲んでください。複数選べます。", style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged { viewport = it }) {
                    Canvas(Modifier.fillMaxSize().testTag("text-scan-photo").semantics {
                        contentDescription = "写真上で読み取りたい範囲を指で囲む"
                    }.pointerInput(photoKey, viewport, busy) {
                        if (!busy && bounds.width > 0 && bounds.height > 0) detectDragGestures(
                            onDragStart = { point ->
                                warning = null
                                stroke = if (bounds.contains(point)) listOf(normalized(point)) else emptyList()
                            },
                            onDrag = { change, _ ->
                                if (stroke.isNotEmpty()) {
                                    change.consume()
                                    val point = normalized(change.position)
                                    val previous = stroke.last()
                                    if (stroke.size < 2048 && (kotlin.math.abs(previous.x - point.x) + kotlin.math.abs(previous.y - point.y)) > .001f)
                                        stroke = stroke + point
                                }
                            },
                            onDragCancel = { stroke = emptyList() },
                            onDragEnd = {
                                if (stroke.isNotEmpty()) {
                                    val region = ScanRegion(stroke)
                                    if (region.isValid() && regions.size < 30) regions = regions + region
                                    else warning = if (regions.size >= 30) "一度に選べる範囲は30個までです。" else "文字の周りをぐるっと囲んでください。"
                                }
                                stroke = emptyList()
                            },
                        )
                    }) {
                        if (bounds.width <= 0 || bounds.height <= 0) return@Canvas
                        drawImage(image, dstOffset = IntOffset(bounds.left.toInt(), bounds.top.toInt()),
                            dstSize = IntSize(bounds.width.toInt(), bounds.height.toInt()))
                        fun path(points: List<ScanPoint>, closed: Boolean): Path = Path().apply {
                            points.forEachIndexed { index, point ->
                                val x = bounds.left + point.x * bounds.width
                                val y = bounds.top + point.y * bounds.height
                                if (index == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            if (closed) close()
                        }
                        regions.forEach { region ->
                            val path = path(region.points, true)
                            drawPath(path, tint.copy(alpha = .18f))
                            drawPath(path, tint, style = Stroke(2.5.dp.toPx()))
                        }
                        if (stroke.size > 1) drawPath(path(stroke, false), tint, style = Stroke(2.5.dp.toPx()))
                    }
                }
                Text("選択した範囲：${regions.size}個", Modifier.testTag("text-scan-selection-count"), color = tint)
                (warning ?: error)?.let { Text(it, color = Color(0xFFFFC8A8), style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScanGlassButton("ひとつ戻す", !busy && regions.isNotEmpty(), Modifier.weight(1f)) { regions = regions.dropLast(1) }
                    ScanGlassButton("すべて解除", !busy && regions.isNotEmpty(), Modifier.weight(1f)) { regions = emptyList() }
                }
                ScanGlassButton(if (busy) "読み取り中…" else "囲んだ部分を読み取る", !busy && regions.isNotEmpty(),
                    Modifier.fillMaxWidth().testTag("text-scan-read")) { onRead(photo, regions) }
            }
        }
    }
}

/** 写真操作のボタンも、既存の軽いばね押下に合わせる。 */
@Composable
internal fun ScanGlassButton(label: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .98f else 1f,
        spring(dampingRatio = .9f, stiffness = 520f), label = "文字スキャンの押下")
    TextButton(onClick, modifier.graphicsLayer { scaleX = scale; scaleY = scale }.liquidGlass(24.dp),
        enabled = enabled, interactionSource = interaction,
        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF8DDEFA), disabledContentColor = Color(0xFF8DDEFA).copy(alpha = .4f))) {
        Text(label)
    }
}
