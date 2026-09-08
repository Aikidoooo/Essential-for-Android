package jp.essential.app.update

internal object UpdateStoragePolicy {
    const val DOWNLOAD_RETENTION_MILLIS = 24L * 60L * 60L * 1_000L

    /** 再起動後も更新APKを保持してよい状態かを判定する。 */
    fun shouldDeleteDownloadedApk(
        targetVersion: String?,
        sourceVersionCode: Long,
        downloadedAtMillis: Long,
        currentVersionName: String,
        currentVersionCode: Long,
        nowMillis: Long,
    ): Boolean {
        if (targetVersion.isNullOrBlank() || sourceVersionCode <= 0L || downloadedAtMillis <= 0L) return true
        if (sourceVersionCode > currentVersionCode) return true
        if (!UpdatePolicy.isNewer(targetVersion, currentVersionName)) return true
        return nowMillis - downloadedAtMillis > DOWNLOAD_RETENTION_MILLIS
    }
}
