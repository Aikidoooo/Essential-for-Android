package jp.essential.app.feature.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokRequestPolicyTest {
    @Test fun 通常版とLite版の短縮URLを判定する() {
        assertTrue(TikTokRequestPolicy.isShortShareUrl("https://vt.tiktok.com/ZSqKX8ssW/"))
        assertTrue(TikTokRequestPolicy.isShortShareUrl("https://lite.tiktok.com/t/ZSqcAG2vG/"))
        assertFalse(TikTokRequestPolicy.isShortShareUrl("https://www.tiktok.com/@user/video/1234567890"))
    }

    @Test fun モバイルAPI用のextractorArgsを生成する() {
        assertEquals(
            "tiktok:app_info=1234567890123456789/musical_ly/35.1.3/2023501030/1233;api_hostname=api16-normal-c-useast1a.tiktokv.com",
            TikTokRequestPolicy.extractorArgsFor("1234567890123456789"),
        )
    }

    @Test fun 不正なインストールIDを拒否する() {
        assertTrue(runCatching { TikTokRequestPolicy.extractorArgsFor("123") }.isFailure)
        assertTrue(runCatching { TikTokRequestPolicy.extractorArgsFor("12345678901234567890") }.isFailure)
    }
}
