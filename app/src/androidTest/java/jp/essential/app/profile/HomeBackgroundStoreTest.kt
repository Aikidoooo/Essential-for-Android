package jp.essential.app.profile

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** 実ユーザーの設定を触らず、ホーム背景の再読込みと独立した保存を確認する。 */
class HomeBackgroundStoreTest {
    @Test fun persistsAndResetsWithoutChangingProfileMedia() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val key = "home-background-test-${java.util.UUID.randomUUID()}"
        val directory = File(app.cacheDir, key).apply { mkdirs() }
        val isolated = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(key, mode)
        }
        val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
        try {
            val store = ProfileStore(isolated)
            val icon = store.saveCroppedImage(bitmap, false)
            val banner = store.saveCroppedImage(bitmap, true)
            val home = store.saveCroppedImage(bitmap, true, true)
            assertNotNull(home)
            assertEquals(home, ProfileStore(isolated).loadHomeBackgroundPath())
            assertEquals(icon, store.loadImagePath())
            assertEquals(banner, store.loadBannerPath())
            assertTrue(File(home!!).isFile)
            store.clearHomeBackground()
            assertNull(ProfileStore(isolated).loadHomeBackgroundPath())
            assertFalse(File(home).exists())
            assertTrue(File(icon!!).exists())
            assertTrue(File(banner!!).exists())
        } finally {
            bitmap.recycle()
            directory.deleteRecursively()
            app.getSharedPreferences(key, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
