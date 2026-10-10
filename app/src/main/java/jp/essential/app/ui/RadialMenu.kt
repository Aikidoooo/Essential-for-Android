package jp.essential.app.ui

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/** 持ち上げ済みのウィジェット操作だけを、一筆の終了まで除外する。 */
internal class RadialGestureGate {
    var widgetHeld = false
    var movedBeforeHold = false
    fun blockWidgetGesture() { if (!movedBeforeHold) widgetHeld = true }
}
internal val LocalRadialGestureGate = staticCompositionLocalOf<RadialGestureGate?> { null }

/** 既存操作を観察し、消費済みの操作でも円なら認識する。自身はイベントを消費しない。 */
internal fun Modifier.radialCircleGesture(gate: RadialGestureGate, enabled: Boolean, onCircle: () -> Unit): Modifier = pointerInput(gate, enabled, onCircle) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        gate.widgetHeld = false
        gate.movedBeforeHold = false
        val minimumDiameter = min(size.width, size.height) / 20f
        val movementThreshold = min(viewConfiguration.touchSlop, minimumDiameter * .15f).coerceAtLeast(2f)
        val points = ArrayList<CirclePoint>(96)
        points.add(CirclePoint(down.position.x, down.position.y))
        var lastSampleTime = down.uptimeMillis
        fun appendPoint(position: Offset, sampleTime: Long) {
            if (sampleTime <= lastSampleTime) return
            lastSampleTime = sampleTime
            val last = points.last()
            if (hypot(position.x - last.x, position.y - last.y) < .5f) return
            // 高頻度のマウス入力でも失敗させず、始点を保ちながら経路を間引く。
            if (points.size >= 256) {
                var write = 1
                for (read in 2 until points.size step 2) points[write++] = points[read]
                while (points.size > write) points.removeAt(points.lastIndex)
            }
            points.add(CirclePoint(position.x, position.y))
        }
        var rejected = false
        var endTime = down.uptimeMillis
        do {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (event.changes.size != 1 || change == null) rejected = true
            if (gate.widgetHeld) rejected = true
            if (change != null) {
                endTime = change.uptimeMillis
                // 小さな円を描いている最中に長押し時間が来ても、ウィジェットを持ち上げない。
                if (endTime - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis &&
                    (change.position - down.position).getDistance() > movementThreshold) gate.movedBeforeHold = true
                change.historical.forEach { sample ->
                    appendPoint(sample.position, sample.uptimeMillis)
                }
                appendPoint(change.position, change.uptimeMillis)
            }
            if (endTime - down.uptimeMillis > 1100) rejected = true
        } while (event.changes.any { it.pressed })
        val accepted = !rejected && !gate.widgetHeld && isRadialCircle(points, endTime - down.uptimeMillis, minimumDiameter)
        if (jp.essential.app.BuildConfig.DEBUG) android.util.Log.d("RadialGesture", "入力=${down.type} 時間=${endTime - down.uptimeMillis} 点数=${points.size} 保持=${gate.widgetHeld} 円=$accepted")
        if (accepted) onCircle()
    }
}

@Composable
internal fun RadialMenuHost(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val gate = remember { RadialGestureGate() }
    val open = remember { { visible = true } }
    CompositionLocalProvider(LocalRadialGestureGate provides gate) {
    GlassBackdropScope(Modifier.fillMaxSize().testTag("radial-gesture-host").radialCircleGesture(gate, !visible, open), enabled = visible,
        background = { CompositionLocalProvider(LocalGlassBackdrop provides null) { content() } }) {
        AnimatedVisibility(visible, enter = fadeIn() + scaleIn(animationSpec = spring(dampingRatio = 1f, stiffness = 380f), initialScale = .92f),
            exit = fadeOut() + scaleOut(targetScale = .96f)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                RadialMenu(onClose = { visible = false })
            }
        }
    }
    }
}

private data class LauncherApp(val shortcut: RadialShortcut, val icon: Bitmap?)

