package jp.essential.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 背景の座標を取得できるComposeの変形で、一覧の端に控えめな伸縮を与える。 */
internal fun Modifier.elasticListEdges(): Modifier = composed {
    val scope = rememberCoroutineScope()
    val limit = with(LocalDensity.current) { 72.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val connection = remember(limit) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                settling?.cancel()
                if (pull * available.y >= 0f || pull == 0f) return Offset.Zero
                val consumed = if (pull > 0f) available.y.coerceAtLeast(-pull / .3f)
                    else available.y.coerceAtMost(-pull / .3f)
                pull += consumed * .3f
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                settling?.cancel()
                // 引く距離が増えるほど抵抗を強め、端の変形を過剰にしない。
                pull = (pull + available.y * .3f * (1f - abs(pull) / limit)).coerceIn(-limit, limit)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (abs(pull) < .01f) return Velocity.Zero
                settling?.cancel()
                val start = pull
                settling = scope.launch {
                    Animatable(start).animateTo(0f, spring(dampingRatio = .9f, stiffness = 420f),
                        initialVelocity = available.y * .15f) { pull = value }
                    pull = 0f
                }
                return available
            }
        }
    }
    this.nestedScroll(connection).graphicsLayer {
        val displacement = pull
        scaleY = 1f + (abs(displacement) / size.height.coerceAtLeast(1f)).coerceAtMost(.05f)
        transformOrigin = TransformOrigin(.5f, if (displacement >= 0f) 0f else 1f)
        translationY = displacement * .25f
    }
}
