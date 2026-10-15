package jp.essential.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.essential.app.ui.theme.LocalEssentialDark
import kotlin.math.cos
import kotlin.math.sin

/** 設定の背面だけをゆっくり動かし、前面の配置と操作を変えない。 */
@Composable
internal fun SettingsGradientBackground() {
    val owner = LocalLifecycleOwner.current
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val phase = if (active) {
        val transition = rememberInfiniteTransition(label = "設定背景の流れ")
        transition.animateFloat(0f, (Math.PI * 2).toFloat(),
            infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "設定背景の周期")
    } else remember { mutableFloatStateOf(0f) }
    val dark = LocalEssentialDark.current
    val base = if (dark) MaterialTheme.colorScheme.background else Color(0xFFFBEFF8)
    Box(Modifier.fillMaxSize().drawWithCache {
        val radius = size.maxDimension * 0.85f
        val blue = Brush.radialGradient(listOf(Color(0xFF284BDB).copy(alpha = if (dark) 0.42f else 0.14f), Color.Transparent), Offset.Zero, radius)
        val purple = Brush.radialGradient(listOf(Color(0xFF9953D6).copy(alpha = if (dark) 0.34f else 0.12f), Color.Transparent), Offset.Zero, radius)
        // 参照画像の淡いラベンダー、中央の桜色、右下の水色を広い範囲で重ねる。
        val lightWash = Brush.verticalGradient(listOf(Color(0xFFE9E8FC), Color(0xFFFBEAF5), Color(0xFFF8F3FA)))
        val pink = Brush.radialGradient(listOf(Color(0xFFF7CDDF).copy(alpha = .72f), Color.Transparent), Offset.Zero, size.maxDimension * .58f)
        val lavender = Brush.radialGradient(listOf(Color(0xFFDAD7F5).copy(alpha = .65f), Color.Transparent), Offset.Zero, size.maxDimension * .52f)
        val sky = Brush.radialGradient(listOf(Color(0xFFCADBFF).copy(alpha = .85f), Color.Transparent), Offset.Zero, size.maxDimension * .65f)
        onDrawBehind {
            // 進行度を描画時だけ読み、グラデーションやレイアウトをフレームごとに作り直さない。
            val value = phase.value
            drawRect(base)
            if (!dark) {
                drawRect(lightWash)
                translate(size.width * (.55f + .035f * sin(value)), size.height * .15f) {
                    drawCircle(lavender, size.maxDimension * .52f, Offset.Zero)
                }
                translate(size.width * (.42f + .035f * cos(value)), size.height * (.52f + .025f * sin(value))) {
                    drawCircle(pink, size.maxDimension * .58f, Offset.Zero)
                }
                translate(size.width * 1.04f, size.height * (.82f + .025f * cos(value))) {
                    drawCircle(sky, size.maxDimension * .65f, Offset.Zero)
                }
                return@onDrawBehind
            }
            translate(size.width * (0.2f + 0.18f * sin(value)), size.height * (0.28f + 0.12f * cos(value))) {
                drawCircle(blue, radius, Offset.Zero)
            }
            translate(size.width * (0.82f + 0.16f * cos(value)), size.height * (0.48f + 0.16f * sin(value))) {
                drawCircle(purple, radius, Offset.Zero)
            }
        }
    })
}
