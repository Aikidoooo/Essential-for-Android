package jp.essential.app.feature.routine

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.essential.app.R
import jp.essential.app.ui.liquidGlass
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** 押下中の縮小と復元を同じSpringへ接続し、連打でも現在値から追従する。 */
@Composable
internal fun TaskGlassButton(label: String, modifier: Modifier = Modifier, icon: Int? = null,
    selected: Boolean = false, iconOnly: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) TaskMotion.pressedScale else 1f,
        TaskMotion.feedback, label = "タスクボタンの押下")
    Row(modifier.graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else .45f }
        .clip(RoundedCornerShape(18.dp)).liquidGlass(18.dp)
        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .22f) else Color.Transparent)
        .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
        .then(if (iconOnly) Modifier.semantics { contentDescription = label } else Modifier)
        .padding(horizontal = if (iconOnly) 10.dp else 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(22.dp)) }
        if (!iconOnly) {
            if (icon != null) Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun TaskCalendar(month: YearMonth, selected: LocalDate?, highlighted: Map<LocalDate, TaskStatus> = emptyMap(),
    onMonthChange: (YearMonth) -> Unit, onDate: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxWidth().animateContentSize(TaskMotion.layout),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TaskGlassButton("前の月", icon = R.drawable.ic_mannaka_arrow_back, iconOnly = true,
                modifier = Modifier.size(44.dp), onClick = { onMonthChange(month.minusMonths(1)) })
            Text("${month.year}年 ${month.monthValue}月", Modifier.weight(1f).padding(horizontal = 12.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TaskGlassButton("次の月", icon = R.drawable.ic_mannaka_chevron_right, iconOnly = true,
                modifier = Modifier.size(44.dp), onClick = { onMonthChange(month.plusMonths(1)) })
        }
        Row { listOf("月", "火", "水", "木", "金", "土", "日").forEach {
            Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        val offset = month.atDay(1).dayOfWeek.value - 1
        repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { column ->
                    val day = week * 7 + column - offset + 1
                    if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(44.dp))
                    else {
                        val date = month.atDay(day)
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val scale by animateFloatAsState(if (pressed) TaskMotion.pressedScale else 1f,
                            TaskMotion.feedback, label = "日付の押下")
                        val isToday = date == LocalDate.now()
                        val status = highlighted[date]
                        val base = when (status) {
                            TaskStatus.Pending -> MaterialTheme.colorScheme.primary
                            TaskStatus.Completed -> Color(0xFF39B98A)
                            TaskStatus.Expired -> Color(0xFFE7A453)
                            null -> Color.Transparent
                        }
                        val color by animateColorAsState(if (date == selected) MaterialTheme.colorScheme.primary
                            else if (isToday && status == null) MaterialTheme.colorScheme.primary.copy(alpha = .22f)
                            else base.copy(alpha = if (status == null) 0f else .28f),
                            TaskMotion.calendarColor, label = "タスクのある日")
                        Box(Modifier.weight(1f).height(44.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(RoundedCornerShape(14.dp))
                            .background(color)
                            .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
                            .clickable(interactionSource = interaction, indication = null) { onDate(date) }
                            .testTag("task-date-$date")
                            .semantics { contentDescription = "$date ${if (isToday) "今日 " else ""}${status?.label ?: "予定なし"}" },
                            contentAlignment = Alignment.Center) {
                            Text(day.toString(), fontWeight = if (isToday || status != null) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    date == selected -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskStatusFilters(statuses: Set<TaskStatus>, onChange: (Set<TaskStatus>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskStatus.entries.forEach { status ->
            FilterChip(selected = status in statuses,
                onClick = { onChange(if (status in statuses) statuses - status else statuses + status) },
                label = { Text(status.label) }, modifier = Modifier.testTag("task-filter-${status.name}"))
        }
    }
}

/** 期間と完了状態は一つの予定にまとめ、編集と完了のタップ領域を分ける。 */
@Composable
internal fun ScheduledTaskCard(occurrence: TaskOccurrence, now: LocalDateTime, enabled: Boolean,
    onEdit: () -> Unit, onComplete: () -> Unit) {
    val status = occurrence.status(now)
    Row(Modifier.fillMaxWidth().liquidGlass(24.dp).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(checked = status == TaskStatus.Completed,
            onCheckedChange = { if (it) onComplete() }, enabled = enabled && status != TaskStatus.Completed,
            modifier = Modifier.semantics { contentDescription = "${occurrence.task.title}を完了" })
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onEdit)
            .padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(occurrence.task.title, fontWeight = FontWeight.Bold)
            Text(taskPeriodLabel(occurrence), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (occurrence.task.memo.isNotBlank()) Text(occurrence.task.memo, maxLines = 2,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${status.label} · ${occurrence.task.xp} XP" +
                if (occurrence.task.repeat != TaskRepeat.None) " · ${occurrence.task.repeat.label}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = if (status == TaskStatus.Expired) Color(0xFFE7A453) else MaterialTheme.colorScheme.primary)
        }
    }
}

internal fun taskPeriodLabel(occurrence: TaskOccurrence): String {
    val date = DateTimeFormatter.ofPattern("M/d")
    val time = DateTimeFormatter.ofPattern("HH:mm")
    return if (occurrence.task.allDay) {
        val last = occurrence.end.minusNanos(1).toLocalDate()
        "${occurrence.start.format(date)}" + if (last != occurrence.start.toLocalDate()) " 〜 ${last.format(date)} · 終日" else " · 終日"
    } else "${occurrence.start.format(date)} ${occurrence.start.format(time)} 〜 ${occurrence.end.format(date)} ${occurrence.end.format(time)}"
}
