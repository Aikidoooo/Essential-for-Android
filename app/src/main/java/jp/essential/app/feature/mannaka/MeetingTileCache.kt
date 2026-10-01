package jp.essential.app.feature.mannaka

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/** 表示中の地図タイルだけを取得し、専用キャッシュを12MB以内に保つ。 */
internal class MeetingTileCache(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "mannaka-tiles")

    fun load(url: String): WebResourceResponse? = runCatching {
            val uri = URI(url)
            require(uri.scheme == "https" && uri.host == "tile.openstreetmap.org" && uri.path.endsWith(".png"))
            val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(directory, key)
            val now = System.currentTimeMillis()
            val cached = synchronized(lock) {
                directory.mkdirs()
                if (file.isFile && file.length() <= MAX_TILE_BYTES + 8) {
                    runCatching {
                        file.inputStream().use { java.io.DataInputStream(it).run { readLong() to readBytes() } }
                    }.getOrNull()?.takeIf { it.first > now }?.second?.also { file.setLastModified(now) }
                } else null
            }
            if (cached != null) return@runCatching response(cached)
            // 通信中はディスク用ロックを保持せず、複数タイルの描画を待たせない。
            val connection = uri.toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", "Essential-Android-Mannaka/0.6.4")
                check(connection.responseCode == 200)
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= MAX_TILE_BYTES) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                check(bytes.size <= MAX_TILE_BYTES)
                val control = connection.getHeaderField("Cache-Control").orEmpty()
                if (!control.contains("no-store", true)) {
                    val maxAge = Regex("max-age=(\\d+)").find(control)?.groupValues?.get(1)?.toLongOrNull()
                    val age = connection.getHeaderField("Age")?.toLongOrNull() ?: 0L
                    val expires = if (control.contains("no-cache", true)) now else maxAge?.let {
                        now + (it - age).coerceIn(0L, 365L * 24 * 3600) * 1000
                    } ?: connection.getHeaderFieldDate("Expires", now + 7L * 24 * 3600 * 1000)
                    // 削除するのは、この機能が作成したタイルだけに限定する。
                    synchronized(lock) {
                        trim(bytes.size.toLong() + 8)
                        file.outputStream().use { java.io.DataOutputStream(it).run { writeLong(expires); write(bytes) } }
                    }
                }
                response(bytes)
            } finally { connection.disconnect() }
        }.getOrNull()

    private fun trim(incoming: Long) {
        val entries = directory.listFiles().orEmpty().filter { it.isFile }
        var total = entries.sumOf { it.length() }
        entries.sortedBy { it.lastModified() }.forEach { file ->
            if (total + incoming > MAX_CACHE_BYTES) {
                val length = file.length()
                if (file.delete()) total -= length
            }
        }
        check(total + incoming <= MAX_CACHE_BYTES)
    }

    private fun response(bytes: ByteArray) = WebResourceResponse("image/png", null, ByteArrayInputStream(bytes))

    companion object {
        private val lock = Any()
        private const val MAX_CACHE_BYTES = 12_000_000L
        private const val MAX_TILE_BYTES = 512_000
    }
}
