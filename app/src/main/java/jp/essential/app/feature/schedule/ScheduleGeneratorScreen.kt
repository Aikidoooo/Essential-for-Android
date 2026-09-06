package jp.essential.app.feature.schedule

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import jp.essential.app.ui.ProgressiveWidget
import jp.essential.app.ui.progressiveItem

private enum class ScheduleOutput { Pdf, Text, Image }

@Composable
fun ScheduleGeneratorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val attendeeNames = remember { mutableStateListOf("") }
    val scheduleEntries = remember { mutableStateListOf(ScheduleEntry()) }
    var isWriting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun currentData() = ScheduleData(
        title = title,
        purpose = purpose,
        place = place,
        startDate = startDate,
        endDate = endDate,
        attendees = attendeeNames.filter(String::isNotBlank),
        notes = notes,
        entries = scheduleEntries.toList(),
    )

    fun safeName() = title.trim()
        .replace(Regex("[\\/:*?\"<>|]"), "_")
        .take(60)
        .ifBlank { "Essential-行程表" }

    fun write(uri: Uri?, output: ScheduleOutput) {
        if (uri == null) return
        scope.launch {
            isWriting = true
            message = null
            runCatching {
                when (output) {
                    ScheduleOutput.Pdf -> ScheduleDocumentGenerator.writePdf(context, uri, currentData())
                    ScheduleOutput.Text -> ScheduleDocumentGenerator.writeText(context, uri, currentData())
                    ScheduleOutput.Image -> ScheduleDocumentGenerator.writeImage(context, uri, currentData())
                }
            }.onSuccess { message = "保存しました" }
                .onFailure { error -> message = error.message ?: "保存できませんでした" }
            isWriting = false
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        write(it, ScheduleOutput.Pdf)
    }
    val textLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        write(it, ScheduleOutput.Text)
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {
        write(it, ScheduleOutput.Image)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        var motionIndex = 0
        progressiveItem(motionIndex++) { ScheduleTopBar(onBack) }
        progressiveItem(motionIndex++) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f), RoundedCornerShape(22.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "日付範囲、参加者、経由地を含む行程を入力すると、3形式へ自動整形します。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        progressiveItem(motionIndex++) { SectionTitle("行程全体", "題名・日付範囲・参加者") }
        progressiveItem(motionIndex++) { ScheduleField("題名", title, { title = it }, "例: 京都2日間旅行") }
        progressiveItem(motionIndex++) { ScheduleField("目的", purpose, { purpose = it }, "例: 友人との観光") }
        progressiveItem(motionIndex++) { ScheduleField("集合場所・主な場所", place, { place = it }, "集合場所・主な目的地") }
        progressiveItem(motionIndex++) {
            DateRangeField(startDate, endDate) { start, end ->
                startDate = start
                endDate = end
            }
        }
        progressiveItem(motionIndex++) { SectionTitle("誰が来るか", "名前を埋めると次の入力欄が現れます") }
        attendeeNames.forEachIndexed { index, name ->
            item(key = "attendee-$index") {
                ProgressiveWidget(motionIndex + index) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScheduleField(
                        label = "参加者 ${index + 1}",
                        value = name,
                        onValueChange = { value ->
                            attendeeNames[index] = value
                            if (index == attendeeNames.lastIndex && value.isNotBlank() && attendeeNames.size < 50) {
                                attendeeNames += ""
                            }
                        },
                        placeholder = "氏名",
                        modifier = Modifier.weight(1f),
                    )
                    if (attendeeNames.size > 1 && index < attendeeNames.lastIndex) {
                        TextButton(onClick = { attendeeNames.removeAt(index) }) { Text("削除") }
                    }
                }
                }
            }
        }
        motionIndex += attendeeNames.size
        progressiveItem(motionIndex++) { SectionTitle("行程表", "出発・経由・到着と所要時間を追加") }
        scheduleEntries.forEachIndexed { index, entry ->
            item(key = "schedule-entry-$index") {
                ProgressiveWidget(motionIndex + index) {
                ScheduleEntryEditor(
                    index = index,
                    entry = entry,
                    canDelete = scheduleEntries.size > 1,
                    onChange = { scheduleEntries[index] = it },
                    onDelete = { scheduleEntries.removeAt(index) },
                )
                }
            }
        }
        motionIndex += scheduleEntries.size
        progressiveItem(motionIndex++) {
            OutlinedButton(
                onClick = { if (scheduleEntries.size < 20) scheduleEntries += ScheduleEntry() },
                enabled = scheduleEntries.size < 20,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (scheduleEntries.size < 20) "行程を追加" else "行程は20件まで") }
        }
        progressiveItem(motionIndex++) {
            ScheduleField("全体の注意事項・メモ", notes, { notes = it }, "持ち物、予約番号、変更条件など", minLines = 4)
        }
        progressiveItem(motionIndex++) { SectionTitle("出力", "同じ内容から3形式を自動生成") }
        progressiveItem(motionIndex++) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutputButton("PDF表", Modifier.weight(1f), isWriting) { pdfLauncher.launch("${safeName()}.pdf") }
                OutputButton("文章", Modifier.weight(1f), isWriting) { textLauncher.launch("${safeName()}.txt") }
                OutputButton("縦型画像", Modifier.weight(1f), isWriting) { imageLauncher.launch("${safeName()}.png") }
            }
        }
        progressiveItem(motionIndex++) {
            AnimatedVisibility(isWriting || message != null) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.80f), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isWriting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        if (isWriting) "行程表を生成しています" else message.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeField(startDate: String, endDate: String, onSelected: (String, String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val rangeText = when {
        startDate.isBlank() -> "開始日と終了日を選択"
        endDate.isBlank() || startDate == endDate -> startDate
        else -> "$startDate 〜 $endDate"
    }
    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text("日付範囲　$rangeText", modifier = Modifier.weight(1f))
        Text("カレンダー")
    }
    if (showPicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = formatDate(pickerState.selectedStartDateMillis)
                        val end = formatDate(pickerState.selectedEndDateMillis ?: pickerState.selectedStartDateMillis)
                        onSelected(start, end)
                        showPicker = false
                    },
                ) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("キャンセル") } },
        ) {
            DateRangePicker(
                state = pickerState,
                title = { Text("開始日と終了日を選択", modifier = Modifier.padding(16.dp)) },
                headline = { Text("開始日 － 終了日", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) },
                showModeToggle = false,
                modifier = Modifier.heightIn(max = 560.dp),
            )
        }
    }
}

