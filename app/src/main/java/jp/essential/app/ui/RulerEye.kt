package jp.essential.app.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 描画資源を保持し、眼の開閉と赤い虹彩だけを動かす。 */
internal class RulerEye {
    private val aperture = Path().apply {
        moveTo(-.20f, 0f)
        cubicTo(-.10f, -.20f, .10f, -.20f, .20f, 0f)
        cubicTo(.10f, .20f, -.10f, .20f, -.20f, 0f)
        close()
    }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC9C1.toInt() }
    private val iris = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(0xFFFFF0DA.toInt(), 0xFFFF313B.toInt(), 0xFF9C001C.toInt(), 0xFF300714.toInt()),
            floatArrayOf(0f, .14f, .62f, 1f), Shader.TileMode.CLAMP)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF660D24.toInt(); style = Paint.Style.STROKE; strokeWidth = .012f
    }
    private val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFDFCA.toInt() }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private val gazeX = floatArrayOf(0f, -.65f, .60f, .25f, -.25f)
    private val gazeY = floatArrayOf(0f, -.20f, .10f, -.30f, .25f)

    fun draw(canvas: Canvas, artwork: android.graphics.Bitmap, centerX: Float, centerY: Float,
             width: Float, blinkPhase: Float, gazePhase: Float, dark: Boolean) {
        val height = width * artwork.height / artwork.width
        val openness = rulerEyeOpenness(blinkPhase)
        val position = (gazePhase * 5f).coerceAtMost(4.99999f)
        val index = position.toInt()
        val previous = (index + 4) % 5
        // 4秒ごとの視線変更は約600msで滑らかに移り、残りは視線を保持する。
        val progress = ((position - index) / .15f).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        val x = gazeX[previous] + (gazeX[index] - gazeX[previous]) * eased
        val y = gazeY[previous] + (gazeY[index] - gazeY[previous]) * eased
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(width, height * openness)
        white.alpha = if (dark) 195 else 165
        canvas.drawPath(aperture, white)
        canvas.save()
        canvas.clipPath(aperture)
        canvas.translate(x * .052f, y * .050f)
        canvas.scale(.064f, .064f * width / height)
        canvas.drawCircle(0f, 0f, 1f, iris)
        for (index in 0 until 32) {
            val angle = index * (2 * PI / 32).toFloat()
            canvas.drawLine(cos(angle) * .26f, sin(angle) * .26f,
                cos(angle) * .94f, sin(angle) * .94f, line)
        }
        canvas.drawCircle(0f, 0f, .10f, glint)
        canvas.drawRect(-.025f, -.48f, .025f, .48f, glint)
        canvas.drawRect(-.40f, -.025f, .40f, .025f, glint)
        canvas.restore()
        destination.set(-.5f, -.5f, .5f, .5f)
        framePaint.alpha = if (dark) 225 else 190
        canvas.drawBitmap(artwork, null, destination, framePaint)
        canvas.restore()
    }
}

/** 5秒周期の末尾約320msだけ閉じて開く。閉じ切った瞬間も輪郭を残す。 */
internal fun rulerEyeOpenness(phase: Float): Float {
    if (phase < .936f) return 1f
    val progress = ((phase - .936f) / .064f).coerceIn(0f, 1f)
    return (1f - sin(progress * PI).toFloat()).coerceAtLeast(.035f)
}
