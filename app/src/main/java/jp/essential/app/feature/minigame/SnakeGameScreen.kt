package jp.essential.app.feature.minigame

import jp.essential.app.ui.fixedHeader
import jp.essential.app.ui.FeatureHeader
import jp.essential.app.ui.GlassFeatureTitle

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import jp.essential.app.profile.AppProgressStore
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

private const val SNAKE_COLUMNS = 16
private const val SNAKE_ROWS = 22

private data class SnakeCell(val column: Int, val row: Int)

private enum class SnakeDirection(val columnStep: Int, val rowStep: Int, val label: String) {
    Up(0, -1, "上"),
    Right(1, 0, "右"),
    Down(0, 1, "下"),
    Left(-1, 0, "左"),
}

private enum class SnakeStatus { Ready, Playing, Paused, Over, Cleared }

/** スワイプと移動スティックで遊べる、成長・衝突判定・一時停止に対応したヘビゲーム。 */
@Composable
internal fun SnakeGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("snake_game", Context.MODE_PRIVATE) }
    val initialSnake = remember { listOf(SnakeCell(7, 11), SnakeCell(6, 11), SnakeCell(5, 11)) }
    var snake by remember { mutableStateOf(initialSnake) }
    var direction by remember { mutableStateOf(SnakeDirection.Right) }
    var food by remember { mutableStateOf(makeSnakeFood(initialSnake)) }
    var status by remember { mutableStateOf(SnakeStatus.Ready) }
    var foodCount by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(preferences.getInt("best_score", 0)) }
    var xpAwarded by remember { mutableStateOf(false) }
    val pulse by rememberInfiniteTransition(label = "snake-food-pulse").animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "snake-food-scale",
    )
    GamePlayXp("snake", status == SnakeStatus.Playing, 16)
    val score = foodCount * 10

    fun restart() {
        val nextSnake = listOf(SnakeCell(7, 11), SnakeCell(6, 11), SnakeCell(5, 11))
        snake = nextSnake
        direction = SnakeDirection.Right
        food = makeSnakeFood(nextSnake)
        foodCount = 0
        xpAwarded = false
        status = SnakeStatus.Ready
    }

    fun chooseDirection(next: SnakeDirection) {
        val isReverse = next.columnStep == -direction.columnStep && next.rowStep == -direction.rowStep
        if (!isReverse && status == SnakeStatus.Ready) {
            direction = next
            status = SnakeStatus.Playing
        } else if (!isReverse && status == SnakeStatus.Playing) {
            direction = next
        }
    }

    fun finishGame(cleared: Boolean) {
        status = if (cleared) SnakeStatus.Cleared else SnakeStatus.Over
        if (score > bestScore) {
            bestScore = score
            preferences.edit().putInt("best_score", score).apply()
        }
        if (!xpAwarded && cleared) {
            AppProgressStore(context).addXp(50)
            xpAwarded = true
        }
    }

    LaunchedEffect(status) {
        while (status == SnakeStatus.Playing) {
            delay((310L - foodCount * 7L).coerceAtLeast(105L))
            val head = snake.first()
            val nextHead = SnakeCell(head.column + direction.columnStep, head.row + direction.rowStep)
            val eatsFood = nextHead == food
            val collisionBody = if (eatsFood) snake else snake.dropLast(1)
            val hitWall = nextHead.column !in 0 until SNAKE_COLUMNS || nextHead.row !in 0 until SNAKE_ROWS
            if (hitWall || nextHead in collisionBody) {
                finishGame(cleared = false)
            } else {
                snake = if (eatsFood) listOf(nextHead) + snake else listOf(nextHead) + snake.dropLast(1)
                if (eatsFood) {
                    foodCount += 1
                    if (snake.size == SNAKE_COLUMNS * SNAKE_ROWS) finishGame(cleared = true)
                    else food = makeSnakeFood(snake)
                }
            }
        }
    }

    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FeatureHeader("ヘビゲーム", onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SnakeStat("スコア", score.toString(), Modifier.weight(1f))
            SnakeStat("ベスト", bestScore.toString(), Modifier.weight(1f))
            SnakeStat("エサ", foodCount.toString(), Modifier.weight(1f))
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val boardHeight = (maxWidth * SNAKE_ROWS.toFloat() / SNAKE_COLUMNS.toFloat()).coerceAtMost(maxHeight)
            val boardWidth = boardHeight * SNAKE_COLUMNS.toFloat() / SNAKE_ROWS.toFloat()
            Box(
                modifier = Modifier
                    .size(boardWidth, boardHeight)
                    .shadow(14.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF102C31))
                    .border(1.dp, Color(0xFF77D6A8).copy(alpha = 0.34f), RoundedCornerShape(28.dp))
                            .semantics { contentDescription = "ヘビゲームの盤面。スワイプまたは移動スティックで方向を変更" }
                    .pointerInput(status, direction) {
                        var dragX = 0f
                        var dragY = 0f
                        detectDragGestures(
                            onDragEnd = { dragX = 0f; dragY = 0f },
                            onDragCancel = { dragX = 0f; dragY = 0f },
                        ) { change, amount ->
                            change.consume()
                            dragX += amount.x
                            dragY += amount.y
                            if (maxOf(abs(dragX), abs(dragY)) > 24f) {
                                if (abs(dragX) > abs(dragY)) chooseDirection(if (dragX > 0) SnakeDirection.Right else SnakeDirection.Left)
                                else chooseDirection(if (dragY > 0) SnakeDirection.Down else SnakeDirection.Up)
                                dragX = 0f
                                dragY = 0f
                            }
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                val cellWidth = size.width / SNAKE_COLUMNS
                val cellHeight = size.height / SNAKE_ROWS
                for (row in 0 until SNAKE_ROWS) {
                    for (column in 0 until SNAKE_COLUMNS) {
                        val isAlternate = (row + column) % 2 == 0
                        drawRect(
                            color = if (isAlternate) Color(0xFF15383A) else Color(0xFF133336),
                            topLeft = Offset(column * cellWidth, row * cellHeight),
                            size = Size(cellWidth, cellHeight),
                        )
                    }
                }
                val foodCenter = Offset((food.column + 0.5f) * cellWidth, (food.row + 0.5f) * cellHeight)
                drawCircle(Color(0xFFFF866F).copy(alpha = 0.17f), cellWidth * 0.47f * pulse, foodCenter)
                drawCircle(Color(0xFFFF8B70), cellWidth * 0.29f, foodCenter)
                snake.asReversed().forEachIndexed { index, cell ->
                    val inset = cellWidth * 0.09f
                    val isHead = cell == snake.first()
                    drawRoundRect(
                        color = if (isHead) Color(0xFFB9F56B) else Color(0xFF49D0A0).copy(alpha = 1f - index * 0.006f),
                        topLeft = Offset(cell.column * cellWidth + inset, cell.row * cellHeight + inset),
                        size = Size(cellWidth - inset * 2, cellHeight - inset * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellWidth * 0.24f),
                    )
                }
            }
                if (status != SnakeStatus.Playing) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCC07191B), RoundedCornerShape(28.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            when (status) {
                                SnakeStatus.Ready -> "準備完了"
                                SnakeStatus.Paused -> "一時停止中"
                                SnakeStatus.Cleared -> "盤面クリア！"
                                else -> "ゲームオーバー"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (status == SnakeStatus.Ready) {
                            Spacer(Modifier.height(6.dp))
                            Text("スティックを動かすか盤面をスワイプして開始", color = Color(0xFFBFC2D3), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("$score 点", color = Color(0xFFB9F56B), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    when (status) {
                        SnakeStatus.Playing -> status = SnakeStatus.Paused
                        SnakeStatus.Ready, SnakeStatus.Paused -> status = SnakeStatus.Playing
                        else -> restart()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF235E57), contentColor = Color.White),
            ) {
                Text(
                    when (status) {
                        SnakeStatus.Playing -> "一時停止"
                        SnakeStatus.Ready -> "スタート"
                        SnakeStatus.Paused -> "再開"
                        else -> "もう一度"
                    },
                )
            }
            if (status == SnakeStatus.Over || status == SnakeStatus.Cleared) {
                Button(modifier = Modifier.weight(1f), onClick = onBack) { Text("メニューへ") }
            }
        }

        SnakeMovementStick(status = status, direction = direction, onDirection = ::chooseDirection)
        Text(
            "エサで加速　・　壁／自身に衝突で終了",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
        )
    }
}

@Composable
private fun SnakeStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.58f), Color.White.copy(alpha = 0.29f))))
            .border(1.dp, Color.White.copy(alpha = 0.68f), RoundedCornerShape(18.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SnakeMovementStick(
    status: SnakeStatus,
    direction: SnakeDirection,
    onDirection: (SnakeDirection) -> Unit,
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val latestStatus by rememberUpdatedState(status)
    val latestDirection by rememberUpdatedState(direction)
    val latestOnDirection by rememberUpdatedState(onDirection)
    val isEnabled = status == SnakeStatus.Playing || status == SnakeStatus.Ready

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("移動スティック", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .size(138.dp)
                .graphicsLayer { alpha = if (isEnabled) 1f else 0.52f }
                .clip(CircleShape)
                .background(Color(0xB32B5660))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                .semantics { contentDescription = "移動スティック。現在の方向は${direction.label}です" }
                .pointerInput(Unit) {
                    val stickDiameter = minOf(size.width, size.height).toFloat()
                    val radius = stickDiameter * 0.34f
                    val deadZone = stickDiameter * 0.14f

                    fun applyPosition(position: Offset) {
                        if (latestStatus != SnakeStatus.Playing && latestStatus != SnakeStatus.Ready) return
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val rawOffset = position - center
                        val length = rawOffset.getDistance()
                        val clampedOffset = if (length > radius) rawOffset * (radius / length) else rawOffset
                        thumbOffset = clampedOffset
                        if (clampedOffset.getDistance() < deadZone) return
                        val nextDirection = if (abs(clampedOffset.x) > abs(clampedOffset.y)) {
                            if (clampedOffset.x > 0f) SnakeDirection.Right else SnakeDirection.Left
                        } else {
                            if (clampedOffset.y > 0f) SnakeDirection.Down else SnakeDirection.Up
                        }
                        val isReverse = nextDirection.columnStep == -latestDirection.columnStep &&
                            nextDirection.rowStep == -latestDirection.rowStep
                        if (!isReverse && (nextDirection != latestDirection || latestStatus == SnakeStatus.Ready)) {
                            latestOnDirection(nextDirection)
                        }
                    }

                    detectDragGestures(
                        onDragStart = ::applyPosition,
                        onDragEnd = { thumbOffset = Offset.Zero },
                        onDragCancel = { thumbOffset = Offset.Zero },
                    ) { change, amount ->
                        change.consume()
                        if (latestStatus == SnakeStatus.Playing || latestStatus == SnakeStatus.Ready) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            applyPosition(center + thumbOffset + amount)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = size.minDimension * 0.36f,
                    center = center,
                    style = Stroke(width = size.minDimension * 0.008f),
                )
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                    .size(54.dp)
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xC77FB3BA))
                    .border(1.dp, Color.White.copy(alpha = 0.82f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.White.copy(alpha = 0.32f), CircleShape)
                        .border(0.75.dp, Color.White.copy(alpha = 0.62f), CircleShape),
                )
            }
        }
    }
}

private fun makeSnakeFood(snake: List<SnakeCell>): SnakeCell {
    val freeCells = buildList {
        for (row in 0 until SNAKE_ROWS) for (column in 0 until SNAKE_COLUMNS) {
            val cell = SnakeCell(column, row)
            if (cell !in snake) add(cell)
        }
    }
    return freeCells.random(Random.Default)
}
