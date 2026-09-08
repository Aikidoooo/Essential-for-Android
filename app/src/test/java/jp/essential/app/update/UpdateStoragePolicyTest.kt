package jp.essential.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateStoragePolicyTest {
    @Test
    fun `現在より新しく24時間以内のAPKだけ保持する`() {
        val now = 2_000_000_000L
        assertFalse(
            UpdateStoragePolicy.shouldDeleteDownloadedApk(
                targetVersion = "0.5.5",
                sourceVersionCode = 15,
                downloadedAtMillis = now - 60_000,
                currentVersionName = "0.5.4",
                currentVersionCode = 15,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `更新後は旧APKを削除する`() {
        assertTrue(
            UpdateStoragePolicy.shouldDeleteDownloadedApk(
                targetVersion = "0.5.5",
                sourceVersionCode = 15,
                downloadedAtMillis = 1_000,
                currentVersionName = "0.5.5",
                currentVersionCode = 16,
                nowMillis = 2_000,
            ),
        )
    }

    @Test
    fun `管理情報なしと期限切れAPKを削除する`() {
        val now = UpdateStoragePolicy.DOWNLOAD_RETENTION_MILLIS + 10_000L
        assertTrue(UpdateStoragePolicy.shouldDeleteDownloadedApk(null, -1, -1, "0.5.4", 15, now))
        assertTrue(UpdateStoragePolicy.shouldDeleteDownloadedApk("0.5.5", 15, 1, "0.5.4", 15, now))
    }
}
