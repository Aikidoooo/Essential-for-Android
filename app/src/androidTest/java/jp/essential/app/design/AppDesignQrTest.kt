package jp.essential.app.design

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.AppIconManager
import jp.essential.app.MainActivity
import jp.essential.app.feature.qr.DesignVoucher
import jp.essential.app.feature.qr.DesignVoucherOffer
import jp.essential.app.profile.AppProgressStore
import jp.essential.app.ui.AppDesignSettingsCard
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class AppDesignQrTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val base get() = ApplicationProvider.getApplicationContext<Context>()
    private val ids get() = AppIconManager.options.map { it.id }.toSet()
    private fun signed(id: String = UUID.randomUUID().toString(), design: String = "mint", issued: Long,
        start: Long, end: Long, domain: String = "app-design"): String {
        val payload = "1|jp.essential.app|$domain|$id|$design|$issued|$start|$end".toByteArray(Charsets.US_ASCII)
        val bytes = Signature.getInstance("SHA256withRSA").run { initSign(key.private); update(payload); sign() }
        return DesignVoucher.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload) + "." +
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    private class Isolated(base: Context) : ContextWrapper(base) {
        private val prefix = "design_test_${UUID.randomUUID()}_"
        private val names = mutableSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            synchronized(names) { names.add(prefix + name) }
            return super.getSharedPreferences(prefix + name, mode)
        }
        fun clean() { names.forEach { baseContext.deleteSharedPreferences(it) } }
    }

    @Test fun signatureRejectsTamperingWrongDomainUnknownDesignAndInvalidPeriods() {
        val now = System.currentTimeMillis() / 1000
        val good = signed(issued = now, start = now + 10, end = now + 100)
        val grant = DesignVoucher.verify(good, modulus, ids, now)
        assertEquals("mint", grant.designId)
        assertEquals(now + 10, grant.startsAt)
        assertTrue(runCatching { DesignVoucher.verify(good, modulus, ids, now + 100) }.isFailure)
        val parts = good.removePrefix(DesignVoucher.PREFIX).split('.')
        val text = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.US_ASCII).replace("mint", "sunset")
        val changed = DesignVoucher.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray()) + "." + parts[1]
        assertTrue(runCatching { DesignVoucher.verify(changed, modulus, ids, now) }.isFailure)
        assertTrue(runCatching { DesignVoucher.verify(good + "=", modulus, ids, now) }.isFailure)
        for (bad in listOf(
            signed(issued = now, start = now, end = now + 100, design = "unknown"),
            signed(issued = now, start = now, end = now + 100, domain = "xp"),
            signed(issued = now, start = now + 100, end = now + 100),
            signed(issued = now, start = now, end = now + 367L * 86400),
            signed(issued = now + 301, start = now + 301, end = now + 400))) {
            assertTrue(runCatching { DesignVoucher.verify(bad, modulus, ids, now) }.isFailure)
        }
    }

    @Test fun concurrentReceiptIsOnceAndDoesNotChangeXp() {
        val context = Isolated(base)
        val now = System.currentTimeMillis() / 1000
        val token = signed(issued = now, start = now, end = now + 100)
        val store = AppDesignAccessStore(context, modulus)
        val pool = Executors.newFixedThreadPool(6)
        try {
            AppProgressStore(context).setXp(17)
            val results = pool.invokeAll((1..12).map { Callable { AppDesignAccessStore(context, modulus).redeem(token, now) } }).map { it.get() }
            assertEquals(1, results.count { it })
            assertFalse(AppDesignAccessStore(context, modulus).redeem(token, now))
            assertEquals(now + 100, store.activeUntil("mint", now))
            assertEquals(17, AppProgressStore(context).loadXp())
            assertEquals(1, store.preferences.getStringSet("received_ids", emptySet())!!.size)
        } finally { pool.shutdownNow(); context.clean() }
    }

    @Test fun futureStartExactEndPersistenceAndNormalFallback() {
        val context = Isolated(base)
        val now = System.currentTimeMillis() / 1000
        val store = AppDesignAccessStore(context, modulus)
        try {
            AppIconManager.save(context, AppIconManager.defaultDark)
            val token = signed(issued = now, start = now + 1, end = now + 60)
            assertTrue(store.redeem(token, now))
            assertNull(store.activeUntil("mint", now))
            assertEquals(now + 1, store.nextBoundary(now))
            assertEquals(now + 60, store.activeUntil("mint", now + 1))
            store.refresh(now + 1)
            AppIconManager.save(context, AppIconManager.optionForId("mint"))
            assertEquals("mint", AppIconManager.load(context).id)
            assertEquals("mint", AppIconManager.load(IsolatedView(context)).id)
            store.refresh(now + 60)
            assertNull(store.activeUntil("mint", now + 60))
            assertNull(store.activeUntil("mint", now))
            assertEquals("default_dark", AppIconManager.load(context).id)
            assertTrue(runCatching { store.redeem(token, now) }.isFailure)
            assertEquals(0, AppProgressStore(context).loadXp())
        } finally { context.clean() }
    }
    private class IsolatedView(base: Context) : ContextWrapper(base) { override fun getApplicationContext(): Context = this }

    @Test fun expiryReconcilesActualLauncherThenRestoresOriginal() {
        val context = Isolated(base)
        val original = AppIconManager.load(base)
        val now = System.currentTimeMillis() / 1000
        try {
            val store = AppDesignAccessStore(context, modulus)
            AppIconManager.save(context, AppIconManager.defaultDark)
            store.redeem(signed(issued = now, start = now, end = now + 60), now)
            AppIconManager.apply(context, AppIconManager.optionForId("mint"))
            store.refresh(now + 60)
            AppDesignExpiry.reconcile(context)
            assertEquals("default_dark", AppIconManager.load(context).id)
            val component = android.content.ComponentName(base, AppIconManager.launcherClassName(AppIconManager.defaultDark.aliasName))
            assertEquals(android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, base.packageManager.getComponentEnabledSetting(component))
        } finally {
            AppIconManager.apply(base, original)
            AppDesignExpiry.reconcile(base)
            context.clean()
        }
    }

    @Test fun settingsRenameTemporarySelectionAndReceiptUi() {
        val scenario = ActivityScenario.launch<MainActivity>(Intent(base, MainActivity::class.java))
        var context: Isolated? = null
        val now = System.currentTimeMillis() / 1000
        val token = signed(issued = now, start = now - 1, end = now + 120)
        try {
            scenario.onActivity { activity ->
                val isolated = Isolated(activity).also { context = it }
                AppIconManager.save(isolated, AppIconManager.defaultDark)
                val store = AppDesignAccessStore(isolated, modulus)
                activity.setContent {
                    CompositionLocalProvider(LocalContext provides isolated) {
                        EssentialTheme(darkTheme = true) {
                            Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
                                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                                    DesignVoucherOffer(token, store, modulus)
                                    var selected by remember { mutableStateOf(AppIconManager.defaultDark) }
                                    AppDesignSettingsCard(selected, 0) { selected = it; AppIconManager.save(isolated, it) }
                                }
                            }
                        }
                    }
                }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText("デザインを受け取る").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("デザインを受け取る").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("期間限定デザインを受け取りました").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("アプリデザイン", substring = false).performClick()
            compose.onNodeWithTag("app-icon-mint").assertIsEnabled().performClick()
            compose.onNodeWithTag("app-icon-sunset").assertIsNotEnabled()
            assertEquals("mint", AppIconManager.load(context!!).id)
            screenshot("app-design-qr-settings.png")
            AppDesignAccessStore(context!!, modulus).refresh(now + 120)
            compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("app-icon-mint").assertIsNotEnabled() }.isSuccess }
            assertEquals("default_dark", AppIconManager.load(context!!).id)
        } finally { scenario.close(); context?.clean() }
    }

    @Test fun pcIssuerPngUsesBundledPublicKey() {
        val file = File(base.getExternalFilesDir(null), "design-issuer-smoke.png")
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        requireNotNull(bitmap) { "発行PCで作った検証用PNGが必要です" }
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val decoded = com.google.zxing.MultiFormatReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(
                com.google.zxing.RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
            val expected = File(base.getExternalFilesDir(null), "design-issuer-smoke.txt").readText().trim().removePrefix("\uFEFF")
            assertEquals(expected, decoded)
            val payload = String(Base64.getUrlDecoder().decode(decoded.removePrefix(DesignVoucher.PREFIX).substringBefore('.')), Charsets.US_ASCII).split('|')
            val publicKey = base.assets.open("design-issuer-public.txt").bufferedReader().use { it.readText() }
            val grant = DesignVoucher.verify(decoded, publicKey, ids, payload[5].toLong())
            assertEquals("mint", grant.designId)
            assertTrue(grant.endsAt > grant.startsAt)
            assertTrue(runCatching { DesignVoucher.verify(decoded, modulus, ids, grant.issuedAt) }.isFailure)
        } finally { bitmap.recycle() }
    }

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(base.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    companion object {
        private val key by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair() }
        private val modulus get() = Base64.getEncoder().encodeToString((key.public as RSAPublicKey).modulus.toByteArray())
    }
}
