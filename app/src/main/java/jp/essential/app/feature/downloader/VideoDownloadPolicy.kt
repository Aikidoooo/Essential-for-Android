package jp.essential.app.feature.downloader

import java.net.URI

internal object VideoDownloadPolicy {
    fun isTwitter(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com")
    }

    fun format(selection: DownloaderSelection, separateFiles: Boolean): String {
        val limits = "[height<=?${selection.videoResolution.maxHeight}][fps<=?${selection.frameRate.maxFps}]"
        val video = "bv$limits"
        val audio = "ba"
        val preferred = if (selection.videoFormat == VideoFormat.Mp4) "[ext=mp4]" else ""
        // CDNが単一MP4を返せるサイトでは、音声付きファイルを優先して結合失敗を減らす。
        val separatePair = if (isDirectMp4Site(selection.url)) "b$limits$preferred/$video$preferred,$audio" else "($video$preferred,$audio)"
        val pair = if (separateFiles) separatePair else "$video$preferred+$audio"
        val fallbackPair = if (separateFiles) separatePair else "$video+$audio"
        val general = "$pair/b$limits$preferred/$fallbackPair/b$limits/$video"
        // Xの分割HLSより、映像と音声が同じ時間軸の直接配信MP4を優先する。
        return if (isTwitter(selection.url)) {
            "b$limits[ext=mp4][protocol=https]/$video[ext=mp4][protocol=https]/$general"
        } else general
    }

    private fun isDirectMp4Site(url: String): Boolean = isTikTok(url) || InstagramRequestPolicy.isInstagramUrl(url)

    private fun isTikTok(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host == "tiktok.com" || host.endsWith(".tiktok.com")
    }
}
