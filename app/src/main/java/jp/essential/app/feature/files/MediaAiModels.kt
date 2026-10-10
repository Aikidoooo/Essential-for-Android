package jp.essential.app.feature.files

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** モデルは固定リビジョンとSHA-256で検証し、取得途中のファイルを使用しない。 */
internal data class MediaAiModel(val id: String, val url: String, val sha256: String)

enum class UpscaleMode(val label: String, val scale: Int) {
    Quality("高画質化", 2), Ultra("超高画質化", 4), Fast("高速", 4);
    internal val model: MediaAiModel get() = when (this) {
        Quality -> MediaAiModel("realesrgan-x2", "https://huggingface.co/JoPmt/Real_Esrgan_x2_Onnx_Tflite_Tfjs/resolve/6f8b8b64f28bb8a75bb6637e209584a3d39b8a6a/model.onnx", "4b650e0f8cc10057635e24ac411902a54307401fb6c9f265bf6e1eaf9ae7c949")
        Ultra -> MediaAiModel("realesrgan-x4", "https://huggingface.co/SceneWorks/real-esrgan-onnx/resolve/09f741bac80a246b407da3ee902bf5f3291b602f/real_esrgan_x4.onnx", "5c586662929cbc686c1a5c38d9c060dbdb4ea5863a1f7672b8c0761e6b89c033")
        Fast -> MediaAiModel("realesr-general-x4v3", "https://huggingface.co/Samo629/real-esrgan-onnx/resolve/4e98d089bf844f4ddeb2e76248e5c94d3938bf7c/realesr-general-x4v3-dynfix.onnx", "a526e5038461585b29231de194a0cc9883181a491360082f3df4c114f4d4c6ec")
    }
}

internal object MediaAiModels {
    private val mutex = Mutex()
    val stems = MediaAiModel("htdemucs-6s", "https://huggingface.co/StemSplitio/htdemucs-6s-onnx/resolve/49df9b6989cf2150840ea65b0bef77a2e471b678/htdemucs_6s_fp16weights.onnx", "7ce55792e2231c93fbf92de95f5fd5b3a5e6c89f7db690dfd693e8f1dce56869")

    suspend fun obtain(context: Context, model: MediaAiModel, progress: (Float?) -> Unit): File = mutex.withLock {
        withContext(Dispatchers.IO) {
            val target = File(context.noBackupFilesDir, "media-ai/${model.id}.onnx")
            target.parentFile?.mkdirs()
            fun digest(file: File): String {
                val sha = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val bytes = ByteArray(65536)
                    while (true) { val count = input.read(bytes); if (count < 0) break; sha.update(bytes, 0, count) }
                }
                return sha.digest().joinToString("") { "%02x".format(it) }
            }
            if (target.exists() && digest(target) == model.sha256) return@withContext target
            val partial = File(target.parentFile, "${model.id}.part")
            val connection = URL(model.url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            try {
                progress(null)
                check(connection.responseCode == 200) { "モデルを取得できませんでした（${connection.responseCode}）" }
                val total = connection.contentLengthLong
                var received = 0L
                var reported = -1
                connection.inputStream.use { input -> partial.outputStream().use { output ->
                    val bytes = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(bytes)
                        if (count < 0) break
                        output.write(bytes, 0, count)
                        received += count
                        val percent = if (total > 0) (received * 100 / total).toInt() else -1
                        if (percent != reported) { reported = percent; progress(if (total > 0) received.toFloat() / total else null) }
                    }
                } }
                check(digest(partial) == model.sha256) { "モデルの検証に失敗しました。再度取得してください" }
                check(partial.renameTo(target)) { "モデルを保存できませんでした" }
                progress(1f)
                target
            } finally { partial.delete(); connection.disconnect() }
        }
    }
}
