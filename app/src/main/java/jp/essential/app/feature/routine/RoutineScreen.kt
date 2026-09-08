package jp.essential.app.feature.routine

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.essential.app.ui.progressiveItem
import jp.essential.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

internal enum class RoutineCadence(val label: String, val pointRange: IntRange) {
    Daily("デイリー", 1..5),
    Weekly("ウィークリー", 5..15),
}

internal data class RoutineTask(
    val id: String,
    val title: String,
    val emoji: String,
    val cadence: RoutineCadence,
    val points: Int,
    val completedPeriod: String? = null,
)

internal data class RoutineLevel(
    val level: Int,
    val pointsInLevel: Int,
    val pointsForNextLevel: Int,
    val progress: Float,
)

private data class RoutineReward(
    val level: Int,
    val title: String,
    val description: String,
    val imageRes: Int,
)

private val routineRewards = listOf(
    RoutineReward(16, "Anemo Essential", "アネモカラーのアプリアイコン", R.drawable.routine_reward_lv16),
    RoutineReward(20, "Geo Essential", "ジオカラーのアプリアイコン", R.drawable.routine_reward_lv20),
    RoutineReward(25, "Electro Essential", "エレクトロカラーのアプリアイコン", R.drawable.routine_reward_lv25),
    RoutineReward(30, "Dendro Essential", "デンドロカラーのアプリアイコン", R.drawable.routine_reward_lv30),
    RoutineReward(35, "Hydro Essential", "ハイドロカラーのアプリアイコン", R.drawable.routine_reward_lv35),
    RoutineReward(40, "Pyro Essential", "パイロカラーのアプリアイコン", R.drawable.routine_reward_lv40),
    RoutineReward(50, "Lunar Essential", "月夜と青空のアプリアイコン", R.drawable.routine_reward_lv50),
    RoutineReward(55, "Cryo Essential", "クライオカラーのアプリアイコン", R.drawable.routine_reward_lv55),
    RoutineReward(60, "アイコン変更権", "ローズカラーのアプリアイコンと変更権", R.drawable.routine_reward_lv60),
)

/** 指定レベルから次のレベルへ進むために必要なXPを返す。最大レベルでは0を返す。 */
internal fun requiredRoutineXp(level: Int): Int {
    if (level < 1 || level >= 60) return 0
    val raw = when {
        level < 16 -> 375 + 118 * (level - 1)
        level < 40 -> 2375 + 290 * (level - 16)
        level < 50 -> 10550 + 960 * (level - 40)
        level < 55 -> 26400 + 2400 * (level - 50)
        else -> {
            val offset = level - 55
            232350 + 26490 * offset + 110 * offset * offset
        }
    }
    return kotlin.math.round(raw / 15.0).toInt()
}

/** 合計XPから、最大60のレベルと次のレベルまでの進捗を求める。 */
internal fun calculateRoutineLevel(totalPoints: Int): RoutineLevel {
    var level = 1
    var remaining = totalPoints.coerceAtLeast(0)
    while (level < 60) {
        val required = requiredRoutineXp(level)
        if (remaining < required) {
            return RoutineLevel(level, remaining, required, remaining.toFloat() / required)
        }
        remaining -= required
        level++
    }
    return RoutineLevel(60, remaining, 0, 1f)
}

/** 達成状態をデイリーは日付、ウィークリーはISO週単位で区切る。 */
internal fun routinePeriodKey(cadence: RoutineCadence, date: LocalDate = LocalDate.now()): String =
    when (cadence) {
        RoutineCadence.Daily -> "D:$date"
        RoutineCadence.Weekly -> {
            val weekFields = WeekFields.ISO
            "W:${date.get(weekFields.weekBasedYear())}-${date.get(weekFields.weekOfWeekBasedYear())}"
        }
    }

private class RoutineStore(context: Context) {
    private val preferences = context.getSharedPreferences("routine", Context.MODE_PRIVATE)

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
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun loadTotalPoints(): Int = preferences.getInt("total_points", 0).coerceAtLeast(0)

