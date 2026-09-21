package jp.essential.app.profile

import android.content.Context
import android.net.Uri
import jp.essential.app.R
import java.io.File
import kotlin.math.floor
import kotlin.math.round

/** アプリ全体で共有するレベル情報。 */
data class AppLevel(
    val level: Int,
    val pointsInLevel: Int,
    val pointsForNextLevel: Int,
    val progress: Float,
)

/** アプリレベルで解放される報酬。 */
data class AppReward(
    val level: Int,
    val title: String,
    val description: String,
    val imageRes: Int,
)

val appRewards = listOf(
    AppReward(16, "Anemo Essential", "アネモカラーのアプリアイコン", R.drawable.routine_reward_lv16),
    AppReward(20, "Geo Essential", "ジオカラーのアプリアイコン", R.drawable.routine_reward_lv20),
    AppReward(25, "Electro Essential", "エレクトロカラーのアプリアイコン", R.drawable.routine_reward_lv25),
    AppReward(30, "Dendro Essential", "デンドロカラーのアプリアイコン", R.drawable.routine_reward_lv30),
    AppReward(35, "Hydro Essential", "ハイドロカラーのアプリアイコン", R.drawable.routine_reward_lv35),
    AppReward(40, "Pyro Essential", "パイロカラーのアプリアイコン", R.drawable.routine_reward_lv40),
    AppReward(50, "Lunar Essential", "月夜と青空のアプリアイコン", R.drawable.routine_reward_lv50),
    AppReward(55, "Cryo Essential", "クライオカラーのアプリアイコン", R.drawable.routine_reward_lv55),
    AppReward(60, "アイコン変更権", "ローズカラーのアプリアイコンと変更権", R.drawable.routine_reward_lv60),
)

/** Block Blastの最終スコアをアプリXPへ変換する。小数点以下は四捨五入する。 */
fun blockBlastXp(finalScore: Int): Int = floor(finalScore.coerceAtLeast(0) / 10.0 + 0.5).toInt()

/** マインスイーパーのクリア盤面をアプリXPへ変換する。 */
fun minesweeperXp(columns: Int, rows: Int): Int = when {
    columns == 8 && rows == 8 -> 1
    columns == 9 && rows == 9 -> 1
    columns == 12 && rows == 12 -> 2
    columns == 12 && rows == 24 -> 5
    else -> 0
}

/** 指定レベルから次のレベルへ進むために必要なXPを返す。最大レベルでは0を返す。 */
fun requiredAppXp(level: Int): Int {
    if (level < 1 || level >= 60) return 0
    val raw = when {
        level < 16 -> 375 + 118 * (level - 1)
        level < 40 -> 2375 + 290 * (level - 16)
        level < 50 -> 10550 + 960 * (level - 40)
        level < 55 -> 26400 + 2400 * (level - 50)
        else -> {
            val offset = level - 55
            232350 + 26490 * offset + 110 * offset * offset
        }
    }
    return round(raw / 15.0).toInt()
}

/** 合計XPから、最大60のアプリレベルと次のレベルまでの進捗を求める。 */
fun calculateAppLevel(totalXp: Int): AppLevel {
    var level = 1
    var remaining = totalXp.coerceAtLeast(0)
    while (level < 60) {
        val required = requiredAppXp(level)
        if (remaining < required) {
            return AppLevel(level, remaining, required, (remaining.toFloat() / required).coerceIn(0f, 1f))
        }
        remaining -= required
        level++
    }
    return AppLevel(60, remaining, 0, 1f)
}

