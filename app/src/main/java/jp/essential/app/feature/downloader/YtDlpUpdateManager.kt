package jp.essential.app.feature.downloader

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class YtDlpUpdateResult(
    val previousVersion: String,
    val currentVersion: String,
    val updated: Boolean,
)

class YtDlpUpdateManager(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun currentVersion(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            initialize()
            readVersion()
        }
    }

    suspend fun updateStable(): Result<YtDlpUpdateResult> = withContext(Dispatchers.IO) {
        runCatching {
            initialize()
            val previousVersion = readVersion()
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                applicationContext,
                YoutubeDL.UpdateChannel._STABLE,
            )
            YtDlpUpdateResult(
                previousVersion = previousVersion,
                currentVersion = readVersion(),
                updated = status == YoutubeDL.UpdateStatus.DONE,
            )
        }
    }

    private fun initialize() {
        YoutubeDL.getInstance().init(applicationContext)
    }

    private fun readVersion(): String = YoutubeDL.getInstance()
        .version(applicationContext)
        ?.trim()
        ?.ifBlank { "不明" }
        ?: "不明"
}
