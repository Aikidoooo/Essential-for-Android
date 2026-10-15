package jp.essential.app.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import jp.essential.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** 色鉛筆画の顔を安定させ、髪・衣装・翼の外側へ緩やかな風を伝える。 */
@Composable
internal fun RulerOfDeathBackground(dark: Boolean, motionEnabled: Boolean) {
    val artwork = ImageBitmap.imageResource(R.drawable.ruler_of_death_pencil_v1)
    val bitmap = remember(artwork) { artwork.asAndroidBitmap() }
    val eyeArtwork = ImageBitmap.imageResource(R.drawable.ruler_eye_pencil_v1)
    val eyeBitmap = remember(eyeArtwork) { eyeArtwork.asAndroidBitmap() }
    val eye = remember { RulerEye() }
    val context = LocalContext.current
    var reducedMotion by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion = !ValueAnimator.areAnimatorsEnabled()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val phase = rememberDecorativePhase(3_200, enabled = motionEnabled && !reducedMotion)
    val dustPhase = rememberDecorativePhase(24_000, enabled = motionEnabled && !reducedMotion)
    val blinkPhase = rememberDecorativePhase(5_000, enabled = motionEnabled && !reducedMotion)
    val gazePhase = rememberDecorativePhase(20_000, enabled = motionEnabled && !reducedMotion)
    val dustPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(0f, 0f, 1f,
                intArrayOf(0xFFFFE5DB.toInt(), 0xFFFF6359.toInt(), 0xB8FF172E.toInt(), 0x00FF172E),
                floatArrayOf(0f, .12f, .36f, 1f), android.graphics.Shader.TileMode.CLAMP)
        }
    }
    // 配列と描画用Paintはフレームごとに生成しない。位相は描画内だけで読む。
    val columns = 20
    val rows = 32
    val vertices = remember { FloatArray((columns + 1) * (rows + 1) * 2) }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }
    Canvas(Modifier.fillMaxSize()) {
        val scale = min(size.width * .98f / bitmap.width, size.height * .90f / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        val angle = phase.value * (2 * PI).toFloat()
        val breath = sin(angle)
        for (row in 0..rows) {
            val y = row.toFloat() / rows
            for (column in 0..columns) {
                val x = column.toFloat() / columns
                val edgeWeight = abs(x - .5f) * 2f
                // 中央の顔はほぼ固定。端の髪と裾ほど大きく、位相をずらして揺らす。
                val wind = sin(angle + y * 2.4f) * width * .008f * edgeWeight * edgeWeight
                val hem = sin(angle - x * 2f) * height * .003f * y * y
                val index = (row * (columns + 1) + column) * 2
                vertices[index] = left + x * width + wind
                vertices[index + 1] = top + y * height + breath * height * .0015f + hem
            }
        }
        paint.alpha = if (dark) 220 else 180
        // 人物より先に描き、頭の少し上に置いて翼と髪の背後へ収める。
        eye.draw(drawContext.canvas.nativeCanvas, eyeBitmap, size.width * .5f,
            top + height * .155f, size.width * .72f, blinkPhase.value, gazePhase.value, dark)
        drawContext.canvas.nativeCanvas.drawBitmapMesh(bitmap, columns, rows, vertices, 0, null, 0, paint)
        // 64粒の半数は下端から2/3の高さまでに消す。残りは上端で消す。
        // 単位サイズの発光Shaderを共用し、Blurや粒ごとのオブジェクト生成を避ける。
        val canvas = drawContext.canvas.nativeCanvas
        for (index in 0 until 64) {
            val progress = (dustPhase.value * (1 + index % 2) + index * .618034f) % 1f
            val opacity = rulerDustOpacity(progress, index < 32)
            if (opacity <= 0f) continue
            val seed = (index * .7548777f) % 1f
            val drift = sin(progress * (2 * PI).toFloat() + index) * size.width * .022f
            val x = size.width * (.035f + seed * .93f) + drift
            val y = size.height * (1f - progress)
            val radius = size.width * (.006f + (index % 5) * .0015f)
            dustPaint.alpha = (opacity * if (dark) 220f else 185f).toInt()
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(radius, radius)
            canvas.drawCircle(0f, 0f, 1f, dustPaint)
            canvas.restore()
        }
    }
}

/** 出現と消滅を滑らかに補間し、短命の粒は指定の高さで完全に消す。 */
internal fun rulerDustOpacity(progress: Float, shortLived: Boolean): Float {
    fun smooth(value: Float): Float {
        val bounded = value.coerceIn(0f, 1f)
        return bounded * bounded * (3f - 2f * bounded)
    }
    val end = if (shortLived) 2f / 3f else 1f
    val fadeStart = if (shortLived) .54f else .86f
    return smooth(progress / .07f) * (1f - smooth((progress - fadeStart) / (end - fadeStart)))
}
