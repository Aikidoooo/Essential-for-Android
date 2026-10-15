package jp.essential.app.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** 背景鑑賞・タブバー・画面スワイプで共通に調整する値。 */
internal object TabMotionParameters {
    const val travelFraction = .65f
    const val translationFraction = .12f
    const val fastSwipeDpPerSecond = 500f
    const val dampingRatio = 1f
    const val stiffness = 380f
}

/** 操作元に関わらず、画面・背景・選択枠で一つの物理状態を共有する。 */
@Stable
internal class TabMotionState(initialPage: Int) {
    var position by mutableFloatStateOf(initialPage.toFloat())
        private set
    var targetPage by mutableIntStateOf(initialPage)
        private set
    var dragging by mutableStateOf(false)
        private set
    var animating by mutableStateOf(false)
        private set
    var progressivePage by mutableStateOf<Int?>(initialPage)
        private set
    private var velocity = 0f
    private var animation: Job? = null
    private var generation = 0
    private var dragMinimum = -1f
    private var dragMaximum = 2f
    var transitionSourcePage by mutableIntStateOf(initialPage)
        private set
    val previewProgress: Float get() = (-position).coerceIn(0f, 1f)
    val homeWeight: Float get() = (1f - abs(position)).coerceIn(0f, 1f)

    fun beginDrag(multiplePages: Boolean = false) {
        generation++
        animation?.cancel()
        animating = false
        dragging = true
        progressivePage = null
        val origin = position.roundToInt()
        dragMinimum = if (multiplePages) 0f else (origin - 1).coerceAtLeast(-1).toFloat()
        dragMaximum = if (multiplePages) 2f else (origin + 1).coerceAtMost(2).toFloat()
    }

    fun dragBy(delta: Float, allowPreview: Boolean) {
        position = (position + delta).coerceIn(
            dragMinimum.coerceAtLeast(if (allowPreview) -1f else 0f), dragMaximum)
    }

    fun release(scope: CoroutineScope, speed: Float, fast: Boolean, allowPreview: Boolean,
        commit: (Int, Boolean) -> Unit) {
        val page = (if (fast) {
            if (speed > 0f) floor(position).toInt() + 1 else ceil(position).toInt() - 1
        } else if (position < 0f) -(-position).roundToInt() else position.roundToInt()).coerceIn(
            dragMinimum.toInt().coerceAtLeast(if (allowPreview) -1 else 0), dragMaximum.toInt())
        select(scope, page, fast, speed, commit)
    }

    fun cancelDrag(scope: CoroutineScope) {
        select(scope, targetPage, false, 0f) { _, _ -> }
    }

    fun select(scope: CoroutineScope, page: Int, progressive: Boolean = true,
        initialVelocity: Float = velocity, commit: (Int, Boolean) -> Unit) {
        val currentGeneration = ++generation
        animation?.cancel()
        dragging = false
        // 離れたタブへの移動でも、途中にある別画面は表示しない。
        transitionSourcePage = position.roundToInt().coerceIn(0, 2)
        // 背景鑑賞からは項目を再入場させず、ホーム全体を元の姿へ戻す。
        val playProgressive = progressive && !(page == 0 && (targetPage == -1 || position < 0f))
        targetPage = page
        progressivePage = if (playProgressive && page >= 0) page else null
        commit(page.coerceAtLeast(0), playProgressive)
        animating = true
        animation = scope.launch {
            try {
                AnimationState(position, initialVelocity).animateTo(page.toFloat(),
                    spring(dampingRatio = TabMotionParameters.dampingRatio, stiffness = TabMotionParameters.stiffness)) {
                    position = value.coerceIn(-1f, 2f)
                    this@TabMotionState.velocity = this.velocity
                }
                position = page.toFloat()
                velocity = 0f
            } finally {
                if (generation == currentGeneration) animating = false
            }
        }
    }

    fun resetTo(page: Int) {
        generation++
        animation?.cancel()
        position = page.toFloat()
        targetPage = page
        dragging = false
        animating = false
        velocity = 0f
    }
}

/** 実画面試験で追従状態を読むための値。読み上げの表示文言には使わない。 */
internal val TabMotionPosition = SemanticsPropertyKey<Float>("TabMotionPosition")
internal val TabMotionProgressive = SemanticsPropertyKey<Boolean>("TabMotionProgressive")

/** 縦操作と子の横操作を優先し、未消費の画面スワイプだけを共通状態へ渡す。 */
@Composable
internal fun Modifier.tabMotionGesture(state: TabMotionState, enabled: Boolean,
    commit: (Int, Boolean) -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val onCommit by rememberUpdatedState(commit)
    return if (!enabled) this else pointerInput(state) {
        val tracker = VelocityTracker()
        val travel = (size.width * TabMotionParameters.travelFraction).coerceAtLeast(1f)
        detectHorizontalDragGestures(
            onDragStart = { state.beginDrag(); tracker.resetTracking() },
            onHorizontalDrag = { change, amount ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                state.dragBy(-amount / travel, allowPreview = true)
            },
            onDragCancel = { state.cancelDrag(scope) },
            onDragEnd = {
                val speed = -tracker.calculateVelocity().x
                state.release(scope, speed / travel, abs(speed) > TabMotionParameters.fastSwipeDpPerSecond.dp.toPx(), true, onCommit)
            },
        )
    }
}

/** 隣り合う画面を共通背景上で連続補間し、隠れた画面への入力を防ぐ。 */
@Composable
internal fun TabMotionPages(state: TabMotionState, selectedPage: Int, content: @Composable (Int) -> Unit) {
    val visiblePages by remember(state, selectedPage) {
        derivedStateOf {
            if (state.animating && !state.dragging) {
                listOf(state.transitionSourcePage, state.targetPage.coerceAtLeast(0)).distinct()
            } else {
                (0..2).filter { abs(it - state.position.coerceAtLeast(0f)) < 1f || it == selectedPage }
            }
        }
    }
    Box(Modifier.fillMaxSize().testTag("main-tabs").semantics {
        this[TabMotionPosition] = state.position
        this[TabMotionProgressive] = state.progressivePage != null
    }) {
        for (page in visiblePages) {
                key(page) {
                    Box(Modifier.fillMaxSize().testTag("tab-motion-page-$page")
                        .then(if (page != selectedPage || state.dragging) Modifier.clearAndSetSemantics {} else Modifier)
                        .graphicsLayer {
                            val span = if (state.animating && !state.dragging)
                                abs(state.targetPage - state.transitionSourcePage).coerceAtLeast(1) else 1
                            val distance = (page - state.position.coerceAtLeast(0f)) / span
                            // 中間点を背景だけにし、二画面の文字やガラスを重ねない。
                            alpha = (1f - abs(distance) * 2f).coerceIn(0f, 1f)
                            translationX = size.width * TabMotionParameters.translationFraction * distance
                        }) { content(page) }
                }
        }
        if (state.dragging || state.animating) {
            // タップだけを受け止め、親のドラッグによる中断・再操作は妨げない。
            Box(Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null) {})
        }
    }
}
