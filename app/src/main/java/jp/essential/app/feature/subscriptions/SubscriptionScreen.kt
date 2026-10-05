package jp.essential.app.feature.subscriptions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.essential.app.ui.FeatureHeader
import jp.essential.app.ui.fixedHeader
import jp.essential.app.ui.GlassFeatureTitle
import jp.essential.app.ui.theme.LocalEssentialDark
import java.time.LocalDate
import java.time.YearMonth
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun yenText(amount: Long): String = "¥${NumberFormat.getIntegerInstance(Locale.JAPAN).format(amount)}"

@Composable
internal fun SubscriptionGlass(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Surface(Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = .4f), shape),
        shape = shape, color = MaterialTheme.colorScheme.surface.copy(alpha = .65f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun SubscriptionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SubscriptionStore(context) }
    var entries by remember { mutableStateOf(store.load()) }
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthValue)
    var selectedValue by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selected = LocalDate.parse(selectedValue)
    var editor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Subscription?>(null) }
    var deleting by remember { mutableStateOf<Subscription?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun persist(updated: List<Subscription>, completed: () -> Unit) {
        saving = true
        scope.launch {
            val success = withContext(Dispatchers.IO) { store.save(updated) }
            saving = false
            if (success) { entries = updated; error = null; completed() }
            else error = "保存できませんでした。もう一度お試しください。"
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        fixedHeader { FeatureHeader("サブスク管理", onBack) }
        item { SubscriptionGlass {
            Text("毎月の合計", style = MaterialTheme.typography.titleMedium)
            Text(yenText(entries.filter { it.paymentIn(YearMonth.now()) != null }.sumOf { it.yen }),
                style = MaterialTheme.typography.headlineLarge)
            Text("${entries.size}件登録 · 円／月")
            Button(onClick = { editing = null; editor = true }, enabled = !saving) { Text("サブスクを追加") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }
        item { SubscriptionGlass {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { monthValue = month.minusMonths(1).toString(); selectedValue = month.minusMonths(1).atDay(1).toString() }) { Text("前月") }
                Text("${month.year}年${month.monthValue}月", Modifier.weight(1f))
                TextButton(onClick = { monthValue = month.plusMonths(1).toString(); selectedValue = month.plusMonths(1).atDay(1).toString() }) { Text("翌月") }
            }
            Text("この月の決済 ${yenText(entries.filter { it.paymentIn(month) != null }.sumOf { it.yen })}")
            Row { listOf("月", "火", "水", "木", "金", "土", "日").forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it) }
            } }
            val offset = month.atDay(1).dayOfWeek.value - 1
            repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
                Row {
                    repeat(7) { column ->
                        val day = week * 7 + column - offset + 1
                        if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(56.dp))
                        else {
                            val date = month.atDay(day)
                            val count = entries.count { it.paymentIn(month) == date }
                            val color by animateColorAsState(if (date == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "決済日の選択")
                            Surface(onClick = { selectedValue = date.toString() }, modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(16.dp), color = color) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(day.toString())
                                    Text(if (count > 0) "● $count" else "", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            Text("${selected.monthValue}月${selected.dayOfMonth}日の決済", style = MaterialTheme.typography.titleMedium)
            val due = entries.filter { it.paymentIn(month) == selected }
            if (due.isEmpty()) Text("決済予定はありません")
            due.forEach { Text("${it.name}  ${yenText(it.yen)}") }
        } }
        item { Text("登録したサブスク", style = MaterialTheme.typography.titleLarge) }
        if (entries.isEmpty()) item { Text("月額と決済日を追加すると、カレンダーに表示されます。") }
        entries.forEach { entry -> item(key = entry.id) { SubscriptionGlass {
            Text(entry.name, style = MaterialTheme.typography.titleMedium)
            Text("${yenText(entry.yen)}／月 · 毎月${entry.billingDay}日")
            Text("開始日 ${entry.startDate} · 月末にない日は末日に決済", style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { editing = entry; editor = true }, enabled = !saving) { Text("編集") }
                TextButton(onClick = { deleting = entry }, enabled = !saving) { Text("削除") }
            }
        } } }
    }
    if (editor) SubscriptionEditor(editing, saving, error, { if (!saving) editor = false }) { entry ->
        persist(entries.filterNot { it.id == entry.id } + entry) { editor = false }
    }
    deleting?.let { entry -> SubscriptionDeleteDialog(entry, saving, error,
        onDismiss = { deleting = null },
        onDelete = { persist(entries.filterNot { it.id == entry.id }) { deleting = null } }) }

}

/** 追加・削除画面で共通の、背面だけをぼかすガラス表現。 */
@Composable
private fun SubscriptionDialogBackdrop() {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val previousDim = window?.attributes?.dimAmount
            val activity = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }
                .filterIsInstance<android.app.Activity>().firstOrNull()
            // Essentialカレンダーと同様、背面だけをぼかしてガラス越しの色を残す。
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val radius = 18f * view.resources.displayMetrics.density
                activity?.window?.decorView?.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                    radius, radius, android.graphics.Shader.TileMode.CLAMP))
            }
            window?.setDimAmount(.14f)
            onDispose {
                previousDim?.let { window?.setDimAmount(it) }
                if (android.os.Build.VERSION.SDK_INT >= 31) activity?.window?.decorView?.setRenderEffect(null)
            }
        }
}

