package jp.essential.app.ui

import android.animation.ValueAnimator
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

/** 縁の光の色、幅、強さと開閉時間を共通管理する。 */
internal data class EdgeGlowStyle(
    val speed: Float = 0.7f,
    val brightness: Float = 1.15f,
    val widthDp: Float = 2.1f,
    val blurDp: Float = 13f,
    val opacity: Float = 0.88f,
    val enterMillis: Int = 650,
    val exitMillis: Int = 450,
    val colors: List<Int> = listOf(0xFF278AFF.toInt(), 0xFF865CFF.toInt(), 0xFFED68E9.toInt(),
        0xFFFF668C.toInt(), 0xFFFF9A43.toInt(), 0xFFFFD977.toInt()),
) {
    init {
        require(colors.size == 6 && speed >= 0 && brightness >= 0 && widthDp > 0 && blurDp > 0)
        require(opacity in 0f..1f && enterMillis >= 0 && exitMillis >= 0)
    }
}

/** タッチを受け取らない全画面の光。非表示と画面停止中はフレーム更新を止める。 */
@Composable
internal fun SiriEdgeGlow(
    active: Boolean,
    modifier: Modifier = Modifier,
    style: EdgeGlowStyle = remember { EdgeGlowStyle() },
    forceCanvas: Boolean = false,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val density = LocalDensity.current.density
    val alpha = remember { Animatable(0f) }
    val time = remember { mutableFloatStateOf(0f) }
    var reduced by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(view) {
        val resolver = view.context.contentResolver
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = !ValueAnimator.areAnimatorsEnabled() }
        }
        resolver.registerContentObserver(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(active, reduced, style.enterMillis, style.exitMillis) {
        alpha.animateTo(if (active) 1f else 0f, tween(if (reduced) 0 else if (active) style.enterMillis else style.exitMillis))
    }
    LaunchedEffect(active, reduced, lifecycle) {
        if (reduced) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (active || alpha.value > 0f) {
                withInfiniteAnimationFrameNanos { frame ->
                    if (previous != 0L) time.floatValue += ((frame - previous) / 1_000_000_000f).coerceAtMost(.05f)
                    previous = frame
                }
            }
        }
    }
    val renderer = remember(style, density, forceCanvas) {
        if (Build.VERSION.SDK_INT >= 33 && !forceCanvas)
            runCatching<EdgeGlowRenderer> { ShaderEdgeGlow(style, density) }.getOrElse { CanvasEdgeGlow(style, density) }
        else CanvasEdgeGlow(style, density)
    }
    Canvas(modifier.fillMaxSize().testTag("settings-edge-glow")) {
        if (alpha.value <= 0f) return@Canvas
        val radius = if (Build.VERSION.SDK_INT >= 31) {
            view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
                ?.takeIf { it > 0f } ?: (28f * density)
        } else 28f * density
        drawIntoCanvas { renderer.draw(it.nativeCanvas, size.width, size.height,
            radius.coerceAtMost(min(size.width, size.height) / 4), time.floatValue, alpha.value) }
    }
}

private interface EdgeGlowRenderer {
    fun draw(canvas: android.graphics.Canvas, width: Float, height: Float, radius: Float, time: Float, alpha: Float)
}

