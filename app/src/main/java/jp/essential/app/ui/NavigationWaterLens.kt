package jp.essential.app.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// カプセルの縁で背面の画素を曲げ、動いている間だけ水滴の屈折を強める。
private const val WATER_LENS_SHADER = """
uniform shader contents;
uniform float2 center;
uniform float2 extent;
uniform float motion;
uniform float grip;
half4 main(float2 p) {
    float2 q = p - center;
    float r = extent.y;
    float2 axis = float2(clamp(q.x, -extent.x + r, extent.x - r), 0.0);
    float2 radial = q - axis;
    float distance = length(radial);
    float2 normal = radial / max(distance, 0.001);
    float edge = smoothstep(0.38 * r, r, distance);
    float bend = edge * edge * r * (0.18 * motion + 0.10 * grip);
    float2 samplePoint = p + normal * bend;
    half4 base = contents.eval(samplePoint);
    half4 red = contents.eval(samplePoint - normal * motion * 1.4);
    half4 blue = contents.eval(samplePoint + normal * motion * 1.4);
    base.r = red.r;
    base.b = blue.b;
    return base;
}
"""

/** つかんだ水滴を1.2倍にし、背面だけを内側へ屈折させる。 */
@Composable
internal fun NavigationWaterLens(backdrop: GraphicsLayer, origin: Offset, position: () -> Float,
    expansion: () -> Float, motion: () -> Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(WATER_LENS_SHADER) }.getOrNull() else null
    }
    // ぼかしと屈折の効果を先に用意し、指の移動や拡大の各フレームで作り直さない。
    val effects = remember(density, shader) {
        List(21) { step ->
            if (Build.VERSION.SDK_INT >= 31) {
                val radius = with(density) { (14f - 13.4f * step / 20f).dp.toPx() }
                val blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                if (Build.VERSION.SDK_INT >= 33 && shader != null)
                    RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "contents"), blur).asComposeRenderEffect()
                else blur.asComposeRenderEffect()
            } else null
        }
    }
    Canvas(modifier.layout { measurable, constraints ->
        // 拡大した水滴がバーの上下左右で切れないよう、描画領域だけを広げる。
        val margin = 20.dp.roundToPx()
        val expandedConstraints = constraints.copy(minWidth = constraints.maxWidth + margin * 2,
            maxWidth = constraints.maxWidth + margin * 2, minHeight = constraints.maxHeight + margin * 2,
            maxHeight = constraints.maxHeight + margin * 2)
        val placeable = measurable.measure(expandedConstraints)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-margin, -margin) }
    }.drawWithContent {
        val expansion = expansion()
        val position = position()
        val margin = 20.dp.toPx()
        val cellWidth = (size.width - margin * 2) / 3
        val width = (cellWidth - 8.dp.toPx()) * (1f + .2f * expansion)
        val height = (size.height - margin * 2 - 10.dp.toPx()) * (1f + .2f * expansion)
        val left = margin + cellWidth * (position + .5f) - width / 2
        val top = (size.height - height) / 2
        val path = Path().apply { addRoundRect(RoundRect(left, top, left + width, top + height, CornerRadius(height / 2))) }
        clipPath(path) { this@drawWithContent.drawContent() }
    }.graphicsLayer {
        val expansion = expansion()
        val position = position()
        renderEffect = effects[kotlin.math.round(expansion * 20).toInt().coerceIn(0, 20)]
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            val margin = 20.dp.toPx()
            val cellWidth = (size.width - margin * 2) / 3
            val width = (cellWidth - 8.dp.toPx()) * (1f + .2f * expansion)
            val height = (size.height - margin * 2 - 10.dp.toPx()) * (1f + .2f * expansion)
            shader.setFloatUniform("center", margin + cellWidth * (position + .5f), size.height / 2)
            shader.setFloatUniform("extent", width / 2, height / 2)
            shader.setFloatUniform("motion", motion())
            shader.setFloatUniform("grip", expansion)
        }
    }) {
        translate(origin.x + 20.dp.toPx(), origin.y + 20.dp.toPx()) { drawLayer(backdrop) }
    }
}
