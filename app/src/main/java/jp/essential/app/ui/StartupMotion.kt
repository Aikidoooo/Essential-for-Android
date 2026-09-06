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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import jp.essential.app.R
import jp.essential.app.ui.theme.LocalEssentialDark
import kotlinx.coroutines.delay

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
    var revealed by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (revealed) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!revealed) {
            delay((index * 65L).coerceIn(0L, 520L))
            progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            revealed = true
        }
    }
    // 各フレームの状態は描画レイヤーで読み、子の再コンポーズや再計測を避ける。
    Box(modifier.graphicsLayer {
        val fraction = progress.value
        alpha = fraction
        translationY = (1f - fraction) * 34.dp.toPx()
        scaleX = 0.99f + 0.01f * fraction
        scaleY = scaleX
    }) { content() }
}

internal fun LazyListScope.progressiveItem(index: Int, content: @Composable LazyItemScope.() -> Unit) {
    item(key = "progressive-$index") {
        ProgressiveWidget(index) {
            // モーションのBox内でも、見出し・余白・説明を縦に配置する。
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}
