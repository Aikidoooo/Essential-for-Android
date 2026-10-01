package jp.essential.app.feature.mannaka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.essential.app.ui.GlassBackButton
import jp.essential.app.ui.ProgressiveWidget
import jp.essential.app.ui.progressiveItem

/** 選択した中心駅での活動指定と、周辺施設の検索結果を表示する。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetingFacilities(station: MeetingPoint, categories: List<String>, custom: String,
    places: List<MeetingPoint>, busy: Boolean, error: String?, notice: String?, choosingActivity: Boolean,
    onBack: () -> Unit, onActivity: () -> Unit, onSearch: (List<String>, String) -> Unit,
    onRetry: () -> Unit, onMap: (MeetingPoint, String) -> Unit,
    onShare: () -> Unit, onCopy: () -> Unit, onFavorite: () -> Unit) {
    val searches = (categories + listOf(custom).filter { it.isNotBlank() }).ifEmpty { meetingCategories }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()
        .testTag("mannaka-facilities"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        progressiveItem(0, keyPrefix = "mannaka-facilities") {
            GlassBackButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "中心駅候補に戻る" })
        }
        progressiveItem(1, keyPrefix = "mannaka-facilities") {
            Text(if (choosingActivity) "集まって何する？" else "付近の施設", style = MaterialTheme.typography.headlineLarge)
            Text("${station.name}の周りで遊ぶ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(station.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (choosingActivity) {
            progressiveItem(2, keyPrefix = "mannaka-facilities") {
                MeetingGlassCard(Modifier.testTag("mannaka-facility-activity"), padding = 0.dp) {
                    Spacer(Modifier.height(14.dp))
                    MeetingActivityPanel(categories, custom, doneLabel = "付近の施設を検索", showTitle = false, onDone = onSearch)
                }
            }
        } else {
            progressiveItem(2, keyPrefix = "mannaka-facilities") {
                MeetingGlassCard {
                    Text("検索条件：${searches.joinToString("、")}", fontSize = 14.sp)
                    Text("周辺2.5kmの登録施設", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onActivity) { Text("何をするか変更する") }
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("付近の施設を探しています…", fontSize = 14.sp)
                    }
                    error?.let {
                        Text(it, fontSize = 14.sp)
                        TextButton(onClick = onRetry, enabled = !busy) { Text("施設を再検索") }
                    }
                    if (!busy && error == null && places.isEmpty()) Text("登録施設が見つかりませんでした。地図でも探せます。", fontSize = 14.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        searches.forEach { query -> TextButton(onClick = { onMap(station, query) }) { Text("${query}を地図で探す") } }
                    }
                }
            }
            itemsIndexed(places, key = { _, point -> point.id }) { index, point ->
                ProgressiveWidget(index + 3) {
                MeetingGlassCard {
                    Text(point.name, style = MaterialTheme.typography.titleMedium)
                    Text("${point.category}・約${meetingDistanceLabel(MeetingGeometry.distance(station, point))}km",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    TextButton(onClick = { onMap(point, point.name) }) { Text("地図で見る") }
                }

                }
            }
            progressiveItem(7, keyPrefix = "mannaka-facilities") {
                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("この結果を共有する") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onCopy) { Text("予定をコピー") }
                    TextButton(onClick = onFavorite) { Text("お気に入りに保存") }
                }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
    }
}
