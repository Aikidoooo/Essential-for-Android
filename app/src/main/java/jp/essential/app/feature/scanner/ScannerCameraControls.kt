package jp.essential.app.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import jp.essential.app.feature.qr.zoomRatioToFraction
import jp.essential.app.feature.qr.fractionToZoomRatio
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.essential.app.feature.qr.GalleryIcon
import jp.essential.app.feature.qr.ZoomQuickButtons
import jp.essential.app.feature.qr.ZoomRuler

/** 倍率、白いシャッター、写真とモード選択をカメラ映像の上に共通配置する。 */
@Composable
internal fun ScannerCameraControls(
    textMode: Boolean,
    modePosition: Float,
    onModeChange: () -> Unit,
    zoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoom: (Float) -> Unit,
    onPhoto: () -> Unit,
    onCapture: () -> Unit,
    captureEnabled: Boolean,
    busy: Boolean,
    auto: Boolean = false,
    onAutoChange: (Boolean) -> Unit = {},
) {
    val progress = modePosition.coerceIn(0f, 1f)
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScannerZoomControl(zoom, minZoom, maxZoom, onZoom)
        Surface(onClick = onCapture, enabled = textMode && captureEnabled && !busy, color = Color.White,
            shape = CircleShape, modifier = Modifier.size(84.dp).testTag("scanner-shutter")
                .border(6.dp, Color(0xFF999999), CircleShape).semantics { contentDescription = if (textMode) "撮影して読み取る" else "QRは自動読み取り・撮影は使用不可" }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (progress < 0.999f) Canvas(Modifier.size(54.dp).testTag("scanner-shutter-disabled-mark")
                    .semantics { this[ScannerMotionProgress] = 1f - progress }) {
                    drawLine(Color(0xFF777777).copy(alpha = 1f - progress), Offset(0f, size.height),
                        Offset(size.width * (1f - progress), size.height * progress),
                        3.dp.toPx(), StrokeCap.Round)
                }
                if (busy) CircularProgressIndicator(Modifier.size(26.dp), color = Color.DarkGray, strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(onClick = onPhoto, enabled = !busy, color = Color.Black.copy(alpha = 0.48f),
                shape = CircleShape, modifier = Modifier.size(44.dp).semantics { contentDescription = "写真を選ぶ" }) {
                Box(contentAlignment = Alignment.Center) { GalleryIcon() }
            }
            jp.essential.app.ui.GlassModeSelector(
                listOf("QRスキャナー", "文字スキャン"), listOf("scanner-qr-mode", "scanner-text-mode"),
                if (textMode) 1 else 0, modePosition, { onModeChange() }, Modifier.weight(1f), enabled = !busy)
            Surface(onClick = { onAutoChange(!auto) }, enabled = !textMode && !busy, color = Color.Black.copy(alpha = 0.48f),
                contentColor = if (textMode) Color.White.copy(alpha = 0.38f) else if (auto) Color(0xFFFFEB86) else Color.White, shape = CircleShape,
                modifier = Modifier.width(54.dp).height(46.dp).testTag("scanner-auto")
                    .semantics { contentDescription = if (textMode) "Auto 使用不可" else if (auto) "Auto オン" else "Auto オフ" }) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Auto", style = MaterialTheme.typography.labelMedium)
                    if (progress > 0.001f) Canvas(Modifier.width(34.dp).height(22.dp).testTag("scanner-auto-disabled-mark")
                        .semantics { this[ScannerMotionProgress] = progress }) {
                        drawLine(Color.White.copy(alpha = 0.65f * progress), Offset(0f, size.height),
                            Offset(size.width * progress, size.height * (1f - progress)), 1.5.dp.toPx(), StrokeCap.Round)
                    }
                }
            }
        }
    }
}

/** 通常はプリセットだけを表示し、横ドラッグ中は同じ位置で目盛りを滑走させる。 */
@Composable
internal fun ScannerZoomControl(zoom: Float, minZoom: Float, maxZoom: Float, onZoom: (Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnZoom by rememberUpdatedState(onZoom)
    var previousZoom by remember { mutableStateOf(zoom) }
    LaunchedEffect(zoom) {
        if (zoom != previousZoom) expanded = true
        previousZoom = zoom
    }
    val expansion by animateFloatAsState(if (expanded) 1f else 0f,
        spring(dampingRatio = 0.85f, stiffness = 500f), label = "倍率目盛りの展開")
    LaunchedEffect(dragging, expanded, zoom) {
        if (expanded && !dragging) { delay(1200); expanded = false }
    }
    Box(Modifier.fillMaxWidth().height(82.dp).testTag("scanner-zoom-control")
        .pointerInput(minZoom, maxZoom) {
            var fraction = 0f
            detectHorizontalDragGestures(
                onDragStart = {
                    fraction = zoomRatioToFraction(currentZoom, minZoom, maxZoom)
                    dragging = true
                    expanded = true
                },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
            ) { change, amount ->
                change.consume()
                if (maxZoom > minZoom && size.width > 0) {
                    fraction = (fraction - amount / (size.width * 2.5f)).coerceIn(0f, 1f)
                    currentOnZoom(fractionToZoomRatio(fraction, minZoom, maxZoom))
                }
            }
        }, contentAlignment = Alignment.BottomCenter) {
        if (expansion > 0.01f) Box(Modifier.fillMaxWidth()
            .then(Modifier.graphicsLayer { alpha = expansion })) {
            // ドラッグは親で継続して処理し、目盛り自身のジェスチャーはここでは使わない。
            ZoomRuler(zoom, minZoom, maxZoom, onZoom, Modifier.fillMaxWidth(), gesturesEnabled = false)
        }
        if (!expanded) ZoomQuickButtons(zoom, minZoom, maxZoom, onZoom,
            Modifier.testTag("scanner-zoom-presets").graphicsLayer { alpha = 1f - expansion })
    }
}
