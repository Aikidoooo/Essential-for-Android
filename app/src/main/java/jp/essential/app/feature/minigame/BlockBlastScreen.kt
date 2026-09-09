package jp.essential.app.feature.minigame

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/** 行と列を同時判定してから消去する、8×8のブロックパズル。 */
internal object BlockBlastRules {
    val shapes = listOf(
        listOf(0 to 0), listOf(0 to 0, 1 to 0), listOf(0 to 0, 0 to 1),
        listOf(0 to 0, 1 to 0, 2 to 0), listOf(0 to 0, 0 to 1, 0 to 2),
        listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
        listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2),
        listOf(1 to 0, 1 to 1, 0 to 2, 1 to 2),
        listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1),
        listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0),
        listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3),
        listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),
    )
    fun fits(board: List<Int>, shape: Int, x: Int, y: Int): Boolean =
        shape in shapes.indices && shapes[shape].all { (dx, dy) ->
            x + dx in 0..7 && y + dy in 0..7 && board[(y + dy) * 8 + x + dx] == 0
        }
    fun canPlay(board: List<Int>, hand: List<Int>) = hand.any { shape ->
        (0..63).any { fits(board, shape, it % 8, it / 8) }
    }
    fun place(board: List<Int>, shape: Int, x: Int, y: Int): Pair<List<Int>, Int> {
        require(fits(board, shape, x, y))
        val next = board.toMutableList()
        shapes[shape].forEach { (dx, dy) -> next[(y + dy) * 8 + x + dx] = shape % 4 + 1 }
        val rows = (0..7).filter { row -> (0..7).all { next[row * 8 + it] != 0 } }
        val columns = (0..7).filter { col -> (0..7).all { next[it * 8 + col] != 0 } }
        rows.forEach { row -> (0..7).forEach { next[row * 8 + it] = 0 } }
        columns.forEach { col -> (0..7).forEach { next[it * 8 + col] = 0 } }
        return next to (rows.size + columns.size)
    }
}