    fun loadClaimedRewards(): Set<Int> = preferences.getStringSet("claimed_rewards", emptySet())
        .orEmpty()
        .mapNotNull { it.toIntOrNull() }
        .toSet()

    fun save(tasks: List<RoutineTask>, totalPoints: Int) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("emoji", task.emoji)
                    .put("cadence", task.cadence.name)
                    .put("points", task.points)
                    .put("completedPeriod", task.completedPeriod.orEmpty()),
            )
        }
        preferences.edit().putString("tasks", array.toString()).putInt("total_points", totalPoints).apply()
    }

    fun saveClaimedRewards(levels: Set<Int>) {
        preferences.edit().putStringSet("claimed_rewards", levels.map(Int::toString).toSet()).apply()
    }
}

@Composable
internal fun RoutineScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { RoutineStore(context.applicationContext) }
    var tasks by remember { mutableStateOf(store.loadTasks()) }
    var totalPoints by remember { mutableStateOf(store.loadTotalPoints()) }
    var claimedRewards by remember { mutableStateOf(store.loadClaimedRewards()) }
    var rewardsExpanded by rememberSaveable { mutableStateOf(true) }
    var addCadence by remember { mutableStateOf<RoutineCadence?>(null) }
    var editingTask by remember { mutableStateOf<RoutineTask?>(null) }
    var showPointResetConfirmation by remember { mutableStateOf(false) }
    val level = calculateRoutineLevel(totalPoints)

    fun save(updatedTasks: List<RoutineTask>, updatedPoints: Int = totalPoints) {
        tasks = updatedTasks
        totalPoints = updatedPoints
        store.save(updatedTasks, updatedPoints)
    }

    fun complete(task: RoutineTask) {
        val period = routinePeriodKey(task.cadence)
        if (task.completedPeriod == period) return
        save(
            tasks.map { if (it.id == task.id) it.copy(completedPeriod = period) else it },
            totalPoints + task.points,
        )
    }

    fun claimReward(rewardLevel: Int) {
        if (level.level < rewardLevel || rewardLevel in claimedRewards) return
        claimedRewards = claimedRewards + rewardLevel
        store.saveClaimedRewards(claimedRewards)
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
                onResetPoints = { showPointResetConfirmation = true },
            )
        }
        progressiveItem(2) {
            RoutineRewards(
                currentLevel = level,
                claimedRewards = claimedRewards,
                onClaim = ::claimReward,
                expanded = rewardsExpanded,
                onExpandedChange = { rewardsExpanded = it },
            )
        }
        RoutineCadence.entries.forEachIndexed { index, cadence ->
            progressiveItem(index + 3) {
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
            onSave = { title, emoji, selectedCadence, points ->
                val existing = editingTask
                val savedTask = RoutineTask(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    title = title,
                    emoji = emoji.ifBlank { "✨" },
                    cadence = selectedCadence,
                    points = points,
                    completedPeriod = existing?.completedPeriod
                        ?.takeIf { existing.cadence == selectedCadence },
                )
                save(if (existing == null) tasks + savedTask else tasks.map { if (it.id == existing.id) savedTask else it })
                addCadence = null
                editingTask = null
            },
        )
    }

    if (showPointResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showPointResetConfirmation = false },
            shape = RoundedCornerShape(32.dp),
            title = { Text("ポイントをリセット", fontWeight = FontWeight.Black) },
            text = { Text("合計ポイントとレベルを0へ戻します。作成した日課と達成状態は残ります。") },
            confirmButton = {
                Button(
                    onClick = {
                        save(tasks, 0)
                        showPointResetConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("0 ptに戻す") }
            },
            dismissButton = {
                TextButton(onClick = { showPointResetConfirmation = false }) { Text("キャンセル") }
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

@Composable
private fun RoutineLevelCard(totalPoints: Int, level: RoutineLevel, onResetPoints: () -> Unit) {
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
            LinearProgressIndicator(
                progress = { level.progress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.20f),
            )
            Text("レベルが上がるほど、次のレベルに必要なポイントが増えます", color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = onResetPoints,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) { Text("ポイントをリセット") }
        }
    }
}

