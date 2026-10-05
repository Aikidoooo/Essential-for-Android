package jp.essential.app.feature.tuning

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.essential.app.feature.subscriptions.SubscriptionGlass
import jp.essential.app.ui.GlassModeSelector
import kotlin.math.*
import java.util.Locale

/** 選択した弦に対する一致だけを判定し、別オクターブを合致扱いにしない。 */
internal fun isStringInTune(frequency: Double?, midi: Int?, reference: Int): Boolean =
    frequency != null && midi != null && frequency > 0 && abs(centsFrom(frequency, noteFrequency(midi, reference))) <= 5

@Composable
internal fun TuningPanel(preset: TuningPreset, presetIndex: Int, targetNote: Int?, manualNote: Int?,
    frequency: Double?, reference: Int, running: Boolean, canStart: Boolean,
    onPreset: (Int) -> Unit, onNote: (Int?) -> Unit, onToggle: () -> Unit,
    clockwiseToRaise: (Int) -> Boolean? = { null }, onDirectionChange: (Int, Boolean) -> Unit = { _, _ -> }) {
    val colors = MaterialTheme.colorScheme
    val accent = Color(0xFF87D5F0)
    val glow = Color(0xFF7AF0C5)
    val displayed = targetNote ?: preset.midiNotes.first()
    val cents = frequency?.let { centsFrom(it, noteFrequency(displayed, reference)) }
    val aligned = running && isStringInTune(frequency, targetNote, reference)
    val chosen = manualNote ?: targetNote
    val clockwise = chosen?.let(clockwiseToRaise)
    val turnClockwise = if (running && cents != null && !aligned && clockwise != null) (cents < 0) == clockwise else null
    val modePosition by animateFloatAsState(if (presetIndex == 3 || presetIndex == 4) 0f else 1f,
        spring(dampingRatio = .76f, stiffness = 420f), label = "楽器の選択位置")
    val differences = remember(presetIndex, reference) { mutableStateMapOf<Int, Double>() }
    LaunchedEffect(frequency, targetNote, running) {
        if (!running) differences.clear()
        else if (frequency != null && targetNote != null) differences[targetNote] = centsFrom(frequency, noteFrequency(targetNote, reference))
    }
    val needle by animateFloatAsState((cents ?: 0.0).coerceIn(-50.0, 50.0).toFloat(), tween(140), label = "扇形メーターの針")
    val noteColor by animateColorAsState(if (aligned) glow else colors.onSurface, tween(180), label = "合致した音名の発光")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassModeSelector(listOf("ウクレレ  g C E A", "ギター  E A D G B E"),
            listOf("tuning-ukulele-mode", "tuning-guitar-mode"),
            if (presetIndex == 3) 0 else if (presetIndex == 0) 1 else -1,
            modePosition, { onPreset(if (it == 0) 3 else 0) }, Modifier.fillMaxWidth())
        SubscriptionGlass {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Text(noteLabel(displayed - 1).dropLast(1), fontSize = 30.sp, color = colors.onSurface.copy(alpha = .35f))
                Text(noteLabel(displayed).dropLast(1), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = noteColor)
                Text(noteLabel(displayed + 1).dropLast(1), fontSize = 30.sp, color = colors.onSurface.copy(alpha = .35f))
            }
            Text(frequency?.let { String.format(Locale.JAPAN, "%.1f Hz · %+.1f セント", it, cents) }
                ?: "目標 ${noteLabel(displayed)} · ${String.format(Locale.JAPAN, "%.1f Hz", noteFrequency(displayed, reference))}",
                Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall)
            val density = LocalDensity.current
            Canvas(Modifier.fillMaxWidth().height(154.dp)) {
                val pivot = Offset(size.width / 2, size.height * .88f)
                // 参照の浅い弧に合わせ、針の支点と目盛りの曲線を別々に定義する。
                fun point(value: Float): Offset {
                    val fraction = value / 50f
                    return Offset(pivot.x + fraction * size.width * .42f,
                        size.height * (.18f + .16f * fraction * fraction))
                }
                val arc = Path()
                for (value in -50..50) {
                    val p = point(value.toFloat())
                    if (value == -50) arc.moveTo(p.x, p.y) else arc.lineTo(p.x, p.y)
                }
                drawPath(arc, colors.onSurface.copy(alpha = .38f), style = Stroke(2.dp.toPx()))
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = colors.onSurface.copy(alpha = .55f).toArgb()
                    textSize = with(density) { 11.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                listOf(-40, -20, 0, 20, 40).forEach { tick ->
                    val position = point(tick.toFloat())
                    drawLine(colors.onSurface.copy(alpha = .5f), position.copy(y = position.y - 7.dp.toPx()),
                        position.copy(y = position.y + 6.dp.toPx()), 2.dp.toPx())
                    val label = position.copy(y = position.y - 15.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(if (tick > 0) "+$tick" else "$tick", label.x, label.y, paint)
                }
                val color = if (aligned) glow else accent
                drawLine(color.copy(alpha = if (cents != null) 1f else .3f), pivot,
                    point(needle), 4.dp.toPx(), StrokeCap.Round)
                drawCircle(color, 6.dp.toPx(), pivot)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
            PegTurnArrow(clockwise = false, active = turnClockwise == false, glow = glow)
            Surface(onClick = onToggle, enabled = running || canStart, shape = CircleShape,
                color = if (running) accent.copy(alpha = .24f) else colors.onSurface.copy(alpha = .12f),
                modifier = Modifier.size(72.dp)
                    .border(1.dp, Color.White.copy(alpha = .4f), CircleShape)
                    .semantics { contentDescription = if (running) "チューニングを停止" else "チューニングを開始" }) {
                Canvas(Modifier.padding(20.dp)) {
                    drawRoundRect(colors.onSurface, Offset(size.width * .36f, 0f),
                        androidx.compose.ui.geometry.Size(size.width * .28f, size.height * .64f),
                        androidx.compose.ui.geometry.CornerRadius(size.width * .14f))
                    drawArc(colors.onSurface, 0f, 180f, false, Offset(size.width * .15f, size.height * .25f),
                        androidx.compose.ui.geometry.Size(size.width * .7f, size.height * .58f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    drawLine(colors.onSurface, Offset(size.width * .5f, size.height * .8f), Offset(size.width * .5f, size.height), 2.dp.toPx())
                    drawLine(colors.onSurface, Offset(size.width * .3f, size.height), Offset(size.width * .7f, size.height), 2.dp.toPx())
                }
            }
            PegTurnArrow(clockwise = true, active = turnClockwise == true, glow = glow)
            }
            Text(when {
                !running -> "マイクを押して開始"
                cents == null -> "選んだ弦を1本ずつ鳴らしてください"
                aligned -> "音程が合っています"
                cents < 0 -> "低い音 · 弦を締めてください"
                else -> "高い音 · 弦を緩めてください"
            }, Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall, color = if (aligned) glow else colors.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            preset.midiNotes.forEachIndexed { index, midi ->
                val selected = manualNote == midi || (manualNote == null && targetNote == midi)
                val matched = selected && aligned
                val color by animateColorAsState(if (matched) glow else if (selected) accent else accent.copy(alpha = .38f), tween(180), label = "選択した弦の発光")
                val peg = remember { GenericShape { size, _ ->
                    moveTo(size.width * .34f, 0f); lineTo(size.width * .66f, 0f)
                    lineTo(size.width * .66f, size.height * .16f); lineTo(size.width * .9f, size.height * .44f)
                    lineTo(size.width * .8f, size.height * .95f); lineTo(size.width * .2f, size.height * .95f)
                    lineTo(size.width * .1f, size.height * .44f); lineTo(size.width * .34f, size.height * .16f); close()
                } }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (matched) Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(glow.copy(alpha = .6f), Color.Transparent))))
                    Surface(onClick = { onNote(midi) }, color = color, shape = peg, shadowElevation = if (matched) 16.dp else 0.dp,
                        modifier = Modifier.fillMaxWidth().height(88.dp).semantics {
                            contentDescription = "${preset.midiNotes.size - index}弦 ${noteLabel(midi)}"
                            stateDescription = if (matched) "音程一致" else if (selected) "選択中" else "未選択"
                            this.selected = selected
                        }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(if (presetIndex == 3 && index == 0) "g" else noteLabel(midi).dropLast(1), fontSize = if (preset.midiNotes.size > 4) 22.sp else 30.sp,
                                fontWeight = FontWeight.Bold, color = Color(0xFF142E3A))
                            Text("${preset.midiNotes.size - index}弦", style = MaterialTheme.typography.labelSmall, color = Color(0xFF142E3A))
                        }
                    }
                }
                val difference = if (selected && running) cents else differences[midi]
                Text(difference?.let { String.format(Locale.JAPAN, "%+.0f", it) } ?: "—",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text("セント", fontSize = 10.sp, color = colors.onSurfaceVariant)
                Text(if (difference == null) "未測定" else if (!selected) "前回" else if (abs(difference) <= 5) "一致" else if (difference < 0) "低い" else "高い",
                    fontSize = 10.sp, color = if (matched) glow else colors.onSurfaceVariant)
                }
            }
        }
        SubscriptionGlass {
            Text("ペグを回す方向", style = MaterialTheme.typography.titleMedium)
            Text(when {
                aligned -> "音程が合っています · 回さずそのまま"
                !running || cents == null -> "弦を選び、マイクを開始して音を鳴らしてください"
                clockwise == null -> if (cents < 0) "締めて音を上げる ↑" else "緩めて音を下げる ↓"
                (cents < 0) == clockwise -> "↻ 時計回り · ${if (cents < 0) "締めて音を上げる" else "緩めて音を下げる"}"
                else -> "↺ 反時計回り · ${if (cents < 0) "締めて音を上げる" else "緩めて音を下げる"}"
            }, style = MaterialTheme.typography.titleMedium)
            if (chosen != null) {
                Text("${noteLabel(chosen)}のペグをつまみ側から見て、音が上がる方向を設定", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = clockwise == true, onClick = { onDirectionChange(chosen, true) }, label = { Text("↻ 時計回り") })
                    FilterChip(selected = clockwise == false, onClick = { onDirectionChange(chosen, false) }, label = { Text("↺ 反時計回り") })
                }
            } else Text("弦を選択すると回転方向を設定できます", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { onNote(null) }) { Text(if (manualNote == null) "自動選択中" else "自動選択に戻す") }
    }
}

