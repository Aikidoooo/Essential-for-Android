package jp.essential.app.feature.routine

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import jp.essential.app.R
import jp.essential.app.ui.elasticListEdges
import jp.essential.app.ui.liquidGlass
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** 添付の縦配置に合わせた、ドラッグで開閉できるガラスの編集シート。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskEditor(existing: ScheduledTask?, initial: LocalDateTime, busy: Boolean,
    onDismiss: () -> Unit, onSave: (ScheduledTask) -> Unit, onDelete: () -> Unit,
    onNotificationEnabled: () -> Unit, closeRequested: Boolean = false,
    onCloseInterrupted: () -> Unit = {}, saveError: String? = null) {
    val identity = existing?.id ?: initial.toString()
    val taskId = rememberSaveable(identity) { existing?.id ?: UUID.randomUUID().toString() }
    val originallyEditing = rememberSaveable { existing != null }
    var title by rememberSaveable(identity) { mutableStateOf(existing?.title.orEmpty()) }
    var memo by rememberSaveable(identity) { mutableStateOf(existing?.memo.orEmpty()) }
    var startDate by rememberSaveable(identity) { mutableStateOf((existing?.start ?: initial).toLocalDate().toString()) }
    var endDate by rememberSaveable(identity) { mutableStateOf((existing?.let {
        if (it.allDay) it.end.minusNanos(1) else it.end
    } ?: initial.plusHours(1)).toLocalDate().toString()) }
    var startTime by rememberSaveable(identity) { mutableStateOf((existing?.start ?: initial).toLocalTime().toString()) }
    var endTime by rememberSaveable(identity) { mutableStateOf(if (existing?.allDay == true) "23:59" else (existing?.end ?: initial.plusHours(1)).toLocalTime().toString()) }
    var allDay by rememberSaveable(identity) { mutableStateOf(existing?.allDay ?: false) }
    var repeatName by rememberSaveable(identity) { mutableStateOf((existing?.repeat ?: TaskRepeat.None).name) }
    var repeatDays by rememberSaveable(identity) { mutableStateOf((existing?.repeatDays ?: 1).toString()) }
    var repeatUntil by rememberSaveable(identity) { mutableStateOf(existing?.repeatUntil?.toString()) }
    var notify by rememberSaveable(identity) { mutableStateOf(existing?.notificationEnabled ?: false) }
    var reminder by rememberSaveable(identity) { mutableStateOf(existing?.reminderMinutes ?: 0) }
    var legacyTime by rememberSaveable(identity) { mutableStateOf(existing?.legacyNotificationTime?.toString()) }
    var customReminder by rememberSaveable(identity) { mutableStateOf(reminder.toString()) }
    var xp by rememberSaveable(identity) { mutableStateOf((((existing?.xp ?: 30) / 5f).roundToInt() * 5).coerceIn(5, 300).toString()) }
    var pane by rememberSaveable { mutableStateOf<String?>(null) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerMonth by rememberSaveable { mutableStateOf(YearMonth.from(initial).toString()) }
    var deleteConfirmation by remember { mutableStateOf(false) }
    val repeat = TaskRepeat.valueOf(repeatName)
    val start = LocalDate.parse(startDate).atTime(if (allDay) LocalTime.MIN else LocalTime.parse(startTime))
    val end = if (allDay) LocalDate.parse(endDate).plusDays(1).atStartOfDay()
        else LocalDate.parse(endDate).atTime(LocalTime.parse(endTime))
    val validation = when {
        title.isBlank() -> "題名を入力してください"
        title.length > 200 -> "題名は200文字以内にしてください"
        !end.isAfter(start) -> "終了は開始より後にしてください"
        xp.toIntOrNull() !in 5..300 || xp.toIntOrNull()?.rem(5) != 0 -> "XPは5〜300の範囲で、5XP刻みで入力してください"
        repeat == TaskRepeat.Custom && repeatDays.toIntOrNull() !in 1..365 -> "繰り返しは1〜365日で入力してください"
        repeat != TaskRepeat.None && repeatUntil?.let { LocalDate.parse(it) < start.toLocalDate() } == true -> "繰り返しの終了日は開始日以降にしてください"
        else -> null
    }
    val fieldColors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent)
    val currentBusy by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { !currentBusy || it != SheetValue.Hidden })
    val scope = rememberCoroutineScope()
    LaunchedEffect(closeRequested) {
        if (closeRequested) {
            try { sheetState.hide(); onDismiss() }
            catch (exception: CancellationException) { onCloseInterrupted(); throw exception }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, dragHandle = null,
        sheetState = sheetState,
        // テーマの割合指定の角丸では長いシートの上部まで削れるため、固定半径で揃える。
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("task-editor")) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        fun finishInput() { focus.clearFocus(); keyboard?.hide() }
        Column(Modifier.fillMaxWidth().liquidGlass(24.dp).imePadding().padding(horizontal = 16.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).width(40.dp).height(4.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .25f)))
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                TaskGlassButton(if (pane == null) "キャンセル" else "戻る", iconOnly = true,
                    icon = if (pane == null) R.drawable.ic_close else R.drawable.ic_mannaka_arrow_back,
                    modifier = Modifier.size(44.dp), enabled = !busy,
                    onClick = { if (pane == null) scope.launch { sheetState.hide(); onDismiss() } else { pane = null; picker = null } })
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(when (pane) { "repeat" -> "繰り返し"; "reminder" -> "リマインダー"; else -> if (!originallyEditing) "タスクを追加" else "タスクを編集" },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                TaskGlassButton("タスクを保存", iconOnly = true, icon = R.drawable.ic_task_check,
                    modifier = Modifier.size(44.dp).testTag("task-save"), enabled = validation == null && !busy,
                    onClick = { onSave(ScheduledTask(taskId, title.trim(), memo,
                        start, end, allDay, repeat, repeatDays.toIntOrNull()?.coerceIn(1, 365) ?: 1,
                        if (repeat == TaskRepeat.None) null else repeatUntil?.let(LocalDate::parse),
                        notify, reminder, xp.toInt(), existing?.completed.orEmpty(), legacyTime?.let(LocalTime::parse),
                        existing?.legacyFirstReminderNotBefore)) })
            }
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).elasticListEdges()
                .verticalScroll(rememberScrollState(), overscrollEffect = null)
                .padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (pane == "repeat") {
                    EditorGlassGroup {
                        TaskRepeat.entries.forEach { option ->
                            Row(Modifier.fillMaxWidth().clickable {
                                repeatName = option.name
                                if (option != TaskRepeat.Custom) pane = null
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(option.label, Modifier.weight(1f))
                                RadioButton(selected = option == repeat, onClick = {
                                    repeatName = option.name
                                    if (option != TaskRepeat.Custom) pane = null
                                })
                            }
                        }
                        if (repeat == TaskRepeat.Custom) {
                            TextField(repeatDays, { repeatDays = it.filter(Char::isDigit).take(3) },
                                label = { Text("何日ごと") }, colors = fieldColors,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("終了日を指定", Modifier.weight(1f))
                            Switch(checked = repeatUntil != null, onCheckedChange = {
                                repeatUntil = if (it) LocalDate.parse(endDate).toString() else null
                            })
                        }
                        repeatUntil?.let { until ->
                            TaskGlassButton(until.replace('-', '/'), onClick = {
                                picker = "until"; pickerMonth = YearMonth.from(LocalDate.parse(until)).toString()
                            })
                            if (picker == "until") TaskCalendar(YearMonth.parse(pickerMonth), LocalDate.parse(until),
                                onMonthChange = { pickerMonth = it.toString() }, onDate = { repeatUntil = it.toString(); picker = null })
                        }
                        TaskGlassButton("決定", onClick = { pane = null; picker = null })
                    }
                } else if (pane == "reminder") {
                    EditorGlassGroup {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("通知する", Modifier.weight(1f))
                            Switch(checked = notify, onCheckedChange = { notify = it; if (it) onNotificationEnabled() })
                        }
                    }
                    EditorGlassGroup {
                        listOf(0, 5, 10, 15, 30, 60, 1440, 2880, 10080).forEach { minutes ->
                            Row(Modifier.fillMaxWidth().clickable {
                                reminder = minutes; legacyTime = null; notify = true; onNotificationEnabled(); pane = null
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(reminderLabel(minutes), Modifier.weight(1f))
                                RadioButton(selected = reminder == minutes, onClick = {
                                    reminder = minutes; legacyTime = null; notify = true; onNotificationEnabled(); pane = null
                                })
                            }
                        }
                    }
                    EditorGlassGroup {
                        Text("カスタム · 何分前に通知するか")
                        TextField(customReminder, { customReminder = it.filter(Char::isDigit).take(5) },
                            colors = fieldColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        TaskGlassButton("決定", enabled = customReminder.toIntOrNull() in 0..10080, onClick = {
                            reminder = customReminder.toInt(); legacyTime = null; notify = true; onNotificationEnabled(); pane = null
                        })
                    }
                } else {
                    TextField(title, { title = it }, placeholder = { Text("題名を入力してください") },
                        modifier = Modifier.fillMaxWidth().liquidGlass(24.dp).testTag("task-title"),
                        colors = fieldColors, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { finishInput() }))
                    EditorGlassGroup {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("終日", Modifier.weight(1f))
                            Switch(checked = allDay, modifier = Modifier.testTag("task-all-day"),
                                onCheckedChange = { allDay = it; picker = null })
                        }
                        EditorDateRow("開始時刻", startDate, startTime, allDay,
                            onDate = { finishInput(); picker = if (picker == "start-date") null else "start-date"; pickerMonth = YearMonth.from(LocalDate.parse(startDate)).toString() },
                            onTime = { finishInput(); picker = if (picker == "start-time") null else "start-time" })
                        if (picker == "start-date") TaskCalendar(YearMonth.parse(pickerMonth), LocalDate.parse(startDate),
                            onMonthChange = { pickerMonth = it.toString() }, onDate = {
                                startDate = it.toString(); if (LocalDate.parse(endDate) < it) endDate = it.toString(); picker = null
                            })
                        if (picker == "start-time") WheelTimePicker(LocalTime.parse(startTime)) { startTime = it.toString() }
                        EditorDateRow("終了時刻", endDate, endTime, allDay,
                            onDate = { finishInput(); picker = if (picker == "end-date") null else "end-date"; pickerMonth = YearMonth.from(LocalDate.parse(endDate)).toString() },
                            onTime = { finishInput(); picker = if (picker == "end-time") null else "end-time" })
                        if (picker == "end-date") TaskCalendar(YearMonth.parse(pickerMonth), LocalDate.parse(endDate),
                            onMonthChange = { pickerMonth = it.toString() }, onDate = { endDate = it.toString(); picker = null })
                        if (picker == "end-time") WheelTimePicker(LocalTime.parse(endTime)) { endTime = it.toString() }
                    }
                    EditorNavigationRow("繰り返し", if (repeat == TaskRepeat.Custom) "${repeatDays}日ごと" else repeat.label) { finishInput(); pane = "repeat"; picker = null }
                    EditorNavigationRow("リマインダー", if (notify) legacyTime?.let { "毎回 $it" } ?: reminderLabel(reminder) else "通知しない") { finishInput(); pane = "reminder"; picker = null }
                    TextField(memo, { memo = it }, placeholder = { Text("メモ") }, minLines = 3,
                        modifier = Modifier.fillMaxWidth().liquidGlass(24.dp).testTag("task-memo"), colors = fieldColors)
                    EditorGlassGroup {
                        Text("獲得できるXP", fontWeight = FontWeight.Bold)
                        TextField(xp, { xp = it.filter(Char::isDigit).take(3) }, suffix = { Text("XP") },
                            singleLine = true, colors = fieldColors, modifier = Modifier.testTag("task-xp"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { finishInput() }))
                        Slider(value = (xp.toIntOrNull() ?: 30).coerceIn(5, 300).toFloat(),
                            onValueChange = { xp = ((it / 5f).roundToInt() * 5).coerceIn(5, 300).toString() },
                            valueRange = 5f..300f, steps = 58)
                        Text("5〜300 XP（5XP刻み）", style = MaterialTheme.typography.bodySmall)
                    }
                    (validation ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("task-validation")) }
                    if (existing != null) TaskGlassButton("タスクを削除", enabled = !busy,
                        modifier = Modifier.fillMaxWidth(), onClick = { deleteConfirmation = true })
                }
            }
        }
    }
    if (deleteConfirmation) AlertDialog(onDismissRequest = { deleteConfirmation = false },
        title = { Text("タスクを削除しますか？") }, text = { Text("獲得済みのXPは保持されます。") },
        confirmButton = { TextButton(onClick = { deleteConfirmation = false; onDelete() }) { Text("削除") } },
        dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { Text("キャンセル") } })
}

