package jp.essential.app.feature.textscan

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.webkit.WebView
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.MainActivity
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min

class TextScanRenewalTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun region(left: Float, top: Float, right: Float, bottom: Float) = ScanRegion(listOf(
        ScanPoint(left, top), ScanPoint(right, top), ScanPoint(right, bottom), ScanPoint(left, bottom)))

    @Test fun maskPreservesOnlyPolygonUnionIncludingConcaveCorners() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val triangle = ScanRegion(listOf(ScanPoint(.1f, .1f), ScanPoint(.8f, .1f), ScanPoint(.1f, .8f), ScanPoint(.1f, .1f)))
        val second = region(.6f, .6f, .95f, .95f)
        val output = maskScanRegions(source, listOf(triangle, second, region(.15f, .15f, .3f, .3f)))
        try {
            assertEquals(Color.BLACK, output.getPixel(20, 20))
            assertEquals(Color.BLACK, output.getPixel(85, 85))
            assertEquals(Color.WHITE, output.getPixel(55, 55))
            assertEquals(Color.WHITE, output.getPixel(4, 4))
            assertEquals(Color.BLACK, source.getPixel(4, 4))
            assertFalse(ScanRegion(listOf(ScanPoint(.1f, .1f), ScanPoint(.2f, .2f), ScanPoint(.3f, .3f), ScanPoint(.4f, .4f))).isValid())
            assertFalse(region(-.1f, 0f, 1f, 1f).isValid())
        } finally { output.recycle(); source.recycle() }
    }

    @Test fun decodeHonorsExifRotationAndBoundsMemory() {
        val file = File(context.cacheDir, "scan-exif-renewal-test.jpg")
        val source = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
            val decoded = decodeScanPhoto(context, Uri.fromFile(file))
            try { assertEquals(60, decoded.width); assertEquals(100, decoded.height) } finally { decoded.recycle() }
            for ((width, height) in listOf(12000 to 8000, 8000 to 12000, 20000 to 500, 100 to 60)) {
                val size = scanDecodeSize(width, height)
                assertTrue(size.first <= SCAN_MAX_EDGE && size.second <= SCAN_MAX_EDGE)
                assertTrue(size.first.toLong() * size.second <= SCAN_MAX_PIXELS)
            }
        } finally { source.recycle(); file.delete() }
    }

    @Test fun translationKeepsAllUnicodeTextAndRestrictsNavigation() {
        val text = "a".repeat(1499) + "😀" + "\n" + "日本語 & ? # \"引用\"".repeat(500)
        val chunks = translationChunks(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 1500 })
        chunks.forEach { chunk ->
            val uri = Uri.parse(googleTranslationUrl(chunk))
            assertEquals(chunk, uri.getQueryParameter("text"))
            assertEquals("auto", uri.getQueryParameter("sl"))
            assertEquals("ja", uri.getQueryParameter("tl"))
            assertTrue(isTranslationNavigationAllowed(uri))
        }
        assertTrue(isTranslationNavigationAllowed(Uri.parse("https://consent.google.com/")))
        assertFalse(isTranslationNavigationAllowed(Uri.parse("https://google.com.evil.example/")))
        assertFalse(isTranslationNavigationAllowed(Uri.parse("http://translate.google.com/")))
        assertFalse(isTranslationNavigationAllowed(Uri.parse("file:///sdcard/test.html")))
        assertTrue(translationChunks("").isEmpty())
    }

    private fun fixture(): File {
        val file = File(context.cacheDir, "scan-renewal-fixture.png")
        val image = Bitmap.createBitmap(1000, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 72f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        canvas.drawText("HELLO", 100f, 260f, paint)
        canvas.drawText("SECRET", 620f, 260f, paint)
        canvas.drawText("WORLD", 100f, 650f, paint)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        return file
    }

    private fun circle(cx: Float, cy: Float) {
        compose.onNodeWithTag("text-scan-photo").performTouchInput {
            val scale = min(width / 1000f, height / 800f)
            val left = (width - 1000f * scale) / 2
            val top = (height - 800f * scale) / 2
            fun point(angle: Double) = Offset(left + (cx + .21f * cos(angle).toFloat()) * 1000f * scale,
                top + (cy + .13f * sin(angle).toFloat()) * 800f * scale)
            down(point(0.0))
            for (index in 1..40) moveTo(point(index * 2 * Math.PI / 40), 16)
            up()
        }
    }

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(300)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { image ->
            File(context.getExternalFilesDir(null), name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }

    @Test fun circlesSupportUndoClearAndOnlySelectedOcrReachesTranslation() {
        val file = fixture()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            scenario.onActivity { activity -> activity.setContent {
                EssentialTheme(darkTheme = true) {
                    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
                        TextScanScreen(initialPhotoUri = Uri.fromFile(file), onBack = {})
                    }
                }
            } }
            compose.waitUntil(15000) { compose.onAllNodesWithTag("text-scan-photo").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("text-scan-read").assertIsNotEnabled()
            circle(.27f, .29f); circle(.27f, .77f)
            compose.onNodeWithText("選択した範囲：2個").assertExists()
            compose.onNodeWithText("ひとつ戻す").performClick()
            compose.onNodeWithText("選択した範囲：1個").assertExists()
            compose.onNodeWithText("すべて解除").performClick()
            compose.onNodeWithTag("text-scan-read").assertIsNotEnabled()
            circle(.27f, .29f); circle(.27f, .77f)
            screenshot("text-scan-selection.png")
            compose.onNodeWithTag("text-scan-read").performClick()
            compose.waitUntil(30000) { compose.onAllNodesWithTag("text-scan-result").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("HELLO", substring = true).assertExists()
            compose.onNodeWithText("WORLD", substring = true).assertExists()
            compose.onNodeWithText("SECRET", substring = true).assertDoesNotExist()
            screenshot("text-scan-result.png")
            compose.onNodeWithTag("text-scan-translate").performClick()
            compose.onNodeWithTag("text-scan-translation").assertExists()
            val captured = AtomicReference<String>()
            compose.waitUntil(15000) {
                scenario.onActivity { activity -> captured.set(findWebView(activity.window.decorView)?.url) }
                captured.get()?.startsWith("https://translate.google.com/") == true
            }
            val sent = Uri.parse(captured.get()).getQueryParameter("text").orEmpty()
            assertTrue(sent.contains("HELLO") && sent.contains("WORLD"))
            assertFalse(sent.contains("SECRET"))
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("text-scan-result").assertExists()
            compose.onNodeWithText("範囲を選び直す").performClick()
            compose.onNodeWithText("選択した範囲：2個").assertExists()
        } finally { scenario.close(); file.delete() }
    }

    @Test fun cameraCaptureOpensSelectionBeforeRecognition() {
        val prefs = context.getSharedPreferences("scanner", Context.MODE_PRIVATE)
        val original = if (prefs.contains("text_mode")) prefs.getBoolean("text_mode", false) else null
        prefs.edit().putBoolean("text_mode", true).commit()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_FEATURE, "scanner"))
        try {
            compose.waitUntil(15000) { compose.onAllNodesWithTag("scanner-shutter").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(15000) {
                runCatching { compose.onNodeWithTag("scanner-shutter").assertIsEnabled() }.isSuccess
            }
            compose.onNodeWithTag("scanner-shutter").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("text-scan-selection").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("text-scan-read").assertIsNotEnabled()
            compose.onNodeWithTag("text-scan-result").assertDoesNotExist()
            androidx.test.espresso.Espresso.pressBack()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("text-scan-selection").fetchSemanticsNodes().isEmpty() }
        } finally {
            scenario.close()
            prefs.edit().apply { if (original == null) remove("text_mode") else putBoolean("text_mode", original) }.commit()
        }
    }

    @Test fun liveGoogleTranslationShowsJapaneseInsideApp() {
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        val body = AtomicReference("")
        try {
            scenario.onActivity { activity -> activity.setContent {
                EssentialTheme(darkTheme = true) { ScanTranslationScreen("Hello world") {} }
            } }
            compose.waitUntil(45000) {
                scenario.onActivity { activity ->
                    findWebView(activity.window.decorView)?.evaluateJavascript("document.body ? document.body.innerText : ''") {
                        body.set(it.orEmpty())
                    }
                }
                body.get().contains("こんにちは") || body.get().contains("ハローワールド") || body.get().contains("世界")
            }
            compose.onNodeWithTag("text-scan-translation").assertIsDisplayed()
            screenshot("text-scan-google-live.png")
        } finally {
            File(context.getExternalFilesDir(null), "text-scan-google-live-body.txt").writeText(body.get())
            scenario.close()
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        return null
    }
}
