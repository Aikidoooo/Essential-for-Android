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
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** ホームタブの軽いぼかしを全ガラスの基準にする。 */
internal object LiquidGlassStyle {
    const val blurStrength = 0.20f
    val blurRadius = (15f * blurStrength).dp
    val tint = listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))
    val edge = listOf(Color.White.copy(alpha = 0.48f), Color.White.copy(alpha = 0.10f))
}

internal class GlassBackdrop(val layer: GraphicsLayer, val origin: () -> Offset)
internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** 背景だけを一度共有し、各ガラスから自身と同じ座標を参照する。 */
@Composable
internal fun GlassBackdropScope(modifier: Modifier = Modifier, enabled: Boolean = true,
    background: @Composable BoxScope.() -> Unit, content: @Composable BoxScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val inherited = LocalGlassBackdrop.current
    val backdrop = remember(layer) { GlassBackdrop(layer) { origin } }
    CompositionLocalProvider(LocalGlassBackdrop provides if (enabled) backdrop else inherited) {
        Box(modifier) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }.drawWithContent {
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
internal fun Modifier.liquidGlass(shape: Shape): Modifier = composed {
    val backdrop = LocalGlassBackdrop.current
    val density = LocalDensity.current
    val sample = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val effect = remember(density) {
        if (Build.VERSION.SDK_INT >= 31) {
            val radius = with(density) { LiquidGlassStyle.blurRadius.toPx() }
            android.graphics.RenderEffect.createBlurEffect(radius, radius,
                android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else null
    }
    remember(sample, effect) { sample.renderEffect = effect; true }
    this.clip(shape).onGloballyPositioned { origin = it.positionInWindow() }
        .drawWithContent {
            if (backdrop != null) {
                sample.record {
                    translate(backdrop.origin().x - origin.x, backdrop.origin().y - origin.y) { drawLayer(backdrop.layer) }
                }
                drawLayer(sample)
            }
            drawContent()
        }
        .background(Brush.linearGradient(LiquidGlassStyle.tint))
        .border(1.dp, Brush.linearGradient(LiquidGlassStyle.edge), shape)
}

internal fun Modifier.liquidGlass(radius: Dp): Modifier = liquidGlass(RoundedCornerShape(radius))
