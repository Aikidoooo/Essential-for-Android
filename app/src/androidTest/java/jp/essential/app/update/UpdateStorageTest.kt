package jp.essential.app.update

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.BuildConfig
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class UpdateStorageTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun replacementCleanupRemovesCopiesAndPreservesSavedData() {
        val root = File(context.noBackupFilesDir, "update-storage-isolated-test")
        root.mkdirs()
        val wrapped = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences("update-storage-test-$name", mode)
        }
        val repository = GitHubUpdateRepository(wrapped)
        val saved = File(root, "saved-log.txt").apply { writeText("保存データ") }
        repository.apk.writeBytes(ByteArray(1024))
        File(repository.apk.parentFile, "update.part").writeBytes(ByteArray(512))
        val prefs = wrapped.getSharedPreferences("app_update", 0)
        prefs.edit().putString("download_target_version", "0.6.5").putBoolean("startup_check", false).commit()
        try {
            assertEquals(1536L, repository.cleanupAfterPackageReplacement())
            assertFalse(repository.apk.exists())
            assertFalse(File(repository.apk.parentFile, "update.part").exists())
            assertEquals("保存データ", saved.readText())
            assertFalse(prefs.contains("download_target_version"))
            assertFalse(prefs.getBoolean("startup_check", true))
        } finally {
            root.deleteRecursively()
            prefs.edit().clear().commit()
        }
    }

    @Test fun actualReplacementFixture() {
        val phase = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("update_storage_phase")
        assumeTrue("実際のadb更新を間に挟む検証だけで実行する", phase == "prepare" || phase == "verify")
        if (phase == "prepare") prepareReplacementFixture() else verifyReplacementFixtureWasCleaned()
    }

    private fun prepareReplacementFixture() {
        // adb install -rの実更新前に、自分の更新一時領域へテストデータだけを作る。
        val root = File(context.noBackupFilesDir, "app-update")
        assumeTrue("既存の更新ダウンロードは上書きしない", root.listFiles().isNullOrEmpty())
        root.mkdirs()
        File(root, "update.apk").writeBytes(ByteArray(1024 * 1024))
        File(root, "update.part").writeBytes(ByteArray(1024 * 1024))
        val stats = context.getSystemService(android.app.usage.StorageStatsManager::class.java)
            .queryStatsForUid(android.os.storage.StorageManager.UUID_DEFAULT, context.applicationInfo.uid)
        context.getSharedPreferences("update-storage-sentinel", 0).edit().putString("saved", "保持する設定")
            .putLong("app_bytes_before", stats.appBytes).commit()
        context.getSharedPreferences("app_update", 0).edit()
            .putString("download_target_version", "99.0.0")
            .putLong("download_source_version_code", BuildConfig.VERSION_CODE.toLong())
            .putLong("downloaded_at", System.currentTimeMillis()).commit()
    }

    private fun verifyReplacementFixtureWasCleaned() {
        val root = File(context.noBackupFilesDir, "app-update")
        val prefs = context.getSharedPreferences("update-storage-sentinel", 0)
        try {
            assertEquals("保持する設定", prefs.getString("saved", null))
            assertFalse("画面を開く前に更新APKを回収する", File(root, "update.apk").exists())
            assertFalse("中断ファイルを回収する", File(root, "update.part").exists())
            assertFalse(context.getSharedPreferences("app_update", 0).contains("download_target_version"))
            val stats = context.getSystemService(android.app.usage.StorageStatsManager::class.java)
                .queryStatsForUid(android.os.storage.StorageManager.UUID_DEFAULT, context.applicationInfo.uid)
            val before = prefs.getLong("app_bytes_before", 0)
            assertTrue("旧版の本体容量が加算されない", before > 0 && stats.appBytes < before * 1.1)
            assertTrue("アプリと保存データが400MB以内", stats.appBytes + stats.dataBytes <= 400_000_000)
            File(context.getExternalFilesDir(null), "update-replacement-size.json").writeText(
                org.json.JSONObject().put("app_bytes_before", before).put("app_bytes_after", stats.appBytes)
                    .put("data_bytes_after", stats.dataBytes).put("total_bytes_after", stats.appBytes + stats.dataBytes)
                    .put("old_update_copies_removed", true).put("saved_preferences_preserved", true).toString(2))
        } finally { prefs.edit().clear().commit() }
    }
}
