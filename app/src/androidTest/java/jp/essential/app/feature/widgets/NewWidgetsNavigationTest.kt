package jp.essential.app.feature.widgets

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import org.junit.Rule
import org.junit.Test
import java.io.File



/** 最新の本体上でQR Createとダウンローダーの入力・表示を確認する。 */
class NewWidgetsNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun capture(name: String) {
        val directory = File(context.getExternalFilesDir(null), "new-widgets-qa").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
            File(directory, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun qrGenerationRendersInMainActivity() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("open_feature", "qr_create")).use {
            compose.onNodeWithText("QR Create").assertExists()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            var originalClip: ClipData? = null
            compose.runOnIdle {
                originalClip = clipboard.primaryClip
                clipboard.setPrimaryClip(ClipData.newPlainText("テストURL", "https://example.com/essential"))
            }
            try {
                compose.onNodeWithContentDescription("クリップボードから貼り付け").performClick()
                compose.onNode(hasSetTextAction()).assertTextContains("https://example.com/essential")
            } finally {
                compose.runOnIdle { originalClip?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip() }
            }
            compose.onNodeWithText("QRコードを作成").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("https://example.com/essential のQRコード").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("画像を保存").performScrollTo().assertIsDisplayed()
            capture("qr-create.png")
            compose.onNodeWithText("共有").performScrollTo().assertIsDisplayed()
        }
    }
    @Test fun downloaderGlassKeepsUrlAndTypeSwitchingUsable() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("open_feature", "downloader")).use {
            compose.onNodeWithText("ダウンローダー").assertExists()
            compose.onNodeWithContentDescription("クリップボードから貼り付け").assertExists()
            compose.onNode(hasSetTextAction()).performTextReplacement("https://example.com/essential")
            compose.onNodeWithText("音声", substring = false).performScrollTo().performClick()
            compose.onNode(hasSetTextAction()).assertTextContains("https://example.com/essential")
            compose.onNodeWithText("動画", substring = false).performScrollTo().performClick()
            capture("downloader-glass.png")
        }
    }
    @Test fun mannakaThemeBackdropRendersInMainActivity() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("open_feature", "mannaka")).use {
            compose.onNodeWithText("まんなか", substring = false).assertExists()
            compose.onNodeWithText("集合時間", substring = false).assertExists()
            compose.mainClock.advanceTimeBy(1500)
            capture("mannaka-theme-glass.png")
        }
    }
}
