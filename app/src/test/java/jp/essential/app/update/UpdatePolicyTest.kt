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
    @Test fun Android端末はAndroid更新用を選ぶ() {
        val names = fourDistributionModels()
        assertEquals(
            "Essential-1.0.0-UPDATE-ANDROID-universal.apk",
            UpdatePolicy.chooseAsset(names, listOf("arm64-v8a"), manufacturer = "Google", brand = "google"),
        )
    }
    @Test fun Xiaomi系arm64端末はXiaomi更新用を選ぶ() {
        val names = fourDistributionModels()
        listOf("Xiaomi", "Redmi", "POCO", "Xiaomi Communications Co., Ltd.").forEach { deviceBrand ->
            assertEquals(
                "Essential-1.0.0-UPDATE-XIAOMI-arm64-v8a.apk",
                UpdatePolicy.chooseAsset(names, listOf("arm64-v8a", "armeabi-v7a"), manufacturer = deviceBrand),
            )
        }
    }
    @Test fun Xiaomi系でもarm64非対応ならAndroid更新用へ戻す() {
        assertEquals(
            "Essential-1.0.0-UPDATE-ANDROID-universal.apk",
            UpdatePolicy.chooseAsset(fourDistributionModels(), listOf("armeabi-v7a"), brand = "POCO"),
        )
    }
    @Test fun 初回用をアプリ内アップデートには選ばない() {
        val names = fourDistributionModels()
        assertFalse(UpdatePolicy.chooseAsset(names, listOf("arm64-v8a"), manufacturer = "Xiaomi")!!.contains("FIRST-INSTALL"))
    }
    @Test fun 四モデル名は旧版のABI選択とも互換性を保つ() {
        val names = fourDistributionModels()
        fun legacyChoose(abis: List<String>): String? {
            val apks = names.filter { it.endsWith(".apk") }
            for (abi in abis) apks.singleOrNull { it.endsWith("-$abi.apk") }?.let { return it }
            return apks.singleOrNull { it.endsWith("-universal.apk") }
        }
        assertEquals("Essential-1.0.0-UPDATE-XIAOMI-arm64-v8a.apk", legacyChoose(listOf("arm64-v8a")))
        assertEquals("Essential-1.0.0-UPDATE-ANDROID-universal.apk", legacyChoose(listOf("x86_64")))
    }
    @Test fun 旧配布形式では対応ABIを優先し汎用APKへ戻す() {
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

    private fun fourDistributionModels() = listOf(
        "Essential-1.0.0-FIRST-INSTALL-ANDROID.apk",
        "Essential-1.0.0-FIRST-INSTALL-XIAOMI.apk",
        "Essential-1.0.0-UPDATE-ANDROID-universal.apk",
        "Essential-1.0.0-UPDATE-XIAOMI-arm64-v8a.apk",
    )
}
