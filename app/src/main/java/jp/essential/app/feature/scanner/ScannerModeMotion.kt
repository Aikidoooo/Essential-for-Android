package jp.essential.app.feature.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

// 切替途中の位置をテストから確認し、画面全体の再表示と区別する。
internal val ScannerMotionProgress = SemanticsPropertyKey<Float>("ScannerMotionProgress")

/** 上部のラベルだけを縦移動と透過で入れ替え、ピルの幅も連続して変える。 */
@Composable
internal fun ScannerModeHeader(textMode: Boolean, modePosition: Float, onAddTile: () -> Unit = {}) {
    val progress = modePosition.coerceIn(0f, 1f)
    Surface(onClick = onAddTile, enabled = !textMode,
        color = Color.Black.copy(alpha = 0.54f), contentColor = Color.White, shape = CircleShape,
        modifier = Modifier.width(lerp(172.dp, 132.dp, progress)).height(44.dp)
            .testTag("scanner-mode-header").semantics { this[ScannerMotionProgress] = progress }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("クイック設定に追加", style = MaterialTheme.typography.labelLarge, maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - progress
                    translationY = -12.dp.toPx() * progress
                }.then(if (textMode) Modifier.clearAndSetSemantics {} else Modifier))
            Text("文字スキャン", style = MaterialTheme.typography.labelLarge, maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    alpha = progress
                    translationY = 12.dp.toPx() * (1f - progress)
                }.then(if (!textMode) Modifier.clearAndSetSemantics {} else Modifier))
        }
    }
}
