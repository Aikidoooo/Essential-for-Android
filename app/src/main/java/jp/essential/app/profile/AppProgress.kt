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

/** 条件達成時のおしらせと、端末に保存された既読状態。 */
data class RewardNotice(val reward: AppReward, val read: Boolean)

/** 報酬とXP配布を共通の一覧・未読表示で扱う。 */
data class AppNotice(
    val id: String,
    val title: String,
    val detail: String,
    val read: Boolean,
    val imageRes: Int? = null,
    val xp: Int? = null,
    val createdAt: Long = 0,
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
    columns == 8 && rows == 8 -> 35
    columns == 9 && rows == 9 -> 42
    columns == 12 && rows == 12 -> 50
    columns == 12 && rows == 24 -> 100
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
    // レベルアップに必要なXPは、既存の必要量を基準に2倍にする。
    return round(raw / 15.0).toInt() * 2
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
        synchronized(updateRewardLock) {
            val editor = preferences.edit()
            if (recordRewardNotices(editor, loadXp())) editor.apply()
        }
    }

    fun loadXp(): Int = preferences.getInt(KEY_TOTAL_XP, 0).coerceAtLeast(0)

    /** 予定の完了IDとXPを同時保存し、再試行でも二重付与しない。 */
    fun awardTaskXp(occurrenceId: String, points: Int): Boolean = synchronized(updateRewardLock) {
        require(points in 1..300)
        val awarded = preferences.getStringSet("task_xp_awarded_v1", emptySet()).orEmpty()
        if (occurrenceId in awarded) return@synchronized false
        val next = (loadXp().toLong() + points).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val editor = preferences.edit().putInt(KEY_TOTAL_XP, next)
            .putStringSet("task_xp_awarded_v1", awarded + occurrenceId)
        recordRewardNotices(editor, next)
        check(editor.commit()) { "完了XPを保存できませんでした" }
        true
    }

    /** 正の値だけを加算し、加算後の累計XPを返す。 */
    fun addXp(points: Int): Int = synchronized(updateRewardLock) {
        if (points <= 0) return@synchronized loadXp()
        val next = (loadXp().toLong() + points.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        preferences.edit().putInt(KEY_TOTAL_XP, next).also { recordRewardNotices(it, next) }.apply()
        next
    }

    /** 更新後の初回起動に1200XPを付与し、同じ版やダウングレードでは再付与しない。 */
    fun awardAppUpdate(currentVersion: Int, updatedInstallation: Boolean = false, now: Long = System.currentTimeMillis() / 1000): Boolean = synchronized(updateRewardLock) {
        val previous = preferences.getInt(KEY_UPDATE_VERSION, -1)
        val reward = if (previous < 0) updatedInstallation else currentVersion > previous
        val nextVersion = maxOf(previous, currentVersion)
        val editor = preferences.edit().putInt(KEY_UPDATE_VERSION, nextVersion)
        // 次回更新のキャンペーンは更新報酬と同時保存し、再起動や後続更新で延長しない。
        if (reward && currentVersion >= 30 && previous < 30 && !preferences.contains("design_update_trial_start_v30")) {
            val start = jp.essential.app.design.AppDesignAccessStore(applicationContext).effectiveNow(now)
            editor.putLong("design_update_trial_start_v30", start)
                .putLong("design_update_trial_end_v30", start + 7L * 24 * 60 * 60)
        }
        if (reward) {
            val current = loadXp()
            val next = (current.toLong() + 1200L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            editor.putInt(KEY_TOTAL_XP, next)
            recordRewardNotices(editor, next)
            recordXpNotice(editor, "update:$currentVersion", "update", next - current)
        }
        check(editor.commit()) { "更新報酬を保存できませんでした" }
        reward
    }
    fun setXp(points: Int) = synchronized(updateRewardLock) {
        val next = points.coerceAtLeast(0)
        preferences.edit().putInt(KEY_TOTAL_XP, next).also { recordRewardNotices(it, next) }.apply()
    }

    /** 使用済みIDとXPを同一コミットで保存し、連打や同時受取で二重加算しない。 */
    fun redeemQrXp(value: String): Boolean = synchronized(updateRewardLock) {
        val modulus = applicationContext.assets.open("xp-issuer-public.txt").bufferedReader().use { it.readText() }
        val grant = jp.essential.app.feature.qr.XpVoucher.verify(value, modulus)
        val claimed = preferences.getStringSet("xp_qr_claimed_v1", emptySet()).orEmpty().toSet()
        if (grant.id in claimed) return@synchronized false
        // 使用済みIDは期限切れ後も削除せず、端末日時を戻した再使用を防ぐ。
        check(claimed.size < 100_000) { "受取履歴の上限に達しました" }
        val current = loadXp()
        val next = (current.toLong() + grant.points).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val editor = preferences.edit().putInt(KEY_TOTAL_XP, next)
            .putStringSet("xp_qr_claimed_v1", claimed + grant.id)
        recordRewardNotices(editor, next)
        recordXpNotice(editor, "qr:${grant.id}", "qr", next - current)
        check(editor.commit()) { "XP受取を保存できませんでした" }
        true
    }

    fun loadRewardNotices(): List<RewardNotice> {
        val levels = preferences.getStringSet(KEY_NOTICE_LEVELS, emptySet()).orEmpty()
        val read = preferences.getStringSet(KEY_NOTICE_READ, emptySet()).orEmpty()
        return appRewards.filter { it.level.toString() in levels }.asReversed()
            .map { RewardNotice(it, it.level.toString() in read) }
    }

    fun loadNotices(): List<AppNotice> {
        val rewards = loadRewardNotices().map {
            AppNotice("reward:${it.reward.level}", "${it.reward.title}を獲得しました",
                "LV ${it.reward.level} 達成\n${it.reward.description}", it.read, imageRes = it.reward.imageRes)
        }
        val read = preferences.getStringSet(KEY_XP_NOTICE_READ, emptySet()).orEmpty()
        val entries = org.json.JSONArray(preferences.getString(KEY_XP_NOTICES, "[]"))
        val xpNotices = (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            val qr = entry.getString("kind") == "qr"
            val id = entry.getString("id")
            AppNotice(id, if (qr) "QRコードのXPを獲得しました" else "アップデートのXPを獲得しました",
                if (qr) "QRコードからのXP受取" else "アップデートによるXP配布", id in read,
                xp = entry.getInt("points"), createdAt = entry.getLong("time"))
        }
        return (xpNotices.asReversed() + rewards).sortedByDescending { it.createdAt }
    }

    /** 一覧に表示した通知のIDだけを既読へ保存する。 */
    fun markNoticesRead(ids: Set<String>) = synchronized(updateRewardLock) {
        val existing = loadNotices().map { it.id }.toSet()
        val selected = ids.intersect(existing)
        val rewardLevels = selected.mapNotNull { it.removePrefix("reward:").toIntOrNull() }.toSet()
        markRewardNoticesRead(rewardLevels)
        val read = preferences.getStringSet(KEY_XP_NOTICE_READ, emptySet()).orEmpty()
        val next = read + selected.filter { it.startsWith("qr:") || it.startsWith("update:") }
        if (next != read) preferences.edit().putStringSet(KEY_XP_NOTICE_READ, next).apply()
    }

    /** XP付与・受取済みIDと同じコミットに通知を含め、再起動や連打で重複しない。 */
    private fun recordXpNotice(editor: android.content.SharedPreferences.Editor, id: String, kind: String, points: Int) {
        val entries = org.json.JSONArray(preferences.getString(KEY_XP_NOTICES, "[]"))
        if ((0 until entries.length()).any { entries.getJSONObject(it).getString("id") == id }) return
        entries.put(org.json.JSONObject().put("id", id).put("kind", kind).put("points", points)
            .put("time", System.currentTimeMillis()))
        editor.putString(KEY_XP_NOTICES, entries.toString())
    }

    /** 一覧を開いた時点の通知だけを既読にする。次に届いた通知は未読のまま残す。 */
    fun markRewardNoticesRead(levels: Set<Int>) = synchronized(updateRewardLock) {
        val existing = preferences.getStringSet(KEY_NOTICE_LEVELS, emptySet()).orEmpty()
        val read = preferences.getStringSet(KEY_NOTICE_READ, emptySet()).orEmpty()
        val next = read + levels.map(Int::toString).filter { it in existing }
        if (next != read) preferences.edit().putStringSet(KEY_NOTICE_READ, next).apply()
    }

    /** 登録した画面が破棄されたら、返された解除処理を呼び出す。 */
    fun observeChanges(onChange: () -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** XPと同じ編集で報酬を自動獲得し、レベルごとに一度だけ通知する。 */
    private fun recordRewardNotices(editor: android.content.SharedPreferences.Editor, xp: Int): Boolean {
        val claimed = loadClaimedRewards().map(Int::toString).toSet()
        // 導入前に受け取った報酬は通知済みとして扱い、過去の獲得を再通知しない。
        val seen = preferences.getStringSet(KEY_NOTICE_SEEN, null)?.toSet() ?: claimed
        val level = calculateAppLevel(xp).level
        val unlocked = appRewards.filter { it.level <= level }.map { it.level.toString() }.toSet()
        val added = unlocked - seen
        val initialize = !preferences.contains(KEY_NOTICE_SEEN)
        if (added.isNotEmpty() || initialize) {
            editor.putStringSet(KEY_NOTICE_SEEN, seen + unlocked)
            editor.putStringSet(KEY_NOTICE_LEVELS,
                preferences.getStringSet(KEY_NOTICE_LEVELS, emptySet()).orEmpty() + added)
            editor.putStringSet(KEY_CLAIMED_REWARDS, claimed + unlocked)
        }
        return added.isNotEmpty() || initialize
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
        const val KEY_XP_NOTICES = "xp_notices_v1"
        const val KEY_XP_NOTICE_READ = "xp_notice_read_v1"
        const val KEY_NOTICE_SEEN = "reward_notice_seen_v1"
        const val KEY_NOTICE_LEVELS = "reward_notice_levels_v1"
        const val KEY_NOTICE_READ = "reward_notice_read_v1"
        val updateRewardLock = Any()
        const val KEY_UPDATE_VERSION = "update_reward_version"
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
                temporary.outputStream().use { output -> copyProfileMedia(source, output) }
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            loadImagePath()?.let { File(it).delete() }
            preferences.edit().putString(KEY_IMAGE_PATH, destination.absolutePath).apply()
            destination.absolutePath
        }.getOrElse {
            temporary.delete()
            null
        }
    }

    /** 編集を確定した画像だけを保存し、成功後に以前の画像を置き換える。 */
    fun saveCroppedImage(bitmap: android.graphics.Bitmap, banner: Boolean, homeBackground: Boolean = false): String? {
        val directory = File(appContext.filesDir, PROFILE_DIRECTORY).apply { mkdirs() }
        val destination = File(directory, "profile_${java.util.UUID.randomUUID()}.png")
        val temporary = File(directory, "${destination.name}.part")
        return runCatching {
            temporary.outputStream().use { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
            check(temporary.renameTo(destination))
            val previous = if (homeBackground) loadHomeBackgroundPath() else if (banner) loadBannerPath() else loadImagePath()
            val key = if (homeBackground) KEY_HOME_BACKGROUND else if (banner) KEY_BANNER_PATH else KEY_IMAGE_PATH
            check(preferences.edit().putString(key, destination.absolutePath).commit())
            previous?.let { File(it).delete() }
            destination.absolutePath
        }.getOrElse {
            temporary.delete()
            destination.delete()
            null
        }
    }

    fun loadHomeBackgroundPath(): String? = preferences.getString(KEY_HOME_BACKGROUND, null)?.takeIf { File(it).isFile }

    fun loadHomeBackgroundEnabled(): Boolean = preferences.getBoolean("home_background_enabled", true)

    fun saveHomeBackgroundEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("home_background_enabled", enabled).apply()
    }

    fun clearHomeBackground() {
        val previous = loadHomeBackgroundPath()
        if (preferences.edit().remove(KEY_HOME_BACKGROUND).commit()) previous?.let { File(it).delete() }
    }

    fun clearImage() {
        loadImagePath()?.let { File(it).delete() }
        preferences.edit().remove(KEY_IMAGE_PATH).apply()
    }

    fun loadBannerPath(): String? = preferences.getString(KEY_BANNER_PATH, null)?.takeIf { File(it).isFile }

    /** 静止画またはGIFの背景をアプリ専用領域へ保存する。 */
    fun saveBanner(uri: Uri): String? {
        val directory = File(appContext.filesDir, PROFILE_DIRECTORY).apply { mkdirs() }
        val destination = File(directory, "profile_banner_${System.currentTimeMillis()}.img")
        val temporary = File(directory, "${destination.name}.part")
        val input = appContext.contentResolver.openInputStream(uri) ?: return null
        return runCatching {
            input.use { source -> temporary.outputStream().use { output -> copyProfileMedia(source, output) } }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            loadBannerPath()?.let { File(it).delete() }
            preferences.edit().putString(KEY_BANNER_PATH, destination.absolutePath).apply()
            destination.absolutePath
        }.getOrElse {
            temporary.delete()
            null
        }
    }

    fun clearBanner() {
        loadBannerPath()?.let { File(it).delete() }
        preferences.edit().remove(KEY_BANNER_PATH).apply()
    }

    /** 巨大な画像やGIFでストレージを使い切らないよう、両方の保存経路に上限を設ける。 */
    private fun copyProfileMedia(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= 64L * 1024 * 1024) { "プロフィール画像は64MB以下を選択してください" }
            output.write(buffer, 0, count)
        }
    }

    private companion object {
        const val KEY_NAME = "profile_name"
        const val KEY_ICON = "profile_icon"
        const val KEY_IMAGE_PATH = "profile_image_path"
        const val KEY_HOME_BACKGROUND = "home_background_path"
        const val KEY_BANNER_PATH = "profile_banner_path"
        const val PROFILE_DIRECTORY = "profile"
        const val MAX_NAME_LENGTH = 15
        const val DEFAULT_ICON = "🌱"
    }
}
