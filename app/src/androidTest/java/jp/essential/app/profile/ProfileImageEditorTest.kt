package jp.essential.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 編集結果の縦横比とキャンセルを、実際の画像を読み込んで確認する。 */
class ProfileImageEditorTest {
    @get:Rule val compose = createComposeRule()

    private fun image(context: Context): File = File(context.cacheDir, "crop-test.png").also { file ->
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 800f, 600f, android.graphics.Color.BLUE, android.graphics.Color.CYAN, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawPaint(paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun bannerSavesWideCrop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = image(context)
        var saved: Bitmap? = null
        try {
            compose.setContent { MaterialTheme { ProfileImageEditor(Uri.fromFile(file), true, context, {}, { saved = it }) } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("ドラッグで移動・指を広げて拡大").fetchSemanticsNodes().isNotEmpty() }
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                File(context.filesDir, "profile-crop-banner.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.onNodeWithText("保存").performClick()
            compose.runOnIdle {
                assertNotNull(saved)
                assertEquals(2.5f, saved!!.width.toFloat() / saved!!.height, .02f)
            }
        } finally { file.delete() }
    }

    @Test fun avatarZoomSavesSmallerSquare() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = image(context)
        var saved: Bitmap? = null
        try {
            compose.setContent { MaterialTheme { ProfileImageEditor(Uri.fromFile(file), false, context, {}, { saved = it }) } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("ドラッグで移動・指を広げて拡大").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
            compose.onNodeWithText("200%").assertExists()
            compose.onNodeWithText("保存").performClick()
            compose.runOnIdle { assertEquals(300, saved!!.width); assertEquals(300, saved!!.height) }
        } finally { file.delete() }
    }

    @Test fun avatarCancelDoesNotSave() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = image(context)
        var cancelled = false
        var saved = false
        try {
            compose.setContent { MaterialTheme { ProfileImageEditor(Uri.fromFile(file), false, context, { cancelled = true }, { saved = true }) } }
            compose.onNodeWithText("キャンセル").performClick()
            compose.runOnIdle { assertTrue(cancelled); assertFalse(saved) }
            assertEquals(Offset(100f, -50f), clampCropOffset(Offset(400f, -400f), Size(400f, 300f), Size(200f, 200f)))
        } finally { file.delete() }
    }
}
