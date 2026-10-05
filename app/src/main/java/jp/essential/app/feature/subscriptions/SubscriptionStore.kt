package jp.essential.app.feature.subscriptions

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal data class Subscription(val id: String = UUID.randomUUID().toString(), val name: String,
    val yen: Long, val billingDay: Int, val startDate: LocalDate) {
    // 31日などが存在しない月は月末を決済日とする。
    fun paymentIn(month: YearMonth): LocalDate? = month.atDay(billingDay.coerceAtMost(month.lengthOfMonth()))
        .takeIf { !it.isBefore(startDate) }
}

internal class SubscriptionStore(context: Context) {
    private val preferences = context.getSharedPreferences("subscriptions", Context.MODE_PRIVATE)
    fun load(): List<Subscription> = runCatching {
        val array = JSONArray(preferences.getString("entries", "[]"))
        (0 until array.length()).map { index -> array.getJSONObject(index).let {
            Subscription(it.getString("id"), it.getString("name"), it.getLong("yen"),
                it.getInt("day"), LocalDate.parse(it.getString("start")))
        } }
    }.getOrDefault(emptyList())
    fun save(entries: List<Subscription>): Boolean {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)
            .put("yen", it.yen).put("day", it.billingDay).put("start", it.startDate.toString())) }
        return preferences.edit().putString("entries", array.toString()).commit()
    }
}
