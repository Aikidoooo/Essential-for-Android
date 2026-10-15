package jp.essential.app.profile

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** 試験専用の保存領域で条件達成、重複防止、既読、移行を検証する。 */
class RewardNoticeStoreTest {
    private fun withStore(test: (Context, AppProgressStore) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "notice_test_${UUID.randomUUID()}_"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        context.getSharedPreferences("app_progress", Context.MODE_PRIVATE).edit()
            .putBoolean("routine_progress_migrated_v1", true).commit()
        try { test(context, AppProgressStore(context)) }
        finally { base.deleteSharedPreferences(prefix + "app_progress") }
    }

    @Test fun crossingAndReadingPersistWithoutDuplicates() = withStore { context, store ->
        val threshold = (1 until 16).sumOf(::requiredAppXp)
        store.setXp(threshold - 1)
        assertTrue(store.loadRewardNotices().isEmpty())
        store.addXp(1)
        assertEquals(listOf(16), store.loadRewardNotices().map { it.reward.level })
        assertFalse(store.loadRewardNotices().single().read)
        assertTrue(16 in store.loadClaimedRewards())
        val opened = store.loadRewardNotices().map { it.reward.level }.toSet()
        store.setXp((1 until 20).sumOf(::requiredAppXp))
        store.markRewardNoticesRead(opened)
        val reloaded = AppProgressStore(context).loadRewardNotices()
        assertEquals(listOf(20, 16), reloaded.map { it.reward.level })
        assertFalse(reloaded.first().read)
        assertTrue(reloaded.last().read)
        store.setXp(0)
        store.setXp(threshold)
        assertEquals(2, store.loadRewardNotices().size)
    }

    @Test fun updateAndConcurrentXpNotifyOnlyOnce() = withStore { _, store ->
        val threshold = (1 until 16).sumOf(::requiredAppXp)
        store.setXp(threshold - 100)
        assertTrue(store.awardAppUpdate(29, updatedInstallation = true))
        assertEquals(1, store.loadRewardNotices().size)
        assertFalse(store.awardAppUpdate(29, updatedInstallation = true))
        val pool = Executors.newFixedThreadPool(4)
        try { pool.invokeAll((1..12).map { Callable { store.addXp(1) } }).forEach { it.get() } }
        finally { pool.shutdown() }
        assertEquals(threshold + 1112, store.loadXp())
        assertEquals(1, store.loadRewardNotices().size)
    }

    @Test fun legacyClaimsStaySilentAndUnclaimedRewardsAreNotified() = withStore { context, _ ->
        val preferences = context.getSharedPreferences("app_progress", Context.MODE_PRIVATE)
        preferences.edit().clear().putBoolean("routine_progress_migrated_v1", true)
            .putInt("total_xp", (1 until 20).sumOf(::requiredAppXp))
            .putStringSet("claimed_rewards", setOf("16")).commit()
        val store = AppProgressStore(context)
        assertEquals(listOf(20), store.loadRewardNotices().map { it.reward.level })
        assertEquals(setOf(16, 20), store.loadClaimedRewards())
        assertEquals(1, AppProgressStore(context).loadRewardNotices().size)
    }

    @Test fun updateAndQrXpNoticesPersistAndNeverRepeat() = withStore { context, store ->
        val token = InstrumentationRegistry.getInstrumentation().context.assets
            .open("xp-voucher-fixture.txt").bufferedReader().use { it.readText().trim().removePrefix("\uFEFF") }
        assertFalse(store.awardAppUpdate(29))
        assertTrue(store.loadNotices().isEmpty())
        assertTrue(store.awardAppUpdate(30))
        val update = store.loadNotices().single()
        assertEquals("update:30", update.id)
        assertEquals(1200, update.xp)
        assertFalse(update.read)
        assertTrue(store.redeemQrXp(token))
        assertFalse(store.redeemQrXp(token))
        assertFalse(store.awardAppUpdate(30))
        assertFalse(store.awardAppUpdate(29))
        val xpNotices = AppProgressStore(context).loadNotices().filter { it.xp != null }
        assertEquals(2, xpNotices.size)
        assertEquals(1, xpNotices.count { it.id.startsWith("qr:") })
        assertEquals(2400, store.loadXp())
        store.markNoticesRead(setOf(update.id))
        val afterReading = AppProgressStore(context).loadNotices()
        assertTrue(afterReading.single { it.id == update.id }.read)
        assertFalse(afterReading.single { it.id.startsWith("qr:") }.read)
        store.markNoticesRead(afterReading.map { it.id }.toSet())
        assertTrue(AppProgressStore(context).loadNotices().all { it.read })
        assertTrue(store.awardAppUpdate(31))
        assertEquals(3, store.loadNotices().count { it.xp != null })
        assertFalse(store.loadNotices().single { it.id == "update:31" }.read)
    }
}
