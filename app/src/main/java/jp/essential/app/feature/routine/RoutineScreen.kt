package jp.essential.app.feature.routine

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.essential.app.ui.progressiveItem
import jp.essential.app.ui.EssentialBubblyProgressBar
import jp.essential.app.ui.EssentialBubblySlider
import jp.essential.app.profile.AppLevel
import jp.essential.app.profile.AppProgressStore
import jp.essential.app.profile.calculateAppLevel
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import android.Manifest
import android.os.Build

internal enum class RoutineCadence(val label: String, val pointRange: IntRange) {
    Daily("デイリー", 1..5),
    Weekly("ウィークリー", 5..15),
    Event("イベントミッション", 1..30),
}

internal data class RoutineTask(
    val id: String,
    val title: String,
    val emoji: String,
    val cadence: RoutineCadence,
    val points: Int,
    val completedPeriod: String? = null,
    val notificationEnabled: Boolean = false,
    val notificationHour: Int = 9,
    val notificationMinute: Int = 0,
    val eventStartDate: String? = null,
    val eventStartTime: String? = null,
    val eventDurationDays: Int = 1,
)

/** 既存の日課テストと互換性を保ちながら、アプリレベル計算へ移行する。 */
internal typealias RoutineLevel = AppLevel
internal fun requiredRoutineXp(level: Int): Int = jp.essential.app.profile.requiredAppXp(level)
internal fun calculateRoutineLevel(totalPoints: Int): RoutineLevel = calculateAppLevel(totalPoints)

/** 達成状態をデイリーは日付、ウィークリーはISO週単位で区切る。 */
internal fun routinePeriodKey(cadence: RoutineCadence, date: LocalDate = LocalDate.now()): String =
    when (cadence) {
        RoutineCadence.Daily -> "D:$date"
        RoutineCadence.Weekly -> {
            val weekFields = WeekFields.ISO
            "W:${date.get(weekFields.weekBasedYear())}-${date.get(weekFields.weekOfWeekBasedYear())}"
        }
        RoutineCadence.Event -> "E:$date"
    }

/** イベント日課が指定期間内かどうかを判定する。日課と週課は常に有効とする。 */
internal fun isRoutineTaskActive(
    task: RoutineTask,
    date: LocalDate = LocalDate.now(),
    time: LocalTime = LocalTime.now(),
): Boolean {
    if (task.cadence != RoutineCadence.Event) return true
    val start = runCatching { LocalDate.parse(task.eventStartDate.orEmpty()) }.getOrNull() ?: return false
    val end = start.plusDays(task.eventDurationDays.coerceAtLeast(1) - 1L)
    if (date !in start..end) return false
    if (date == start) {
        val startTime = runCatching { LocalTime.parse(task.eventStartTime.orEmpty()) }.getOrNull()
        if (startTime != null && time.isBefore(startTime)) return false
    }
    return true
}

/** 日課の種類ごとに、達成済み状態を一日単位で保存するキーを返す。 */
internal fun routineTaskPeriodKey(task: RoutineTask, date: LocalDate = LocalDate.now()): String =
    if (task.cadence == RoutineCadence.Event) routinePeriodKey(RoutineCadence.Event, date) else routinePeriodKey(task.cadence, date)

private class RoutineStore(context: Context) {
    private val preferences = context.getSharedPreferences("routine", Context.MODE_PRIVATE)
    private val progressStore = AppProgressStore(context)

