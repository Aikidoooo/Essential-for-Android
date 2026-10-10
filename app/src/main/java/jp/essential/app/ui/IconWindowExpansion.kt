package jp.essential.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** 起点はウィンドウ座標で保持し、入場時だけ固定して戻る操作に再利用する。 */
internal class WindowExpansionOrigin {
    var bounds: Rect? = null
    var icon: WindowExpansionIconSource? = null
}
internal class WindowExpansionIconSource {
    var bounds: Rect? = null
    var layer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null
}
internal val LocalWindowExpansionIconSource = compositionLocalOf<WindowExpansionIconSource?> { null }

/** 元のアイコンの位置と描画を保持し、遷移中の色や形の切り替わりをなくす。 */
@Composable
internal fun Modifier.windowExpansionIcon(): Modifier {
    val source = LocalWindowExpansionIconSource.current ?: return this
    val layer = rememberGraphicsLayer()
    return onGloballyPositioned { source.bounds = it.boundsInWindow() }.drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        source.layer = layer
        drawLayer(layer)
    }
}
internal val LocalWindowExpansionOrigin = compositionLocalOf { WindowExpansionOrigin() }

@Composable
internal fun rememberWindowExpansionClick(onClick: () -> Unit): Pair<Modifier, () -> Unit> =
    rememberWindowExpansionClick(onClick, null)

@Composable
internal fun rememberWindowExpansionClick(onClick: () -> Unit, sourceIcon: WindowExpansionIconSource?): Pair<Modifier, () -> Unit> {
    val origin = LocalWindowExpansionOrigin.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val latestClick by rememberUpdatedState(onClick)
    return Modifier.onGloballyPositioned { bounds = it.boundsInWindow() } to {
        origin.bounds = bounds
        origin.icon = sourceIcon
        latestClick()
    }
}

