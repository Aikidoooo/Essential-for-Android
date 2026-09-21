package jp.essential.app.storage

import android.content.Context
import android.os.Build
import android.util.Log
import jp.essential.app.BuildConfig
import java.io.File

/** アプリ更新後に、アプリが作成した一時データだけを回収する保守処理。 */
internal object StorageMaintenance {
    private const val PREFERENCES = "storage_maintenance"
    private const val KEY_VERSION_CODE = "last_cleanup_version_code"
    private const val KEY_RUN_AT = "last_cleanup_at"
    private const val MIN_INTERVAL_MILLIS = 6L * 60L * 60L * 1_000L

    /** 削除した項目数と回収した容量を呼び出し元へ返す。 */
    internal data class Result(val removedEntries: Int, val reclaimedBytes: Long)

    /** アプリ更新後、または一定時間が経過したときだけ保守を実行する。 */
    fun runIfNeeded(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Result {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val lastVersion = preferences.getInt(KEY_VERSION_CODE, 0)
        val lastRunAt = preferences.getLong(KEY_RUN_AT, 0L)
        val isDue = lastVersion != BuildConfig.VERSION_CODE ||
            lastRunAt <= 0L ||
            nowMillis < lastRunAt ||
            nowMillis - lastRunAt >= MIN_INTERVAL_MILLIS
        if (!isDue) return Result(0, 0L)

        val result = runCatching { cleanup(appContext, nowMillis) }
            .onFailure { error ->
                Log.w(TAG, "ストレージ保守を完了できませんでした", error)
            }
            .getOrElse { return Result(0, 0L) }
        preferences.edit()
            .putInt(KEY_VERSION_CODE, BuildConfig.VERSION_CODE)
            .putLong(KEY_RUN_AT, nowMillis)
            .apply()
        return result
    }

    private fun cleanup(context: Context, nowMillis: Long): Result {
        var removedEntries = 0
        var reclaimedBytes = 0L
        val cache = context.cacheDir
        cache.listFiles().orEmpty()
            .filter { StorageMaintenancePolicy.isTransientCacheName(it.name) }
            .filter { StorageMaintenancePolicy.isStale(it.lastModifiedRecursively(), nowMillis) }
            .forEach { file ->
                reclaimedBytes += file.sizeRecursively()
                if (file.deleteRecursively()) removedEntries += 1
            }

        val legacyRuntime = context.filesDir.resolve("youtubedl-android")
        if (StorageMaintenancePolicy.isLegacyYtDlpRoot(legacyRuntime)) {
            reclaimedBytes += legacyRuntime.sizeRecursively()
            if (legacyRuntime.deleteRecursively()) removedEntries += 1
        }

        // arm64／x86_64はFFmpegKitを使うため、別ライブラリの展開済みFFmpegは不要。
        if (StorageMaintenancePolicy.shouldRemoveBundledFfmpeg(Build.SUPPORTED_ABIS.firstOrNull())) {
            val packages = context.noBackupFilesDir.resolve("youtubedl-android/packages")
            val ffmpeg = packages.resolve("ffmpeg")
            if (ffmpeg.isDirectory && ffmpeg.canonicalFile.parentFile == packages.canonicalFile) {
                reclaimedBytes += ffmpeg.sizeRecursively()
                if (ffmpeg.deleteRecursively()) removedEntries += 1
            }
        }
        return Result(removedEntries, reclaimedBytes)
    }

    private fun File.sizeRecursively(): Long = when {
        !exists() -> 0L
        isFile -> length()
        else -> listFiles().orEmpty().sumOf { it.sizeRecursively() }
    }

    private fun File.lastModifiedRecursively(): Long = when {
        !exists() -> 0L
        isFile -> lastModified()
        else -> maxOf(lastModified(), listFiles().orEmpty().maxOfOrNull { it.lastModifiedRecursively() } ?: 0L)
    }

    private const val TAG = "StorageMaintenance"
}

/** 削除対象の名前と世代判定を純粋関数として定義する。 */
internal object StorageMaintenancePolicy {
    const val TRANSIENT_RETENTION_MILLIS = 24L * 60L * 60L * 1_000L

    private val transientPrefixes = listOf(
        "Essential-",
        "essential-",
        "yt-dlp",
    )

    fun isTransientCacheName(name: String): Boolean =
        name == "gecko_temp" || transientPrefixes.any(name::startsWith)

    fun isStale(lastModifiedMillis: Long, nowMillis: Long): Boolean =
        lastModifiedMillis > 0L && nowMillis >= lastModifiedMillis &&
            nowMillis - lastModifiedMillis >= TRANSIENT_RETENTION_MILLIS

    fun shouldRemoveBundledFfmpeg(abi: String?): Boolean =
        abi == "arm64-v8a" || abi == "x86_64"

    fun isLegacyYtDlpRoot(root: File): Boolean =
        root.isDirectory && root.listFiles().orEmpty().any { it.name in setOf("packages", "yt-dlp") }
}
