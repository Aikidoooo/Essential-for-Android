package jp.essential.app.feature.mannaka

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.MainActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After

class MannakaUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalDark = false
    private var hadDarkPreference = false
    private var originalHistory: String? = null
    private var originalFavorites: String? = null

    @Before fun prepareLightAppearance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        hadDarkPreference = preferences.contains("dark_theme")
        originalDark = preferences.getBoolean("dark_theme", false)
        preferences.edit().putBoolean("dark_theme", false).commit()
        val plans = context.getSharedPreferences("mannaka", Context.MODE_PRIVATE)
        originalFavorites = plans.getString("favorites", null)
        originalHistory = plans.getString("history", null)
        // 今回の比較検証で生成した完全一致の予定だけを取り除き、利用者の予定は保持する。
        val sample = "まんなか！\n集合：2026-10-01 19:00・入谷駅\nゲスト：町屋駅\n2人目：両国駅\n遊び：カラオケ、公園、ショッピングモール、ごはん\n" +
            "https://www.google.com/maps/search/?api=1&query=" + android.net.Uri.encode("入谷駅 35.72,139.784")
        originalHistory?.let { raw ->
            val entries = org.json.JSONArray(raw)
            val kept = (0 until entries.length()).map { entries.getString(it) }.filter { it != sample }
            originalHistory = org.json.JSONArray(kept).toString()
        }
    }

    @After fun restoreAppearance() {
        scenario?.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val editor = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
        if (hadDarkPreference) editor.putBoolean("dark_theme", originalDark) else editor.remove("dark_theme")
        editor.commit()
        // UIテストの検索・保存が実際の端末内履歴を変更したままにならないよう復元する。
        val plans = context.getSharedPreferences("mannaka", Context.MODE_PRIVATE).edit()
        originalHistory?.let { plans.putString("history", it) } ?: plans.remove("history")
        originalFavorites?.let { plans.putString("favorites", it) } ?: plans.remove("favorites")
        plans.commit()
    }

    private fun launchMannaka() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_FEATURE, "mannaka"))
        compose.waitUntil(10000) { compose.onAllNodesWithText("今日 19:00").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // ネイティブの押下演出も収まってから、比較用の静止画を取得する。
        automation.waitForIdle(400, 2000)
        val bitmap = automation.takeScreenshot()
            ?: error("検証用の画面を取得できませんでした。")
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun referenceTimeSheetChangesDaysAndTime() {
        launchMannaka()
        compose.onNodeWithText("中心駅を探す").assertIsNotEnabled()
        capture("mannaka-reference-main.png")
        compose.onNodeWithText("今日 19:00").performClick()
        compose.onAllNodesWithText("19:00", useUnmergedTree = true).assertCountEquals(2)
        capture("mannaka-reference-time-today.png")
        compose.onNodeWithText("明日").performClick()
        capture("mannaka-reference-time-tomorrow.png")
        compose.onNodeWithText("明後日").performClick()
        capture("mannaka-reference-time-day-after.png")
        compose.onNodeWithText("+5").performClick()
        compose.onNodeWithText("19:05").assertExists()
        compose.onNodeWithText("−30").performClick()
        compose.onNodeWithText("18:35").assertExists()
        compose.onNodeWithText("18:30").performClick()
        compose.onNodeWithText("決定").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("明後日 18:30").assertExists()
    }

    @Test fun participantsActivityAndWalkingRemainEditable() {
        launchMannaka()
        compose.onNodeWithContentDescription("1人目の名前").performTextInput("友達A")
        compose.onNodeWithText("参加者を追加").performScrollTo().performClick()
        compose.onNodeWithTag("mannaka-participant-2").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("3人目を削除").performClick()
        compose.onNodeWithTag("mannaka-participant-2").assertDoesNotExist()
        compose.onNodeWithContentDescription("1人目の駅までの時間").performScrollTo().performClick()
        compose.onNode(hasText("駅までの時間（分）") and hasSetTextAction()).performTextInput("12")
        compose.onNodeWithText("決定").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("駅まで 12分").assertExists()
        compose.onNodeWithText("集まって何する？（任意）").performScrollTo().performClick()
        listOf("カラオケ", "公園", "ごはん").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("ショッピングモール").assertDoesNotExist()
        compose.onNodeWithText("公園").performClick()
        compose.onNode(hasText("その他・自由指定") and hasSetTextAction()).performTextInput("美術館")
        compose.onNodeWithText("決定").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("公園、美術館").assertExists()
    }

    @Test fun selectedStationAndParticipantFieldsSurviveSaving() {
        val original = listOf(MeetingParticipant(0, "友達", "8", "新宿", selected = MeetingPoint("新宿", 35.69, 139.70, "node/1", "鉄道")), MeetingParticipant(1))
        assertEquals(original, decodeParticipants(encodeParticipants(original)))
    }

    @Test fun inlineSuggestionsRejectOldResultsAndOpenRankedScreen() {
        launchMannaka()
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val first = MeetingPoint("町屋駅", 35.742, 139.781, "test/1", "東京都荒川区・テスト用")
        val second = MeetingPoint("両国駅", 35.696, 139.794, "test/2", "東京都墨田区・テスト用")
        val candidates = listOf(
            MeetingPoint("鶯谷駅", 35.721, 139.778, "test/3", "東京都台東区・テスト用"),
            MeetingPoint("上野駅", 35.713, 139.777, "test/4", "東京都台東区・テスト用"),
            MeetingPoint("入谷駅", 35.720, 139.784, "test/5", "東京都台東区・テスト用"))
        val source = object : MeetingDataSource {
            override suspend fun exactStations(input: String) = if (input == "町屋") listOf(first) else emptyList()
            override suspend fun suggestStations(input: String): List<MeetingPoint> {
                requests += input
                if (input == "古い入力") { delay(1500); return listOf(first.copy(name = "古い候補")) }
                if (input == "町屋") {
                    delay(1200)
                    return listOf(first, first.copy(id = "test/7", detail = "京成電鉄・テスト用"),
                        first.copy(name = "町屋駅前駅", id = "test/6"), first.copy(name = "町屋二丁目駅", id = "test/8"))
                }
                return listOf(second)
            }
            override suspend fun nearbyStations(center: MeetingPoint) = candidates
            override suspend fun places(center: MeetingPoint, categories: Set<String>, custom: String) = emptyList<MeetingPoint>()
        }
        scenario!!.onActivity { activity -> activity.setContent { jp.essential.app.ui.theme.EssentialTheme(darkTheme = false) { MannakaTheme {
            Box(Modifier.fillMaxSize().background(meetingBackgroundColor()).safeDrawingPadding()) { MannakaContent({}, source) }
        } } } }
        compose.onNodeWithContentDescription("1人目の名前").performTextInput("ゲスト")
        val input = compose.onNodeWithContentDescription("1人目の最寄り駅")
        input.performTextInput("古い入力")
        compose.waitUntil(5000) { requests.contains("古い入力") }
        input.performTextReplacement("町屋")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("mannaka-suggestion-0-test/1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("古い候補").assertDoesNotExist()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("mannaka-suggestion-0-test/8").fetchSemanticsNodes().isNotEmpty() }
        capture("mannaka-reference-inline.png")
        compose.onNodeWithTag("mannaka-suggestion-0-test/1").performClick()
        compose.onNodeWithContentDescription("2人目の最寄り駅").performScrollTo().performTextInput("両国")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("mannaka-suggestion-1-test/2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mannaka-suggestion-1-test/2").performClick()
        compose.onNodeWithText("中心駅を探す").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("mannaka-results").assertExists()
        compose.onNodeWithText("まんなか候補").assertExists()
        compose.onNodeWithText("本命").assertExists()
        // 実際のタイルの表示を待ち、地図を含む画面を記録する。
        Thread.sleep(3000)
        var mapState = ""
        val mapRead = java.util.concurrent.CountDownLatch(1)
        scenario!!.onActivity { activity ->
            val view = activity.window.decorView.findViewWithTag<android.webkit.WebView>("mannaka-map")
            view.evaluateJavascript("JSON.stringify({library:typeof L,render:typeof render,markers:document.querySelectorAll('.candidate').length,height:innerHeight,width:innerWidth,mapHeight:document.getElementById('map').offsetHeight,mapStyle:document.getElementById('map').getAttribute('style'),computedHeight:getComputedStyle(document.getElementById('map')).height,tiles:document.querySelectorAll('.leaflet-tile-loaded').length,body:document.body.innerText})") {
                mapState = it; mapRead.countDown()
            }
        }
        org.junit.Assert.assertTrue(mapRead.await(5, java.util.concurrent.TimeUnit.SECONDS))
        File(ApplicationProvider.getApplicationContext<Context>().getExternalFilesDir(null), "mannaka-map-state.txt").writeText(mapState)
        org.junit.Assert.assertTrue("地図状態：$mapState", mapState.contains("markers\\\":3"))
        capture("mannaka-reference-results.png")
        val mapDetails = org.json.JSONObject(org.json.JSONArray("[$mapState]").getString(0))
        org.junit.Assert.assertTrue("地図の表示高さ：$mapState", mapDetails.getInt("mapHeight") > 0)
        scenario!!.onActivity { activity ->
            activity.window.decorView.findViewWithTag<android.webkit.WebView>("mannaka-map")
                .evaluateJavascript("markers[1].fire('click');", null)
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("mannaka-facilities").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mannaka-facility-activity").assertExists()
        compose.onNodeWithContentDescription("中心駅候補に戻る").performClick()
        compose.onNodeWithTag("mannaka-results").performScrollToNode(hasTestTag("mannaka-candidate-test/4"))
        compose.onNodeWithTag("mannaka-candidate-test/4").performClick()
        compose.onNodeWithTag("mannaka-facility-activity").assertExists()
        compose.onNodeWithContentDescription("中心駅候補に戻る").performClick()
        compose.onNodeWithTag("mannaka-results").performScrollToNode(hasText("選択中"))
        compose.onNodeWithText("選択中").assertExists()
        compose.onNodeWithTag("mannaka-results").performScrollToNode(hasContentDescription("駅入力に戻る"))
        compose.onNodeWithContentDescription("駅入力に戻る").performClick()
        compose.onNodeWithTag("mannaka-results").assertDoesNotExist()
        compose.onNodeWithContentDescription("1人目の最寄り駅").assertTextEquals("町屋駅")
    }
    @Test fun bundledStationsKeepResultsWhenFacilityServiceFails() {
        launchMannaka()
        val local = MeetingRepository(ApplicationProvider.getApplicationContext<Context>())
        val source = object : MeetingDataSource by local {
            override suspend fun places(center: MeetingPoint, categories: Set<String>, custom: String): List<MeetingPoint> {
                throw java.io.IOException("施設検索サービスに接続できません。")
            }
        }
        scenario!!.onActivity { activity -> activity.setContent {
            jp.essential.app.ui.theme.EssentialTheme(darkTheme = false) { MannakaTheme {
                Box(Modifier.fillMaxSize().background(meetingBackgroundColor()).safeDrawingPadding()) { MannakaContent({}, source) }
            } }
        } }
        listOf("町屋", "両国").forEachIndexed { index, name ->
            compose.onNodeWithContentDescription("${index + 1}人目の最寄り駅").performTextInput(name)
            compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("mannaka-suggestion-$index-mlit/"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(name + "駅").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText(name + "駅").onFirst().performClick()
        }
        compose.onNodeWithText("中心駅を探す").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("mannaka-results").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mannaka-recommendation").assertExists()
        compose.onNodeWithTag("mannaka-select-station").performScrollTo().performClick()
        compose.onNodeWithTag("mannaka-facility-activity").assertExists()
        capture("mannaka-facility-activity.png")
        compose.onNodeWithText("公園").performClick()
        compose.onNodeWithText("付近の施設を検索").performScrollTo().performClick()
        compose.onNodeWithTag("mannaka-facilities").performScrollToNode(hasText("施設検索サービスに接続できません。"))
        compose.onNodeWithText("施設検索サービスに接続できません。").assertExists()
        compose.onNodeWithText("施設を再検索").assertExists()
        capture("mannaka-facility-error.png")
        compose.onNodeWithContentDescription("中心駅候補に戻る").performClick()
        compose.onNodeWithTag("mannaka-results").performScrollToNode(hasContentDescription("駅入力に戻る"))
        capture("mannaka-offline-results.png")
        compose.onNodeWithContentDescription("駅入力に戻る").performClick()
        compose.onNodeWithContentDescription("1人目の最寄り駅").assertTextEquals("町屋駅")
    }

    @Test fun customActivitySkipsSelectionAndSearchesSelectedStation() = verifyPresetActivity(custom = "美術館")

    @Test fun templateActivitySkipsSelectionAndSearchesSelectedStation() = verifyPresetActivity(category = "ごはん")

    private fun verifyPresetActivity(category: String = "", custom: String = "") {
        launchMannaka()
        val local = MeetingRepository(ApplicationProvider.getApplicationContext<Context>())
        val requests = java.util.concurrent.CopyOnWriteArrayList<Triple<MeetingPoint, Set<String>, String>>()
        val source = object : MeetingDataSource by local {
            override suspend fun places(center: MeetingPoint, categories: Set<String>, custom: String): List<MeetingPoint> {
                requests.add(Triple(center, categories, custom))
                return listOf(MeetingPoint("検索した施設", center.latitude, center.longitude, "test/facility", category = "その他"))
            }
        }
        scenario!!.onActivity { activity -> activity.setContent {
            jp.essential.app.ui.theme.EssentialTheme(darkTheme = false) { MannakaTheme {
                Box(Modifier.fillMaxSize().background(meetingBackgroundColor()).safeDrawingPadding()) { MannakaContent({}, source) }
            } }
        } }
        compose.onNodeWithText("集まって何する？（任意）").performScrollTo().performClick()
        if (category.isNotBlank()) compose.onNodeWithText(category).performClick()
        if (custom.isNotBlank()) compose.onNode(hasText("その他・自由指定") and hasSetTextAction()).performTextInput(custom)
        compose.onNodeWithText("決定").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("決定").fetchSemanticsNodes().isEmpty() }
        listOf("町屋", "両国").forEachIndexed { index, name ->
            compose.onNodeWithContentDescription("${index + 1}人目の最寄り駅").performTextInput(name)
            compose.waitUntil(5000) { compose.onAllNodesWithText(name + "駅").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText(name + "駅").onFirst().performClick()
        }
        compose.onNodeWithText("中心駅を探す").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("mannaka-results").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, requests.size)
        compose.onNodeWithTag("mannaka-select-station").performScrollTo().performClick()
        compose.waitUntil(5000) { requests.size == 1 }
        compose.onNodeWithTag("mannaka-facility-activity").assertDoesNotExist()
        compose.onNodeWithText("付近の施設").assertExists()
        compose.onNodeWithTag("mannaka-facilities").performScrollToNode(hasText("検索した施設"))
        compose.onNodeWithText("検索した施設").assertExists()
        assertEquals(custom, requests.single().third)
        assertEquals(if (category.isEmpty()) emptySet<String>() else setOf(category), requests.single().second)
        org.junit.Assert.assertTrue(requests.single().first.id.startsWith("mlit/"))
        compose.onNodeWithTag("mannaka-facilities").performScrollToNode(hasContentDescription("中心駅候補に戻る"))
        capture("mannaka-facility-preset-${if (custom.isBlank()) "template" else "custom"}.png")
        compose.onNodeWithContentDescription("中心駅候補に戻る").performClick()
        compose.onNodeWithTag("mannaka-results").assertExists()
    }

}