@Composable
private fun RoutineRewards(
    currentLevel: RoutineLevel,
    claimedRewards: Set<Int>,
    onClaim: (Int) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.animateContentSize(animationSpec = tween(440, easing = FastOutSlowInEasing)),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onExpandedChange(!expanded) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("レベル報酬", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(if (expanded) "到達したレベルの報酬を受け取れます" else "タップして報酬一覧を表示", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("${claimedRewards.size}/${routineRewards.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp))
            RoutineChevron(expanded = expanded)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(440, easing = FastOutSlowInEasing)) +
                fadeIn(animationSpec = tween(260, delayMillis = 40)),
            exit = shrinkVertically(animationSpec = tween(380, easing = FastOutSlowInEasing)) +
                fadeOut(animationSpec = tween(180)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                routineRewards.forEach { reward ->
                    val unlocked = currentLevel.level >= reward.level
                    val claimed = reward.level in claimedRewards
                    val shape = RoundedCornerShape(25.dp, 25.dp, 13.dp, 25.dp)
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(shape)
                            .background(
                                if (unlocked) MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                            )
                            .border(
                                1.dp,
                                if (unlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                                else Color.White.copy(alpha = 0.28f),
                                shape,
                            )
                            .padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(reward.imageRes),
                                contentDescription = reward.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(17.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(reward.title, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(reward.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("LV${reward.level}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                        }
                        when {
                            claimed -> Text("✓ 獲得済み", color = Color(0xFF168B74), fontWeight = FontWeight.Black)
                            unlocked -> Button(onClick = { onClaim(reward.level) }, modifier = Modifier.fillMaxWidth()) {
                                Text("報酬を受け取る")
                            }
                            else -> Text("LV${reward.level}で解放", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/** 日課報酬の展開状態を広いV字で示すインジケーター。 */
@Composable
private fun RoutineChevron(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "日課報酬の折りたたみ矢印",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer { rotationZ = rotation },
    ) {
        val stroke = 3.6.dp.toPx()
        drawLine(color, Offset(size.width * 0.12f, size.height * 0.35f), Offset(size.width * 0.5f, size.height * 0.65f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.5f, size.height * 0.65f), Offset(size.width * 0.88f, size.height * 0.35f), stroke, StrokeCap.Round)
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
                Text(if (cadence == RoutineCadence.Daily) "毎日リセット" else "毎週月曜日にリセット", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                RoutineTaskCard(task, task.completedPeriod == routinePeriodKey(task.cadence), onEdit, onComplete, onDelete)
            }
        }
    }
}

@Composable
private fun RoutineTaskCard(
    task: RoutineTask,
    completed: Boolean,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onEdit(task) }) { Text("編集") }
            TextButton(onClick = { onDelete(task) }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            if (!completed) {
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
    onSave: (String, String, RoutineCadence, Int) -> Unit,
) {
    var title by remember(existingTask?.id) { mutableStateOf(existingTask?.title.orEmpty()) }
    var emoji by remember(existingTask?.id) { mutableStateOf(existingTask?.emoji ?: "✨") }
    var cadence by remember(existingTask?.id, initialCadence) { mutableStateOf(existingTask?.cadence ?: initialCadence) }
    var points by remember(existingTask?.id, initialCadence) {
        mutableStateOf(existingTask?.points ?: initialCadence.pointRange.first)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(34.dp),
        title = { Text(if (existingTask == null) "新しい日課" else "日課を編集", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
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
                Slider(
                    value = points.toFloat(),
                    onValueChange = { points = it.toInt().coerceIn(cadence.pointRange) },
                    valueRange = cadence.pointRange.first.toFloat()..cadence.pointRange.last.toFloat(),
                    steps = (cadence.pointRange.last - cadence.pointRange.first - 1).coerceAtLeast(0),
                )
                Text("${cadence.pointRange.first}〜${cadence.pointRange.last}ptから設定できます", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title.trim(), emoji.trim(), cadence, points) }, enabled = title.isNotBlank()) {
                Text(if (existingTask == null) "追加する" else "変更を保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
