package jp.essential.app.design

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.AppIconManager
import jp.essential.app.profile.AppProgressStore
import jp.essential.app.profile.requiredAppXp
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class AppDesignUpdateTrialTest {
    private class Isolated(base: Context) : ContextWrapper(base) {
        private val prefix = "update_design_test_${UUID.randomUUID()}_"
        private val names = mutableSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            synchronized(names) { names.add(prefix + name) }
            return super.getSharedPreferences(prefix + name, mode)
        }
        fun clean() { names.forEach { baseContext.deleteSharedPreferences(it) } }
    }
    private fun context() = Isolated(ApplicationProvider.getApplicationContext())

    @Test fun nextUpdateGrantsExactlySevenDaysOnceEvenConcurrently() {
        val context = context()
        val pool = Executors.newFixedThreadPool(6)
        val now = System.currentTimeMillis() / 1000
        try {
            val progress = AppProgressStore(context)
            assertFalse(progress.awardAppUpdate(29, now = now))
            assertNull(AppDesignAccessStore(context).updateTrialUntil(now))
            val results = pool.invokeAll((1..12).map { Callable {
                AppProgressStore(context).awardAppUpdate(30, now = now)
            } }).map { it.get() }
            assertEquals(1, results.count { it })
            val end = now + 604800L
            val access = AppDesignAccessStore(context)
            AppIconManager.options.forEach { assertEquals(end, access.activeUntil(it.id, now)) }
            assertEquals(end, access.nextBoundary(now))
            assertEquals(end, access.updateTrialUntil(end - 1))
            assertNull(access.updateTrialUntil(end))
            assertFalse(progress.awardAppUpdate(30, now = now + 10))
            assertTrue(progress.awardAppUpdate(31, now = now + 20))
            assertFalse(progress.awardAppUpdate(29, now = now + 30))
            assertEquals(end, AppDesignAccessStore(context).updateTrialUntil(now + 40))
            access.refresh(end)
            assertNull(access.updateTrialUntil(now))
            assertNull(access.nextBoundary(end))
        } finally { pool.shutdownNow(); context.clean() }
    }

    @Test fun freshInstallDoesNotGrantAndSkippedUpdateDoesGrant() {
        val fresh = context()
        val updated = context()
        val now = System.currentTimeMillis() / 1000
        try {
            assertFalse(AppProgressStore(fresh).awardAppUpdate(30, now = now))
            assertNull(AppDesignAccessStore(fresh).updateTrialUntil(now))
            AppProgressStore(fresh).awardAppUpdate(31, now = now + 1)
            assertNull(AppDesignAccessStore(fresh).updateTrialUntil(now + 1))
            assertTrue(AppProgressStore(updated).awardAppUpdate(31, updatedInstallation = true, now = now))
            assertEquals(now + 604800L, AppDesignAccessStore(updated).updateTrialUntil(now))
        } finally { fresh.clean(); updated.clean() }
    }

    @Test fun expiryPreservesEveryPermanentDesignAndRestoresNormalAtEveryLevel() {
        val now = System.currentTimeMillis() / 1000
        for (level in listOf(1, 16, 20, 25, 30, 35, 40, 50, 55, 60)) {
            val context = context()
            try {
                val progress = AppProgressStore(context)
                progress.awardAppUpdate(29, now = now)
                progress.awardAppUpdate(30, now = now)
                progress.setXp((1 until level).sumOf { requiredAppXp(it) })
                val permanent = AppIconManager.options.filter { it.unlockLevel <= level }
                val normal = permanent.last()
                AppIconManager.save(context, normal)
                val temporary = AppIconManager.options.last()
                AppIconManager.save(context, temporary)
                assertEquals(temporary.id, AppIconManager.load(context).id)
                val beforeXp = progress.loadXp()
                AppDesignAccessStore(context).refresh(now + 604800L)
                assertEquals(beforeXp, progress.loadXp())
                AppIconManager.options.forEach { option ->
                    assertEquals(option.unlockLevel <= level, AppIconManager.available(context, option))
                    assertEquals(option.unlockLevel <= level, AppIconManager.permanentlyAvailable(context, option))
                }
                assertEquals(if (level == 60) temporary.id else normal.id, AppIconManager.load(context).id)
                permanent.forEach { option ->
                    AppIconManager.save(context, option)
                    assertEquals(option.id, AppIconManager.load(context).id)
                }
            } finally { context.clean() }
        }
    }

    @Test fun newlyEarnedDesignAndOverlappingQrSurviveCampaignExpiry() {
        val context = context()
        val now = System.currentTimeMillis() / 1000
        val end = now + 604800L
        try {
            val progress = AppProgressStore(context)
            progress.awardAppUpdate(29, now = now)
            progress.awardAppUpdate(30, now = now)
            progress.setXp(0)
            AppIconManager.save(context, AppIconManager.optionForId("default_dark"))
            AppIconManager.save(context, AppIconManager.optionForId("mint"))
            progress.setXp((1 until 16).sumOf { requiredAppXp(it) })
            // 署名受取後の保存形式を使い、独立したQR期限との合成だけを確認する。
            val access = AppDesignAccessStore(context)
            access.preferences.edit().putString("grants", """[{"id":"test","design":"sunset","issued":$now,"start":$now,"end":${end + 100}}]""").commit()
            access.refresh(end)
            assertTrue(AppIconManager.available(context, AppIconManager.optionForId("mint")))
            assertEquals("mint", AppIconManager.load(context).id)
            assertEquals(end + 100, access.activeUntil("sunset", end))
            assertEquals(end + 100, access.nextBoundary(end))
            access.refresh(end + 100)
            assertNull(access.activeUntil("sunset", end + 100))
            assertTrue(AppIconManager.available(context, AppIconManager.optionForId("mint")))
        } finally { context.clean() }
    }
}
