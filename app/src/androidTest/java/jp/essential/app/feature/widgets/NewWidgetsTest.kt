package jp.essential.app.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import jp.essential.app.feature.qr.QrCreateScreen
import jp.essential.app.feature.qr.createQrBitmap
import jp.essential.app.feature.qr.validQrUrl
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** QRコードの復号、共有URI、入力の検証を確認する。 */
class NewWidgetsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun qrRoundTripIncludesUnicodeAndPngProvider() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val value = "https://example.com/path?name=日本語&value=%23"
        val bitmap = createQrBitmap(value)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
        assertEquals(value, decoded.text)
        assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(bitmap.width / 2, 0))
        val directory = File(context.cacheDir, "qr-images").apply { mkdirs() }
        val file = File(directory, "test-qr.png")
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.qr-images", file)
            assertEquals("image/png", context.contentResolver.getType(uri))
            context.contentResolver.openInputStream(uri)!!.use { assertEquals(137, it.read()); assertEquals(80, it.read()) }
        } finally { file.delete() }
    }

    @Test fun qrRejectsUnsafeOrIncompleteUrls() {
        listOf("javascript:alert(1)", "file:///sdcard/test", "https://", "example.com", "https://example.com/" + "a".repeat(2000)).forEach {
            assertFalse(it, validQrUrl(it))
        }
        assertTrue(validQrUrl("https://example.com"))
    }

    @Test fun qrCreationTracksGeneratedUrlAfterInputChanges() {
        compose.setContent { MaterialTheme { QrCreateScreen {} } }
        compose.onNode(hasSetTextAction()).performTextInput("https://example.com")
        compose.onNodeWithText("QRコードを作成").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("https://example.com のQRコード").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("https://example.com のQRコード").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("https://example.org")
        compose.onNodeWithContentDescription("https://example.com のQRコード").assertExists()
        compose.onNodeWithText("画像を保存").assertExists()
        compose.onNodeWithText("共有").assertExists()
    }

}
