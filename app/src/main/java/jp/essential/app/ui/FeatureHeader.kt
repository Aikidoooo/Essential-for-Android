package jp.essential.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.essential.app.ui.theme.LocalEssentialDark

/** スクロールしても戻る操作と機能名を上端へ残す。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun LazyListScope.fixedHeader(content: @Composable () -> Unit) {
    stickyHeader(key = "fixed-feature-header") {
        Box(Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(vertical = 6.dp).testTag("fixed-feature-header")) { content() }
    }
}

/** 半透明の囲みへ機能名を収め、長い名前も操作ボタンを押し出さない。 */
@Composable
fun GlassFeatureTitle(title: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.heightIn(min = 46.dp)
        .liquidGlass(shape)
        .padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 全画面で共通の戻るボタンとガラスの機能名。 */
@Composable
fun FeatureHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier,
    backDescription: String = "戻る",
    actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassBackButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backDescription })
        GlassFeatureTitle(title, Modifier.weight(1f, fill = false))
        actions()
    }
}
