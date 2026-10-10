package jp.essential.app.feature.files

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.MainActivity
import jp.essential.app.ui.GlassBackdropScope
import jp.essential.app.ui.theme.EssentialTheme
import java.io.File
import org.junit.*
import org.junit.Assert.*

class MediaEditorUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var scenario: ActivityScenario<MainActivity>? = null
    @After fun close() { scenario?.close() }
    private fun launch(content: @Composable () -> Unit) {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        scenario!!.onActivity { activity -> activity.setContent {
            EssentialTheme(darkTheme = true) {
                GlassBackdropScope(Modifier.fillMaxSize(), background = {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF123B53), Color(0xFF40294E), Color(0xFF121721)))))
                }) { content() }
            }
        } }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.waitForIdle(); InstrumentationRegistry.getInstrumentation().waitForIdleSync(); Thread.sleep(150)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun referenceAndImageEditorNavigation() {
        val source = File(context.cacheDir, "ui-preview.png")
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(70, 140, 200)) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val file = ReferencedFile(Uri.fromFile(source), source.name, "image/png", ReferencedMediaType.Image, source.length())
        launch { FileReferenceScreen(initialFile = file, onBack = {}) }
        compose.onNodeWithText("別のファイルを参照").assertIsDisplayed()
        capture("files-glass-reference.png")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("AI画像編集を開く"))
        compose.onNodeWithText("AI画像編集を開く").performClick()
        compose.onNodeWithTag("media-editor").assertIsDisplayed()
        val header = compose.onNodeWithTag("fixed-feature-header").fetchSemanticsNode().boundsInRoot
        assertTrue("編集ヘッダーが時計領域に重ならない", header.top > 100f)
        compose.onNodeWithText("高画質化").assertIsDisplayed()
        capture("files-image-editor.png")
        source.delete()
    }
    @Test fun waveformHandlesAdjustBothEnds() {
        var selected = 0f..1f
        launch {
            var range by remember { mutableStateOf(0f..1f) }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                WaveformTimeline(FloatArray(180) { .5f }, range, .5f, true) { range = it; selected = it }
            }
        }
        compose.onNodeWithTag("waveform-timeline").performTouchInput {
            down(Offset(2f, height / 2f)); moveTo(Offset(width * .2f, height / 2f), 150); up()
        }
        assertTrue(selected.start > .15f)
        compose.onNodeWithTag("waveform-timeline").performTouchInput {
            down(Offset(width - 2f, height / 2f)); moveTo(Offset(width * .8f, height / 2f), 150); up()
        }
        assertTrue(selected.endInclusive < .85f)
        capture("files-waveform-handles.png")
    }
    @Test fun audioTrimScreenPlaysAndAdjustsSelection() {
        val source = File(context.cacheDir, "ui-audio.wav")
        AudioStemSeparationEngine.PcmWavWriter(source, 2, 44100).use {
            it.write(Array(2) { FloatArray(132300) { index -> (.1 * kotlin.math.sin(index * 2 * Math.PI * 330 / 44100)).toFloat() } }, 0, 132300)
        }
        val file = ReferencedFile(Uri.fromFile(source), source.name, "audio/wav", ReferencedMediaType.Audio, source.length(), 3000)
        launch { MediaEditorScreen(file, MediaEditorMode.Trim, onBack = {}) }
        compose.waitUntil(10000) { compose.onAllNodes(hasText("再生") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("waveform-timeline").performTouchInput {
            down(Offset(2f, height / 2f)); moveTo(Offset(width * .15f, height / 2f), 150); up()
        }
        compose.onNodeWithText("再生").performClick()
        compose.onNodeWithText("一時停止").assertIsDisplayed().performClick()
        capture("files-audio-editor.png")
        source.delete()
    }
}