@Composable
private fun EditorGlassGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().liquidGlass(24.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun EditorNavigationRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().liquidGlass(24.dp).clickable(onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(" ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EditorDateRow(label: String, date: String, time: String, allDay: Boolean,
    onDate: () -> Unit, onTime: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TaskGlassButton(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),
            modifier = Modifier.testTag(if (label == "開始時刻") "task-start-date" else "task-end-date"), onClick = onDate)
        if (!allDay) TaskGlassButton(LocalTime.parse(time).format(DateTimeFormatter.ofPattern("HH:mm")),
            modifier = Modifier.testTag(if (label == "開始時刻") "task-start-time" else "task-end-time"), onClick = onTime)
    }
}

internal fun reminderLabel(minutes: Int): String = when (minutes) {
    0 -> "タスク開始時"
    60 -> "1時間前"
    1440 -> "1日前"
    2880 -> "2日前"
    10080 -> "1週間前"
    else -> "${minutes}分前"
}

@Composable
private fun WheelTimePicker(time: LocalTime, onChange: (LocalTime) -> Unit) {
    Row(Modifier.fillMaxWidth().height(144.dp), horizontalArrangement = Arrangement.Center) {
        TimeWheel(24, time.hour, Modifier.weight(1f)) { onChange(time.withHour(it)) }
        Box(Modifier.height(144.dp), contentAlignment = Alignment.Center) { Text(":") }
        TimeWheel(60, time.minute, Modifier.weight(1f)) { onChange(time.withMinute(it)) }
    }
}

@Composable
private fun TimeWheel(count: Int, value: Int, modifier: Modifier, onValue: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value)
    val latest by rememberUpdatedState(onValue)
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
        }.distinctUntilChanged().collect { index -> if (index != null) latest(index) }
    }
    LazyColumn(modifier.height(144.dp).testTag(if (count == 24) "task-time-hours" else "task-time-minutes"), state = state, flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = 48.dp), overscrollEffect = null) {
        items((0 until count).toList()) { index ->
            Box(Modifier.fillMaxWidth().height(48.dp).clickable { scope.launch { state.animateScrollToItem(index) } },
                contentAlignment = Alignment.Center) {
                Text(index.toString().padStart(2, '0'), style = if (value == index) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    color = if (value == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
