package jp.essential.app.feature.minigame

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 行と列を同時判定してから消去する、8×8のブロックパズル。 */
internal object BlockBlastRules {
    data class ScoreResult(
        val points: Int,
        val clearStreak: Int,
        val multiplier: Int,
        val multiplierEndsAt: Long,
    )

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

    /** 指に最も近い配置可能位置を探し、少し外れたドロップを隙間へ吸着させる。 */
    fun nearestFit(
        board: List<Int>,
        shape: Int,
        targetX: Int,
        targetY: Int,
        maxDistance: Float = 1.65f,
    ): Pair<Int, Int>? = (0..63)
        .map { it % 8 to it / 8 }
        .filter { (x, y) -> fits(board, shape, x, y) }
        .map { cell -> cell to hypot((cell.first - targetX).toFloat(), (cell.second - targetY).toFloat()) }
        .filter { (_, distance) -> distance <= maxDistance }
        .minWithOrNull(compareBy<Pair<Pair<Int, Int>, Float>> { it.second }.thenBy { it.first.second }.thenBy { it.first.first })
        ?.first

    /** 連続消去で倍率を更新し、発動済み倍率は残り時間中の配置得点すべてへ適用する。 */
    fun scoreMove(
        shapeSize: Int,
        clearedLines: Int,
        previousClearStreak: Int,
        currentMultiplier: Int,
        currentMultiplierEndsAt: Long,
        nowMillis: Long,
    ): ScoreResult {
        val nextStreak = if (clearedLines > 0) previousClearStreak + 1 else 0
        val activeMultiplier = if (currentMultiplierEndsAt > nowMillis) currentMultiplier.coerceAtLeast(1) else 1
        val nextMultiplier = if (nextStreak >= 2) nextStreak else activeMultiplier
        val nextEndsAt = when {
            nextStreak >= 2 -> nowMillis + 10_000L
            activeMultiplier > 1 -> currentMultiplierEndsAt
            else -> 0L
        }
        val basePoints = shapeSize + clearedLines * clearedLines * 10
        return ScoreResult(
            points = basePoints * nextMultiplier,
            clearStreak = nextStreak,
            multiplier = nextMultiplier,
            multiplierEndsAt = nextEndsAt,
        )
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

private val BlockBlastBackground = Brush.verticalGradient(
    0f to Color(0xFFE0F8F6),
    0.52f to Color(0xFFD4F2EF),
    1f to Color(0xFFC8ECEA),
)

private val BlockBlastCell = Color(0xFFE5F6F4)
private val BlockBlastCellLine = Color(0xFFC7E8E5)
private val BlockBlastInk = Color(0xFF2F6C6D)
private val BlockBlastMuted = Color(0xFF78A6A5)
private val BlockBlastBlockColors = listOf(
    Color.Transparent,
    Color(0xFF70CABE),
    Color(0xFF64B9D1),
    Color(0xFF879FE2),
    Color(0xFFC68FD1),
)

private data class BlockShapeMetrics(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
) {
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
}

private fun blockShapeMetrics(shape: Int): BlockShapeMetrics? {
    if (shape !in BlockBlastRules.shapes.indices) return null
    val cells = BlockBlastRules.shapes[shape]
    return BlockShapeMetrics(
        minX = cells.minOf { it.first },
        maxX = cells.maxOf { it.first },
        minY = cells.minOf { it.second },
        maxY = cells.maxOf { it.second },
    )
}

private fun blockOriginForPointer(
    pointer: Offset,
    grid: Rect,
    boardGapPx: Float,
    shape: Int,
): Pair<Int, Int>? {
    val metrics = blockShapeMetrics(shape) ?: return null
    if (pointer.x !in grid.left..grid.right || pointer.y !in grid.top..grid.bottom) return null
    val pitchX = (grid.width - boardGapPx * 7f) / 8f + boardGapPx
    val pitchY = (grid.height - boardGapPx * 7f) / 8f + boardGapPx
    val centerCellX = (pointer.x - grid.left) / pitchX - 0.5f
    val centerCellY = (pointer.y - grid.top) / pitchY - 0.5f
    return (
        (centerCellX - metrics.centerX).roundToInt() to
            (centerCellY - metrics.centerY).roundToInt()
        )
}

private fun assistedBlockOriginForPointer(
    pointer: Offset,
    grid: Rect,
    boardGapPx: Float,
    board: List<Int>,
    shape: Int,
): Pair<Int, Int>? {
    val raw = blockOriginForPointer(pointer, grid, boardGapPx, shape) ?: return null
    if (BlockBlastRules.fits(board, shape, raw.first, raw.second)) return raw
    return BlockBlastRules.nearestFit(board, shape, raw.first, raw.second)
}

private fun decodeIntList(value: String?, expectedSize: Int): List<Int>? = value
    ?.split(',')
    ?.mapNotNull(String::toIntOrNull)
    ?.takeIf { it.size == expectedSize }

private fun blockPreviewOffset(
    pointer: Offset,
    shape: Int,
    cellSizePx: Float,
    gapPx: Float,
): IntOffset {
    val metrics = blockShapeMetrics(shape)
    if (metrics == null) return IntOffset(pointer.x.roundToInt(), pointer.y.roundToInt())
    val centerX = (metrics.centerX - metrics.minX + 0.5f) * cellSizePx +
        (metrics.centerX - metrics.minX) * gapPx
    val centerY = (metrics.centerY - metrics.minY + 0.5f) * cellSizePx +
        (metrics.centerY - metrics.minY) * gapPx
    return IntOffset(
        (pointer.x - centerX).roundToInt(),
        (pointer.y - centerY).roundToInt(),
    )
}

/** 掴んだ指を隠さず、ブロックの下端が指の少し上へ来る表示・当たり判定位置を返す。 */
private fun blockPointerAboveFinger(pointer: Offset, shape: Int, cellSizePx: Float, gapPx: Float): Offset {
    val metrics = blockShapeMetrics(shape) ?: return pointer
    val pitch = cellSizePx + gapPx
    val lift = (metrics.maxY - metrics.minY + 1f) * pitch * 0.72f
    return Offset(pointer.x, pointer.y - lift)
}

/** 参考画面の淡い盤面と、ドラッグ配置に対応したBlock Blast画面。 */
@Composable
internal fun BlockBlastScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("block_blast", 0) }
    var board by rememberSaveable {
        mutableStateOf(decodeIntList(prefs.getString("board", null), 64) ?: List(64) { 0 })
    }
    var hand by rememberSaveable {
        mutableStateOf(
            decodeIntList(prefs.getString("hand", null), 3)
                ?: List(3) { BlockBlastRules.shapes.indices.random() },
        )
    }
    var score by rememberSaveable { mutableIntStateOf(prefs.getInt("score", 0)) }
    var best by remember { mutableIntStateOf(prefs.getInt("best", 0)) }
    var clearStreak by rememberSaveable { mutableIntStateOf(prefs.getInt("clear_streak", 0)) }
    var multiplier by rememberSaveable { mutableIntStateOf(prefs.getInt("multiplier", 1).coerceAtLeast(1)) }
    var multiplierEndsAt by rememberSaveable { mutableStateOf(prefs.getLong("multiplier_ends_at", 0L)) }
    var multiplierRemainingMillis by remember { mutableStateOf((multiplierEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)) }
    var message by remember { mutableStateOf("ブロックをドラッグして盤面へ置こう") }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragPointer by remember { mutableStateOf<Offset?>(null) }
    var boardBounds by remember { mutableStateOf<Rect?>(null) }
    var handBounds by remember { mutableStateOf<Map<Int, Rect>>(emptyMap()) }
    val density = LocalDensity.current
    val boardPaddingPx = with(density) { 10.dp.toPx() }
    val boardGapPx = with(density) { 4.dp.toPx() }
    val gridBounds = boardBounds?.let { bounds ->
        Rect(
            bounds.left + boardPaddingPx,
            bounds.top + boardPaddingPx,
            bounds.right - boardPaddingPx,
            bounds.bottom - boardPaddingPx,
        )
    }
    val draggingShape = hand.getOrNull(draggingIndex) ?: -1
    val dragTargetPointer = dragPointer?.let { pointer ->
        blockPointerAboveFinger(pointer, draggingShape, with(density) { 18.dp.toPx() }, with(density) { 1.dp.toPx() })
    }
    val dropCell = dragTargetPointer?.let { pointer ->
        gridBounds?.let { grid ->
            assistedBlockOriginForPointer(
                pointer,
                grid,
                boardGapPx,
                board,
                draggingShape,
            )
        }
    }
    val gameOver = !BlockBlastRules.canPlay(board, hand)

