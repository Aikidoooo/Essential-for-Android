package jp.essential.app.feature.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramRequestPolicyTest {
    @Test fun Instagramの公式ホストだけを判定する() {
        assertTrue(InstagramRequestPolicy.isInstagramUrl("https://www.instagram.com/reel/ABC123/"))
        assertTrue(InstagramRequestPolicy.isInstagramUrl("https://instagram.com/p/ABC123/"))
        assertFalse(InstagramRequestPolicy.isInstagramUrl("https://instagram.com.example.org/reel/ABC123/"))
        assertFalse(InstagramRequestPolicy.isInstagramUrl("https://example.com/reel/ABC123/"))
    }

    @Test fun 公開制限エラーは更新再試行しない() {
        val message = "Requested content is not available, rate-limit reached or login required"
        assertFalse(InstagramRequestPolicy.isRecoverableExtractorFailure(message))
        assertEquals(
            "Instagramの動画を取得できませんでした。公開状態・ログイン要求・アクセス制限を確認してください。ログイン必須や非公開の投稿には対応していません",
            InstagramRequestPolicy.userFacingFailure(message),
        )
    }

    @Test fun extractor仕様変更らしいエラーは更新再試行する() {
        assertTrue(InstagramRequestPolicy.isRecoverableExtractorFailure("Unable to extract video data"))
        assertEquals(
            "Instagramの動画情報を取得できませんでした。yt-dlpを更新してから、もう一度試してください",
            InstagramRequestPolicy.userFacingFailure("Unable to extract video data"),
        )
    }
}
