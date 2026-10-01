package jp.essential.app.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** 縦スクロールや子の長押しドラッグが消費した操作を奪わず、横スワイプだけで隣のタブへ進む。 */
internal fun Modifier.tabSwipeNavigation(onSwipe: (Int) -> Unit): Modifier = composed {
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    pointerInput(Unit) {
        var distance = 0f
        val threshold = 64.dp.toPx().coerceAtMost(size.width * 0.25f)
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragCancel = { distance = 0f },
            onDragEnd = {
                if (kotlin.math.abs(distance) >= threshold) currentOnSwipe(if (distance < 0f) 1 else -1)
                distance = 0f
            },
            onHorizontalDrag = { change, amount ->
                distance += amount
                change.consume()
            },
        )
    }
}