@RequiresApi(33)
private class ShaderEdgeGlow(style: EdgeGlowStyle, density: Float) : EdgeGlowRenderer {
    private val shader = RuntimeShader(EDGE_GLOW_SHADER)
    private val paint = Paint().apply { isAntiAlias = true; this.shader = this@ShaderEdgeGlow.shader }
    init {
        shader.setFloatUniform("coreWidth", style.widthDp * density)
        shader.setFloatUniform("haloWidth", style.blurDp * density)
        shader.setFloatUniform("speed", style.speed)
        shader.setFloatUniform("strength", style.brightness)
        shader.setFloatUniform("opacity", style.opacity)
        style.colors.forEachIndexed { index, color ->
            shader.setFloatUniform("color$index", android.graphics.Color.red(color) / 255f,
                android.graphics.Color.green(color) / 255f, android.graphics.Color.blue(color) / 255f)
        }
    }
    override fun draw(canvas: android.graphics.Canvas, width: Float, height: Float, radius: Float, time: Float, alpha: Float) {
        shader.setFloatUniform("extent", width, height)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("reveal", alpha)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}

// 縁までの距離へガウス分布を重ね、位置と時間が異なる波で光を流す。
internal const val EDGE_GLOW_SHADER = """
uniform float2 extent;
uniform float radius;
uniform float time;
uniform float speed;
uniform float coreWidth;
uniform float haloWidth;
uniform float strength;
uniform float opacity;
uniform float reveal;
uniform float3 color0;
uniform float3 color1;
uniform float3 color2;
uniform float3 color3;
uniform float3 color4;
uniform float3 color5;
float3 palette(float phase) {
    float p = fract(phase) * 6.0;
    float f = smoothstep(0.0, 1.0, fract(p));
    if (p < 1.0) return mix(color0, color1, f);
    if (p < 2.0) return mix(color1, color2, f);
    if (p < 3.0) return mix(color2, color3, f);
    if (p < 4.0) return mix(color3, color4, f);
    if (p < 5.0) return mix(color4, color5, f);
    return mix(color5, color0, f);
}
half4 main(float2 p) {
    float2 q = p - extent * 0.5;
    float2 d = abs(q) - (extent * 0.5 - radius);
    float signedDistance = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
    if (signedDistance > 0.0) return half4(0.0);
    float edge = max(-signedDistance - coreWidth * 0.5, 0.0);
    if (edge > haloWidth * 4.5) return half4(0.0);
    float2 uv = q / (extent * 0.5);
    float angle = atan(uv.y, uv.x);
    float t = time * speed;
    float phase = angle / 6.2831853 + 0.045 * t
        + 0.17 * sin(angle * 2.0 - t * 0.37)
        + 0.075 * sin(angle * 3.0 + t * 0.23);
    float pulse = 0.82 + 0.11 * sin(t * 1.31 + angle * 3.0)
        + 0.07 * sin(t * 0.71 - angle * 5.0);
    float spread = haloWidth * (0.85 + 0.15 * sin(angle * 4.0 + t * 0.53))
        * (0.6 + 0.4 * reveal);
    float core = exp(-0.5 * pow(edge / coreWidth, 2.0));
    float halo = exp(-0.5 * pow(edge / spread, 2.0));
    float atmosphere = exp(-0.5 * pow(edge / (spread * 2.1), 2.0));
    float a = clamp((core * 0.78 + halo * 0.48 + atmosphere * 0.12)
        * pulse * strength * opacity * reveal, 0.0, 1.0);
    float3 color = mix(palette(phase), float3(1.0), core * 0.24);
    return half4(color * a, a);
}
"""

/** 古いOSでは、再利用する縁のパスと多層ストロークで柔らかい光を描く。 */
private class CanvasEdgeGlow(private val style: EdgeGlowStyle, private val density: Float) : EdgeGlowRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE }
    private val boundary = Path()
    private val rect = RectF()
    private val colors = IntArray(49)
    private var lastWidth = 0f
    private var lastHeight = 0f
    private var lastRadius = 0f
    private var lastTime = Float.NaN
    private val widths = FloatArray(18) { index -> 5.6f - index * .3f }
    private val alphas = FloatArray(18).apply {
        var previous = 0f
        indices.forEach { index ->
            val target = .48f * exp(-.5f * (widths[index] * .5f).pow(2))
            this[index] = (target - previous) / (1f - previous)
            previous = target
        }
    }
    override fun draw(canvas: android.graphics.Canvas, width: Float, height: Float, radius: Float, time: Float, alpha: Float) {
        val resized = width != lastWidth || height != lastHeight || radius != lastRadius
        if (resized) {
            lastWidth = width; lastHeight = height; lastRadius = radius
            boundary.reset(); rect.set(0f, 0f, width, height)
            boundary.addRoundRect(rect, radius, radius, Path.Direction.CW)
        }
        if (time != lastTime || resized) {
            lastTime = time
            for (index in 0 until colors.lastIndex) {
                val angle = index * 2f * PI.toFloat() / colors.lastIndex
                val normalizedAngle = atan2(sin(angle) / height, cos(angle) / width)
                val t = time * style.speed
                val phase = normalizedAngle / (2f * PI.toFloat()) + .045f * t + .17f * sin(2f * normalizedAngle - .37f * t) + .075f * sin(3f * normalizedAngle + .23f * t)
                val position = ((phase % 1f + 1f) % 1f) * 6f
                val start = position.toInt().coerceAtMost(5)
                val fraction = position - start
                val mix = fraction * fraction * (3f - 2f * fraction)
                val first = style.colors[start]; val second = style.colors[(start + 1) % 6]
                fun channel(shift: Int): Int = ((first shr shift and 255) * (1f - mix) + (second shr shift and 255) * mix).roundToInt()
                colors[index] = android.graphics.Color.rgb(channel(16), channel(8), channel(0))
            }
            colors[colors.lastIndex] = colors[0]
            // Canvasの勾配は色の更新APIがないため、色が動くフレームだけ一つ更新する。
            // 回転行列は使わず、各位置の色を独立した波で変形する。
            paint.shader = android.graphics.SweepGradient(width / 2f, height / 2f, colors, null)
        }
        val checkpoint = canvas.save()
        canvas.clipPath(boundary)
        rect.set(style.widthDp * density * .5f, style.widthDp * density * .5f,
            width - style.widthDp * density * .5f, height - style.widthDp * density * .5f)
        val pulse = .88f + .12f * sin(time * style.speed * 1.31f)
        for (layer in widths.indices) {
            paint.alpha = (255 * alphas[layer] * pulse * alpha * style.opacity * style.brightness).coerceIn(0f, 255f).roundToInt()
            paint.strokeWidth = style.blurDp * density * widths[layer] * (.88f + .12f * sin(time * style.speed * .53f)) * (.6f + .4f * alpha)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        paint.alpha = (255 * .72f * alpha * style.opacity * style.brightness).coerceIn(0f, 255f).roundToInt()
        paint.strokeWidth = style.widthDp * density * 2f
        canvas.drawRoundRect(rect, radius, radius, paint)
        canvas.restoreToCount(checkpoint)
    }
}

/** Android Studioのインタラクティブプレビューで起動・停止を確認できる。 */
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
internal fun EdgeGlowPreview() {
    var active by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color(0xFF0C1022))) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Essential Universal", color = Color.White)
            Button(onClick = { active = !active }, modifier = Modifier.testTag("edge-glow-toggle")) {
                Text(if (active) "光を停止" else "光を起動")
            }
        }
        SiriEdgeGlow(active)
    }
}
