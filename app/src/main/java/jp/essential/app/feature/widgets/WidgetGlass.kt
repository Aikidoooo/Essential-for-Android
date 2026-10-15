package jp.essential.app.feature.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import jp.essential.app.ui.ProgressiveWidget
import jp.essential.app.ui.liquidGlass

/** 共通のガラスと入場モーションを新しい機能でも維持する。 */
@Composable
internal fun WidgetGlass(index: Int, content: @Composable ColumnScope.() -> Unit) {
    ProgressiveWidget(index) {
        val shape = RoundedCornerShape(26.dp)
        Surface(Modifier.fillMaxWidth().liquidGlass(shape), shape = shape, color = Color.Transparent) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** 押下途中の反転でも現在値を引き継ぎ、システムのモーション倍率を尊重する。 */
@Composable
internal fun WidgetButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.98f else 1f,
        spring(dampingRatio = 0.86f, stiffness = 650f), label = "ウィジェットボタンの押下")
    Button(onClick, enabled = enabled, interactionSource = source,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { Text(text) }
}
