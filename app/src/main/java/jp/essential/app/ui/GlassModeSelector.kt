package jp.essential.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** スキャナーと楽器選択で同じガラス背景・選択位置・押下フィードバックを使う。 */
@Composable
internal fun GlassModeSelector(labels: List<String>, tags: List<String>, selectedIndex: Int,
    position: Float, onSelected: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val haptics = LocalHapticFeedback.current
    Box(modifier.height(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = .48f))) {
        Canvas(Modifier.matchParentSize()) {
            val inset = 3.dp.toPx()
            val width = size.width / labels.size - inset * 2
            val height = size.height - inset * 2
            val left = size.width / labels.size * position + inset
            val radius = CornerRadius(height / 2)
            drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .20f),
                Color(0xFFB9DFFF).copy(alpha = .18f), Color.White.copy(alpha = .16f)),
                Offset(left, inset), Offset(left + width, inset + height)),
                Offset(left, inset), Size(width, height), radius)
            drawRoundRect(Color.White.copy(alpha = .34f), Offset(left, inset), Size(width, height),
                radius, style = Stroke(1.1.dp.toPx()))
        }
        Row(Modifier.fillMaxSize().padding(3.dp)) {
            labels.forEachIndexed { index, label ->
                Surface(onClick = {
                    if (selectedIndex != index) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelected(index)
                    }
                }, enabled = enabled, color = Color.Transparent,
                    contentColor = if (selectedIndex == index) Color(0xFFFFEB86) else Color.White,
                    shape = CircleShape, modifier = Modifier.weight(1f).testTag(tags[index])
                        .semantics { selected = selectedIndex == index; role = Role.Tab }) {
                    Box(Modifier.height(46.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            modifier = Modifier.graphicsLayer {
                                val selection = (1f - kotlin.math.abs(position - index)).coerceIn(0f, 1f)
                                scaleX = 1f + selection * .04f
                                scaleY = scaleX
                            })
                    }
                }
            }
        }
    }
}
