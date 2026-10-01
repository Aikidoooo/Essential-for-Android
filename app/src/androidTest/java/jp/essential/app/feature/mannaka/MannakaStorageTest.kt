package jp.essential.app.feature.mannaka

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.storage.StorageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MannakaStorageTest {
    @Test fun installedAppAndMeetingCacheStayWithinBudget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stats = context.getSystemService(StorageStatsManager::class.java)
            .queryStatsForUid(StorageManager.UUID_DEFAULT, context.applicationInfo.uid)
        val tiles = File(context.cacheDir, "mannaka-tiles").listFiles().orEmpty().sumOf { it.length() }
        val result = JSONObject().put("app_bytes", stats.appBytes).put("data_bytes", stats.dataBytes)
            .put("cache_bytes", stats.cacheBytes).put("total_bytes", stats.appBytes + stats.dataBytes)
            .put("meeting_tile_cache_bytes", tiles).put("limit_bytes", 400_000_000)
        // AndroidのdataBytesにはcacheBytesが含まれるため、二重加算しない。
        File(context.getExternalFilesDir(null), "mannaka-storage-size.json").writeText(result.toString(2))
        assertTrue("端末上のアプリ容量：$result", stats.appBytes + stats.dataBytes <= 400_000_000)
        assertTrue("地図タイルキャッシュ容量：$tiles", tiles <= 12_000_000)
    }
}
