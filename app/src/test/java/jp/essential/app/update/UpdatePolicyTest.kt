package jp.essential.app.update

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun ハッシュの不一致を拒否し大文字小文字は許容する() {
        UpdatePolicy.verifyDigest("a".repeat(64), "A".repeat(64))
        UpdatePolicy.verifyDigest("a".repeat(64), null)
        assertTrue(runCatching { UpdatePolicy.verifyDigest("a".repeat(64), "b".repeat(64)) }.isFailure)
    }
    @Test fun バージョンは数値で比較する() {
        assertTrue(UpdatePolicy.isNewer("v0.10.0", "0.9.9"))
        assertFalse(UpdatePolicy.isNewer("0.5.0", "0.5.0"))
        assertFalse(UpdatePolicy.isNewer("0.4.9", "0.5.0"))
        assertTrue(UpdatePolicy.isNewer("1.0.0", "0.99.99"))
    }
    @Test fun 不正な版とプレリリースを拒否する() {
        listOf("latest", "1.2", "1.2.3-rc1", "2147483648.0.0").forEach {
            assertTrue(runCatching { UpdatePolicy.version(it) }.isFailure)
        }
    }
    @Test fun 対応ABIを優先し汎用APKへ戻す() {
        val names = listOf("Essential-1.0.0-arm64-v8a.apk", "Essential-1.0.0-universal.apk", "app-debug.apk")
        assertEquals(names[0], UpdatePolicy.chooseAsset(names, listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals(names[1], UpdatePolicy.chooseAsset(names, listOf("x86_64")))
        assertNull(UpdatePolicy.chooseAsset(listOf("app-debug.apk"), listOf("arm64-v8a")))
    }
    @Test fun 配信URLを固定リポジトリに限定する() {
        UpdatePolicy.validateAssetUrl("https://github.com/owner/repo/releases/download/v1/a.apk", "owner/repo")
        listOf("http://github.com/owner/repo/releases/download/v1/a.apk", "https://github.com/other/repo/releases/download/v1/a.apk", "https://evil.example/a.apk").forEach {
            assertTrue(runCatching { UpdatePolicy.validateAssetUrl(it, "owner/repo") }.isFailure)
        }
    }
}
