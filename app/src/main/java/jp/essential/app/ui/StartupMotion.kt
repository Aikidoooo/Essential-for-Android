package jp.essential.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import jp.essential.app.R
import jp.essential.app.ui.theme.LocalEssentialDark

internal val LocalProgressiveMotionCycle = compositionLocalOf { 0L }
internal val LocalProgressiveMotionDirection = compositionLocalOf { 0 }
internal val LocalProgressiveMotionCompleted = compositionLocalOf { false }

@Composable
internal fun StartupGate(content: @Composable () -> Unit) {
    var ready by rememberSaveable { mutableStateOf(false) }
    val startupProgress = remember { Animatable(if (ready) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!ready) {
            // 初回描画だけを待ち、起動演出のための最低待機時間は設けない。
            withFrameNanos { }
            startupProgress.snapTo(1f)
            ready = true
        }
    }
    Crossfade(ready, animationSpec = tween(200), label = "起動画面の退場") { started ->
        if (started) content() else {
            val colors = MaterialTheme.colorScheme
            Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.background, colors.primaryContainer)))
                    .safeDrawingPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // 80dp角の対角線より大きい156dp径で、四隅とリングも重ならない。
                Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { startupProgress.value }, modifier = Modifier.fillMaxSize(), strokeWidth = 8.dp,
                        color = colors.primary, trackColor = colors.primary.copy(alpha = 0.12f))
                    Image(painterResource(if (LocalEssentialDark.current) R.drawable.essential_icon_dark else R.drawable.essential_icon),
                        contentDescription = "Essentialの起動アイコン", modifier = Modifier.size(80.dp))
                }
                Spacer(Modifier.height(28.dp))
                Text("Essential", style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
                Spacer(Modifier.height(8.dp))
                Text("起動中", style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
            }
        }
    }
}

@Composable
internal fun ProgressiveWidget(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motionCycle = LocalProgressiveMotionCycle.current
    val horizontalDirection = LocalProgressiveMotionDirection.current
    val motionCompleted = LocalProgressiveMotionCompleted.current
    // タブ入場時は保存済みの表示完了状態を復元せず、毎回最初から再生する。
    var revealed by if (motionCycle == 0L) {
        rememberSaveable { mutableStateOf(false) }
    } else {
        remember(motionCycle) { mutableStateOf(motionCompleted) }
    }
    var visibleInViewport by remember { mutableStateOf(false) }
    val progress = remember(motionCycle) { Animatable(if (revealed) 1f else 0f) }
    val rootView = LocalView.current
    LaunchedEffect(visibleInViewport, revealed, motionCycle, motionCompleted) {
        if (motionCompleted) {
            progress.snapTo(1f)
            revealed = true
        } else if (visibleInViewport && !revealed) {
            // 画面内の項目だけを、上から順に短い間隔で表示する。
            progress.snapTo(0f)
            delay(index.coerceIn(0, 7) * 72L)
            progress.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
            revealed = true
        }
    }
    // 各フレームの状態は描画レイヤーで読み、子の再コンポーズや再計測を避ける。
    Box(
        modifier
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val viewportWidth = rootView.width.toFloat()
                val viewportHeight = rootView.height.toFloat()
                visibleInViewport = viewportWidth > 0f && viewportHeight > 0f &&
                    bounds.right > 0f && bounds.left < viewportWidth &&
                    bounds.bottom > 0f && bounds.top < viewportHeight
            }
            .graphicsLayer {
                val fraction = progress.value
                alpha = fraction
                if (horizontalDirection == 0) {
                    translationY = (fraction - 1f) * 34.dp.toPx()
                } else {
                    translationX = (1f - fraction) * horizontalDirection * 38.dp.toPx()
                }
            },
    ) { content() }
}

internal fun LazyListScope.progressiveItem(
    index: Int,
    keyPrefix: String = "progressive",
    content: @Composable LazyItemScope.() -> Unit,
) {
    item(key = "$keyPrefix-$index") {
        ProgressiveWidget(index) {
            // モーションのBox内でも、見出し・余白・説明を縦に配置する。
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}
