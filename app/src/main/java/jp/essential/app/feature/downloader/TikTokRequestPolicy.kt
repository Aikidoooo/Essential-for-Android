package jp.essential.app.feature.downloader

import android.content.Context
import java.security.SecureRandom

/** TikTokのWeb抽出を避け、yt-dlpのモバイルAPIを使うための設定を管理する。 */
internal object TikTokRequestPolicy {
    private const val PREFERENCES_NAME = "downloader_runtime"
    private const val INSTALL_ID_KEY = "tiktok_install_id"
    private const val APP_NAME = "musical_ly"
    private const val APP_VERSION = "35.1.3"
    private const val MANIFEST_APP_VERSION = "2023501030"
    private const val APP_ID = "1233"
    private const val API_HOSTNAME = "api16-normal-c-useast1a.tiktokv.com"
    private const val INSTALL_ID_MIN = 7_250_000_000_000_000_000L
    private const val INSTALL_ID_MAX = 7_325_099_999_999_994_577L
    private val installIdPattern = Regex("\\d{19}")

    /** yt-dlpがTikTokモバイルAPIを選択するextractor-argsを返す。 */
    fun extractorArgs(context: Context): String {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val installId = preferences.getString(INSTALL_ID_KEY, null)
            ?.takeIf(installIdPattern::matches)
            ?: newInstallId().toString().also {
                preferences.edit().putString(INSTALL_ID_KEY, it).apply()
            }
        return extractorArgsFor(installId)
    }

    private fun newInstallId(): Long {
        val random = SecureRandom().nextLong().ushr(1)
        return INSTALL_ID_MIN + random % (INSTALL_ID_MAX - INSTALL_ID_MIN)
    }

    internal fun extractorArgsFor(installId: String): String {
        require(installIdPattern.matches(installId)) { "TikTokのインストールIDが不正です" }
        return "tiktok:app_info=$installId/$APP_NAME/$APP_VERSION/$MANIFEST_APP_VERSION/$APP_ID;api_hostname=$API_HOSTNAME"
    }

    internal fun isShortShareUrl(rawUrl: String): Boolean {
        val uri = runCatching { java.net.URI(rawUrl) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty()
        return host in setOf("vt.tiktok.com", "vm.tiktok.com", "lite.tiktok.com") ||
            (host == "tiktok.com" || host.endsWith(".tiktok.com")) && uri.path.orEmpty().startsWith("/t/")
    }
}
