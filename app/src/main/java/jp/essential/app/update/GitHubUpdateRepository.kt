package jp.essential.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import jp.essential.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class AppRelease(val version: String, val notes: String, val url: String, val size: Long, val digest: String?)

internal class GitHubUpdateRepository(private val context: Context) {
    private val directory get() = File(context.noBackupFilesDir, "app-update").apply { mkdirs() }
    private val preferences get() = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    val apk get() = File(directory, "update.apk")

    /** 更新完了通知がプロセス終了で失われても、次回起動時に古いAPKを回収する。 */
    fun cleanupAfterAppStart(nowMillis: Long = System.currentTimeMillis()): Long {
        var reclaimedBytes = 0L
        directory.listFiles().orEmpty().filter { it.name != apk.name }.forEach { file ->
            reclaimedBytes += file.sizeRecursively()
            file.deleteRecursively()
        }
        val shouldDelete = apk.exists() && UpdateStoragePolicy.shouldDeleteDownloadedApk(
            targetVersion = preferences.getString(KEY_TARGET_VERSION, null),
            sourceVersionCode = preferences.getLong(KEY_SOURCE_VERSION_CODE, -1L),
            downloadedAtMillis = preferences.getLong(KEY_DOWNLOADED_AT, -1L),
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
            nowMillis = nowMillis,
        )
        if (shouldDelete) reclaimedBytes += discardDownloadedApk()
        return reclaimedBytes
    }

    /** PackageInstallerへコピー済みの元APKと管理情報を直ちに削除する。 */
    fun discardDownloadedApk(): Long {
        val bytes = apk.sizeRecursively()
        apk.delete()
        preferences.edit()
            .remove(KEY_TARGET_VERSION)
            .remove(KEY_SOURCE_VERSION_CODE)
            .remove(KEY_DOWNLOADED_AT)
            .apply()
        if (directory.list().isNullOrEmpty()) directory.delete()
        return bytes
    }

    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val repository = BuildConfig.UPDATE_REPOSITORY
        require(repository.isNotBlank()) { "配信リポジトリが未設定です" }
        val connection = connection("https://api.github.com/repos/$repository/releases/latest")
        val json = try {
            require(connection.responseCode == 200) { "更新確認に失敗しました（HTTP ${connection.responseCode}）。公開Releaseと通信状態を確認してください" }
            val bytes = connection.inputStream.use { it.readBytesLimited(2 * 1024 * 1024) }
            JSONObject(String(bytes, Charsets.UTF_8))
        } finally { connection.disconnect() }
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext null
        val version = json.getString("tag_name").removePrefix("v")
        if (!UpdatePolicy.isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
        val assets = json.getJSONArray("assets")
        val entries = (0 until assets.length()).map { assets.getJSONObject(it) }
        val selected = UpdatePolicy.chooseAsset(entries.map { it.getString("name") }, Build.SUPPORTED_ABIS.toList())
            ?: error("この端末に対応するrelease APKがありません")
        val asset = entries.single { it.getString("name") == selected }
        val url = asset.getString("browser_download_url")
        UpdatePolicy.validateAssetUrl(url, repository)
        val size = asset.getLong("size")
        require(size in 1..1_073_741_824L) { "APKのサイズが不正です" }
        val digest = asset.optString("digest").takeIf { it.isNotBlank() && it != "null" }
        require(digest == null || digest.matches(Regex("sha256:[a-fA-F0-9]{64}"))) { "ハッシュ形式が不正です" }
        AppRelease(version, json.optString("body").take(40_000), url, size, digest?.substringAfter(':'))
    }

    suspend fun download(release: AppRelease, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val part = File(directory, "update.part")
        try {
            discardDownloadedApk()
            require(directory.usableSpace > release.size + 32L * 1024 * 1024) { "保存領域が不足しています" }
            val connection = connection(release.url)
            try {
                require(connection.responseCode == 200) { "ダウンロードに失敗しました（HTTP ${connection.responseCode}）" }
                val hash = MessageDigest.getInstance("SHA-256")
                var count = 0L
                var lastPercent = -1
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            count += read
                            require(count <= release.size) { "APKのサイズが一致しません" }
                            output.write(buffer, 0, read)
                            hash.update(buffer, 0, read)
                            val percent = (count * 100 / release.size).toInt()
                            if (percent != lastPercent) { progress(count.toFloat() / release.size); lastPercent = percent }
                        }
                    }
                }
                require(count == release.size) { "ダウンロードが途中で終了しました" }
                val actual = hash.digest().joinToString("") { "%02x".format(it) }
                UpdatePolicy.verifyDigest(actual, release.digest)
                validateApk(part, release.version)
                apk.delete()
                check(part.renameTo(apk)) { "APKを保存できませんでした" }
                preferences.edit()
                    .putString(KEY_TARGET_VERSION, release.version)
                    .putLong(KEY_SOURCE_VERSION_CODE, BuildConfig.VERSION_CODE.toLong())
                    .putLong(KEY_DOWNLOADED_AT, System.currentTimeMillis())
                    .apply()
                apk
            } finally { connection.disconnect() }
        } finally { part.delete() }
    }

    @Suppress("DEPRECATION")
    fun validateApk(file: File, version: String? = null) {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val incoming = manager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("APKを解析できません")
        val installed = manager.getPackageInfo(context.packageName, flags)
        require(incoming.packageName == context.packageName) { "applicationIdが一致しません" }
        require(PackageInfoCompat.getLongVersionCode(incoming) > PackageInfoCompat.getLongVersionCode(installed)) { "versionCodeが新しくありません" }
        require(version == null || incoming.versionName == version) { "ReleaseとAPKのバージョンが一致しません" }
        require((incoming.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) { "このAndroidバージョンには対応していません" }
        fun certificates(info: android.content.pm.PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
                .orEmpty().map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
        val current = certificates(installed)
        require(current.isNotEmpty() && current == certificates(incoming)) { "署名が一致しません。現在のアプリと同じ署名鍵が必要です" }
    }

    private fun connection(initial: String): HttpURLConnection {
        var url = URL(initial)
        repeat(6) {
            require(url.protocol == "https" && url.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")) { "許可されていない配信ホストです" }
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Essential/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", if (url.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
            }
            val status = try { connection.responseCode } catch (error: Exception) { connection.disconnect(); throw error }
            if (status !in listOf(301, 302, 303, 307, 308)) return connection
            val next = connection.getHeaderField("Location")
            connection.disconnect()
            require(next != null) { "配信先への転送が不正です" }
            url = URL(url, next)
        }
        error("配信先への転送回数が多すぎます")
    }

    private fun File.sizeRecursively(): Long = when {
        !exists() -> 0L
        isFile -> length()
        else -> listFiles().orEmpty().sumOf { it.sizeRecursively() }
    }

    private companion object {
        const val KEY_TARGET_VERSION = "download_target_version"
        const val KEY_SOURCE_VERSION_CODE = "download_source_version_code"
        const val KEY_DOWNLOADED_AT = "downloaded_at"
    }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        require(output.size() + read <= limit) { "更新情報が大きすぎます" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
