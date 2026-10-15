package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.AppIconManager
import jp.essential.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 全アイコンで表示モードが独立し、再起動後も両方の選択を保持することを確認する。 */
class AppIconAppearanceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun allIconsSupportBothModesWithoutChangingTheirSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val originalIcon = AppIconManager.load(context)
        val originalDark = preferences.getBoolean("dark_theme", false)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            compose.onNodeWithContentDescription("プロフィール").performClick()
            compose.onNodeWithText("設定").performClick()
            compose.onNodeWithText("アプリデザイン").performClick()
            for (dark in listOf(false, true)) {
                val toggle = compose.onNodeWithTag("settings-theme-toggle")
                toggle.performScrollTo()
                if (preferences.getBoolean("dark_theme", false) != dark) toggle.performClick()
                compose.waitForIdle()
                for (option in AppIconManager.options) {
                    val light = option.backgroundColors(false)
                    val night = option.backgroundColors(true)
                    for (index in 0..1) {
                        assertTrue("${option.id}のライト背景で本文のコントラストを確保", androidx.core.graphics.ColorUtils.calculateContrast(android.graphics.Color.BLACK, light[index]) >= 4.5)
                        assertTrue("${option.id}のダーク背景で本文のコントラストを確保", androidx.core.graphics.ColorUtils.calculateContrast(android.graphics.Color.WHITE, night[index]) >= 4.5)
                    }
                    // 下部タブとの重なりに左右されず、各選択項目の操作と状態の連動を検証する。
                    compose.onNodeWithTag("app-icon-${option.id}").performScrollTo()
                        .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
                    compose.waitForIdle()
                    assertEquals(option.id, AppIconManager.load(context).id)
                    assertEquals(dark, preferences.getBoolean("dark_theme", false))
                }
                toggle.performScrollTo().performClick()
                compose.waitForIdle()
                assertEquals("reference", AppIconManager.load(context).id)
                assertEquals(!dark, preferences.getBoolean("dark_theme", false))
            }
            scenario.recreate()
            compose.waitForIdle()
            assertEquals("reference", AppIconManager.load(context).id)
            assertEquals(false, preferences.getBoolean("dark_theme", true))
        } finally {
            scenario.close()
            AppIconManager.apply(context, originalIcon)
            preferences.edit().putBoolean("dark_theme", originalDark).commit()
        }
    }
}