@Composable
internal fun RadialMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val store = remember { RadialShortcutStore(context) }
    var shortcuts by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf<List<LauncherApp>?>(null) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var dragTarget by remember { mutableStateOf<Int?>(null) }
    fun update(items: List<RadialShortcut>) { shortcuts = items; store.save(items) }
    fun launch(shortcut: RadialShortcut) {
        val intent = shortcut.component?.let(ComponentName::unflattenFromString)?.let {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(it)
        } ?: context.packageManager.getLaunchIntentForPackage(shortcut.packageName)
            ?: if (shortcut.packageName == "com.zhiliaoapp.musically") context.packageManager.getLaunchIntentForPackage("com.ss.android.ugc.trill") else null
        if (intent == null) Toast.makeText(context, "${shortcut.label}がインストールされていません", Toast.LENGTH_SHORT).show()
        else runCatching { context.startActivity(intent); onClose() }.onFailure {
            Toast.makeText(context, "アプリを開けませんでした", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        catalog = withContext(Dispatchers.IO) {
            val manager = context.packageManager
            manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { info ->
                    LauncherApp(RadialShortcut(info.activityInfo.packageName, info.loadLabel(manager).toString(),
                        ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString()),
                        runCatching { val drawable = info.loadIcon(manager); val square = if (drawable is android.graphics.drawable.AdaptiveIconDrawable) android.graphics.drawable.LayerDrawable(arrayOf(drawable.background, drawable.foreground)) else drawable; square.toBitmap(96, 96) }.getOrNull())
                }.distinctBy { it.shortcut.packageName }.sortedBy { it.shortcut.label.lowercase() }
        }
    }
    BackHandler { if (picking) picking = false else if (editing) editing = false else onClose() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .25f))
        .clickable(onClick = onClose).testTag("radial-menu"), contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth - 24.dp, maxHeight - 160.dp, 380.dp)
        val radius = with(density) { (diameter / 2 - 44.dp).toPx() }
        val verticalRadius = minOf(radius * (1f + (shortcuts.size - 8).coerceAtLeast(0) * .18f),
            with(density) { (maxHeight / 2 - 100.dp).toPx() })
        val slots = remember(shortcuts.size, radius, verticalRadius) {
            List(shortcuts.size) { index ->
                val angle = -PI / 2 + index * 2 * PI / shortcuts.size
                // 個数が増えるほど縦へ広げ、画面幅を守りながらアイコンと名前の間隔を確保する。
                Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * verticalRadius)
            }
        }
        val previewOrder = remember(shortcuts, dragged, dragTarget) {
            shortcuts.toMutableList().apply {
                val source = indexOfFirst { it.packageName == dragged }
                val target = dragTarget
                if (source >= 0 && target != null) {
                    // 全体を詰め直さず、移動元と移動先の2個だけを入れ替える。
                    val destination = target.coerceIn(0, lastIndex)
                    val original = this[source]
                    this[source] = this[destination]
                    this[destination] = original
                }
            }
        }
        Box(Modifier.fillMaxSize().clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            shortcuts.forEachIndexed { index, shortcut ->
                key(shortcut.packageName) {
                    val app = catalog?.firstOrNull { it.shortcut.packageName == shortcut.packageName }
                        ?: catalog?.firstOrNull { shortcut.packageName == "com.zhiliaoapp.musically" && it.shortcut.packageName == "com.ss.android.ugc.trill" }
                    val previewIndex = previewOrder.indexOfFirst { it.packageName == shortcut.packageName }
                    val targetPosition = slots[previewIndex]
                    val isDragging = dragged == shortcut.packageName
                    val animatedPosition = remember { Animatable(slots[index], Offset.VectorConverter) }
                    var wasDragging by remember { mutableStateOf(false) }
                    LaunchedEffect(targetPosition, isDragging) {
                        if (isDragging) {
                            wasDragging = true
                        } else {
                            // 指を離した場所から収束させ、移動開始位置へ飛び戻らないようにする。
                            if (wasDragging) animatedPosition.snapTo(dragPosition)
                            wasDragging = false
                            animatedPosition.animateTo(targetPosition, spring(dampingRatio = .9f, stiffness = 420f))
                        }
                    }
                    val lift by androidx.compose.animation.core.animateFloatAsState(
                        if (isDragging) 1f else 0f, spring(dampingRatio = .85f, stiffness = 500f), label = "アイコンの浮き上がり")
                    val position = if (isDragging) dragPosition else animatedPosition.value
                    Box(Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                        .zIndex(if (isDragging) 2f else 0f)
                        .graphicsLayer {
                            scaleX = 1f + lift * .08f
                            scaleY = 1f + lift * .08f
                            shadowElevation = with(density) { 12.dp.toPx() } * lift
                            shape = RoundedCornerShape(21.dp)
                            clip = false
                        }
                        .size(width = 72.dp, height = 88.dp).testTag("radial-shortcut-${shortcut.packageName}")) {
                        Column(Modifier.fillMaxSize().semantics(mergeDescendants = true) {
                            contentDescription = shortcut.label
                            onClick("アプリを開く") { if (!editing) launch(shortcut); true }
                            onLongClick("ショートカットを編集") { editing = true; true }
                        }
                            .pointerInput(shortcut.packageName, slots, editing, shortcuts) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    var released = false
                                    var moved = false
                                    var dragStartDelta = Offset.Zero
                                    // 押下から500ms以内の移動は並べ替え、600msの静止は削除編集にする。
                                    val held = withTimeoutOrNull(600L) {
                                        while (true) {
                                            val change = awaitPointerEvent().changes.first { it.id == down.id }
                                            if (!change.pressed) { released = true; break }
                                            if (change.uptimeMillis - down.uptimeMillis <= 500L &&
                                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                                moved = true
                                                dragStartDelta = change.position - down.position
                                                change.consume()
                                                break
                                            }
                                        }
                                        true
                                    }
                                    if (held == null) editing = true
                                    if (released && !editing) {
                                        launch(shortcut)
                                    } else {
                                        if (moved) {
                                            dragPosition = animatedPosition.value + dragStartDelta
                                            dragTarget = index
                                            dragged = shortcut.packageName
                                            try {
                                                while (true) {
                                                    // 近いスロットへ入ったら周囲のアイコンが先に場所を空ける。
                                                    val nearest = slots.indices.minByOrNull { (slots[it] - dragPosition).getDistance() } ?: index
                                                    val current = dragTarget ?: index
                                                    val hysteresis = with(density) { 12.dp.toPx() }
                                                    if ((slots[nearest] - dragPosition).getDistance() + hysteresis <
                                                        (slots[current] - dragPosition).getDistance()) dragTarget = nearest
                                                    val change = awaitPointerEvent().changes.first { it.id == down.id }
                                                    // 移動する子の座標系が変わっても、同じイベント内の差分で指に追従する。
                                                    dragPosition += change.positionChange()
                                                    change.consume()
                                                    if (!change.pressed) break
                                                }
                                                val target = dragTarget ?: index
                                                val reordered = shortcuts.toMutableList()
                                                val source = reordered.indexOfFirst { it.packageName == shortcut.packageName }
                                                if (source >= 0) {
                                                    val original = reordered[source]
                                                    reordered[source] = reordered[target]
                                                    reordered[target] = original
                                                }
                                                update(reordered)
                                            } finally {
                                                // 中断時もドラッグの見た目や仮配置を残さない。
                                                dragged = null
                                                dragTarget = null
                                            }
                                        }
                                    }
                                }
                            }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(66.dp).liquidGlass(RoundedCornerShape(21.dp)), contentAlignment = Alignment.Center) {
                                if (app?.icon != null) Image(app.icon.asImageBitmap(), shortcut.label, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.15f; scaleY = 1.15f })
                                else Text(shortcut.label.take(2), style = MaterialTheme.typography.titleSmall)
                            }
                            Text(if (shortcut.packageName == "com.android.chrome") "Chrome" else shortcut.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        if (editing) Box(Modifier.align(Alignment.TopEnd).size(24.dp).background(Color(0xFFFF6068), CircleShape)
                            .clickable { update(shortcuts.filterNot { it.packageName == shortcut.packageName }) }
                            .semantics { contentDescription = "${shortcut.label}を削除" }.testTag("radial-remove-${shortcut.packageName}"), contentAlignment = Alignment.Center) {
                            Text("−", color = Color.White)
                        }
                    }
                }
            }
            Box(Modifier.size(52.dp).liquidGlass(CircleShape)
                .clickable {
                    if (editing) editing = false
                    else if (shortcuts.size >= RadialShortcutStore.MAX_SHORTCUTS) editing = true
                    else { search = ""; picking = true }
                }.semantics { contentDescription = if (editing) "編集を終了" else if (shortcuts.size >= RadialShortcutStore.MAX_SHORTCUTS) "ショートカットを削除" else "アプリを追加" }.testTag("radial-add"), contentAlignment = Alignment.Center) {
                Text(if (shortcuts.size >= RadialShortcutStore.MAX_SHORTCUTS || editing) "−" else "+", style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (picking) {
            Box(Modifier.fillMaxWidth(.92f).heightIn(max = 560.dp).safeDrawingPadding()
                .liquidGlass(RoundedCornerShape(28.dp))
                // 明暗と淡い分光色を交互に重ね、液体の縁で光が屈折する表情を作る。
                .border(1.5.dp, Brush.linearGradient(listOf(
                    Color.White.copy(alpha = .75f), Color(0xFFB9EFFF).copy(alpha = .35f),
                    Color.White.copy(alpha = .08f), Color(0xFFE3CCFF).copy(alpha = .40f),
                    Color.White.copy(alpha = .65f), Color.White.copy(alpha = .10f)
                )), RoundedCornerShape(28.dp))
                .clickable { }.testTag("radial-app-picker")) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("アプリを追加", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { picking = false }, modifier = Modifier.liquidGlass(CircleShape)) { Text("戻る") }
                    }
                    TextField(search, { search = it }, placeholder = { Text("アプリを検索") }, singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(18.dp)))
                    if (catalog == null) CircularProgressIndicator(Modifier.padding(24.dp))
                    else LazyColumn {
                        items(catalog.orEmpty().filter { app -> shortcuts.none { it.packageName == app.shortcut.packageName } && app.shortcut.label.contains(search, true) }, key = { it.shortcut.packageName }) { app ->
                            Row(Modifier.fillMaxWidth().clickable {
                                if (shortcuts.size < RadialShortcutStore.MAX_SHORTCUTS) update(shortcuts + app.shortcut)
                                picking = false
                            }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                app.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))) }
                                Text(app.shortcut.label, Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