/** 対象の金額と決済日を確認してから、記録だけを削除する専用画面。 */
@Composable
internal fun SubscriptionDeleteDialog(entry: Subscription, saving: Boolean, error: String?, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val dark = LocalEssentialDark.current
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(240)) }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = !saving, dismissOnClickOutside = !saving)) {
        SubscriptionDialogBackdrop()
        val shape = RoundedCornerShape(32.dp)
        Surface(Modifier.widthIn(max = 460.dp).fillMaxWidth().safeDrawingPadding().padding(18.dp)
            .graphicsLayer { alpha = entrance.value; scaleX = .96f + .04f * entrance.value; scaleY = scaleX }
            .border(1.dp, Color.White.copy(alpha = .48f), shape), shape = shape,
            color = Color.White.copy(alpha = if (dark) .16f else .36f), shadowElevation = 12.dp) {
            Column(Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .16f), Color.White.copy(alpha = .02f))))
                .verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                GlassFeatureTitle("サブスクを削除")
                SubscriptionGlass {
                    Text(entry.name, style = MaterialTheme.typography.titleLarge)
                    Text("${yenText(entry.yen)}／月", style = MaterialTheme.typography.headlineSmall)
                    Text("毎月${entry.billingDay}日に決済", style = MaterialTheme.typography.bodyMedium)
                }
                Text("このサブスクの記録を削除します。サービスの契約自体は解約されません。")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassSubscriptionButton("キャンセル", !saving, false, Modifier.weight(1f), onDismiss)
                    GlassSubscriptionButton(if (saving) "削除中…" else "記録を削除", !saving, true,
                        Modifier.weight(1f), onDelete, destructive = true)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionEditor(entry: Subscription?, saving: Boolean, error: String?, onDismiss: () -> Unit, onSave: (Subscription) -> Unit) {
    var name by rememberSaveable { mutableStateOf(entry?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(entry?.yen?.toString().orEmpty()) }
    var day by rememberSaveable { mutableStateOf((entry?.billingDay ?: LocalDate.now().dayOfMonth).toString()) }
    var start by rememberSaveable { mutableStateOf((entry?.startDate ?: LocalDate.now()).toString()) }
    val parsedDate = runCatching { LocalDate.parse(start) }.getOrNull()
    val valid = name.isNotBlank() && amount.toLongOrNull() in 0L..999999999L && day.toIntOrNull() in 1..31 && parsedDate != null
    val dark = LocalEssentialDark.current
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(240)) }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnBackPress = !saving, dismissOnClickOutside = !saving)) {
        SubscriptionDialogBackdrop()
        BoxWithConstraints(Modifier.widthIn(max = 460.dp).fillMaxWidth().safeDrawingPadding().imePadding().padding(18.dp)) {
            val shape = RoundedCornerShape(32.dp)
            Surface(Modifier.fillMaxWidth().heightIn(max = maxHeight).graphicsLayer {
                alpha = entrance.value
                scaleX = .96f + .04f * entrance.value
                scaleY = scaleX
                translationY = (1f - entrance.value) * 16.dp.toPx()
            }.border(1.dp, Color.White.copy(alpha = .48f), shape), shape = shape,
                color = Color.White.copy(alpha = if (dark) .16f else .36f),
                contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 12.dp) {
                Column(Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .16f), Color.White.copy(alpha = .02f))))
                    .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GlassFeatureTitle(if (entry == null) "サブスクを追加" else "サブスクを編集")
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassSubscriptionField(name, { name = it.take(80) }, "サービス名", enabled = !saving)
                        GlassSubscriptionField(amount, { amount = it }, "月額（円）", KeyboardType.Number, !saving)
                        GlassSubscriptionField(day, { day = it }, "毎月の決済日（1〜31）", KeyboardType.Number, !saving)
                        GlassSubscriptionField(start, { start = it }, "開始日（yyyy-MM-dd）", enabled = !saving)
                        Text("決済日がない月は、月末に予定を表示します。", style = MaterialTheme.typography.bodySmall)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassSubscriptionButton("キャンセル", !saving, false, Modifier.weight(1f), onDismiss)
                        GlassSubscriptionButton(if (saving) "保存中" else "保存", valid && !saving, true, Modifier.weight(1f), onClick = {
                            onSave(Subscription(entry?.id ?: java.util.UUID.randomUUID().toString(), name.trim(), amount.toLong(), day.toInt(), parsedDate!!))
                        })
                    }
                }
            }
        }
    }
}

/** 下線を除き、入力領域も半透明の丸いガラス面へ統一する。 */
@Composable
private fun GlassSubscriptionField(value: String, onChange: (String) -> Unit, label: String,
    keyboardType: KeyboardType = KeyboardType.Text, enabled: Boolean = true) {
    val shape = RoundedCornerShape(20.dp)
    TextField(value, onChange, modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = .32f), shape),
        enabled = enabled, label = { Text(label) }, singleLine = true, shape = shape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = .22f),
            unfocusedContainerColor = Color.White.copy(alpha = .10f),
            disabledContainerColor = Color.White.copy(alpha = .06f),
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent))
}

/** 押下時に少し縮み、保存の可否を明度で伝えるガラスボタン。 */
@Composable
private fun GlassSubscriptionButton(label: String, enabled: Boolean, primary: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit, destructive: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, tween(100), label = "ガラスボタンの押下")
    val shape = RoundedCornerShape(24.dp)
    val color = if (primary) (if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = if (enabled) .82f else .20f)
        else Color.White.copy(alpha = .12f)
    Surface(onClick = onClick, enabled = enabled, interactionSource = interaction,
        modifier = modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .border(1.dp, Color.White.copy(alpha = if (enabled) .42f else .16f), shape),
        shape = shape, color = color,
        contentColor = if (primary && enabled) (if (destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary) else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .45f)) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
