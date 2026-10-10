package jp.essential.app.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class AppUpdateXpTest {
    @Test fun updateAwardsOnceAndFreshInstallDoesNotAward() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AppProgressStore(context)
        val preferences = context.getSharedPreferences("app_progress", 0)
        val originalXp = store.loadXp()
        val key = "update_reward_version"
        val previous = if (preferences.contains(key)) preferences.getInt(key, -1) else null
        try {
            preferences.edit().remove(key).commit()
            store.setXp(10)
            assertFalse(store.awardAppUpdate(100))
            assertEquals(10, store.loadXp())
            assertTrue(store.awardAppUpdate(101))
            assertEquals(1210, store.loadXp())
            assertFalse(store.awardAppUpdate(101, true))
            assertFalse(store.awardAppUpdate(100, true))
            assertFalse(store.awardAppUpdate(101, true))
            assertEquals(1210, store.loadXp())
            assertTrue(store.awardAppUpdate(102))
            assertEquals(2410, store.loadXp())
            preferences.edit().remove(key).commit()
            assertTrue(store.awardAppUpdate(103, true))
            assertFalse(store.awardAppUpdate(103, true))
            assertEquals(3610, store.loadXp())
        } finally {
            store.setXp(originalXp)
            preferences.edit().apply { if (previous == null) remove(key) else putInt(key, previous) }.commit()
        }
    }
}