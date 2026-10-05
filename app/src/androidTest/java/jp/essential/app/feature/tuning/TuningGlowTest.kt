package jp.essential.app.feature.tuning

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.MainActivity
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.Rule
import org.junit.Test

/** 合成周波数を表示層へ渡し、選択弦だけが一致状態へ変わることを検証する。 */
class TuningGlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun selectedStringGlowsOnlyForCorrectPitchAndOctave() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var measured by mutableStateOf<Double?>(noteFrequency(67))
        var selected by mutableStateOf<Int?>(67)
        var clockwise by mutableStateOf<Boolean?>(null)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            scenario.onActivity { activity -> activity.setContent {
                EssentialTheme(darkTheme = true) {
                    TuningPanel(tuningPresets[3], 3, selected, selected, measured, 440, true, true, {}, { selected = it }, {},
                        { clockwise }, { _, value -> clockwise = value })
                }
            } }
            val matched = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "音程一致")
            compose.onNodeWithContentDescription("4弦 G4").assert(matched)
            val lit = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "緑に点灯")
            compose.onNodeWithContentDescription("時計回りの回転矢印").assert(lit.not())
            compose.onNodeWithContentDescription("反時計回りの回転矢印").assert(lit.not())
            compose.waitForIdle()
            // OSの起動スプラッシュが前面から退場してから端末画面を撮影する。
            Thread.sleep(1000)
            // 合成入力での発光表示を保存し、実機マイク入力の証拠とは区別する。
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null), "tuning-glow.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithContentDescription("3弦 C4").assert(matched.not())
            compose.runOnIdle { measured = noteFrequency(55) }
            compose.onNodeWithContentDescription("4弦 G4").assert(matched.not())
            compose.runOnIdle { measured = noteFrequency(67) * 1.01 }
            compose.onNodeWithContentDescription("4弦 G4").assert(matched.not())
            compose.onNodeWithText("緩めて音を下げる ↓").assertExists()
            compose.runOnIdle { clockwise = true }
            compose.onNodeWithText("↺ 反時計回り · 緩めて音を下げる").assertExists()
            compose.onNodeWithContentDescription("反時計回りの回転矢印").assert(lit)
            compose.onNodeWithContentDescription("時計回りの回転矢印").assert(lit.not())
            compose.waitForIdle()
            val directionScreenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null), "tuning-direction-glow.png").outputStream().use {
                directionScreenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            directionScreenshot.recycle()
            compose.runOnIdle { clockwise = false }
            compose.onNodeWithText("↻ 時計回り · 緩めて音を下げる").assertExists()
            compose.onNodeWithContentDescription("時計回りの回転矢印").assert(lit)
            compose.runOnIdle { measured = noteFrequency(67) / 1.01 }
            compose.onNodeWithText("↺ 反時計回り · 締めて音を上げる").assertExists()
            compose.runOnIdle { measured = noteFrequency(67) }
            compose.onNodeWithContentDescription("4弦 G4").assert(matched)
            compose.onNodeWithContentDescription("3弦 C4").performClick()
            compose.onNodeWithContentDescription("4弦 G4").assert(matched.not())
            compose.runOnIdle { measured = noteFrequency(60) }
            compose.onNodeWithContentDescription("3弦 C4").assert(matched)
            compose.runOnIdle { measured = null }
            compose.onNodeWithContentDescription("3弦 C4").assert(matched.not())
            compose.onNodeWithContentDescription("時計回りの回転矢印").assert(lit.not())
            compose.onNodeWithContentDescription("反時計回りの回転矢印").assert(lit.not())
        } finally { scenario.close() }
    }
}