/** 全画面のレイアウトを固定し、GPUレイヤーだけで起点から拡大する。 */
@Composable
internal fun <T : Any> IconWindowContent(
    targetState: T?,
    modifier: Modifier = Modifier,
    sharedOrigin: WindowExpansionOrigin? = null,
    icon: (@Composable (T) -> Unit)? = null,
    contentTopInset: Dp = 0.dp,
    content: @Composable (T?) -> Unit,
) {
    val origin = sharedOrigin ?: remember { WindowExpansionOrigin() }
    val progress = remember { Animatable(0f) }
    val windowSpring = remember { spring<Float>(dampingRatio = 1f, stiffness = 380f) }
    val backdrop = rememberGraphicsLayer()
    val backdropReady = remember { booleanArrayOf(false) }
    val density = LocalDensity.current
    val blurEffect = remember(density) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val radius = with(density) { LiquidGlassStyle.blurRadius.toPx() }
            android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else null
    }
    val surfaceColor = MaterialTheme.colorScheme.background
    var displayed by remember { mutableStateOf(targetState) }
    var source by remember { mutableStateOf<Rect?>(null) }
    var sourceIcon by remember { mutableStateOf<WindowExpansionIconSource?>(null) }
    var hostBounds by remember { mutableStateOf(Rect.Zero) }
    val resolvedSource = remember(source, hostBounds, density) {
        source ?: with(density) {
            Rect(hostBounds.center.x - 28.dp.toPx(), hostBounds.center.y - 28.dp.toPx(),
                hostBounds.center.x + 28.dp.toPx(), hostBounds.center.y + 28.dp.toPx())
        }
    }
    LaunchedEffect(targetState) {
        if (targetState != null) {
            if (displayed == null) {
                source = origin.bounds
                sourceIcon = origin.icon?.let { original ->
                    WindowExpansionIconSource().apply { bounds = original.bounds; layer = original.layer }
                }
            }
            displayed = targetState
            progress.animateTo(1f, windowSpring)
        } else {
            progress.animateTo(0f, windowSpring)
            displayed = null
        }
    }
    CompositionLocalProvider(LocalWindowExpansionOrigin provides origin) {
        Box(modifier.onGloballyPositioned { hostBounds = it.boundsInWindow() }) {
            Box(Modifier.fillMaxSize().then(if (displayed != null) Modifier.clearAndSetSemantics { } else Modifier)
                .graphicsLayer { alpha = 1f - progress.value.coerceIn(0f, 1f) }
                .drawWithContent {
                    // 開いた直後に一度だけ背面を記録し、閉じ終わるまで共有する。
                    if (displayed == null) {
                        backdropReady[0] = false
                        drawContent()
                    } else {
                        if (!backdropReady[0]) {
                            backdrop.record { this@drawWithContent.drawContent() }
                            backdropReady[0] = true
                        }
                        drawLayer(backdrop)
                    }
                }) {
                content(null)
            }
            displayed?.let { current ->
                Box(Modifier.fillMaxSize().testTag("icon-window-surface").graphicsLayer {
                    val fraction = progress.value.coerceIn(0f, 1f)
                    val width = size.width.coerceAtLeast(1f)
                    val height = size.height.coerceAtLeast(1f)
                    val start = resolvedSource
                    scaleX = start.width.coerceAtLeast(1f) / width + (1f - start.width.coerceAtLeast(1f) / width) * fraction
                    scaleY = start.height.coerceAtLeast(1f) / height + (1f - start.height.coerceAtLeast(1f) / height) * fraction
                    translationX = (start.left - hostBounds.left) * (1f - fraction)
                    translationY = (start.top - hostBounds.top) * (1f - fraction)
                    transformOrigin = TransformOrigin(0f, 0f)
                    // 縮小された画面上でも起点の角丸がつぶれないよう半径を補正する。
                    shape = RoundedCornerShape((24f * (1f - fraction) / minOf(scaleX, scaleY).coerceAtLeast(0.01f)).dp)
                    clip = true
                }) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        val fraction = progress.value.coerceIn(0f, 1f)
                        val width = hostBounds.width.coerceAtLeast(1f)
                        val height = hostBounds.height.coerceAtLeast(1f)
                        val start = resolvedSource
                        val initialWidth = start.width.coerceAtLeast(1f)
                        val initialHeight = start.height.coerceAtLeast(1f)
                        val sx = initialWidth / width + (1f - initialWidth / width) * fraction
                        val sy = initialHeight / height + (1f - initialHeight / height) * fraction
                        scaleX = 1f / sx.coerceAtLeast(0.01f)
                        scaleY = 1f / sy.coerceAtLeast(0.01f)
                        translationX = -(start.left - hostBounds.left) * (1f - fraction) / sx.coerceAtLeast(0.01f)
                        translationY = -(start.top - hostBounds.top) * (1f - fraction) / sy.coerceAtLeast(0.01f)
                        transformOrigin = TransformOrigin(0f, 0f)
                        renderEffect = blurEffect
                        alpha = 1f - fraction
                    }.drawWithContent { drawLayer(backdrop) })
                    Box(Modifier.fillMaxSize().drawWithCache {
                        val reflection = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f)))
                        onDrawBehind {
                            val fraction = progress.value.coerceIn(0f, 1f)
                            drawRect(surfaceColor, alpha = 0.04f)
                            drawRect(reflection, alpha = 1f - fraction)
                        }
                    })
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        // アイコンと画面内容を同じ進行度で交差させ、逆操作でも表示を連続させる。
                        val value = ((progress.value - 0.45f) / 0.5f).coerceIn(0f, 1f)
                        alpha = if (icon == null) 1f else value * value * (3f - 2f * value)
                    }.padding(top = contentTopInset)) { content(current) }
                    if (icon != null) {
                        Box(Modifier.align(Alignment.Center).size(64.dp).clearAndSetSemantics { }.graphicsLayer {
                            val fraction = progress.value.coerceIn(0f, 1f)
                            val value = ((fraction - 0.45f) / 0.5f).coerceIn(0f, 1f)
                            alpha = 1f - value * value * (3f - 2f * value)
                            val start = resolvedSource
                            val windowScaleX = start.width.coerceAtLeast(1f) / hostBounds.width.coerceAtLeast(1f) +
                                (1f - start.width.coerceAtLeast(1f) / hostBounds.width.coerceAtLeast(1f)) * fraction
                            val windowScaleY = start.height.coerceAtLeast(1f) / hostBounds.height.coerceAtLeast(1f) +
                                (1f - start.height.coerceAtLeast(1f) / hostBounds.height.coerceAtLeast(1f)) * fraction
                            val glyph = sourceIcon?.bounds
                            val initialSize = glyph?.width ?: with(density) { 32.dp.toPx() }
                            val desiredSize = initialSize + (size.width - initialSize) * fraction
                            scaleX = desiredSize / size.width / windowScaleX.coerceAtLeast(.01f)
                            scaleY = desiredSize / size.height / windowScaleY.coerceAtLeast(.01f)
                            val initialCenter = glyph?.center ?: start.center
                            val centerX = initialCenter.x - hostBounds.left +
                                (hostBounds.width / 2f - (initialCenter.x - hostBounds.left)) * fraction
                            val centerY = initialCenter.y - hostBounds.top +
                                (hostBounds.height / 2f - (initialCenter.y - hostBounds.top)) * fraction
                            // 親の拡大と移動を相殺し、元のアイコンの位置から画面中央へつなぐ。
                            translationX = (centerX - (start.left - hostBounds.left) * (1f - fraction)) / windowScaleX.coerceAtLeast(.01f) - hostBounds.width / 2f
                            translationY = (centerY - (start.top - hostBounds.top) * (1f - fraction)) / windowScaleY.coerceAtLeast(.01f) - hostBounds.height / 2f
                        }, contentAlignment = Alignment.Center) {
                            val captured = sourceIcon?.layer
                            if (captured != null) {
                                Box(Modifier.fillMaxSize().drawWithContent {
                                    withTransform({
                                        scale(size.width / captured.size.width.coerceAtLeast(1),
                                            size.height / captured.size.height.coerceAtLeast(1), androidx.compose.ui.geometry.Offset.Zero)
                                    }) { drawLayer(captured) }
                                })
                            } else icon(current)
                        }
                    }
                }
            }
        }
    }
}
