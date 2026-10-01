package jp.essential.app.feature.mannaka

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.essential.app.ui.GlassBackButton
import jp.essential.app.ui.ProgressiveWidget
import jp.essential.app.ui.progressiveItem
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import jp.essential.app.R
import java.util.Locale

internal fun meetingDistanceLabel(distance: Double) = "%.1f".format(Locale.US, distance)

@Composable
private fun ParticipantDistance(participant: MeetingParticipant, index: Int, distance: Double, longest: Boolean) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 58.dp)) {
        Box(Modifier.padding(top = 8.dp)) {
            Surface(shape = CircleShape, color = colors[index % colors.size], shadowElevation = 3.dp, border = BorderStroke(2.dp, Color.White), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(participant.name.ifBlank { "ゲスト" }.take(1), color = listOf(MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.onSecondary, MaterialTheme.colorScheme.onTertiary)[index % 3], fontSize = 20.sp) }
            }
            if (longest) Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-7).dp)) {
                Text("最長", color = MaterialTheme.colorScheme.onPrimary, fontSize = 9.sp, lineHeight = 11.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
        Text(participant.name.ifBlank { "${index + 1}人目" }, maxLines = 1, fontSize = 11.sp, lineHeight = 14.sp)
        Text("${meetingDistanceLabel(distance)}km", fontSize = 13.sp, lineHeight = 16.sp,
            color = if (longest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetingResults(participants: List<MeetingParticipant>, stations: List<MeetingPoint>, selected: MeetingPoint,
    dateLabel: String, busy: Boolean, notice: String?,
    onBack: () -> Unit, onSelect: (MeetingPoint) -> Unit, onMap: (MeetingPoint, String) -> Unit,
    onShare: () -> Unit, onCopy: () -> Unit, onFavorite: () -> Unit) {
    val candidates = stations.ifEmpty { listOf(selected) }
    val origins = participants.mapNotNull { it.selected }
    fun maximum(point: MeetingPoint) = origins.maxOfOrNull { MeetingGeometry.distance(it, point) } ?: 0.0
    val distances = participants.map { it.selected?.let { origin -> MeetingGeometry.distance(origin, selected) } ?: 0.0 }
    val longest = distances.indices.maxByOrNull { distances[it] }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("mannaka-results"),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        progressiveItem(0, keyPrefix = "mannaka-candidates") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassBackButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "駅入力に戻る" })
                Column {
                    Text("まんなか候補", fontSize = 27.sp)
                    Text(origins.joinToString("・") { it.name.removeSuffix("駅") } + " のまんなか", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        progressiveItem(1, keyPrefix = "mannaka-candidates") {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_mannaka_spark), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("いちばん遠い人の距離が短くなる順", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
                }
            }
        }
        progressiveItem(2, keyPrefix = "mannaka-candidates") { MeetingCandidateMap(candidates, selected, onSelect) }
        progressiveItem(3, keyPrefix = "mannaka-candidates") {
            MeetingGlassCard(Modifier.testTag("mannaka-recommendation"), padding = 16.dp, spacing = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(if (selected == candidates.first()) "♛" else "${candidates.indexOf(selected) + 1}", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 29.sp) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(selected.name, fontSize = 25.sp)
                        Text(selected.detail.ifBlank { "集合場所の詳細は地図で確認" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
                        Text(if (selected == candidates.first()) "本命" else "選択中", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp))
                    }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    participants.forEachIndexed { index, participant -> ParticipantDistance(participant, index, distances[index], index == longest) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                    Text("最大 ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                    Text(meetingDistanceLabel(maximum(selected)), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 55.sp)
                    Text(" km", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 24.sp, modifier = Modifier.padding(bottom = 9.dp))
                }
                Text("直線距離の目安です。電車の所要時間は経路で確認できます。", fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onSelect(selected) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("mannaka-select-station")) {
                    Text("この駅で集まる")
                }
            }
        }
        itemsIndexed(candidates.filter { it != selected }, key = { _, point -> point.id.ifBlank { point.name } }) { index, point ->
                ProgressiveWidget(index + 4) {
            Surface(onClick = { onSelect(point) }, enabled = !busy, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().testTag("mannaka-candidate-${point.id}")) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${candidates.indexOf(point) + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(point.name, fontSize = 18.sp)
                        Text(point.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("最大（直線）", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${meetingDistanceLabel(maximum(point))}km", fontSize = 19.sp)
                    }
                    Icon(painterResource(R.drawable.ic_mannaka_chevron_right), null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

                }
            }
        progressiveItem(6, keyPrefix = "mannaka-candidates") {
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().height(50.dp), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))) { Text("この結果を共有する", fontSize = 16.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onCopy) { Text("予定をコピー") }
                TextButton(onClick = onFavorite) { Text("お気に入りに保存") }
            }
            Text(dateLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onMap(selected, selected.name) }) { Text("集合場所・経路を地図で確認") }
            TextButton(onClick = { onMap(MeetingGeometry.center(origins), "みんなの中間地点") }) { Text("中間地点を地図で見る") }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    }
}
