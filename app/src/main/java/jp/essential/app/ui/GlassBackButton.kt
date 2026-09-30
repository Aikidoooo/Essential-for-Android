package jp.essential.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.essential.app.ui.theme.LocalEssentialDark

/** 透過した円形ガラスと線描画の矢印で、各画面の戻る操作を統一する。 */
@Composable
fun GlassBackButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    val dark = LocalEssentialDark.current
    val glassColors = if (dark) {
        listOf(Color(0xFFBFC3CA).copy(alpha = 0.54f), Color(0xFF9198A3).copy(alpha = 0.40f))
    } else {
        listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.64f))
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "戻るボタンの押下反応",
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    glassColors,
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.38f), CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button,
                onClickLabel = "戻る", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(19.dp, 24.dp)) {
            val color = if (dark) Color.White.copy(alpha = 0.96f) else Color(0xFF626975)
            val centerX = this.size.width * 0.57f
            val centerY = this.size.height * 0.5f
            val leftX = this.size.width * 0.19f
            val halfHeight = this.size.height * 0.36f
            drawLine(color, Offset(centerX, centerY - halfHeight), Offset(leftX, centerY), 2.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(leftX, centerY), Offset(centerX, centerY + halfHeight), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}
