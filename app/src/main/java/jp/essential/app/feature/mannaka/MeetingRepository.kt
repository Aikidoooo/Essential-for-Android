package jp.essential.app.feature.mannaka

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

interface MeetingDataSource {
    suspend fun exactStations(input: String): List<MeetingPoint> = emptyList()
    suspend fun suggestStations(input: String): List<MeetingPoint>
    suspend fun nearbyStations(center: MeetingPoint): List<MeetingPoint>
    suspend fun places(center: MeetingPoint, categories: Set<String>, custom: String): List<MeetingPoint>
}

class MeetingRepository(context: android.content.Context) : MeetingDataSource {
    private val assets = context.applicationContext.assets
    private val indexLock = kotlinx.coroutines.sync.Mutex()
    private var stationIndex: LocalStationIndex? = null

    private suspend fun index(): LocalStationIndex = indexLock.withLock {
        stationIndex ?: withContext(Dispatchers.IO) {
            assets.open("mannaka/stations.tsv").reader(Charsets.UTF_8).use(LocalStationIndex::read)
        }.also { stationIndex = it }
    }

    private val cache = linkedMapOf<String, List<MeetingPoint>>()
    private val requests = Semaphore(2)

    override suspend fun suggestStations(input: String): List<MeetingPoint> {
        val local = index()
        return withContext(Dispatchers.Default) { local.suggest(input) }
    }

    override suspend fun exactStations(input: String): List<MeetingPoint> {
        val local = index()
        return withContext(Dispatchers.Default) { local.exact(input) }
    }

    override suspend fun nearbyStations(center: MeetingPoint): List<MeetingPoint> {
        val local = index()
        return withContext(Dispatchers.Default) { local.nearby(center) }
    }

    private fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\""

    override suspend fun places(center: MeetingPoint, categories: Set<String>, custom: String): List<MeetingPoint> {
        val around = "(around:2500,${center.latitude},${center.longitude})"
        val selectors = categories.flatMap { category -> when (category) {
            "カラオケ" -> listOf("[amenity=karaoke]", "[leisure=karaoke]", "[name~\"カラオケ\"]")
            "公園" -> listOf("[leisure=park]")
            "ショッピングモール" -> listOf("[shop=mall]")
            "ごはん" -> listOf("[amenity~\"^(restaurant|cafe|fast_food|food_court)$\"]")
            else -> emptyList()
        } }.toMutableList()
        if (custom.isNotBlank()) {
            val literal = custom.trim().replace(Regex("([\\\\.\\[\\]{}()*+?^$|])"), "\\\\$1")
            selectors += "[name~${quoted(literal)},i]"
        }
        if (selectors.isEmpty()) return emptyList()
        val found = query(selectors.joinToString("", "(", ");") { "nwr$it$around;" })
            .sortedBy { MeetingGeometry.distance(center, it) }
        return (categories.flatMap { category -> found.filter { it.category == category }.take(6) } +
            if (custom.isBlank()) emptyList() else found.filter { it.name.contains(custom.trim(), ignoreCase = true) }.take(6))
            .distinctBy { it.id }
    }

    private suspend fun query(body: String, limit: Int = 1000): List<MeetingPoint> = requests.withPermit { withContext(Dispatchers.IO) {
        synchronized(cache) { cache[body] }?.let { return@withContext it }
        ensureActive()
        val connection = URI("https://overpass-api.de/api/interpreter").toURL().openConnection() as HttpURLConnection
        coroutineScope {
        // 入力変更で検索が不要になったら、待機中の通信も切断する。
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { connection.disconnect() }
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 40000
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", "Essential-Android-Mannaka/0.6.4")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            val data = "data=" + URLEncoder.encode("[out:json][timeout:25][maxsize:16777216];$body out center tags $limit;", "UTF-8")
            connection.outputStream.use { it.write(data.toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            check(responseCode == 200) {
                when (responseCode) {
                    429 -> "施設検索の利用回数が制限されています。時間をおいて再検索してください。"
                    502, 503, 504 -> "施設検索サービスが混み合っています。中心駅はそのまま利用できます。"
                    else -> "施設検索に失敗しました（HTTP $responseCode）。中心駅はそのまま利用できます。"
                }
            }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 4 * 1024 * 1024) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            check(bytes.size <= 4 * 1024 * 1024) { "検索結果が多すぎます。駅名や条件を絞ってください。" }
            ensureActive()
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            check(!json.has("remark")) { "検索を完了できませんでした。時間をおいて再検索してください。" }
            val elements = json.getJSONArray("elements")
            val points = buildList {
                for (index in 0 until elements.length()) {
                    val element = elements.getJSONObject(index)
                    val tags = element.optJSONObject("tags") ?: continue
                    val name = tags.optString("name:ja").ifBlank { tags.optString("name") }
                    val position = element.optJSONObject("center") ?: element
                    if (name.isNotBlank() && position.has("lat") && position.has("lon"))
                        add(MeetingPoint(name, position.getDouble("lat"), position.getDouble("lon"), element.getString("type") + "/" + element.getLong("id"),
                            listOf(tags.optString("operator"), tags.optString("addr:province"), tags.optString("addr:city")).filter { it.isNotBlank() }.joinToString("・"),
                            when {
                                tags.optString("amenity") == "karaoke" || tags.optString("leisure") == "karaoke" || name.contains("カラオケ") -> "カラオケ"
                                tags.optString("leisure") == "park" -> "公園"
                                tags.optString("shop") == "mall" -> "ショッピングモール"
                                tags.optString("amenity") in setOf("restaurant", "cafe", "fast_food", "food_court") -> "ごはん"
                                else -> "その他"
                            }))
                }
            }.distinctBy { it.id }
            synchronized(cache) {
                if (cache.size >= 32) cache.remove(cache.keys.first())
                cache[body] = points
            }
            points
        } finally { cancellation.cancel(); connection.disconnect() }
        }
    } }
}
