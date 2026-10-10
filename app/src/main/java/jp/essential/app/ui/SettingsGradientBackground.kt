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
    val base = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().drawWithCache {
        val radius = size.maxDimension * 0.85f
        val blue = Brush.radialGradient(listOf(Color(0xFF284BDB).copy(alpha = if (dark) 0.42f else 0.14f), Color.Transparent), Offset.Zero, radius)
        val purple = Brush.radialGradient(listOf(Color(0xFF9953D6).copy(alpha = if (dark) 0.34f else 0.12f), Color.Transparent), Offset.Zero, radius)
        onDrawBehind {
            // 進行度を描画時だけ読み、グラデーションやレイアウトをフレームごとに作り直さない。
            val value = phase.value
            drawRect(base)
            translate(size.width * (0.2f + 0.18f * sin(value)), size.height * (0.28f + 0.12f * cos(value))) {
                drawCircle(blue, radius, Offset.Zero)
            }
            translate(size.width * (0.82f + 0.16f * cos(value)), size.height * (0.48f + 0.16f * sin(value))) {
                drawCircle(purple, radius, Offset.Zero)
            }
        }
    })
}
