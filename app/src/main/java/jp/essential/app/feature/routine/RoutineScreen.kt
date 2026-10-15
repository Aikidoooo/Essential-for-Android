package jp.essential.app.feature.routine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import jp.essential.app.R
import jp.essential.app.ui.FeatureHeader
import jp.essential.app.ui.elasticListEdges
import jp.essential.app.ui.fixedHeader
import jp.essential.app.ui.liquidGlass
import jp.essential.app.ui.progressiveItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** 月の予定、期限順のタスク、日別の24時間表を一つのタスク機能として提供する。 */
@Composable
internal fun RoutineScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { TaskStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf<List<ScheduledTask>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var dayValue by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingSnapshot by remember { mutableStateOf<ScheduledTask?>(null) }
    var editorClosing by remember { mutableStateOf(false) }
    var draftStart by rememberSaveable { mutableStateOf(LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).toString()) }
    var calendarFilter by remember { mutableStateOf(store.loadFilter("calendar")) }
    var listFilter by remember { mutableStateOf(store.loadFilter("list")) }
    var filterTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val homeListState = rememberLazyListState()
    var permissionMissing by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionMissing = !granted
    }
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(store) {
        try { tasks = withContext(Dispatchers.IO) { store.load() }; loaded = true }
        catch (_: Exception) { error = "タスクを読み込めませんでした。保存データは保持しています。" }
    }
    LaunchedEffect(loaded) {
        if (loaded) withContext(Dispatchers.IO) { store.rescheduleAll() }
    }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }

    fun persist(updated: List<ScheduledTask>, closeEditor: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    store.save(updated)
                }
                tasks = updated
                error = null
                if (closeEditor) editorClosing = true
            } catch (_: Exception) { error = "タスクを保存できませんでした。もう一度お試しください。" }
            finally { busy = false }
        }
    }
    fun complete(occurrence: TaskOccurrence) {
        if (busy) return
        busy = true
        scope.launch {
            try { tasks = withContext(Dispatchers.IO) { store.complete(occurrence.task.id, occurrence.key) }; error = null }
            catch (_: Exception) { error = "完了を保存できませんでした。もう一度お試しください。" }
            finally { busy = false }
        }
    }
    fun openEditor(task: ScheduledTask? = null, initial: LocalDateTime = now.withMinute(0).withSecond(0).withNano(0)) {
        if (!loaded || busy) return
        editingId = task?.id
        editingSnapshot = task
        editorClosing = false
        error = null
        draftStart = initial.toString()
        editorOpen = true
    }
    BackHandler(enabled = !editorOpen && filterTarget == null) {
        if (dayValue != null) dayValue = null else onBack()
    }
    val month = YearMonth.parse(monthValue)
    val highlights by produceState<Map<LocalDate, TaskStatus>>(emptyMap(), tasks, month, calendarFilter, now) {
        value = withContext(Dispatchers.Default) {
        (1..month.lengthOfMonth()).mapNotNull { day ->
            val date = month.atDay(day)
            val statuses = tasks.flatMap { occurrencesOn(it, date) }.map { it.status(now) }.filter { it in calendarFilter }
            val status = when {
                TaskStatus.Expired in statuses -> TaskStatus.Expired
                TaskStatus.Pending in statuses -> TaskStatus.Pending
                TaskStatus.Completed in statuses -> TaskStatus.Completed
                else -> null
            }
            status?.let { date to it }
        }.toMap()
        }
    }
    val ordered by produceState<List<TaskOccurrence>>(emptyList(), tasks, listFilter, now) {
        value = withContext(Dispatchers.Default) { taskList(tasks, listFilter, now) }
    }
    AnimatedContent(targetState = dayValue, label = "日別予定への移動", transitionSpec = {
        (fadeIn(TaskMotion.fade) + slideInHorizontally(TaskMotion.navigation) { it / 12 })
            .togetherWith(fadeOut(TaskMotion.fade) + slideOutHorizontally(TaskMotion.navigation) { -it / 12 })
    }) { day ->
        if (day == null) {
            LazyColumn(modifier = Modifier.fillMaxSize().elasticListEdges().testTag("task-home"),
                state = homeListState, overscrollEffect = null, contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                fixedHeader { FeatureHeader("日課", onBack) }
                progressiveItem(0, keyPrefix = "tasks") {
                    Column(Modifier.fillMaxWidth().liquidGlass(28.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("カレンダー", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            TaskGlassButton("Multi-select", modifier = Modifier.testTag("calendar-multi-select"), onClick = { filterTarget = "calendar" })
                        }
                        TaskCalendar(month, null, highlights,
                            onMonthChange = { monthValue = it.toString() }, onDate = { dayValue = it.toString() })
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("● 未完了", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            Text("● 完了", color = Color(0xFF39B98A), style = MaterialTheme.typography.labelSmall)
                            Text("● 期限切れ", color = Color(0xFFE7A453), style = MaterialTheme.typography.labelSmall)
                        }
                        TaskGlassButton("今日", modifier = Modifier.align(Alignment.End), onClick = { monthValue = YearMonth.now().toString() })
                    }
                }
                progressiveItem(1, keyPrefix = "tasks") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("タスク一覧", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            TaskGlassButton("Multi-select", modifier = Modifier.testTag("list-multi-select"), onClick = { filterTarget = "list" })
                            TaskGlassButton("タスクを追加", icon = R.drawable.ic_mannaka_add, iconOnly = true,
                                modifier = Modifier.size(44.dp).testTag("task-add"), enabled = loaded && !busy, onClick = { openEditor() })
                        }
                        Text("終了が早い順 · ${ordered.size}件", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (permissionMissing) Text("通知するには端末の通知許可が必要です", color = MaterialTheme.colorScheme.error)
                        if (!loaded && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                if (loaded && ordered.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().liquidGlass(24.dp).padding(24.dp)) {
                        Text(if (tasks.isEmpty()) "＋から最初のタスクを追加" else "選択した状態のタスクはありません")
                    }
                }
                items(ordered, key = { it.rewardId }) { occurrence ->
                    ScheduledTaskCard(occurrence, now, !busy,
                        onEdit = { openEditor(occurrence.task) }, onComplete = { complete(occurrence) })
                }
            }
        } else {
            TaskDayTable(LocalDate.parse(day), tasks, calendarFilter, now, !busy,
                onBack = { dayValue = null }, onAdd = { openEditor(initial = it) },
                onEdit = { openEditor(it) }, onComplete = { complete(it) },
                onFilter = { filterTarget = "calendar" }, onDay = { dayValue = it.toString() })
        }
    }
    if (editorOpen && loaded) {
        val existing = tasks.firstOrNull { it.id == editingId } ?: editingSnapshot
        TaskEditor(existing, LocalDateTime.parse(draftStart), busy,
            onDismiss = { editorOpen = false; editorClosing = false; editingSnapshot = null },
            onSave = { updated ->
                editingId = updated.id
                editingSnapshot = updated
                persist(if (tasks.any { it.id == updated.id }) tasks.map { if (it.id == updated.id) updated else it } else tasks + updated, true)
            },
            onDelete = { persist(tasks.filterNot { it.id == editingId }, true) }, onNotificationEnabled = ::requestNotifications,
            closeRequested = editorClosing, onCloseInterrupted = { editorClosing = false }, saveError = error)
    }
    filterTarget?.let { target ->
        TaskFilterSheet(if (target == "calendar") calendarFilter else listFilter, onDismiss = { filterTarget = null }, onChange = { statuses ->
            if (target == "calendar") calendarFilter = statuses else listFilter = statuses
            store.saveFilter(target, statuses)
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFilterSheet(statuses: Set<TaskStatus>, onDismiss: () -> Unit, onChange: (Set<TaskStatus>) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().liquidGlass(32.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("表示するタスク", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("状態を複数選択できます", style = MaterialTheme.typography.bodySmall)
            TaskStatusFilters(statuses, onChange)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TaskGlassButton("すべて", onClick = { onChange(TaskStatus.entries.toSet()) })
                TaskGlassButton("解除", onClick = { onChange(emptySet()) })
                TaskGlassButton("決定", onClick = { scope.launch { sheetState.hide(); onDismiss() } })
            }
        }
    }
}

/** 各時間帯に重なる予定を表示し、空き枠からその時刻の予定を追加する。 */
@Composable
private fun TaskDayTable(date: LocalDate, tasks: List<ScheduledTask>, filter: Set<TaskStatus>, now: LocalDateTime,
    enabled: Boolean, onBack: () -> Unit, onAdd: (LocalDateTime) -> Unit, onEdit: (ScheduledTask) -> Unit,
    onComplete: (TaskOccurrence) -> Unit, onFilter: () -> Unit, onDay: (LocalDate) -> Unit) {
    val data by produceState<List<TaskOccurrence>?>(null, tasks, date, filter, now) {
        value = withContext(Dispatchers.Default) {
            tasks.flatMap { occurrencesOn(it, date) }.filter { it.status(now) in filter }.sortedBy { it.start }
        }
    }
    val occurrences = data.orEmpty()
    LazyColumn(Modifier.fillMaxSize().elasticListEdges().testTag("task-day-table"), overscrollEffect = null,
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 36.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fixedHeader { FeatureHeader(date.format(DateTimeFormatter.ofPattern("M月d日")), onBack) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskGlassButton("前の日", icon = R.drawable.ic_mannaka_arrow_back, iconOnly = true,
                    modifier = Modifier.size(44.dp), onClick = { onDay(date.minusDays(1)) })
                Text("24時間の予定", Modifier.weight(1f).then(if (data != null) Modifier.testTag("task-day-ready") else Modifier), fontWeight = FontWeight.Bold)
                TaskGlassButton("Multi-select", onClick = onFilter)
                TaskGlassButton("次の日", icon = R.drawable.ic_mannaka_chevron_right, iconOnly = true,
                    modifier = Modifier.size(44.dp), onClick = { onDay(date.plusDays(1)) })
            }
        }
        val allDay = occurrences.filter { it.task.allDay }
        if (allDay.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("終日", style = MaterialTheme.typography.labelLarge)
                allDay.forEach { occurrence -> ScheduledTaskCard(occurrence, now, enabled,
                    onEdit = { onEdit(occurrence.task) }, onComplete = { onComplete(occurrence) }) }
            }
        }
        items(24, key = { it }) { hour ->
            val start = date.atTime(hour, 0)
            val end = start.plusHours(1)
            val entries = occurrences.filter { !it.task.allDay && it.start < end && it.end > start }
            Row(Modifier.fillMaxWidth().heightIn(min = 76.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${hour.toString().padStart(2, '0')}:00", Modifier.width(48.dp).padding(top = 14.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entries.isEmpty()) {
                    Box(Modifier.weight(1f).height(72.dp).liquidGlass(20.dp).clickable(enabled = enabled) { onAdd(start) }
                        .testTag("task-hour-$hour"), contentAlignment = Alignment.CenterStart) {
                        Text("＋ 予定を追加", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.forEach { occurrence -> ScheduledTaskCard(occurrence, now, enabled,
                            onEdit = { onEdit(occurrence.task) }, onComplete = { onComplete(occurrence) }) }
                        TaskGlassButton("この時間に追加", enabled = enabled, onClick = { onAdd(start) })
                    }
                }
            }
        }
    }
}