/** つまみ側から見た回転方向を、常設の薄灰色と必要な側だけの緑の光で示す。 */
@Composable
private fun PegTurnArrow(clockwise: Boolean, active: Boolean, glow: Color) {
    val color by animateColorAsState(if (active) glow else Color(0xFFB9BEC5).copy(alpha = .6f),
        tween(180), label = "回転方向の発光")
    Canvas(Modifier.size(62.dp).semantics {
        contentDescription = if (clockwise) "時計回りの回転矢印" else "反時計回りの回転矢印"
        stateDescription = if (active) "緑に点灯" else "薄灰色"
    }) {
        val center = Offset(size.width / 2, size.height / 2)
        if (active) drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = .35f), Color.Transparent), center, size.width / 2))
        val radius = size.width * .28f
        val start = if (clockwise) -130f else -50f
        val sweep = if (clockwise) 280f else -280f
        drawArc(color, start, sweep, false, center - Offset(radius, radius),
            androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val angle = Math.toRadians((start + sweep).toDouble())
        val end = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
        val tangent = Offset(-sin(angle).toFloat(), cos(angle).toFloat()) * if (clockwise) 1f else -1f
        val normal = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val back = end - tangent * 8.dp.toPx()
        drawLine(color, back + normal * 5.dp.toPx(), end, 3.dp.toPx(), StrokeCap.Round)
        drawLine(color, back - normal * 5.dp.toPx(), end, 3.dp.toPx(), StrokeCap.Round)
    }
}
