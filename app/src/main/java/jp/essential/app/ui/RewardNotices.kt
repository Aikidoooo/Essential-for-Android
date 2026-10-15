package jp.essential.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.essential.app.profile.AppNotice

/** ベルとプロフィールタブで共通の未読表示。 */
@Composable
internal fun UnreadNoticeDot(tag: String) {
    Badge(containerColor = Color(0xFFFF454F), modifier = Modifier.testTag(tag)
        .semantics { contentDescription = "未読のおしらせ" })
}

/** 獲得した報酬を新しいXP通知と報酬を並べ、再起動後も保存済みの一覧を表示する。 */
@Composable
internal fun RewardNoticesContent(notices: List<AppNotice>, bottomPadding: Dp, onBack: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().elasticListEdges().testTag("reward-notices-list"),
        overscrollEffect = null,
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = bottomPadding + 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ProfileNoticeHeader(onBack) }
        if (notices.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().liquidGlass(24.dp).padding(28.dp), contentAlignment = Alignment.Center) {
                    Text("おしらせはありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(notices, key = { it.id }) { notice ->
            Row(Modifier.fillMaxWidth().liquidGlass(24.dp).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (notice.imageRes != null) {
                    Image(painterResource(notice.imageRes), contentDescription = null, modifier = Modifier.size(48.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(notice.title, fontWeight = FontWeight.Bold)
                    notice.xp?.let { points ->
                        Text("$points XP", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(notice.detail, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProfileNoticeHeader(onBack: () -> Unit) {
    FeatureHeader("おしらせ", onBack)
}
