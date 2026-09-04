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
    fun chooseAsset(names: List<String>, abis: List<String>): String? {
        val apks = names.filter { it.endsWith(".apk") && !it.contains("debug", true) }
        for (abi in abis) apks.singleOrNull { it.endsWith("-$abi.apk") }?.let { return it }
        return apks.singleOrNull { it.endsWith("-universal.apk") }
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
