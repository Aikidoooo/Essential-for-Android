package jp.essential.app.design

import android.content.Context
import jp.essential.app.AppIconManager
import jp.essential.app.feature.qr.DesignVoucher
import org.json.JSONArray
import org.json.JSONObject

/** 署名検証後の利用権を端末内に保存し、同じ配布IDで期間を延ばさせない。 */
internal class AppDesignAccessStore(private val context: Context, private val modulusOverride: String? = null) {
    val preferences = context.getSharedPreferences("app_design_access", Context.MODE_PRIVATE)
    val updatePreferences = context.getSharedPreferences("app_progress", Context.MODE_PRIVATE)
    /** 更新キャンペーンは永久解放やQR利用権を変更せず、独立した期限として扱う。 */
    fun updateTrialUntil(now: Long = System.currentTimeMillis() / 1000): Long? {
        val current = effectiveNow(now)
        val start = updatePreferences.getLong("design_update_trial_start_v30", 0L)
        val end = updatePreferences.getLong("design_update_trial_end_v30", 0L)
        return end.takeIf { start > 0 && current >= start && current < end }
    }
    fun effectiveNow(now: Long = System.currentTimeMillis() / 1000) = maxOf(now, preferences.getLong("last_seen", 0L))
    private fun grants(): List<DesignVoucher.Grant> = runCatching {
        val array = JSONArray(preferences.getString("grants", "[]"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            DesignVoucher.Grant(item.getString("id"), item.getString("design"), item.getLong("issued"), item.getLong("start"), item.getLong("end"))
        }
    }.getOrDefault(emptyList())
    fun activeUntil(designId: String, now: Long = System.currentTimeMillis() / 1000): Long? {
        val current = effectiveNow(now)
        val qrEnd = grants().filter { it.designId == designId && current >= it.startsAt && current < it.endsAt }.maxOfOrNull { it.endsAt }
        val trialEnd = if (AppIconManager.options.any { it.id == designId }) updateTrialUntil(now) else null
        return listOfNotNull(qrEnd, trialEnd).maxOrNull()
    }
    fun upcomingStart(designId: String, now: Long = System.currentTimeMillis() / 1000): Long? =
        grants().filter { it.designId == designId && it.startsAt > effectiveNow(now) }.minOfOrNull { it.startsAt }
    fun nextBoundary(now: Long = System.currentTimeMillis() / 1000): Long? {
        val current = effectiveNow(now)
        return (grants().flatMap { listOf(it.startsAt, it.endsAt) } +
            updatePreferences.getLong("design_update_trial_end_v30", 0L)).filter { it > current }.minOrNull()
    }
    fun redeem(value: String, now: Long = System.currentTimeMillis() / 1000): Boolean = synchronized(lock) {
        val current = effectiveNow(now)
        val modulus = modulusOverride ?: context.assets.open("design-issuer-public.txt").bufferedReader().use { it.readText() }
        val grant = DesignVoucher.verify(value, modulus, AppIconManager.options.map { it.id }.toSet(), current)
        val ids = preferences.getStringSet("received_ids", emptySet()).orEmpty().toSet()
        if (grant.id in ids) return@synchronized false
        check(ids.size < 100_000) { "利用権の保存上限に達しました" }
        val retained = grants().filter { it.endsAt > current }
        check(retained.size < 1000) { "同時に保存できる利用権の上限に達しました" }
        check(preferences.edit().putString("grants", encode(retained + grant)).putStringSet("received_ids", ids + grant.id)
            .putLong("last_seen", current).commit()) { "利用権を保存できませんでした" }
        true
    }
    fun refresh(now: Long = System.currentTimeMillis() / 1000) = synchronized(lock) {
        val current = effectiveNow(now)
        val retained = grants().filter { it.endsAt > current }
        check(preferences.edit().putString("grants", encode(retained)).putLong("last_seen", current).commit()) { "利用権を確認できませんでした" }
    }
    private fun encode(items: List<DesignVoucher.Grant>) = JSONArray().apply {
        items.forEach { grant -> put(JSONObject().put("id", grant.id).put("design", grant.designId)
            .put("issued", grant.issuedAt).put("start", grant.startsAt).put("end", grant.endsAt)) }
    }.toString()
    companion object { private val lock = Any() }
}
