package jp.essential.app.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageMaintenanceTest {
    @Test
    fun `アプリが作成した一時キャッシュだけを対象にする`() {
        assertTrue(StorageMaintenancePolicy.isTransientCacheName("Essential-source-123.bin"))
        assertTrue(StorageMaintenancePolicy.isTransientCacheName("essential-download-abcd"))
        assertTrue(StorageMaintenancePolicy.isTransientCacheName("yt-dlp123"))
        assertFalse(StorageMaintenancePolicy.isTransientCacheName("webview-cache"))
    }

    @Test
    fun `一日以上更新されていないものだけを期限切れとする`() {
        val now = 3_000_000_000L
        val retention = StorageMaintenancePolicy.TRANSIENT_RETENTION_MILLIS
        assertTrue(StorageMaintenancePolicy.isStale(1_000L, now))
        assertFalse(StorageMaintenancePolicy.isStale(now - retention + 1L, now))
        assertFalse(StorageMaintenancePolicy.isStale(0L, now))
        assertFalse(StorageMaintenancePolicy.isStale(now + 1L, now))
    }

    @Test
    fun `FFmpegKit対応ABIだけ展開済みFFmpegを回収する`() {
        assertTrue(StorageMaintenancePolicy.shouldRemoveBundledFfmpeg("arm64-v8a"))
        assertTrue(StorageMaintenancePolicy.shouldRemoveBundledFfmpeg("x86_64"))
        assertFalse(StorageMaintenancePolicy.shouldRemoveBundledFfmpeg("armeabi-v7a"))
        assertFalse(StorageMaintenancePolicy.shouldRemoveBundledFfmpeg(null))
    }

    @Test
    fun `旧yt-dlpルートは実行環境の目印がある場合だけ対象にする`() {
        val root = Files.createTempDirectory("storage-maintenance-").toFile()
        try {
            assertFalse(StorageMaintenancePolicy.isLegacyYtDlpRoot(root))
            File(root, "packages").mkdirs()
            assertTrue(StorageMaintenancePolicy.isLegacyYtDlpRoot(root))
        } finally {
            root.deleteRecursively()
        }
    }
}