    LaunchedEffect(board, hand, score, clearStreak, multiplier, multiplierEndsAt) {
        prefs.edit()
            .putString("board", board.joinToString(","))
            .putString("hand", hand.joinToString(","))
            .putInt("score", score)
            .putInt("clear_streak", clearStreak)
            .putInt("multiplier", multiplier)
            .putLong("multiplier_ends_at", multiplierEndsAt)
            .apply()
    }

    LaunchedEffect(multiplierEndsAt) {
        while (multiplierEndsAt > System.currentTimeMillis()) {
            multiplierRemainingMillis = (multiplierEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(100L)
        }
        multiplierRemainingMillis = 0L
        if (multiplier != 1) multiplier = 1
    }

    fun resetForNextGame() {
        board = List(64) { 0 }
        hand = List(3) { BlockBlastRules.shapes.indices.random() }
        score = 0
        clearStreak = 0
        multiplier = 1
        multiplierEndsAt = 0L
        multiplierRemainingMillis = 0L
        message = "ブロックをドラッグして盤面へ置こう"
    }

    fun placeShape(shape: Int, x: Int, y: Int) {
        if (!BlockBlastRules.fits(board, shape, x, y)) {
            message = "ここには置けません。空いている場所へドラッグしてください"
            return
        }
        val (next, lines) = BlockBlastRules.place(board, shape, x, y)
        board = next
        val now = System.currentTimeMillis()
        val scoreResult = BlockBlastRules.scoreMove(
            shapeSize = BlockBlastRules.shapes[shape].size,
            clearedLines = lines,
            previousClearStreak = clearStreak,
            currentMultiplier = multiplier,
            currentMultiplierEndsAt = multiplierEndsAt,
            nowMillis = now,
        )
        clearStreak = scoreResult.clearStreak
        multiplier = scoreResult.multiplier
        multiplierEndsAt = scoreResult.multiplierEndsAt
        multiplierRemainingMillis = (multiplierEndsAt - now).coerceAtLeast(0L)
        score += scoreResult.points
        if (score > best) {
            best = score
            prefs.edit().putInt("best", best).apply()
        }
        hand = hand.mapIndexed { index, value ->
            if (index == draggingIndex) -1 else value
        }
        if (hand.all { it == -1 }) hand = List(3) { BlockBlastRules.shapes.indices.random() }
        message = when {
            lines > 0 && scoreResult.multiplier > 1 -> "$lines 列クリア！ ${scoreResult.multiplier}倍で +${scoreResult.points}"
            lines > 0 -> "$lines 列クリア！ +${scoreResult.points}"
            scoreResult.multiplier > 1 -> "${scoreResult.multiplier}倍ボーナスで +${scoreResult.points}"
            else -> "次のブロックをドラッグして配置しよう"
        }
    }

    fun dropDraggedShape(index: Int, pointer: Offset?) {
        val shape = hand.getOrNull(index) ?: -1
        val targetPointer = pointer?.let { current ->
            blockPointerAboveFinger(current, shape, with(density) { 18.dp.toPx() }, with(density) { 1.dp.toPx() })
        }
        val cell = targetPointer?.let { current ->
            gridBounds?.let { grid ->
                assistedBlockOriginForPointer(current, grid, boardGapPx, board, shape)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlockBlastBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(50.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(42.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color.White.copy(alpha = 0.48f)),
                ) {
                    Text("‹", color = BlockBlastInk, fontSize = 30.sp, fontWeight = FontWeight.Light)
                }
                Text(
                    "1.0",
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = BlockBlastInk.copy(alpha = 0.13f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("♛", color = Color(0xFFE3B72C), fontSize = 33.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("$best", color = Color(0xFFD8AE26), fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
                Text("BEST", color = BlockBlastMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "$score",
                color = BlockBlastInk,
                fontSize = 62.sp,
                lineHeight = 66.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (gameOver) "ゲーム終了" else message,
                modifier = Modifier.padding(horizontal = 8.dp),
                color = if (gameOver) Color(0xFFB55D68) else BlockBlastMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            AnimatedVisibility(multiplierRemainingMillis > 0L && multiplier > 1) {
                val multiplierScale by animateFloatAsState(
                    targetValue = if (multiplierRemainingMillis % 1_000L < 500L) 1.04f else 1f,
                    animationSpec = tween(220),
                    label = "倍率の脈動",
                )
                Surface(
                    modifier = Modifier.padding(top = 7.dp).scale(multiplierScale),
                    color = Color(0xFFFFE29B).copy(alpha = 0.88f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "${multiplier}倍  ${(multiplierRemainingMillis / 100L) / 10f}秒",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = Color(0xFF8A5B00),
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFD2EFEC), Color(0xFFE9FAF8), Color(0xFFD5F0ED)),
                        ),
                    )
                    .border(3.dp, Color(0xFF92C5C1), RoundedCornerShape(25.dp))
                    .padding(10.dp)
                    .onGloballyPositioned { boardBounds = it.boundsInRoot() },
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(8) { y ->
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            repeat(8) { x ->
                                val draggingShape = hand.getOrNull(draggingIndex) ?: -1
                                val ghostCell = dropCell?.let { (dropX, dropY) ->
                                    draggingShape in BlockBlastRules.shapes.indices &&
                                        (x - dropX to y - dropY) in BlockBlastRules.shapes[draggingShape]
                                } == true
                                val ghostValid = dropCell?.let { (dropX, dropY) ->
                                    BlockBlastRules.fits(board, draggingShape, dropX, dropY)
                                } == true
                                BlastBoardCell(
                                    value = board[y * 8 + x],
                                    ghost = ghostCell,
                                    ghostValid = ghostValid,
                                    shapeColor = BlockBlastBlockColors.getOrElse(draggingShape % 4 + 1) { BlockBlastBlockColors[1] },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                hand.forEachIndexed { index, shape ->
                    val cardBounds = handBounds[index]
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(132.dp)
                            .onGloballyPositioned { coordinates ->
                                handBounds = handBounds + (index to coordinates.boundsInRoot())
                            }
                            .pointerInput(index, shape, gameOver, cardBounds) {
                                if (shape < 0 || gameOver || cardBounds == null) return@pointerInput
                                var lastPointer: Offset? = null
                                detectDragGestures(
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
                            .padding(9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // 手札はブロック形状だけを表示し、カードの枠や状態文言は描画しない。
                        ShapePreviewGrid(shape = shape, cellSize = 25.dp)
                    }
                }
            }
        }

        val previewShape = hand.getOrNull(draggingIndex) ?: -1
        val pointer = dragTargetPointer
        if (previewShape in BlockBlastRules.shapes.indices && pointer != null) {
            val previewOffset = with(density) {
                // 外接矩形に合わせたプレビューの中心を指の位置へ合わせる。
                blockPreviewOffset(pointer, previewShape, 18.dp.toPx(), 1.dp.toPx())
            }
            Box(
                modifier = Modifier
                    .offset { previewOffset }
                    .wrapContentSize()
                    .zIndex(4f),
            ) {
                ShapePreviewGrid(shape = previewShape, cellSize = 18.dp)
            }
        }

        AnimatedVisibility(
            visible = gameOver,
            modifier = Modifier.fillMaxSize().zIndex(8f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF184D50).copy(alpha = 0.26f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.padding(28.dp).fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(32.dp),
                    shadowElevation = 18.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("置ける場所がなくなりました", color = BlockBlastInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("$score", color = BlockBlastInk, fontSize = 64.sp, fontWeight = FontWeight.Black)
                        Text("今回のスコア", color = BlockBlastMuted, fontWeight = FontWeight.Medium)
                        Button(
                            onClick = ::resetForNextGame,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text("次へ", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

    }
}

/** 盤面の1マス。空きマス、配置済み、ドラッグ中の候補を同じ大きさで描画する。 */
@Composable
private fun BlastBoardCell(
    value: Int,
    ghost: Boolean,
    ghostValid: Boolean,
    shapeColor: Color,
    modifier: Modifier = Modifier,
) {
    val baseColor = when {
        ghost && ghostValid -> shapeColor.copy(alpha = 0.65f)
        ghost -> Color(0xFFE997A1).copy(alpha = 0.72f)
        value > 0 -> BlockBlastBlockColors.getOrElse(value) { BlockBlastBlockColors[1] }
        else -> BlockBlastCell
    }
    val color by animateColorAsState(
        targetValue = baseColor,
        animationSpec = tween(130, easing = FastOutSlowInEasing),
        label = "ブロック色",
    )
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = if (value > 0 || ghost) 2.dp else 0.dp,
                shape = shape,
                ambientColor = Color(0xFF559A96).copy(alpha = 0.35f),
                spotColor = Color(0xFF559A96).copy(alpha = 0.42f),
            )
            .clip(shape)
            .background(
                if (value > 0 || ghost) {
                    Brush.linearGradient(
                        listOf(color.copy(alpha = 0.98f), color.copy(alpha = 0.72f), color),
                    )
                } else {
                    Brush.linearGradient(listOf(color, color))
                },
            )
            .border(1.dp, if (value > 0 || ghost) color.copy(alpha = 0.85f) else BlockBlastCellLine, shape)
            .drawBehind {
                if (value > 0 || ghost) {
                    drawRect(Color.White.copy(alpha = 0.18f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.16f))
                }
            }
    )
}

/** 手札とドラッグ中の浮遊表示で共通利用する、ブロック形状の外接矩形プレビュー。 */
@Composable
private fun ShapePreviewGrid(shape: Int, cellSize: Dp) {
    val metrics = blockShapeMetrics(shape) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        for (y in metrics.minY..metrics.maxY) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                for (x in metrics.minX..metrics.maxX) {
                    val filled = (x to y) in BlockBlastRules.shapes[shape]
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (filled) {
                                    Brush.linearGradient(
                                        listOf(
                                            BlockBlastBlockColors[shape % 4 + 1].copy(alpha = 0.98f),
                                            BlockBlastBlockColors[shape % 4 + 1].copy(alpha = 0.68f),
                                        ),
                                    )
                                } else {
                                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                },
                            )
                            .border(
                                width = if (filled) 1.dp else 0.dp,
                                color = if (filled) Color.White.copy(alpha = 0.32f) else Color.Transparent,
                                shape = RoundedCornerShape(3.dp),
                            ),
                    )
                }
            }
        }
    }
}
