package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import org.junit.Rule
import org.junit.Test

/** 設定値を変更せず、カテゴリー切替と一覧の展開・復帰を確認する。 */
class SettingsCategoriesTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun themeSwitchAndCollapsedIconsKeepTheirState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        var original: androidx.compose.ui.state.ToggleableState? = null
        try {
            compose.onNodeWithContentDescription("プロフィール").performClick()
            compose.onNodeWithText("設定").performClick()
            val toggle = compose.onNodeWithTag("settings-theme-toggle")
            original = toggle.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState]
            toggle.performClick()
            org.junit.Assert.assertNotEquals(original, toggle.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState])
            capture("settings-theme-alternate.png")
            toggle.performClick()
            org.junit.Assert.assertEquals(original, toggle.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState])
            compose.onNodeWithText("報酬で解放されたアイコン", substring = true).assertDoesNotExist()
            compose.onNodeWithText("アプリアイコン").performClick()
            compose.onNodeWithText("報酬で解放されたアイコン", substring = true).assertExists()
            compose.onNodeWithText("アプリアイコン").performClick()
            compose.onNodeWithText("報酬で解放されたアイコン", substring = true).assertDoesNotExist()
        } finally {
            if (original != null) {
                val toggle = compose.onNodeWithTag("settings-theme-toggle")
                if (toggle.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] != original) toggle.performClick()
            }
            scenario.close()
        }
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val context = ApplicationProvider.getApplicationContext<Context>()
        java.io.File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun categoriesAndShortcutExpansionKeepNavigationUsable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            compose.onNodeWithContentDescription("プロフィール").performClick()
            compose.onNodeWithText("設定").performClick()
            compose.onNodeWithText(if (jp.essential.app.BuildConfig.IS_XIAOMI_PACKAGE) "Essential Xiaomi" else "Essential Universal").assertExists()
            compose.onNodeWithText(jp.essential.app.BuildConfig.VERSION_NAME).assertExists()
            compose.onNodeWithText("使い心地を整える").assertDoesNotExist()
            compose.onNodeWithTag("settings-segments").assertExists()
            compose.onNodeWithText("更新", substring = false).assertDoesNotExist()
            compose.onNodeWithText("報酬で解放されたアイコン", substring = true).assertDoesNotExist()
            capture("settings-refined.png")
            compose.onNodeWithTag("settings-update-entry").performClick()
            compose.onNodeWithTag("app-updates-screen").assertExists()
            capture("settings-updates.png")
            compose.onNodeWithTag("updates-back").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("app-updates-screen").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("settings-segments").assertExists()
            compose.onNodeWithText("操作").performScrollTo().performClick()
            compose.onNodeWithText("モーションfps").assertDoesNotExist()
            compose.onNodeWithText("ダークテーマ").assertDoesNotExist()
            compose.onNodeWithText("ホームのショートカットを設定").performScrollTo()
            // 半透明のタブバーの背面から、実際にタップできる位置へスクロールする。
            compose.onNodeWithTag("main-tabs").performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.45f) }
            compose.onNodeWithTag("home-background-picker").assertExists()
            compose.onNodeWithTag("home-background-reset").assertExists().assertIsEnabled()
            compose.onNodeWithText("背景画像を選ぶ").assertDoesNotExist()
            compose.onNodeWithText("画像を選んで表示範囲を調整できます。").assertDoesNotExist()
            compose.onNodeWithText("アップデートを開く").assertDoesNotExist()
            capture("settings-shortcut-before.png")
            compose.onNodeWithText("ホームのショートカットを設定").performClick()
            capture("settings-shortcut-after.png")
            compose.onNodeWithText("機能一覧を閉じる").assertExists().performClick()
            compose.onNodeWithText("操作").performScrollTo()
            compose.onNodeWithText("外観").performClick()
            compose.onNodeWithText("ダークテーマ").assertExists()
            compose.onNodeWithText("モーションfps").assertDoesNotExist()
            compose.onNodeWithText("管理").performClick()
            compose.onNodeWithText("キャッシュ").assertExists()
            compose.onNodeWithText("すべて").performClick()
            compose.onNodeWithText("ダークテーマ").assertExists()
            // 選択中のタブを押し直す場合も、ほかのタブから戻る場合もプロフィールを開く。
            compose.onNodeWithContentDescription("プロフィール").performClick()
            compose.onNodeWithText("プロフィール編集").assertExists()
            compose.onNodeWithText("ダークテーマ").assertDoesNotExist()
            compose.onNodeWithText("設定").performClick()
            compose.onNodeWithContentDescription("ホーム").performClick()
            compose.onNodeWithContentDescription("プロフィール").performClick()
            compose.onNodeWithText("プロフィール編集").assertExists()
            compose.onNodeWithText("ダークテーマ").assertDoesNotExist()
        } finally { scenario.close() }
    }
}
