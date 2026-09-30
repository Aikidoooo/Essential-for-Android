package jp.essential.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.sin

/** 泡が流れるレールを、アプリ共通の値入力バーとして表示する。 */
@Composable
internal fun EssentialBubblySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    val start = valueRange.start
    val end = valueRange.endInclusive
    val fraction = ((value - start) / (end - start).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(stiffness = 850f, dampingRatio = 0.82f),
        label = "共通バーの位置",
    )
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    Box(
        modifier = modifier.height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                setProgress { requested ->
                    if (!enabled) false else {
                        currentOnValueChange(requested.coerceIn(start, end))
                        true
                    }
                }
            }
            .pointerInput(start, end, steps, enabled) {
                detectTapGestures { offset ->
                    if (enabled) {
                        val position = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val snapped = if (steps > 0) (position * (steps + 1)).roundToInt().toFloat() / (steps + 1) else position
                        currentOnValueChange(start + snapped * (end - start))
                    }
                }
            }
            .pointerInput(start, end, steps, enabled) {
                fun updatePosition(x: Float, width: Int) {
                    if (!enabled) return
                    val position = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val snapped = if (steps > 0) (position * (steps + 1)).roundToInt().toFloat() / (steps + 1) else position
                    currentOnValueChange(start + snapped * (end - start))
                }
                detectDragGestures(
                    onDragStart = { updatePosition(it.x, size.width) },
                    onDrag = { change, _ ->
                        updatePosition(change.position.x, size.width)
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BubblySliderRail(
            fraction = animatedFraction,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** レールの終端と白いノブを同じピクセル座標から描く。 */
@Composable
private fun BubblySliderRail(fraction: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "スライダーの泡")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "スライダーの泡移動",
    )
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f)
    val bubbles = remember {
        listOf(0.04f to 0.16f, 0.18f to 0.10f, 0.31f to 0.13f, 0.47f to 0.08f,
            0.63f to 0.12f, 0.78f to 0.07f, 0.92f to 0.11f)
    }
    Canvas(modifier.height(48.dp)) {
        val diameter = 28.dp.toPx()
        val radius = diameter / 2f
        val railTop = (size.height - diameter) / 2f
        val thumbCenter = radius + (size.width - diameter).coerceAtLeast(0f) * fraction
        val activeRight = (thumbCenter + radius).coerceIn(0f, size.width)
        val corner = CornerRadius(radius)
        drawRoundRect(inactiveColor, topLeft = Offset(0f, railTop),
            size = Size(size.width, diameter), cornerRadius = corner)
        drawRoundRect(Color(0xFFB77BF4), topLeft = Offset(0f, railTop),
            size = Size(activeRight, diameter), cornerRadius = corner)
        clipRect(left = 0f, top = railTop, right = activeRight, bottom = railTop + diameter) {
            bubbles.forEachIndexed { index, (offset, sizeFactor) ->
                val travel = (phase + offset) % 1f
                val x = size.width * (1f - travel)
                val wave = sin(travel * Math.PI * 2.0 + index * 1.7).toFloat()
                val bubbleRadius = diameter * (sizeFactor +
                    sin(travel * Math.PI * 2.0 + index).toFloat() * 0.025f)
                val alpha = (0.16f + (1f - travel) * 0.38f).coerceIn(0f, 0.58f)
                drawCircle(Color.White.copy(alpha = alpha), bubbleRadius.coerceAtLeast(1f),
                    Offset(x, size.height / 2f + wave * diameter * 0.18f))
            }
        }
        drawCircle(Color.White, radius, Offset(thumbCenter, size.height / 2f))
    }
}

/** 泡と紫色レールを、処理状況の横長バーにも適用する。 */
@Composable
internal fun EssentialBubblyProgressBar(
    progress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "共通進捗バー")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "共通バーの泡",
    )
    val indeterminate by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing), RepeatMode.Reverse),
        label = "共通バーの進捗",
    )
    val fraction = (progress ?: indeterminate).coerceIn(0f, 1f)
    BubblyRail(fraction = fraction, phaseOverride = phase, modifier = modifier.height(28.dp))
}

@Composable
private fun BubblyRail(
    fraction: Float,
    modifier: Modifier,
    phaseOverride: Float? = null,
) {
    val transition = rememberInfiniteTransition(label = "共通バーの泡レール")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "共通バーの泡移動",
    )
    val actualPhase = phaseOverride ?: phase
    val bubbles = remember {
        listOf(0.04f to 0.16f, 0.18f to 0.10f, 0.31f to 0.13f, 0.47f to 0.08f, 0.63f to 0.12f, 0.78f to 0.07f, 0.92f to 0.11f)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val railHeight = 28.dp
        Box(
            modifier = Modifier.fillMaxWidth().height(railHeight)
                .clip(RoundedCornerShape(railHeight / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f)),
        ) {
            Box(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction)
                    .background(Color(0xFFB77BF4)),
            )
            Canvas(Modifier.fillMaxSize()) {
                clipRect(right = size.width * fraction) {
                    bubbles.forEachIndexed { index, (offset, sizeFactor) ->
                        val travel = (actualPhase + offset) % 1f
                        val x = size.width * (1f - travel)
                        val wave = sin(travel * Math.PI * 2.0 + index * 1.7).toFloat()
                        val radius = size.height * (sizeFactor + sin(travel * Math.PI * 2.0 + index).toFloat() * 0.025f)
                        val alpha = (0.16f + (1f - travel) * 0.38f).coerceIn(0f, 0.58f)
                        drawCircle(Color.White.copy(alpha = alpha), radius.coerceAtLeast(1f), androidx.compose.ui.geometry.Offset(x, size.height * (0.5f + wave * 0.18f)))
                    }
                }
            }
        }
    }
}
