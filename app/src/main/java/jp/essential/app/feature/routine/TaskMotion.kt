package jp.essential.app.feature.routine

import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** 日課内の押下・日付・画面移動の初期値を一か所で調整する。 */
internal object TaskMotion {
    const val pressedScale = .98f
    val feedback = spring<Float>(dampingRatio = .86f, stiffness = 650f)
    val calendarColor = spring<Color>(dampingRatio = .92f, stiffness = 650f)
    val layout = spring<IntSize>(dampingRatio = .92f, stiffness = 420f)
    val navigation = spring<IntOffset>(dampingRatio = .92f, stiffness = 420f)
    val fade = spring<Float>(dampingRatio = 1f, stiffness = 420f)
}