private fun formatDate(millis: Long?): String {
    if (millis == null) return ""
    return DateTimeFormatter.ofPattern("yyyy/MM/dd")
        .withZone(ZoneOffset.UTC)
        .format(Instant.ofEpochMilli(millis))
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScheduleEntryEditor(
    index: Int,
    entry: ScheduleEntry,
    canDelete: Boolean,
    onChange: (ScheduleEntry) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f), RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${index + 1}", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Text("行程 ${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (canDelete) TextButton(onClick = onDelete) { Text("削除") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ScheduleField("出発時刻", entry.departureTime, { onChange(entry.copy(departureTime = it)) }, "09:00", Modifier.weight(1f))
            ScheduleField("到着時刻", entry.arrivalTime, { onChange(entry.copy(arrivalTime = it)) }, "10:30", Modifier.weight(1f))
        }
        ScheduleField("所要時間", entry.duration, { onChange(entry.copy(duration = it)) }, "例: 1時間30分")
        RoutePoint("出発", entry.origin, { onChange(entry.copy(origin = it)) }, isEndpoint = true)
        entry.waypoints.forEachIndexed { waypointIndex, waypoint ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoutePoint(
                    label = "経由 ${waypointIndex + 1}",
                    value = waypoint,
                    onValueChange = { value ->
                        val updated = entry.waypoints.toMutableList().apply { set(waypointIndex, value) }
                        onChange(entry.copy(waypoints = updated))
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        val updated = entry.waypoints.toMutableList().apply { removeAt(waypointIndex) }
                        onChange(entry.copy(waypoints = updated))
                    },
                ) { Text("削除") }
            }
        }
        OutlinedButton(
            onClick = { if (entry.waypoints.size < 8) onChange(entry.copy(waypoints = entry.waypoints + "")) },
            enabled = entry.waypoints.size < 8,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) { Text("＋ 経由地を追加") }
        RoutePoint("到着", entry.destination, { onChange(entry.copy(destination = it)) }, isEndpoint = true)
        ScheduleField(
            "備考・体験内容",
            entry.details,
            { onChange(entry.copy(details = it)) },
            "予約、やること、交通手段など",
            minLines = 2,
        )
    }
}

@Composable
private fun RoutePoint(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    isEndpoint: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(if (isEndpoint) 16.dp else 12.dp)
                .background(
                    if (isEndpoint) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    CircleShape,
                ),
        )
        Spacer(Modifier.width(10.dp))
        ScheduleField(label, value, onValueChange, "場所・駅名", Modifier.weight(1f))
    }
}

@Composable
private fun ScheduleField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(20.dp),
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun OutputButton(label: String, modifier: Modifier, isWriting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isWriting,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) { Text(label, maxLines = 1) }
}

@Composable
private fun ScheduleTopBar(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            modifier = Modifier.size(46.dp).clickable(onClick = onBack),
        ) { Box(contentAlignment = Alignment.Center) { Text("‹", style = MaterialTheme.typography.headlineMedium) } }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("行程表ジェネレーター", style = MaterialTheme.typography.headlineMedium)
            Text(
                "経由地を含む行程からPDF・文章・画像へ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
