package jp.essential.app.update

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jp.essential.app.R
import jp.essential.app.ui.ProgressiveWidget
import jp.essential.app.ui.EssentialBubblyProgressBar
import jp.essential.app.ui.theme.EssentialTheme
import jp.essential.app.ui.theme.LocalEssentialDark
import java.util.Locale

@Composable
internal fun UpdateAvailableDialog(
    release: AppRelease,
    currentVersion: String,
    busy: Boolean,
    progress: Float?,
    downloaded: Boolean,
    failed: Boolean,
    message: String,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            val availableHeight = maxHeight
            ProgressiveWidget(0) {
                UpdateAvailableCard(
                    release, currentVersion, busy, progress, downloaded, failed, message, onDismiss, onUpdate,
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = availableHeight),
                )
            }
        }
    }
}

@Composable
internal fun UpdateAvailableCard(
    release: AppRelease,
    currentVersion: String,
    busy: Boolean,
    progress: Float?,
    downloaded: Boolean,
    failed: Boolean,
    message: String,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "更新ボタンの押下")
    val fraction = (progress ?: 0f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(fraction, tween(160), label = "ダウンロード進捗")

    // 半透明の重ね合わせは背景だけに限定し、文字やアイコンの視認性を保つ。
    Surface(modifier, shape = shape, color = colors.surface.copy(alpha = 0.98f), contentColor = colors.onSurface, shadowElevation = 12.dp) {
        Column(
            Modifier.clip(shape)
                .background(Brush.linearGradient(listOf(colors.primaryContainer.copy(alpha = 0.5f), colors.surface, colors.secondaryContainer.copy(alpha = 0.3f))))
                .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), colors.outlineVariant.copy(alpha = 0.4f))), shape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 本文だけをスクロールさせ、長いリリースノートでも操作ボタンを残す。
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(
                        painterResource(if (LocalEssentialDark.current) R.drawable.essential_icon_dark else R.drawable.essential_icon),
                        contentDescription = null, modifier = Modifier.size(64.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text("ESSENTIAL", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        Text("アップデート", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    }
                }
                Text(
                    when { downloaded -> "新しいEssentialの準備ができました"; failed -> "もう一度お試しください"; busy -> "新しいEssentialを準備しています"; else -> "新しいバージョンが届きました" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Surface(shape = RoundedCornerShape(20.dp), color = colors.surface.copy(alpha = 0.65f), contentColor = colors.onSurface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("現在  $currentVersion", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Text("最新  ${release.version}", style = MaterialTheme.typography.headlineMedium, color = colors.primary)
                        Text(String.format(Locale.JAPAN, "ダウンロードサイズ  %.1f MB", release.size / 1_000_000.0), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("更新内容", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                // Release由来の文章は実行・HTML解釈せず、そのまま表示する。
                Text(release.notes.ifBlank { "このバージョンのリリースノートはありません。" }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (release.digest != null) "インストール前にSHA-256・署名・アプリIDを検証します。" else "SHA-256は未提供です。署名・アプリIDを検証します。",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
            }
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (busy) {
                    if (fraction < 1f) EssentialBubblyProgressBar(progress = animatedProgress, modifier = Modifier.fillMaxWidth())
                    else EssentialBubblyProgressBar(modifier = Modifier.fillMaxWidth())
                }
                if (busy || failed || downloaded) {
                    Text(
                        if (busy) { if (fraction >= 1f) "ダウンロード完了・APKを検証中" else "ダウンロード中  ${(fraction * 100).toInt()}%" } else message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (failed) colors.error else colors.onSurfaceVariant,
                        modifier = Modifier.heightIn(max = 100.dp).verticalScroll(rememberScrollState()).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Button(
                    onClick = onUpdate, enabled = !busy, interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = buttonScale; scaleY = buttonScale },
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                ) { Text(when { busy -> "準備中…"; downloaded -> "インストールへ進む"; failed -> "再試行"; else -> "アップデート" }) }
                TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("後で") }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun UpdateAvailablePreview() {
    EssentialTheme(darkTheme = false) {
        UpdateAvailableCard(AppRelease("0.5.2", "表示の改善\n・更新画面を見やすくしました。\n・動作の安定性を改善しました。", "", 84_000_000, "preview"), "0.5.1", false, null, false, false, "", {}, {}, Modifier.padding(16.dp))
    }
}
