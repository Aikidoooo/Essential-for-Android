package jp.essential.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.essential.app.ui.theme.LocalEssentialDark

/** ホームタブの軽いぼかしを全ガラスの基準にする。 */
internal object LiquidGlassStyle {
    const val blurStrength = 0.20f
    val blurRadius = (15f * blurStrength).dp
    val tint = listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))
    // ダークだけに薄いニュートラルグレーを重ね、透明感を保ちながら彩度を少し抑える。
    val darkTint = listOf(Color(0xFF2E2E2E).copy(alpha = 0.432f), Color(0xFF2E2E2E).copy(alpha = 0.189f))
    val edge = listOf(Color.White.copy(alpha = 0.48f), Color.White.copy(alpha = 0.10f))
}

internal class GlassBackdrop(val layer: GraphicsLayer, val origin: () -> Offset)
internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** 背景だけを一度共有し、各ガラスから自身と同じ座標を参照する。 */
@Composable
internal fun GlassBackdropScope(modifier: Modifier = Modifier, enabled: Boolean = true,
    background: @Composable BoxScope.() -> Unit, content: @Composable BoxScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var layoutOrigin by remember { mutableStateOf(Offset.Zero) }
    val inherited = LocalGlassBackdrop.current
    val backdrop = remember(layer) { GlassBackdrop(layer) {
        val lastOrigin = layoutOrigin
        coordinates?.takeIf { it.isAttached }?.positionInWindow() ?: lastOrigin
    } }
    CompositionLocalProvider(LocalGlassBackdrop provides if (enabled) backdrop else inherited) {
        Box(modifier) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates = it; layoutOrigin = it.positionInWindow() }.drawWithContent {
                if (enabled) {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                } else drawContent()
            }, content = background)
            content()
        }
    }
}

/** 子の文字をぼかさず、背景のサンプルだけへ20%のぼかしを適用する。 */
internal fun Modifier.liquidGlass(shape: Shape, drawBorder: Boolean = true): Modifier = composed {
    val dark = LocalEssentialDark.current
    val tint = remember(dark) { Brush.linearGradient(if (dark) LiquidGlassStyle.darkTint else LiquidGlassStyle.tint) }
    val backdrop = LocalGlassBackdrop.current
    val density = LocalDensity.current
    val sample = rememberGraphicsLayer()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var layoutOrigin by remember { mutableStateOf(Offset.Zero) }
    val effect = remember(density) {
        if (Build.VERSION.SDK_INT >= 31) {
            val radius = with(density) { LiquidGlassStyle.blurRadius.toPx() }
            android.graphics.RenderEffect.createBlurEffect(radius, radius,
                android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else null
    }
    remember(sample, effect) { sample.renderEffect = effect; true }
    this.clip(shape).onGloballyPositioned { coordinates = it; layoutOrigin = it.positionInWindow() }
        .drawWithContent {
            if (backdrop != null) {
                // レイアウト時の座標を固定せず、親の移動を含む現在位置で背景を切り出す。
                // スクロールで座標オブジェクトが同一でも、位置変更を描画の無効化へ伝える。
                val lastOrigin = layoutOrigin
                val origin = coordinates?.takeIf { it.isAttached }?.positionInWindow() ?: lastOrigin
                val backdropOrigin = backdrop.origin()
                // 親の伸縮とカード押下の拡縮を逆補正し、ガラス越しの絵を画面座標へ固定する。
                val liveCoordinates = coordinates?.takeIf { it.isAttached }
                val scaleX = liveCoordinates?.let { (it.localToWindow(Offset(1f, 0f)).x - origin.x).coerceAtLeast(.01f) } ?: 1f
                val scaleY = liveCoordinates?.let { (it.localToWindow(Offset(0f, 1f)).y - origin.y).coerceAtLeast(.01f) } ?: 1f
                sample.record {
                    withTransform({
                        translate((backdropOrigin.x - origin.x) / scaleX, (backdropOrigin.y - origin.y) / scaleY)
                        scale(1f / scaleX, 1f / scaleY, pivot = Offset.Zero)
                    }) { drawLayer(backdrop.layer) }
                }
                drawLayer(sample)
            }
            drawContent()
        }
        .background(tint)
        .then(if (drawBorder) Modifier.border(1.dp, Brush.linearGradient(LiquidGlassStyle.edge), shape) else Modifier)
}

internal fun Modifier.liquidGlass(radius: Dp): Modifier = liquidGlass(RoundedCornerShape(radius))
