package jp.essential.app.feature.minigame

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.key
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.MainActivity
import jp.essential.app.ui.GlassBackdropScope
import jp.essential.app.ui.LiquidGlassStyle
import jp.essential.app.ui.liquidGlass
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** 同じ背面に重ねた実メニューの面とホーム共通の面を画素で比較する。 */
class MiniGameGlassTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun menuGlassMatchesHomeInLightAndDarkAndNavigationStillWorks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            for (dark in listOf(false, true)) {
                scenario.onActivity { activity -> activity.setContent {
                    EssentialTheme(darkTheme = dark) {
                        Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
                            GlassBackdropScope(Modifier.fillMaxSize(), background = {
                                Box(Modifier.fillMaxSize().background(Color(0xFF496D91)))
                            }) {
                                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                                    Box(Modifier.weight(1f)) { key(dark) { MiniGameScreen({}, {}) } }
                                    Box(Modifier.fillMaxWidth().height(70.dp).liquidGlass(30.dp).testTag("home-glass-reference"))
                                }
                            }
                        }
                    }
                } }
                compose.mainClock.advanceTimeBy(1500)
                compose.waitForIdle()
                val card = compose.onNodeWithTag("game-menu-card-どすこい").captureToImage().toPixelMap()
                val reference = compose.onNodeWithTag("home-glass-reference").captureToImage().toPixelMap()
                val actual = card[card.width / 2, 20]
                val expected = reference[reference.width / 2, 20]
                assertEquals(expected.red, actual.red, .02f)
                assertEquals(expected.green, actual.green, .02f)
                assertEquals(expected.blue, actual.blue, .02f)
                screenshot(context, "mini-game-glass-${if (dark) "dark" else "light"}.png")
                compose.onNodeWithText("マインスイーパー").performClick()
                compose.onNodeWithText("8 × 8").performClick()
                compose.onAllNodesWithContentDescription("未開封").onFirst().assertExists()
                screenshot(context, "mini-game-mine-${if (dark) "dark" else "light"}.png")
                androidx.test.espresso.Espresso.pressBack()
                androidx.test.espresso.Espresso.pressBack()
                compose.onNodeWithText("ヘビゲーム").performScrollTo().performClick()
                compose.onNodeWithText("移動スティック").assertExists()
                screenshot(context, "mini-game-snake-${if (dark) "dark" else "light"}.png")
            }
        }
    }
    @Test fun dosukoiUsesHomeGlassValuesAfterThemeChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            fun render(dark: Boolean) {
                scenario.onActivity { activity -> activity.setContent {
                    EssentialTheme(darkTheme = dark) { MiniGameScreen({}, {}) }
                } }
            }
            fun web(view: android.view.View): android.webkit.WebView? {
                if (view is android.webkit.WebView) return view
                if (view is android.view.ViewGroup) for (index in 0 until view.childCount) web(view.getChildAt(index))?.let { return it }
                return null
            }
            val computed = java.util.concurrent.atomic.AtomicReference("")
            fun waitForStyle(mode: String, value: String) {
                compose.waitUntil(20000) {
                    scenario.onActivity { activity ->
                        web(activity.window.decorView)?.evaluateJavascript(
                            "(function(){var e=document.querySelector('.dosukoi-avatar-card');return document.documentElement.dataset.essentialGlass+'|'+document.documentElement.style.getPropertyValue('--essential-glass-start')+'|'+(e?getComputedStyle(e).backgroundImage:'');})()") {
                            computed.set(it.orEmpty())
                        }
                    }
                    computed.get().contains(mode) && computed.get().contains(value) && computed.get().contains("linear-gradient(")
                }
            }
            render(true)
            compose.onNodeWithText("どすこい", substring = false).performClick()
            waitForStyle("dark", LiquidGlassStyle.darkTint.first().alpha.toString())
            org.junit.Assert.assertTrue("背景の計算結果：${computed.get()}", computed.get().contains("rgba(46, 46, 46"))
            screenshot(context, "mini-game-dosukoi-dark.png")
            render(false)
            waitForStyle("light", LiquidGlassStyle.tint.first().alpha.toString())
            org.junit.Assert.assertTrue("背景の計算結果：${computed.get()}", computed.get().contains("rgba(255, 255, 255"))
            screenshot(context, "mini-game-dosukoi-light.png")
        }
    }

    private fun screenshot(context: Context, name: String) {
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