    fun loadTasks(): List<RoutineTask> = runCatching {
        val array = JSONArray(preferences.getString("tasks", "[]"))
        buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val cadence = runCatching { RoutineCadence.valueOf(item.getString("cadence")) }
                    .getOrDefault(RoutineCadence.Daily)
                add(
                    RoutineTask(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        emoji = item.optString("emoji", "✨"),
                        cadence = cadence,
                        points = item.optInt("points", cadence.pointRange.first)
                            .coerceIn(cadence.pointRange),
                        completedPeriod = item.optString("completedPeriod").takeIf { it.isNotBlank() },
                        notificationEnabled = item.optBoolean("notificationEnabled", false),
                        notificationHour = item.optInt("notificationHour", 9).coerceIn(0, 23),
                        notificationMinute = item.optInt("notificationMinute", 0).coerceIn(0, 59),
                        eventStartDate = item.optString("eventStartDate").takeIf { it.isNotBlank() },
                        eventStartTime = item.optString("eventStartTime").takeIf { it.isNotBlank() },
                        eventDurationDays = item.optInt("eventDurationDays", 1).coerceIn(1, 365),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun loadTotalPoints(): Int = progressStore.loadXp()

    fun save(tasks: List<RoutineTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("emoji", task.emoji)
                    .put("cadence", task.cadence.name)
                    .put("points", task.points)
                    .put("completedPeriod", task.completedPeriod.orEmpty())
                    .put("notificationEnabled", task.notificationEnabled)
                    .put("notificationHour", task.notificationHour)
                    .put("notificationMinute", task.notificationMinute)
                    .put("eventStartDate", task.eventStartDate.orEmpty())
                    .put("eventStartTime", task.eventStartTime.orEmpty())
                    .put("eventDurationDays", task.eventDurationDays),
            )
        }
        preferences.edit().putString("tasks", array.toString()).apply()
    }
}

@Composable
internal fun RoutineScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { RoutineStore(context.applicationContext) }
    val progressStore = remember { AppProgressStore(context.applicationContext) }
    var tasks by remember { mutableStateOf(store.loadTasks()) }
    var totalPoints by remember { mutableStateOf(store.loadTotalPoints()) }
    var selectedCadenceNames by rememberSaveable {
        mutableStateOf(RoutineCadence.entries.map { it.name }.toSet())
    }
    var addCadence by remember { mutableStateOf<RoutineCadence?>(null) }
    var editingTask by remember { mutableStateOf<RoutineTask?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val level = calculateRoutineLevel(totalPoints)

    LaunchedEffect(tasks) {
        tasks.forEach { RoutineNotificationScheduler.schedule(context, it) }
    }

    fun save(updatedTasks: List<RoutineTask>) {
        tasks.filterNot { previous -> updatedTasks.any { it.id == previous.id } }
            .forEach { RoutineNotificationScheduler.cancel(context, it.id) }
        tasks = updatedTasks
        store.save(updatedTasks)
        updatedTasks.forEach { RoutineNotificationScheduler.schedule(context, it) }
    }

    fun complete(task: RoutineTask) {
        if (!isRoutineTaskActive(task)) return
        val period = routineTaskPeriodKey(task)
        if (task.completedPeriod == period) return
        val updatedTasks = tasks.map { if (it.id == task.id) it.copy(completedPeriod = period) else it }
        totalPoints = progressStore.addXp(task.points)
        save(updatedTasks)
    }

    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        progressiveItem(0) { RoutineHeader(onBack) }
        progressiveItem(1) {
            RoutineLevelCard(
                totalPoints = totalPoints,
                level = level,
            )
        }
        progressiveItem(2) {
            RoutineCadenceMultiFilter(
                selectedNames = selectedCadenceNames,
                onToggle = { cadence ->
                    selectedCadenceNames = if (cadence.name in selectedCadenceNames) {
                        selectedCadenceNames - cadence.name
                    } else {
                        selectedCadenceNames + cadence.name
                    }
                },
            )
        }
        RoutineCadence.entries.forEachIndexed { index, cadence ->
            if (cadence.name in selectedCadenceNames) progressiveItem(index + 3) {
                RoutineSection(
                    cadence = cadence,
                    tasks = tasks.filter { it.cadence == cadence },
                    onAdd = { addCadence = cadence },
                    onEdit = { editingTask = it },
                    onComplete = ::complete,
                    onDelete = { task -> save(tasks.filterNot { it.id == task.id }) },
                )
            }
        }
        if (selectedCadenceNames.isEmpty()) {
            progressiveItem(3) {
                Text(
                    "表示する日課を1つ以上選択してください",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    val editorCadence = editingTask?.cadence ?: addCadence
    editorCadence?.let { cadence ->
        RoutineEditorDialog(
            initialCadence = cadence,
            existingTask = editingTask,
            onDismiss = {
                addCadence = null
                editingTask = null
            },
            onSave = { title, emoji, selectedCadence, points, notificationEnabled, notificationHour, notificationMinute, eventStartDate, eventStartTime, eventDurationDays ->
                val existing = editingTask
                val savedTask = RoutineTask(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    title = title,
                    emoji = emoji.ifBlank { "✨" },
                    cadence = selectedCadence,
                    points = points,
                    completedPeriod = existing?.completedPeriod
                        ?.takeIf { existing.cadence == selectedCadence },
                    notificationEnabled = notificationEnabled,
                    notificationHour = notificationHour,
                    notificationMinute = notificationMinute,
                    eventStartDate = eventStartDate.takeIf { selectedCadence == RoutineCadence.Event },
                    eventStartTime = eventStartTime.takeIf { selectedCadence == RoutineCadence.Event },
                    eventDurationDays = eventDurationDays,
                )
                save(if (existing == null) tasks + savedTask else tasks.map { if (it.id == existing.id) savedTask else it })
                if (savedTask.notificationEnabled && Build.VERSION.SDK_INT >= 33) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                addCadence = null
                editingTask = null
            },
        )
    }

}

@Composable
private fun RoutineHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onBack,
            modifier = Modifier.size(46.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
        ) { Text("‹", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("日課", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("小さな達成を、毎日の力に。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 日課の表示対象を複数選択できるフィルター。 */
@Composable
private fun RoutineCadenceMultiFilter(
    selectedNames: Set<String>,
    onToggle: (RoutineCadence) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("表示する日課", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoutineCadence.entries.forEach { cadence ->
                FilterChip(
                    selected = cadence.name in selectedNames,
                    onClick = { onToggle(cadence) },
                    label = { Text(cadence.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            "複数選択できます",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RoutineLevelCard(totalPoints: Int, level: RoutineLevel) {
    val shape = RoundedCornerShape(36.dp, 36.dp, 18.dp, 36.dp)
    Box(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xCC21B79B), Color(0xCC5B8DFF), Color(0xAA8D6AFF))))
            .border(1.dp, Color.White.copy(alpha = 0.56f), shape)
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(70.dp).clip(RoundedCornerShape(25.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text("LV\n${level.level}", color = Color.White, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("合計 $totalPoints pt", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(
                        if (level.level == 60) "MAX LEVEL" else "次のレベルまで ${level.pointsForNextLevel - level.pointsInLevel} XP",
                        color = Color.White.copy(alpha = 0.82f),
                    )
                    if (level.level < 60) {
                        Text(
                            "必要XP ${level.pointsForNextLevel}",
                            color = Color.White.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            EssentialBubblyProgressBar(
                progress = level.progress,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("レベルが上がるほど、次のレベルに必要なポイントが増えます", color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RoutineSection(
    cadence: RoutineCadence,
    tasks: List<RoutineTask>,
    onAdd: () -> Unit,
    onEdit: (RoutineTask) -> Unit,
    onComplete: (RoutineTask) -> Unit,
    onDelete: (RoutineTask) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cadence.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    when (cadence) {
                        RoutineCadence.Daily -> "毎日リセット"
                        RoutineCadence.Weekly -> "毎週月曜日にリセット"
                        RoutineCadence.Event -> "指定期間中、毎日1回"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onAdd, shape = RoundedCornerShape(18.dp)) { Text("＋ 追加") }
        }
        if (tasks.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
                    .border(1.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(26.dp))
                    .padding(22.dp),
            ) { Text("まだ目標がありません。絵文字と一緒に追加しましょう。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            tasks.forEach { task ->
                RoutineTaskCard(
                    task = task,
                    completed = isRoutineTaskActive(task) && task.completedPeriod == routineTaskPeriodKey(task),
                    active = isRoutineTaskActive(task),
                    onEdit = onEdit,
                    onComplete = onComplete,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun RoutineTaskCard(
    task: RoutineTask,
    completed: Boolean,
    active: Boolean,
    onEdit: (RoutineTask) -> Unit,
    onComplete: (RoutineTask) -> Unit,
    onDelete: (RoutineTask) -> Unit,
) {
    val cardColor by animateColorAsState(
        if (completed) Color(0xFF27B99A).copy(alpha = 0.24f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "日課達成カード",
    )
    val shape = RoundedCornerShape(28.dp, 28.dp, 14.dp, 28.dp)
    Column(
        modifier = Modifier.fillMaxWidth().clip(shape).background(cardColor)
            .border(1.dp, Color.White.copy(alpha = 0.46f), shape).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.pointerInput(task.id) {
                detectTapGestures(onLongPress = { onEdit(task) })
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) { Text(task.emoji, style = MaterialTheme.typography.headlineSmall) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("+${task.points} pt", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            AnimatedVisibility(completed, enter = fadeIn() + scaleIn()) {
                Text("達成済み", color = Color(0xFF15836E), fontWeight = FontWeight.Black)
            }
        }
        if (task.notificationEnabled || task.cadence == RoutineCadence.Event) {
            Text(
                buildString {
                    if (task.notificationEnabled) append("通知 ${task.notificationHour.toString().padStart(2, '0')}:${task.notificationMinute.toString().padStart(2, '0')}")
                    if (task.cadence == RoutineCadence.Event) {
                        if (isNotEmpty()) append("　")
                        append("期間 ${task.eventStartDate ?: "未設定"}〜${task.eventDurationDays}日")
                        if (!active) append("（期間外）")
                    }
                },
                color = if (active) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            if (task.cadence == RoutineCadence.Event) "長押しで通知・開催期間を設定" else "長押しで通知時刻などの詳細設定",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onEdit(task) }) { Text("設定") }
            TextButton(onClick = { onDelete(task) }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            if (!completed && active) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onComplete(task) },
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 9.dp),
                ) { Text("達成") }
            }
        }
    }
}

@Composable
private fun RoutineEditorDialog(
    initialCadence: RoutineCadence,
    existingTask: RoutineTask?,
    onDismiss: () -> Unit,
    onSave: (String, String, RoutineCadence, Int, Boolean, Int, Int, String, String, Int) -> Unit,
) {
    var title by remember(existingTask?.id) { mutableStateOf(existingTask?.title.orEmpty()) }
    var emoji by remember(existingTask?.id) { mutableStateOf(existingTask?.emoji ?: "✨") }
    var cadence by remember(existingTask?.id, initialCadence) { mutableStateOf(existingTask?.cadence ?: initialCadence) }
    var points by remember(existingTask?.id, initialCadence) {
        mutableStateOf(existingTask?.points ?: initialCadence.pointRange.first)
    }
    var notificationEnabled by remember(existingTask?.id, initialCadence) { mutableStateOf(existingTask?.notificationEnabled ?: false) }
    var notificationHour by remember(existingTask?.id, initialCadence) { mutableStateOf((existingTask?.notificationHour ?: 9).toString()) }
    var notificationMinute by remember(existingTask?.id, initialCadence) { mutableStateOf((existingTask?.notificationMinute ?: 0).toString().padStart(2, '0')) }
    var eventStartDate by remember(existingTask?.id, initialCadence) {
        mutableStateOf(existingTask?.eventStartDate ?: LocalDate.now().toString())
    }
    var eventStartTime by remember(existingTask?.id, initialCadence) {
        mutableStateOf(existingTask?.eventStartTime ?: "09:00")
    }
    var eventDurationDays by remember(existingTask?.id, initialCadence) {
        mutableStateOf((existingTask?.eventDurationDays ?: 1).toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(34.dp),
        title = { Text(if (existingTask == null) "新しい日課" else "日課を編集", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutineCadence.entries.forEach { option ->
                        FilterChip(
                            selected = cadence == option,
                            onClick = {
                                cadence = option
                                points = points.coerceIn(option.pointRange)
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
                OutlinedTextField(value = emoji, onValueChange = { emoji = it.take(4) }, label = { Text("絵文字") }, singleLine = true)
                OutlinedTextField(value = title, onValueChange = { title = it.take(60) }, label = { Text("目標") }, singleLine = true)
                Text("達成ポイント　$points pt", fontWeight = FontWeight.Bold)
                EssentialBubblySlider(
                    value = points.toFloat(),
                    onValueChange = { points = it.toInt().coerceIn(cadence.pointRange) },
                    valueRange = cadence.pointRange.first.toFloat()..cadence.pointRange.last.toFloat(),
                    steps = (cadence.pointRange.last - cadence.pointRange.first - 1).coerceAtLeast(0),
                )
                Text("${cadence.pointRange.first}〜${cadence.pointRange.last}ptから設定できます", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (cadence == RoutineCadence.Event) {
                    Text("イベント期間", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = eventStartDate,
                        onValueChange = { eventStartDate = it.take(10) },
                        label = { Text("開始日 (YYYY-MM-DD)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = eventStartTime,
                        onValueChange = { eventStartTime = it.take(5) },
                        label = { Text("開始時刻 (HH:MM)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = eventDurationDays,
                        onValueChange = { eventDurationDays = it.filter(Char::isDigit).take(3) },
                        label = { Text("期間（日数）") },
                        supportingText = { Text("期間中は1日1回達成できます") },
                        singleLine = true,
                    )
                }
                Text("通知設定", fontWeight = FontWeight.Bold)
                FilterChip(
                    selected = notificationEnabled,
                    onClick = { notificationEnabled = !notificationEnabled },
                    label = { Text(if (notificationEnabled) "通知オン" else "通知オフ") },
                )
                if (notificationEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = notificationHour,
                            onValueChange = { notificationHour = it.filter(Char::isDigit).take(2) },
                            label = { Text("時") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = notificationMinute,
                            onValueChange = { notificationMinute = it.filter(Char::isDigit).take(2) },
                            label = { Text("分") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text("指定時刻にこの項目を通知します", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title.trim(),
                        emoji.trim(),
                        cadence,
                        points,
                        notificationEnabled,
                        notificationHour.toIntOrNull()?.coerceIn(0, 23) ?: 9,
                        notificationMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                        eventStartDate,
                        eventStartTime,
                        eventDurationDays.toIntOrNull()?.coerceIn(1, 365) ?: 1,
                    )
                },
                enabled = title.isNotBlank() && (
                    cadence != RoutineCadence.Event || (
                        runCatching { LocalDate.parse(eventStartDate) }.isSuccess &&
                            runCatching { LocalTime.parse(eventStartTime) }.isSuccess
                        )
                    ),
            ) {
                Text(if (existingTask == null) "追加する" else "変更を保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
