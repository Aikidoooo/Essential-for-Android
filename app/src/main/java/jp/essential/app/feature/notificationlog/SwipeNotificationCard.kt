package jp.essential.app.feature.notificationlog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import jp.essential.app.ui.liquidGlass
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 指に追従し、左右の操作後と中断時は現在位置からばねで復元する。 */
@Composable
internal fun SwipeNotificationCard(
    modifier: Modifier = Modifier,
    kept: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var animation by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val save by rememberUpdatedState(onSave)
    val delete by rememberUpdatedState(onDelete)
    val tracker = remember { VelocityTracker() }
    fun restore(velocity: Float = 0f) {
        animation?.cancel()
        animation = scope.launch {
            animate(offset, 0f, initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 1f, stiffness = 420f)) { value, _ -> offset = value }
        }
    }
    Box(modifier.fillMaxWidth().semantics {
        stateDescription = if (kept) "保存済み" else "未保存"
        customActions = listOf(
            CustomAccessibilityAction("通知ログを保存") { save(); true },
            CustomAccessibilityAction("通知ログを削除") { delete(); true },
        )
    }.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { _ ->
                animation?.cancel()
                tracker.resetTracking()

            },
            onHorizontalDrag = { change, amount ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                offset = (offset + amount).coerceIn(-size.width * 0.65f, size.width * 0.65f)
            },
            onDragCancel = { restore() },
            onDragEnd = {
                val velocity = tracker.calculateVelocity().x
                // 距離不足の操作や逆方向への切り返しでは誤操作を起こさない。
                val distanceReached = abs(offset) >= size.width * 0.30f
                val flingReached = abs(offset) >= size.width * 0.12f && abs(velocity) >= 1000f && offset * velocity > 0f
                if ((distanceReached || flingReached) && offset * velocity >= 0f) {
                    if (offset > 0f) delete() else save()
                }
                restore(velocity)
            },
        )
    }) {
        if (abs(offset) > 1f) {
            val color = if (offset > 0f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Canvas(Modifier.align(if (offset > 0f) Alignment.CenterStart else Alignment.CenterEnd).padding(20.dp).size(24.dp)) {
                val stroke = 2.dp.toPx()
                if (offset > 0f) {
                    drawLine(color, Offset(size.width * .15f, size.height * .25f), Offset(size.width * .85f, size.height * .25f), stroke)
                    drawLine(color, Offset(size.width * .35f, size.height * .1f), Offset(size.width * .65f, size.height * .1f), stroke)
                    val bin = Path().apply {
                        moveTo(size.width * .25f, size.height * .35f)
                        lineTo(size.width * .3f, size.height * .9f)
                        lineTo(size.width * .7f, size.height * .9f)
                        lineTo(size.width * .75f, size.height * .35f)
                    }
                    drawPath(bin, color, style = Stroke(stroke))
                } else {
                    val bookmark = Path().apply {
                        moveTo(size.width * .25f, size.height * .1f)
                        lineTo(size.width * .75f, size.height * .1f)
                        lineTo(size.width * .75f, size.height * .9f)
                        lineTo(size.width * .5f, size.height * .7f)
                        lineTo(size.width * .25f, size.height * .9f)
                        close()
                    }
                    drawPath(bookmark, color, style = Stroke(stroke))
                }
            }
        }
        Box(Modifier.graphicsLayer { translationX = offset }) { content() }
    }
}

/** 設定と同じガラスのトラック上を選択面が連続的に移動する。 */
@Composable
internal fun NotificationSegments(keptOnly: Boolean, onSelected: (Boolean) -> Unit) {
    val position by animateFloatAsState(if (keptOnly) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = 520f), label = "通知フィルタの選択位置")
    BoxWithConstraints(Modifier.fillMaxWidth().liquidGlass(24.dp).padding(4.dp)
        .selectableGroup().testTag("notification-segments")) {
        val segmentWidth = maxWidth / 2
        Box(Modifier.offset(x = segmentWidth * position).width(segmentWidth).height(48.dp)
            .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .9f)))
        Row(Modifier.fillMaxWidth()) {
            listOf("すべて", "keep").forEachIndexed { index, label ->
                val selected = keptOnly == (index == 1)
                Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(20.dp))
                    .selectable(selected, role = Role.Tab, onClick = { onSelected(index == 1) }),
                    contentAlignment = Alignment.Center) {
                    Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