@Composable
internal fun BlockBlastScreen(onBack: () -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("block_blast", 0)
    var board by rememberSaveable { mutableStateOf(List(64) { 0 }) }
    var hand by rememberSaveable { mutableStateOf(List(3) { BlockBlastRules.shapes.indices.random() }) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(prefs.getInt("best", 0)) }
    var message by remember { mutableStateOf("ブロックを選んで、盤面の左上のマスをタップ") }
    var restart by remember { mutableStateOf(false) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragPointer by remember { mutableStateOf<Offset?>(null) }
    var boardBounds by remember { mutableStateOf<Rect?>(null) }
    var handBounds by remember { mutableStateOf<Map<Int, Rect>>(emptyMap()) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val boardPaddingPx = with(density) { 10.dp.toPx() }
    val boardGapPx = with(density) { 3.dp.toPx() }
    val gridBounds = boardBounds?.let { bounds ->
        Rect(
            bounds.left + boardPaddingPx,
            bounds.top + boardPaddingPx,
            bounds.right - boardPaddingPx,
            bounds.bottom - boardPaddingPx,
        )
    }
    val dropCell = dragPointer?.let { pointer ->
        gridBounds?.let { grid ->
            val pitchX = (grid.width - boardGapPx * 7f) / 8f + boardGapPx
            val pitchY = (grid.height - boardGapPx * 7f) / 8f + boardGapPx
            val x = ((pointer.x - grid.left) / pitchX).toInt()
            val y = ((pointer.y - grid.top) / pitchY).toInt()
            if (x in 0..7 && y in 0..7) x to y else null
        }
    }
    val over = !BlockBlastRules.canPlay(board, hand)
    val colors = listOf(MaterialTheme.colorScheme.surfaceContainerHighest, Color(0xFF37B9A0), Color(0xFF548CE8), Color(0xFFC577DB), Color(0xFFDA923B))
    fun reset() {
        board = List(64) { 0 }; hand = List(3) { BlockBlastRules.shapes.indices.random() }
        score = 0; selected = 0; restart = false; draggingIndex = -1; dragPointer = null; message = "新しいゲームを開始しました"
    }
    fun placeShape(shape: Int, x: Int, y: Int) {
        if (!BlockBlastRules.fits(board, shape, x, y)) {
            message = "ここには置けません。空いている場所を選んでください"
            return
        }
        val (next, lines) = BlockBlastRules.place(board, shape, x, y)
        board = next
        score += BlockBlastRules.shapes[shape].size + lines * lines * 10
        if (score > best) { best = score; prefs.edit().putInt("best", best).apply() }
        hand = hand.mapIndexed { index, value -> if (index == draggingIndex || (draggingIndex < 0 && index == selected)) -1 else value }
        if (hand.all { it == -1 }) hand = List(3) { BlockBlastRules.shapes.indices.random() }
        selected = hand.indexOfFirst { it >= 0 }
        message = if (lines > 0) "$lines 列クリア！ +${lines * lines * 10} ボーナス" else "縦か横の8マスを埋めて消そう"
    }
    fun dropDraggedShape(index: Int, pointer: Offset?) {
        val shape = hand.getOrNull(index) ?: -1
        val cell = pointer?.let { current ->
            gridBounds?.let { grid ->
                val pitchX = (grid.width - boardGapPx * 7f) / 8f + boardGapPx
                val pitchY = (grid.height - boardGapPx * 7f) / 8f + boardGapPx
                val x = ((current.x - grid.left) / pitchX).toInt()
                val y = ((current.y - grid.top) / pitchY).toInt()
                if (x in 0..7 && y in 0..7) x to y else null
            }
        }
        if (cell == null) message = "盤面の上で指を離すとブロックを置けます"
        else placeShape(shape, cell.first, cell.second)
        draggingIndex = -1
        dragPointer = null
    }
    val currentDropAction by rememberUpdatedState<(Int, Offset?) -> Unit> { index, pointer ->
        dropDraggedShape(index, pointer)
    }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onBack) { Text("戻る") }
            Spacer(Modifier.width(12.dp))
            Text("Block Blast", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Card(shape = RoundedCornerShape(26.dp)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("スコア"); Text("$score", style = MaterialTheme.typography.headlineLarge) }
                Column { Text("ベスト"); Text("$best", style = MaterialTheme.typography.headlineLarge) }
            }
        }
        Text(if (over) "ゲーム終了！ 置けるブロックがありません" else message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface.copy(alpha = .85f), MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .6f))))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)).padding(10.dp)
                .onGloballyPositioned { boardBounds = it.boundsInRoot() },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(8) { y ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(8) { x ->
                        val draggingShape = hand.getOrNull(draggingIndex) ?: -1
                        val ghostCell = dropCell?.let { (dropX, dropY) ->
                            draggingShape in BlockBlastRules.shapes.indices && (x - dropX to y - dropY) in BlockBlastRules.shapes[draggingShape]
                        } == true
                        val ghostValid = dropCell?.let { (dropX, dropY) ->
                            BlockBlastRules.fits(board, draggingShape, dropX, dropY)
                        } == true
                        val targetColor = when {
                            ghostCell && ghostValid -> colors[draggingShape % 4 + 1].copy(alpha = .72f)
                            ghostCell -> Color(0xFFE56B78).copy(alpha = .72f)
                            else -> colors[board[y * 8 + x]]
                        }
                        val color by animateColorAsState(targetColor, label = "マスの色")
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(color)
                            .clickable(enabled = !over) {
                                val shape = hand.getOrElse(selected) { -1 }
                                placeShape(shape, x, y)
                            })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            hand.forEachIndexed { index, shape ->
                val outline = if (index == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                val cardBounds = handBounds[index]
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = .65f))
                    .border(if (index == selected) 2.dp else 1.dp, outline, RoundedCornerShape(18.dp))
                    .clickable(enabled = shape >= 0 && !over) { selected = index }
                    .onGloballyPositioned { coordinates -> handBounds = handBounds + (index to coordinates.boundsInRoot()) }
                    .pointerInput(index, shape, over, cardBounds) {
                        if (shape < 0 || over || cardBounds == null) return@pointerInput
                        var lastPointer: Offset? = null
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                draggingIndex = index
                                lastPointer = cardBounds.topLeft + position
                                dragPointer = lastPointer
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                lastPointer = cardBounds.topLeft + change.position
                                dragPointer = lastPointer
                            },
                            onDragEnd = { currentDropAction(index, lastPointer) },
                            onDragCancel = {
                                draggingIndex = -1
                                dragPointer = null
                            },
                        )
                    }
                    .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    repeat(4) { y ->
                        Row {
                            repeat(4) { x ->
                                val filled = shape >= 0 && (x to y) in BlockBlastRules.shapes[shape]
                                Box(Modifier.size(15.dp).padding(1.dp).clip(RoundedCornerShape(3.dp))
                                    .background(if (filled) colors[shape % 4 + 1] else Color.Transparent))
                            }
                        }
                    }
                    Text(
                        when {
                            shape < 0 -> "配置済み"
                            draggingIndex == index -> "盤面へドロップ"
                            index == selected -> "選択中"
                            else -> "選ぶ"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Text("タップで選択、手札を長押しして盤面へドラッグ。3個すべて置くと次のブロックが出現します。", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { if (over || score == 0) reset() else restart = true }, modifier = Modifier.fillMaxWidth()) { Text("もう一度遊ぶ") }
    }
    val previewShape = hand.getOrNull(draggingIndex) ?: -1
    val pointer = dragPointer
    if (previewShape in BlockBlastRules.shapes.indices && pointer != null) {
        val previewOffset = with(density) {
            IntOffset((pointer.x - 36.dp.toPx()).roundToInt(), (pointer.y - 36.dp.toPx()).roundToInt())
        }
        Box(
            Modifier.offset { previewOffset }.size(72.dp).clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .92f))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("🧩", style = MaterialTheme.typography.headlineMedium) }
    }
    if (restart) AlertDialog(onDismissRequest = { restart = false }, title = { Text("最初から遊びますか？") }, text = { Text("現在の盤面とスコアがリセットされます。ベストスコアは残ります。") }, confirmButton = { TextButton(onClick = { reset() }) { Text("やり直す") } }, dismissButton = { TextButton(onClick = { restart = false }) { Text("続ける") } })
    }
}