/** アプリレベルXP、報酬の受取状態、プロフィールをSharedPreferencesへ保存する。 */
class AppProgressStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences("app_progress", Context.MODE_PRIVATE)

    init {
        migrateRoutineProgress()
    }

    fun loadXp(): Int = preferences.getInt(KEY_TOTAL_XP, 0).coerceAtLeast(0)

    /** 正の値だけを加算し、加算後の累計XPを返す。 */
    fun addXp(points: Int): Int {
        if (points <= 0) return loadXp()
        val next = (loadXp().toLong() + points.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        preferences.edit().putInt(KEY_TOTAL_XP, next).apply()
        return next
    }

    fun setXp(points: Int) {
        preferences.edit().putInt(KEY_TOTAL_XP, points.coerceAtLeast(0)).apply()
    }

    fun loadClaimedRewards(): Set<Int> = preferences.getStringSet(KEY_CLAIMED_REWARDS, emptySet())
        .orEmpty()
        .mapNotNull(String::toIntOrNull)
        .toSet()

    fun claimReward(level: Int): Set<Int> {
        val next = loadClaimedRewards() + level
        preferences.edit().putStringSet(KEY_CLAIMED_REWARDS, next.map(Int::toString).toSet()).apply()
        return next
    }

    private fun migrateRoutineProgress() {
        if (preferences.getBoolean(KEY_ROUTINE_MIGRATED, false)) return
        val routinePreferences = applicationContext.getSharedPreferences("routine", Context.MODE_PRIVATE)
        val legacyXp = routinePreferences.getInt("total_points", 0).coerceAtLeast(0)
        val currentXp = preferences.getInt(KEY_TOTAL_XP, 0).coerceAtLeast(0)
        val legacyRewards = routinePreferences.getStringSet("claimed_rewards", emptySet()).orEmpty()
        val existingRewards = preferences.getStringSet(KEY_CLAIMED_REWARDS, emptySet()).orEmpty()
        preferences.edit()
            .putInt(KEY_TOTAL_XP, maxOf(currentXp, legacyXp))
            .putStringSet(KEY_CLAIMED_REWARDS, existingRewards + legacyRewards)
            .putBoolean(KEY_ROUTINE_MIGRATED, true)
            .apply()
    }

    private companion object {
        const val KEY_TOTAL_XP = "total_xp"
        const val KEY_CLAIMED_REWARDS = "claimed_rewards"
        const val KEY_ROUTINE_MIGRATED = "routine_progress_migrated_v1"
    }
}

/** ホームプロフィールとどすこいで共有する名前・アバターの保存領域。 */
class ProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("profile", Context.MODE_PRIVATE)

    fun loadName(): String = preferences.getString(KEY_NAME, "").orEmpty()

    fun saveName(name: String) {
        preferences.edit().putString(KEY_NAME, name.trim().take(MAX_NAME_LENGTH)).apply()
    }

    fun loadIcon(): String = preferences.getString(KEY_ICON, DEFAULT_ICON) ?: DEFAULT_ICON

    fun saveIcon(icon: String) {
        preferences.edit().putString(KEY_ICON, icon).apply()
    }

    fun loadImagePath(): String? {
        return preferences.getString(KEY_IMAGE_PATH, null)?.takeIf { File(it).isFile }
    }

    /** 選択画像をアプリ専用領域へコピーし、元の参照元を変更せずに保持する。 */
    fun saveImage(uri: Uri): String? {
        val directory = File(appContext.filesDir, PROFILE_DIRECTORY).apply { mkdirs() }
        val destination = File(directory, "profile_icon_${System.currentTimeMillis()}.img")
        val temporary = File(directory, "${destination.name}.part")
        val input = appContext.contentResolver.openInputStream(uri) ?: return null
        return runCatching {
            input.use { source ->
                temporary.outputStream().use { output -> source.copyTo(output) }
            }
            loadImagePath()?.let { File(it).delete() }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            preferences.edit().putString(KEY_IMAGE_PATH, destination.absolutePath).apply()
            destination.absolutePath
        }.getOrElse {
            temporary.delete()
            null
        }
    }

    fun clearImage() {
        loadImagePath()?.let { File(it).delete() }
        preferences.edit().remove(KEY_IMAGE_PATH).apply()
    }

    private companion object {
        const val KEY_NAME = "profile_name"
        const val KEY_ICON = "profile_icon"
        const val KEY_IMAGE_PATH = "profile_image_path"
        const val PROFILE_DIRECTORY = "profile"
        const val MAX_NAME_LENGTH = 15
        const val DEFAULT_ICON = "🌱"
    }
}
