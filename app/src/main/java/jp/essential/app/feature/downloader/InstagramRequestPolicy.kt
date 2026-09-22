package jp.essential.app.feature.downloader

import java.net.URI

/** Instagramの公開動画取得に必要な通信条件と失敗判定をまとめる。 */
internal object InstagramRequestPolicy {
    const val REFERER = "https://www.instagram.com/"
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"

    fun isInstagramUrl(rawUrl: String): Boolean {
        val host = runCatching { URI(rawUrl).host?.lowercase() }.getOrNull() ?: return false
        return host == "instagram.com" || host.endsWith(".instagram.com")
    }

    /** 投稿が公開制限ではなく、extractorの仕様変更で失敗した場合だけ更新対象にする。 */
    internal fun isRecoverableExtractorFailure(rawMessage: String): Boolean {
        val message = rawMessage.lowercase()
        if (listOf(
                "requested content is not available",
                "rate-limit reached",
                "login required",
                "private account",
                "not found",
            ).any(message::contains)
        ) return false
        return listOf(
            "unable to extract",
            "failed to parse json",
            "no video formats found",
            "could not find video",
            "unable to download webpage",
        ).any(message::contains)
    }

    /** Instagramが公開動画を返さないときに、ユーザーが確認すべき状態を説明する。 */
    internal fun userFacingFailure(rawMessage: String): String? {
        val message = rawMessage.lowercase()
        return when {
            listOf(
                "requested content is not available",
                "rate-limit reached",
                "login required",
                "private account",
            ).any(message::contains) ->
                "Instagramの動画を取得できませんでした。公開状態・ログイン要求・アクセス制限を確認してください。ログイン必須や非公開の投稿には対応していません"
            listOf(
                "unable to extract",
                "failed to parse json",
                "no video formats found",
                "could not find video",
            ).any(message::contains) ->
                "Instagramの動画情報を取得できませんでした。yt-dlpを更新してから、もう一度試してください"
            else -> null
        }
    }
}
