package jp.essential.app.feature.schedule

import jp.essential.app.ui.liquidGlass
import jp.essential.app.ui.LiquidGlassStyle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** 画面幅に合わせた七列の専用カレンダーで日付範囲を選ぶ。 */
@Composable
internal fun EssentialCalendar(startDate: String, endDate: String, onDismiss: () -> Unit, onSelected: (String, String) -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd") }
    var start by remember { mutableStateOf(runCatching { LocalDate.parse(startDate, formatter) }.getOrNull()) }
    var end by remember { mutableStateOf(runCatching { LocalDate.parse(endDate, formatter) }.getOrNull()) }
    var month by remember { mutableStateOf(YearMonth.from(start ?: LocalDate.now())) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val previousDim = window?.attributes?.dimAmount
            val activity = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }
                .filterIsInstance<android.app.Activity>().firstOrNull()
            // ダイアログの前面は鮮明に保ち、背面の文字だけをぼかして色の透過を残す。
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val radius = LiquidGlassStyle.blurRadius.value * view.resources.displayMetrics.density
                activity?.window?.decorView?.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP))
            }
            window?.setDimAmount(0.14f)
            onDispose {
                previousDim?.let { window?.setDimAmount(it) }
                if (android.os.Build.VERSION.SDK_INT >= 31) activity?.window?.decorView?.setRenderEffect(null)
            }
        }
        val glassShape = RoundedCornerShape(32.dp)
        Surface(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(18.dp)
            .liquidGlass(glassShape),
            shape = glassShape, color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp, shadowElevation = 12.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("カレンダー", style = MaterialTheme.typography.titleMedium)
                Text("開始日、終了日の順に選択", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("前月") }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("${month.year}年${month.monthValue}月") }
                    TextButton(onClick = { month = month.plusMonths(1) }) { Text("翌月") }
                }
                Row { listOf("月", "火", "水", "木", "金", "土", "日").forEach {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, style = MaterialTheme.typography.labelSmall) }
                } }
                val offset = month.atDay(1).dayOfWeek.value - 1
                repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
                    Row {
                        repeat(7) { column ->
                            val day = week * 7 + column - offset + 1
                            if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                            else {
                                val date = month.atDay(day)
                                val selected = date == start || date == end
                                val inRange = start != null && end != null && date >= start && date <= end
                                val dayColor by animateColorAsState(
                                    if (selected) Color(0xFF008CFF) else if (inRange) Color(0xFF008CFF).copy(alpha = 0.18f) else Color.Transparent,
                                    animationSpec = tween(160), label = "カレンダーの日付選択")
                                TextButton(onClick = {
                                    if (start == null || end != null || date < start) { start = date; end = null }
                                    else end = date
                                }, modifier = Modifier.weight(1f).height(48.dp)
                                    .padding(vertical = 4.dp).clip(CircleShape)
                                    .background(dayColor)
                                    .border(1.dp, if (selected) Color.White.copy(alpha = 0.48f) else Color.Transparent, CircleShape),
                                    colors = ButtonDefaults.textButtonColors(contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface),
                                    contentPadding = PaddingValues(0.dp)) { Text(day.toString(), style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
                Text(start?.format(formatter)?.let { "$it 〜 ${(end ?: start)?.format(formatter)}" } ?: "日付を選択してください", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.background(Color.White.copy(alpha = 0.16f), CircleShape)) { Text("キャンセル") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = start != null, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008CFF), contentColor = Color.White),
                        onClick = { onSelected(start!!.format(formatter), (end ?: start!!).format(formatter)) }) { Text("決定") }
                }
            }
        }
    }
}
