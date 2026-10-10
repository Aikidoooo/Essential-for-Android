package jp.essential.app.feature.mannaka

import jp.essential.app.ui.liquidGlass
import android.app.TimePickerDialog
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import jp.essential.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun meetingBackgroundColor(): Color = MaterialTheme.colorScheme.background

@Composable
internal fun MannakaTheme(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    MaterialTheme(colorScheme = colors, typography = typography.copy(
        headlineLarge = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Normal),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    )) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
    }
}

@Composable
internal fun MeetingGlassCard(modifier: Modifier = Modifier, padding: Dp = 14.dp, spacing: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth().animateContentSize(tween(200)).liquidGlass(28.dp), shape = RoundedCornerShape(28.dp),
        color = Color.Transparent, shadowElevation = 0.dp) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

@Composable
internal fun MeetingSummaryCard(@DrawableRes icon: Int, title: String, value: String, placeholder: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().liquidGlass(28.dp), shape = RoundedCornerShape(28.dp),
        color = Color.Transparent, shadowElevation = 0.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp)
                Text(value, fontSize = 18.sp, lineHeight = 23.sp, color = if (placeholder) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(painterResource(R.drawable.ic_mannaka_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun MeetingParticipantCard(participant: MeetingParticipant, index: Int, canRemove: Boolean, enabled: Boolean,
    repository: MeetingDataSource, onNameChange: (String) -> Unit, onInput: (String) -> Unit,
    onPick: (MeetingPoint) -> Unit, onWalking: () -> Unit, onRemove: () -> Unit) {
    val avatarColors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(emptyList<MeetingPoint>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(participant.input, participant.selected, focused, enabled, retry) {
        suggestions = emptyList(); error = null; loading = false
        if (!focused || !enabled || participant.selected != null || participant.input.isBlank()) return@LaunchedEffect
        delay(120)
        loading = true
        try {
            // 一致した駅を先に表示し、周辺の候補の取得を待たずに選べるようにする。
            suggestions = repository.exactStations(participant.input)
            suggestions = repository.suggestStations(participant.input)
            if (suggestions.isEmpty()) error = "候補がありません。正式な駅名でお試しください。"
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "駅を検索できませんでした。" }
        finally { loading = false }
    }
    MeetingGlassCard(Modifier.testTag("mannaka-participant-${participant.id}"), padding = 12.dp, spacing = 6.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(avatarColors[index % avatarColors.size], CircleShape), contentAlignment = Alignment.Center) {
                if (participant.name.isBlank()) Icon(painterResource(R.drawable.ic_mannaka_person), null, tint = listOf(MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.onSecondary, MaterialTheme.colorScheme.onTertiary)[index % 3], modifier = Modifier.size(25.dp))
                else Text(participant.name.take(1), color = listOf(MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.onSecondary, MaterialTheme.colorScheme.onTertiary)[index % 3], fontSize = 21.sp)
            }
            BasicTextField(value = participant.name, onValueChange = onNameChange, enabled = enabled, singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp), modifier = Modifier.weight(1f).heightIn(min = 40.dp).semantics { contentDescription = "${index + 1}人目の名前" },
                decorationBox = { input -> Box(contentAlignment = Alignment.CenterStart) {
                    if (participant.name.isEmpty()) Text("名前（任意）", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
                    input()
                } })
            if (canRemove) IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(painterResource(R.drawable.ic_mannaka_close), "${index + 1}人目を削除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        suggestions.forEach { station ->
            Surface(onClick = { onPick(station); focusManager.clearFocus() }, enabled = enabled,
                shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().testTag("mannaka-suggestion-${participant.id}-${station.id}")) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(painterResource(R.drawable.ic_mannaka_place), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(station.name, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 15.sp, lineHeight = 18.sp)
                        Text(station.detail.ifBlank { "位置：%.4f, %.4f".format(java.util.Locale.US, station.latitude, station.longitude) },
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 13.sp)
                    }
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            TextButton(onClick = { retry++ }, enabled = enabled) { Text("再検索") }
        }
        OutlinedTextField(participant.input, onInput, enabled = enabled, singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = "${index + 1}人目の最寄り駅" },
            placeholder = { Text("最寄り駅を選ぶ", fontSize = 16.sp) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_mannaka_place), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
            trailingIcon = { if (participant.input.isNotEmpty()) IconButton(onClick = { onInput("") }, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_mannaka_close), "${index + 1}人目の駅名を消す", modifier = Modifier.size(18.dp))
            } },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
        if (participant.selected != null) Text("選択済み：${participant.selected.detail.ifBlank { participant.selected.name }}",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        TextButton(onClick = onWalking, enabled = enabled, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 40.dp).semantics { contentDescription = "${index + 1}人目の駅までの時間" }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Box(Modifier.size(22.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_mannaka_add), null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(7.dp))
            Text(if (participant.walkMinutes.isBlank()) "駅まで何分？（任意）" else "駅まで ${participant.walkMinutes}分", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
internal fun MeetingAddButton(enabled: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(24.dp), color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).drawWithCache {
            val stroke = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
            onDrawBehind { drawRoundRect(color, cornerRadius = CornerRadius(24.dp.toPx()), style = stroke) }
        }) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_mannaka_add), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(8.dp))
            Text("参加者を追加", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetingTimePanel(day: Int, minutes: Int, onDone: (Int, Int) -> Unit) {
    var draftDay by remember { mutableIntStateOf(day) }
    var draftMinutes by remember { mutableIntStateOf(minutes) }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp).heightIn(min = 415.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_mannaka_clock), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
            Text("集合時間", style = MaterialTheme.typography.titleLarge)
        }
        MeetingDaySelector(draftDay) { draftDay = it }
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(-30, -5).forEach { delta -> MeetingTimeStep(delta) { draftMinutes = shiftMeetingTime(draftMinutes, delta) } }
                TextButton(onClick = {
                    TimePickerDialog(context, { _, hour, minute -> draftMinutes = hour * 60 + minute }, draftMinutes / 60, draftMinutes % 60, true).show()
                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                    Text(meetingTimeLabel(draftMinutes), fontSize = 38.sp, lineHeight = 46.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
                listOf(5, 30).forEach { delta -> MeetingTimeStep(delta) { draftMinutes = shiftMeetingTime(draftMinutes, delta) } }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            listOf(720, 1080, 1110, 1140, 1170, 1200).forEach { preset ->
                FilterChip(selected = draftMinutes == preset, onClick = { draftMinutes = preset }, label = { Text(meetingTimeLabel(preset), fontSize = 15.sp) },
                    shape = RoundedCornerShape(30.dp), border = null,
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer, labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
            }
        }
        Button(onClick = { onDone(draftDay, draftMinutes) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("決定", fontSize = 18.sp) }
    }
}

@Composable
private fun MeetingDaySelector(day: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)).padding(4.dp)) {
        val tabWidth = maxWidth / 3
        val position = animateDpAsState(tabWidth * day, tween(220), label = "集合日の選択位置")
        Surface(Modifier.offset { IntOffset(position.value.roundToPx(), 0) }.width(tabWidth).height(38.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {}
        Row(Modifier.fillMaxWidth()) {
            listOf("今日", "明日", "明後日").forEachIndexed { index, label ->
                TextButton(onClick = { onSelect(index) }, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(0.dp)) {
                    Text(label, color = if (index == day) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun MeetingTimeStep(delta: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.width(if (kotlin.math.abs(delta) == 30) 50.dp else 40.dp).height(32.dp).padding(horizontal = 2.dp), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
        Text(if (delta > 0) "+$delta" else "−${-delta}", fontSize = 14.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetingActivityPanel(categories: List<String>, custom: String, doneLabel: String = "決定", showTitle: Boolean = true, onDone: (List<String>, String) -> Unit) {
    var draftCategories by remember { mutableStateOf(categories) }
    var draftCustom by remember { mutableStateOf(custom) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showTitle) Text("集まって何する？", style = MaterialTheme.typography.titleLarge)
        Text("好きなものをいくつでも。選ばなくても周辺の遊びを提案します。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            meetingCategories.forEach { category ->
                FilterChip(selected = category in draftCategories, onClick = {
                    draftCategories = if (category in draftCategories) draftCategories - category else draftCategories + category
                }, label = { Text(category) }, shape = RoundedCornerShape(20.dp))
            }
        }
        OutlinedTextField(draftCustom, { draftCustom = it.take(80) }, label = { Text("その他・自由指定") }, placeholder = { Text("焼肉、カフェ、美術館など") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
        Button(onClick = { onDone(draftCategories, draftCustom.trim()) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(doneLabel, fontSize = 18.sp) }
    }
}


@Composable
internal fun MeetingWalkingPanel(minutes: String, onDone: (String) -> Unit) {
    var draft by remember { mutableStateOf(minutes) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("駅まで何分？", style = MaterialTheme.typography.titleLarge)
        Text("駅までの時間を予定にメモできます。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(draft, { draft = it.filter { character -> character in '0'..'9' }.take(3) }, label = { Text("駅までの時間（分）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
        Button(onClick = { onDone(draft.toIntOrNull()?.toString().orEmpty()) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("決定") }
    }
}
