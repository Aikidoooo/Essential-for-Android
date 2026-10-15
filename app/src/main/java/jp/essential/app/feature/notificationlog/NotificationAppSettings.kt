package jp.essential.app.feature.notificationlog

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import jp.essential.app.ui.elasticListEdges
import jp.essential.app.ui.liquidGlass
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.withContext

internal data class NotificationAppOption(val packageName: String, val label: String)

/** ランチャーアプリに加え、OSから可視のアプリと既存ログの発信元を候補にする。 */
internal fun notificationAppOptions(context: Context, knownPackages: Set<String>): List<NotificationAppOption> {
    val manager = context.packageManager
    val packages = manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.packageName }.toSet() + manager.getInstalledApplications(0).map { it.packageName } + knownPackages
    return packages.map { packageName ->
        NotificationAppOption(packageName, runCatching {
            manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName))
    }.sortedWith(compareBy<NotificationAppOption> { it.label.lowercase() }.thenBy { it.packageName })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationAppSettings(store: NotificationLogStore, selectedPackages: Set<String>,
    onChange: (String, Boolean) -> Unit, saveError: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf("selected") }
    var search by rememberSaveable { mutableStateOf("") }
    var apps by remember { mutableStateOf(emptyList<NotificationAppOption>()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        loading = true
        try {
            apps = withContext(Dispatchers.IO) {
                notificationAppOptions(context, store.knownPackages() + store.selectedPackages())
            }
            failure = false
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            failure = true
        } finally { loading = false }
    }
    val candidates = apps.filter {
        (if (mode == "add") it.packageName !in selectedPackages else it.packageName in selectedPackages) &&
            (search.isBlank() || it.label.contains(search, true) || it.packageName.contains(search, true))
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface, dragHandle = null,
        modifier = Modifier.testTag("notification-app-settings")) {
        Column(Modifier.fillMaxWidth().liquidGlass(30.dp).padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { keyboard?.hide(); scope.launch { sheetState.hide(); onClose() } }) { Text("閉じる") }
                Text("通知ログの設定", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { keyboard?.hide(); mode = "add"; search = "" }, modifier = Modifier.testTag("notification-add-mode").semantics { contentDescription = "アプリを追加" }) { Text("＋") }
                TextButton(onClick = { keyboard?.hide(); mode = "remove"; search = "" }, modifier = Modifier.testTag("notification-remove-mode").semantics { contentDescription = "アプリを削除" }) { Text("－") }
            }
            Text(when(mode) { "add" -> "記録するアプリを追加"; "remove" -> "記録対象からアプリを削除"; else -> "記録対象のアプリ" },
                style = MaterialTheme.typography.titleSmall)
            Text("選択したアプリの新着通知だけを記録します。対象を削除しても保存済みのログは残ります。",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(search, { search = it }, label = { Text("アプリを検索") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().testTag("notification-app-search"))
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (failure) TextButton(onClick = { retry++ }) { Text("一覧を読み込めませんでした。再試行") }
            if (loading) CircularProgressIndicator(Modifier.padding(12.dp))
            LazyColumn(overscrollEffect = null, modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)
                .elasticListEdges().testTag("notification-app-list"), contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!loading && !failure && candidates.isEmpty()) item {
                    Text(if (search.isNotBlank()) "一致するアプリがありません" else if (mode == "add") "追加できるアプリがありません" else "記録対象のアプリはありません。＋から追加してください。")
                }
                items(candidates, key = { it.packageName }) { app ->
                    val interaction = remember(app.packageName) { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed) .98f else 1f,
                        spring(dampingRatio = .9f, stiffness = 520f), label = "アプリ選択の押下")
                    Row(Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.liquidGlass(20.dp)
                        .clickable(interactionSource = interaction, indication = null) {
                            onChange(app.packageName, mode == "add")
                        }
                        .testTag("notification-app-${app.packageName}").padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        NotificationAppIcon(app.packageName, app.label)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleSmall)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onChange(app.packageName, mode == "add") }) {
                            Text(if (mode == "add") "追加" else "削除")
                        }
                    }
                }
            }
        }
    }
}
