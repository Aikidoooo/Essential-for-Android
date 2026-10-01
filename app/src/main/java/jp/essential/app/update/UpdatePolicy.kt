package jp.essential.app.update

import java.net.URI

/** 配信は安定版のv数字.数字.数字に限定し、文字列の辞書順で比較しない。 */
internal object UpdatePolicy {
    fun version(value: String): List<Int> {
        require(value.matches(Regex("v?\\d+\\.\\d+\\.\\d+"))) { "安定版のバージョン形式ではありません" }
        return value.removePrefix("v").split('.').map { it.toInt() }
    }
    fun isNewer(remote: String, current: String): Boolean {
        val a = version(remote)
        val b = version(current)
        return a.indices.firstOrNull { a[it] != b[it] }?.let { a[it] > b[it] } ?: false
    }
    fun chooseAsset(
        names: List<String>,
        abis: List<String>,
        manufacturer: String = "",
        brand: String = "",
    ): String? {
        val apks = names.filter { it.endsWith(".apk") && !it.contains("debug", true) }
        val updateApks = apks.filter { it.contains("-UPDATE-", ignoreCase = true) }
        if (updateApks.isNotEmpty()) {
            val xiaomiOptimized = isXiaomiFamily(manufacturer, brand) && "arm64-v8a" in abis
            if (xiaomiOptimized) {
                updateApks.singleOrNull { it.endsWith("-UPDATE-XIAOMI-arm64-v8a.apk", ignoreCase = true) }
                    ?.let { return it }
            }
            // 端末の優先ABIに合わせ、他CPU向けライブラリを含まない更新を選ぶ。
            for (abi in abis) {
                updateApks.singleOrNull { it.endsWith("-UPDATE-ANDROID-$abi.apk", ignoreCase = true) }
                    ?.let { return it }
                // XIAOMI版も同じパッケージ・機能のarm64で、他のarm64端末でも更新可能。
                if (abi == "arm64-v8a") updateApks.singleOrNull {
                    it.endsWith("-UPDATE-XIAOMI-arm64-v8a.apk", ignoreCase = true)
                }?.let { return it }
            }
            return updateApks.singleOrNull { it.endsWith("-UPDATE-ANDROID-universal.apk", ignoreCase = true) }
        }

        // 新しい4モデル構成より前のReleaseも引き続き選択できるようにする。
        for (abi in abis) apks.singleOrNull { it.endsWith("-$abi.apk") }?.let { return it }
        return apks.singleOrNull { it.endsWith("-universal.apk") }
    }

    internal fun isXiaomiFamily(manufacturer: String, brand: String): Boolean =
        listOf(manufacturer, brand).any { value ->
            val deviceNames = value
                .trim()
                .lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter(String::isNotBlank)
            deviceNames.any { it == "xiaomi" || it == "redmi" || it == "poco" }
        }
    fun validateAssetUrl(url: String, repository: String) {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
            uri.path.startsWith("/$repository/releases/download/")) { "配信元と異なるAPK URLです" }
    }
    fun verifyDigest(actual: String, expected: String?) {
        require(expected == null || actual.equals(expected, true)) { "SHA-256が一致しません" }
    }
}
