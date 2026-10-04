package jp.essential.app.feature.mannaka

import jp.essential.app.ui.fixedHeader
import jp.essential.app.ui.FeatureHeader
import jp.essential.app.ui.GlassFeatureTitle

import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import jp.essential.app.ui.GlassBackButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import jp.essential.app.R
import jp.essential.app.ui.ProgressiveWidget
import jp.essential.app.ui.progressiveItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate
import java.time.ZoneId

private enum class MeetingSheet { Time, Activity, Walking, Favorites, History, Information }

@Composable
fun MannakaScreen(onBack: () -> Unit) {
    MannakaTheme { MannakaContent(onBack) }
}

@Composable
private fun rememberMeetingRepository(): MeetingRepository {
    val context = LocalContext.current
    return remember(context.applicationContext) { MeetingRepository(context) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MannakaContent(onBack: () -> Unit, repository: MeetingDataSource = rememberMeetingRepository()) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("mannaka", Context.MODE_PRIVATE) }
    val participantSaver = remember { Saver<List<MeetingParticipant>, String>(save = { encodeParticipants(it) }, restore = ::decodeParticipants) }
    var participants by rememberSaveable(stateSaver = participantSaver) { mutableStateOf(listOf(MeetingParticipant(0), MeetingParticipant(1))) }
    var categories by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var custom by rememberSaveable { mutableStateOf("") }
    var meetingDay by rememberSaveable { mutableIntStateOf(0) }
    var meetingMinutes by rememberSaveable { mutableIntStateOf(19 * 60) }
    var sheet by remember { mutableStateOf<MeetingSheet?>(null) }
    var editingId by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showResults by remember { mutableStateOf(false) }
    var showFacilities by remember { mutableStateOf(false) }
    var choosingActivity by remember { mutableStateOf(false) }
    var facilityJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var facilityRequest by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var meetingStations by remember { mutableStateOf(emptyList<MeetingPoint>()) }
    var meeting by remember { mutableStateOf<MeetingPoint?>(null) }
    var places by remember { mutableStateOf(emptyList<MeetingPoint>()) }
    var placeError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun readPlans(key: String): List<String> = runCatching {
        val array = JSONArray(preferences.getString(key, "[]"))
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())
    var favorites by remember { mutableStateOf(readPlans("favorites")) }
    var history by remember { mutableStateOf(readPlans("history")) }
    fun savePlans(key: String, entries: List<String>) { preferences.edit { putString(key, JSONArray(entries).toString()) } }
    fun invalidate() { showFacilities = false; choosingActivity = false; meeting = null; meetingStations = emptyList(); places = emptyList(); placeError = null; status = null; notice = null }
    fun changeParticipant(id: Int, update: (MeetingParticipant) -> MeetingParticipant) {
        participants = participants.map { if (it.id == id) update(it) else it }
    }
    fun addParticipant() {
        focusManager.clearFocus()
        participants = participants + MeetingParticipant((participants.maxOfOrNull { it.id } ?: -1) + 1)
        invalidate()
    }
    fun closeSheet() {
        focusManager.clearFocus()
        scope.launch { sheetState.hide(); sheet = null }
    }
    fun openSheet(target: MeetingSheet) {
        focusManager.clearFocus()
        sheet = target
    }
    fun map(point: MeetingPoint, search: String = point.name) {
        val uri = ("https://www.google.com/maps/search/?api=1&query=" + Uri.encode("$search ${point.latitude},${point.longitude}")).toUri()
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { status = "地図を開けませんでした。" }
    }
    fun effectiveCategories() = if (categories.isEmpty() && custom.isBlank()) meetingCategories else categories
    fun planSummary(): String {
        val selected = meeting ?: return ""
        val date = LocalDate.now(ZoneId.of("Asia/Tokyo")).plusDays(meetingDay.toLong())
        return buildString {
            appendLine("まんなか！")
            appendLine("集合：$date ${meetingTimeLabel(meetingMinutes)}・${selected.name}")
            participants.forEachIndexed { index, participant ->
                val person = participant.name.ifBlank { "${index + 1}人目" }
                appendLine("$person：${participant.selected?.name ?: participant.input}" +
                    if (participant.walkMinutes.isBlank()) "" else "（駅まで${participant.walkMinutes}分）")
            }
            appendLine("遊び：${(effectiveCategories() + listOf(custom).filter { it.isNotBlank() }).joinToString("、")}")
            if (places.isNotEmpty()) appendLine("候補：${places.take(6).joinToString("、") { it.name }}")
            append("https://www.google.com/maps/search/?api=1&query=${Uri.encode("${selected.name} ${selected.latitude},${selected.longitude}")}")
        }
    }
    fun copyPlan(value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("まんなかの予定", value))
        notice = "予定をコピーしました"
    }
    fun stopFacilitySearch() {
        facilityRequest += 1
        facilityJob?.cancel()
        facilityJob = null
        busy = false
    }
    fun searchFacilities() {
        val point = meeting ?: return
        stopFacilitySearch()
        val request = facilityRequest
        val selectedCategories = effectiveCategories().toSet()
        val selectedCustom = custom
        places = emptyList(); placeError = null; busy = true
        facilityJob = scope.launch {
            try {
                val found = repository.places(point, selectedCategories, selectedCustom)
                if (request == facilityRequest) places = found
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (request == facilityRequest) placeError = error.message ?: "周辺施設を取得できませんでした。"
            } finally {
                if (request == facilityRequest) {
                    busy = false
                    history = (listOf(planSummary()) + history).distinct().take(10)
                    savePlans("history", history)
                }
            }
        }
    }
    fun chooseStation(point: MeetingPoint) {
        if (busy) return
        meeting = point
        places = emptyList(); placeError = null; notice = null
        showFacilities = true
        choosingActivity = categories.isEmpty() && custom.isBlank()
        if (!choosingActivity) searchFacilities()
    }
    fun returnToCandidates() {
        stopFacilitySearch()
        showFacilities = false
        choosingActivity = false
    }
    BackHandler(enabled = showResults) {
        if (showFacilities) returnToCandidates() else showResults = false
    }
    fun search() {
        scope.launch {
            busy = true; invalidate()
            try {
                require(participants.all { it.selected != null }) { "全員の最寄り駅を選んでください。" }
                status = "みんなの中心駅を探しています…"
                val origins = participants.mapNotNull { it.selected }
                val midpoint = MeetingGeometry.center(origins)
                val nearby = repository.nearbyStations(midpoint)
                val stations = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { MeetingGeometry.rank(nearby, origins) }
                meetingStations = stations
                meeting = stations.firstOrNull() ?: midpoint
                focusManager.clearFocus()
                showResults = true
                status = null
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { status = error.message ?: "検索できませんでした。通信を確認してください。" }
            finally { busy = false }
        }
    }
    val firstMissing = participants.indexOfFirst { it.selected == null }
    val dayLabel = listOf("今日", "明日", "明後日")[meetingDay]
    val activityLabel = (categories + listOf(custom).filter { it.isNotBlank() }).joinToString("、")

    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val searchAction: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (firstMissing >= 0 && !busy) {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("${firstMissing + 1}人目の最寄り駅を選んでください", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp))
                    }
                }
                Button(onClick = ::search, enabled = !busy && firstMissing < 0, modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Icon(painterResource(R.drawable.ic_mannaka_spark), null, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "中心駅を探しています…" else "中心駅を探す", fontSize = 18.sp)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                status?.let { Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp) }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp) }
            }
    }

    val sharePlan: () -> Unit = {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, planSummary())
        runCatching { context.startActivity(Intent.createChooser(intent, "まんなかの予定を共有")) }
            .onFailure { notice = "共有画面を開けませんでした。" }
    }
    val favoritePlan: () -> Unit = {
        favorites = (listOf(planSummary()) + favorites).distinct().take(20)
        savePlans("favorites", favorites); notice = "お気に入りに保存しました"
    }
    val motionStage = when {
        !showResults -> "input"
        !showFacilities -> "candidates"
        choosingActivity -> "activity"
        else -> "facilities"
    }
    MeetingMotionScope(motionStage) {
    if (showResults && meeting != null) {
        if (showFacilities) {
            MeetingFacilities(meeting!!, categories, custom, places, busy, placeError, notice, choosingActivity,
                onBack = ::returnToCandidates,
                onActivity = { stopFacilitySearch(); choosingActivity = true },
                onSearch = { selected, free ->
                    categories = selected; custom = free; choosingActivity = false
                    focusManager.clearFocus(); searchFacilities()
                },
                onRetry = ::searchFacilities, onMap = { point, query -> map(point, query) },
                onShare = sharePlan, onCopy = { copyPlan(planSummary()) }, onFavorite = favoritePlan)
        } else {
            MeetingResults(participants, meetingStations, meeting!!,
                "$dayLabel ${meetingTimeLabel(meetingMinutes)} 集合", busy, notice,
                onBack = { showResults = false }, onSelect = ::chooseStation,
                onMap = { point, query -> map(point, query) },
                onShare = sharePlan, onCopy = { copyPlan(planSummary()) }, onFavorite = favoritePlan)
        }
    } else Box(Modifier.fillMaxSize().imePadding()) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = if (keyboardOpen) 150.dp else 56.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        fixedHeader {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassBackButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "ホームに戻る" })
                Box(Modifier.weight(1f)) { GlassFeatureTitle("まんなか") }
                val actionShape = RoundedCornerShape(28.dp)
                Row(Modifier.background(Color.White.copy(alpha = 0.17f), actionShape)
                    .border(1.dp, Color.White.copy(alpha = 0.48f), actionShape)
                    .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { openSheet(MeetingSheet.Favorites) }, modifier = Modifier.size(46.dp)) {
                    Icon(painterResource(R.drawable.ic_mannaka_favorite_border), "お気に入り", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { openSheet(MeetingSheet.History) }, modifier = Modifier.size(46.dp)) {
                    Icon(painterResource(R.drawable.ic_mannaka_history), "履歴", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                }
            }
        }
        progressiveItem(1, keyPrefix = "mannaka-input") {
            MeetingSummaryCard(R.drawable.ic_mannaka_clock, "集合時間", "$dayLabel ${meetingTimeLabel(meetingMinutes)}", enabled = !busy) { openSheet(MeetingSheet.Time) }
        }
        progressiveItem(2, keyPrefix = "mannaka-input") {
            MeetingSummaryCard(R.drawable.ic_mannaka_restaurant, "集まって何する？（任意）", activityLabel.ifBlank { "焼肉、カフェ、カラオケ など" },
                placeholder = activityLabel.isBlank(), enabled = !busy) { openSheet(MeetingSheet.Activity) }
        }
        progressiveItem(3, keyPrefix = "mannaka-input") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("参加者", fontSize = 15.sp)
                FilledTonalButton(onClick = ::addParticipant, enabled = !busy, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                    Icon(painterResource(R.drawable.ic_mannaka_add), null, modifier = Modifier.size(18.dp))
                    Text("追加", fontSize = 14.sp)
                }
            }
        }
        itemsIndexed(participants, key = { _, participant -> participant.id }) { index, participant ->
            ProgressiveWidget(index + 4) {
            MeetingParticipantCard(participant, index, participants.size > 2, !busy,
                onNameChange = { name -> changeParticipant(participant.id) { it.copy(name = name.take(30)) }; notice = null },
                repository = repository,
                onInput = { input -> changeParticipant(participant.id) { it.copy(input = input.take(80), selected = null) }; invalidate() },
                onPick = { station -> changeParticipant(participant.id) { it.copy(input = station.name, selected = station) }; invalidate() },
                onWalking = { editingId = participant.id; openSheet(MeetingSheet.Walking) },
                onRemove = { participants = participants.filter { it.id != participant.id }; invalidate() })

            }
        }
        progressiveItem(participants.size + 4, keyPrefix = "mannaka-input-add") { MeetingAddButton(!busy, ::addParticipant) }
        if (!keyboardOpen) progressiveItem(participants.size + 5, keyPrefix = "mannaka-input-search") { searchAction() }
        progressiveItem(participants.size + 6, keyPrefix = "mannaka-input-footer") {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("位置と直線距離から提案します。集合時間・駅までの時間は予定のメモです。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Text("駅検索・中心駅の計算は通信不要です。周辺検索時のみ地点と条件を送信します。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                TextButton(onClick = { openSheet(MeetingSheet.Information) }) { Text("まんなかについて", fontSize = 11.sp) }
                Text("地図データ © OpenStreetMap contributors / ODbL", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
    }

        if (keyboardOpen) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { searchAction() }
            }
        }
    }

    }

    sheet?.let { activeSheet ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
            dragHandle = {
                Surface(Modifier.padding(top = 7.dp, bottom = 10.dp).size(width = 56.dp, height = 4.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) {}
            },
            scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.22f)) {
            MeetingMotionScope("sheet-${activeSheet.name}") {
                ProgressiveWidget(0) {
            when (activeSheet) {
                MeetingSheet.Time -> MeetingTimePanel(meetingDay, meetingMinutes) { day, minutes ->
                    meetingDay = day; meetingMinutes = minutes; notice = null; closeSheet()
                }
                MeetingSheet.Activity -> MeetingActivityPanel(categories, custom) { selected, free ->
                    categories = selected; custom = free; invalidate(); closeSheet()
                }
                MeetingSheet.Walking -> participants.firstOrNull { it.id == editingId }?.let { participant ->
                    MeetingWalkingPanel(participant.walkMinutes) { minutes -> changeParticipant(editingId) { it.copy(walkMinutes = minutes) }; notice = null; closeSheet() }
                }
                MeetingSheet.Favorites, MeetingSheet.History -> {
                    val entries = if (activeSheet == MeetingSheet.Favorites) favorites else history
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (activeSheet == MeetingSheet.Favorites) "お気に入り" else "最近の予定", style = MaterialTheme.typography.titleLarge)
                        if (entries.isEmpty()) Text(if (activeSheet == MeetingSheet.Favorites) "検索結果から予定を保存できます。" else "中心駅を検索すると、最近の予定がここに残ります。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(entries, key = { index, _ -> index }) { _, entry ->
                                MeetingGlassCard {
                                    Text(entry.substringBefore("https://").trim(), fontSize = 13.sp)
                                    TextButton(onClick = { copyPlan(entry); closeSheet() }) { Text("予定をコピー") }
                                }
                            }
                        }
                    }
                }
                MeetingSheet.Information -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("まんなかについて", style = MaterialTheme.typography.titleLarge)
                    Text("友達の最寄り駅を選んで、集合しやすい駅と周辺の遊びを探せます。参加者の人数に固定の上限はありません。")
                    Text("位置と直線距離を基準にした候補です。電車の所要時間・運賃・乗換は地図で確認してください。集合時間と駅までの時間は予定に残すためのメモです。")
                    Text("検索は必要なときだけ行い、同じ条件の結果を画面内で再利用します。駅名検索と中心駅計算は端末内で行います。施設検索時のみ集合地点と条件を地図サービスへ送信します。名前・最寄り駅・駅までの時間は送信しません。")
                    Text("お気に入りと最近の予定は、この端末内に保存します。施設の登録状況や営業状況によって候補が見つからない場合は、地図検索も利用できます。")
                    Text("駅データ：国土交通省 国土数値情報（鉄道データ）2025年度版を加工。2025年12月31日時点の駅名・路線・代表座標です。新駅や名称変更が未反映の場合があります。CC BY 4.0。")
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-N02-2025.html".toUri())) }) { Text("駅データの出典") }
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.openstreetmap.org/copyright".toUri())) }
                            .onFailure { notice = "出典ページを開けませんでした。"; closeSheet() }
                    }) { Text("OpenStreetMapの出典・ライセンス") }
                    Button(onClick = ::closeSheet, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("閉じる") }
                }
            }
                }
            }
        }
    }
}
