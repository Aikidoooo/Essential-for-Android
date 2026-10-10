package jp.essential.app.update

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.essential.app.BuildConfig
import jp.essential.app.feature.downloader.YtDlpUpdateSettingsCard
import jp.essential.app.ui.GlassBackdropScope
import jp.essential.app.ui.GlassBackButton
import jp.essential.app.ui.SettingsGradientBackground

/** アプリ本体と内蔵ソフトの更新を一か所で確認する。 */
@Composable
internal fun AppUpdatesScreen(onBack: () -> Unit, bottomPadding: Dp, model: AppUpdateModel = viewModel()) {
    val context = LocalContext.current
    DisposableEffect(model) {
        model.detailOpen = true
        model.showDialog = false
        onDispose { model.detailOpen = false }
    }
    LaunchedEffect(model) { model.check(showPrompt = false) }
    val release = model.release
    val title = if (BuildConfig.IS_XIAOMI_PACKAGE) "Essential Xiaomi" else "Essential Universal"
    GlassBackdropScope(Modifier.fillMaxSize().testTag("app-updates-screen"), background = { SettingsGradientBackground() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp,
            top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 88.dp,
            bottom = bottomPadding + if (release != null) 106.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 50.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(release?.version ?: BuildConfig.VERSION_NAME, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (release != null) "新しいアップデート" else "現在のバージョン", color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("アップデートの詳細", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    ReleaseNotes((if (release != null) release.notes else model.installedNotes).takeIf { it.isNotBlank() }
                        ?: if (model.busy) "更新詳細を確認しています…" else "公開された更新詳細はまだ取得できていません。再確認してください。",
                    )
                    if (model.notesMessage.isNotBlank()) Text(model.notesMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("起動時に更新を確認", Modifier.weight(1f))
                    Switch(model.startupCheck, model::changeStartupCheck)
                }
                TextButton(onClick = { model.check(showPrompt = false) }, enabled = !model.busy) { Text("アップデートを再確認") }
                if (model.message.isNotBlank()) Text(model.message, style = MaterialTheme.typography.bodyMedium)
                if (model.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            item {
                Text("使用ソフトのアップデート", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                YtDlpUpdateSettingsCard()
            }
            item {
                Text("その他の同梱ソフト", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("FFmpeg・画像処理／認識ライブラリなどは、Essential本体のアップデートに含まれます。個別のインストールは不要です。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(onClick = onBack, modifier = Modifier.testTag("updates-back").semantics { contentDescription = "設定に戻る" })
            Spacer(Modifier.width(16.dp))
            Text("アップデート", style = MaterialTheme.typography.titleMedium)
        }
        if (release != null) {
            Button(onClick = {
                if (model.downloaded && model.beginInstallation()) context.startActivity(Intent(context, UpdateInstallActivity::class.java))
                else model.download()
            }, enabled = !model.busy, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp,
                bottom = bottomPadding + WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 16.dp)
                .fillMaxWidth().height(56.dp).testTag("app-update-action")) {
                Text(if (model.downloaded) "インストールへ進む" else "更新する", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Releaseの見出しと段落を、読みやすい余白で表示する。 */
@Composable
private fun ReleaseNotes(notes: String) {
    val paragraphs = remember(notes) {
        val sections = notes.trim().split(Regex("(?m)(?=^#{1,6} )")).filter(String::isNotBlank)
        // 配布説明より機能の変更点を先に読み、公開された説明はすべて残す。
        val (changes, other) = sections.partition { section ->
            val heading = section.lineSequence().first()
            listOf("変更", "新機能", "改善", "Changelog", "What's new").any { heading.contains(it, ignoreCase = true) }
        }
        (changes + other).flatMap { it.trim().split(Regex("\n\\s*\n")) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        paragraphs.forEach { paragraph ->
            val lines = paragraph.lines()
            val heading = lines.firstOrNull()?.takeIf { it.startsWith("#") }
            if (heading != null) {
                Text(heading.trimStart('#', ' '), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            val body = (if (heading != null) lines.drop(1) else lines).joinToString("\n") {
                if (it.startsWith("- ")) "• ${it.removePrefix("- ")}" else it
            }.trim()
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
