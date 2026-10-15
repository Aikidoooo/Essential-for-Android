package jp.essential.app.ui

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** 装飾のフレーム更新は画面が前面にある間だけ行う。値は描画時に読む。 */
@Composable
internal fun rememberDecorativePhase(
    durationMillis: Int,
    initialValue: Float = 0f,
    targetValue: Float = 1f,
    repeatMode: RepeatMode = RepeatMode.Restart,
    enabled: Boolean = true,
): State<Float> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val lastValue = remember { mutableFloatStateOf(initialValue) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!active || !enabled) return lastValue
    val transition = rememberInfiniteTransition(label = "前面の装飾")
    val phase = transition.animateFloat(initialValue, targetValue,
        infiniteRepeatable(tween(durationMillis, easing = LinearEasing), repeatMode), label = "装飾の周期")
    DisposableEffect(phase) { onDispose { lastValue.floatValue = phase.value } }
    return phase
}
