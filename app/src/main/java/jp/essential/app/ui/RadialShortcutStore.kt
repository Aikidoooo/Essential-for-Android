package jp.essential.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class RadialShortcut(val packageName: String, val label: String, val component: String? = null)

internal class RadialShortcutStore(context: Context) {
    private val preferences = context.getSharedPreferences("radial_shortcuts", Context.MODE_PRIVATE)
    fun load(): List<RadialShortcut> {
        val raw = preferences.getString("items", null) ?: return defaults
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                RadialShortcut(item.getString("package"), item.getString("label"), item.optString("component").takeIf { it.isNotBlank() })
            }.distinctBy { it.packageName }.take(MAX_SHORTCUTS)
        }.getOrElse { defaults }
    }
    fun save(items: List<RadialShortcut>) {
        val array = JSONArray()
        items.distinctBy { it.packageName }.take(MAX_SHORTCUTS).forEach {
            array.put(JSONObject().put("package", it.packageName).put("label", it.label).put("component", it.component.orEmpty()))
        }
        preferences.edit().putString("items", array.toString()).apply()
    }
    companion object {
        const val MAX_SHORTCUTS = 12
        val defaults = listOf(
            RadialShortcut("com.twitter.android", "X"), RadialShortcut("jp.naver.line.android", "LINE"),
            RadialShortcut("com.discord", "Discord"), RadialShortcut("com.google.android.youtube", "YouTube"),
            RadialShortcut("com.instagram.android", "Instagram"), RadialShortcut("com.zhiliaoapp.musically", "TikTok"),
            RadialShortcut("com.facebook.katana", "Facebook"), RadialShortcut("com.android.chrome", "Google Chrome"),
        )
    }
}
