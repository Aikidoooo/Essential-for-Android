package jp.essential.app.feature.qr

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.profile.AppProgressStore
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Callable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Rule

class XpVoucherRedemptionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun receiptCardAndGeneratedPng() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val suffix = UUID.randomUUID().toString()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("xp_test_${suffix}_$name", mode)
        }
        val token = instrumentation.context.assets.open("xp-voucher-fixture.txt").bufferedReader().use { it.readText().trim().removePrefix("\uFEFF") }
        val bitmap = instrumentation.context.assets.open("xp-voucher-fixture.png").use { android.graphics.BitmapFactory.decodeStream(it) }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = com.google.zxing.MultiFormatReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(
            com.google.zxing.RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
        assertEquals(token, decoded)
        try {
            compose.setContent { CompositionLocalProvider(LocalContext provides context) { MaterialTheme { XpVoucherOffer(decoded) } } }
            compose.waitUntil(15000) { compose.onAllNodes(androidx.compose.ui.test.hasText("XPを受け取る")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("正規の配布署名を確認しました。1200 XP").assertExists()
            compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
                java.io.File(base.getExternalFilesDir(null), "xp-voucher-card.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.onNodeWithText("XPを受け取る").performClick()
            compose.waitUntil(15000) { compose.onAllNodes(androidx.compose.ui.test.hasText("1200 XPを受け取りました")).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1200, AppProgressStore(context).loadXp())
        } finally { base.deleteSharedPreferences("xp_test_${suffix}_app_progress") }
    }
    @Test fun signedQrConcurrentReplayAndPersistence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val suffix = UUID.randomUUID().toString()
        // 実ユーザーのXPや履歴には触れず、独立した保存領域で確認する。
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("xp_test_${suffix}_$name", mode)
        }
        val token = instrumentation.context.assets.open("xp-voucher-fixture.txt").bufferedReader().use { it.readText().trim().removePrefix("\uFEFF") }
        val store = AppProgressStore(context)
        val pool = Executors.newFixedThreadPool(6)
        try {
            store.setXp(10)
            val results = pool.invokeAll((1..12).map { Callable { AppProgressStore(context).redeemQrXp(token) } }).map { it.get() }
            assertEquals(1, results.count { it })
            assertEquals(1210, store.loadXp())
            assertEquals(1, store.loadNotices().count { it.id.startsWith("qr:") })
            assertEquals(1200, store.loadNotices().single { it.id.startsWith("qr:") }.xp)
            assertFalse(AppProgressStore(context).redeemQrXp(token))
            assertEquals(1210, AppProgressStore(context).loadXp())
            assertEquals(1, AppProgressStore(context).loadNotices().count { it.id.startsWith("qr:") })
            assertTrue(runCatching { store.redeemQrXp(token.dropLast(4) + "AAAA") }.isFailure)
            assertEquals(1210, store.loadXp())
        } finally {
            pool.shutdownNow()
            base.deleteSharedPreferences("xp_test_${suffix}_app_progress")
        }
    }
}
