package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import org.junit.Rule
import org.junit.Test

/** 両表示モードで上部カラーとホーム画像の接続を実画面で確認する。 */
class HomeColorBlendTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun captureHomeColorTransitionInBothModes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val originalDark = preferences.getBoolean("dark_theme", false)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            for (dark in listOf(false, true)) {
                compose.onNodeWithContentDescription("プロフィール").performClick()
                compose.onNodeWithText("設定").performClick()
                if (preferences.getBoolean("dark_theme", false) != dark) {
                    compose.onNodeWithTag("settings-theme-toggle").performClick()
                }
                compose.onNodeWithContentDescription("ホーム").performClick()
                compose.mainClock.advanceTimeBy(1500)
                compose.waitForIdle()
                compose.onNodeWithTag("home-background-artwork").assertExists()
                compose.onNodeWithTag("home-artwork-shortcut").assertExists()
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                java.io.File(context.getExternalFilesDir(null), "home-color-blend-${if (dark) "dark" else "light"}.png")
                    .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally {
            scenario.close()
            preferences.edit().putBoolean("dark_theme", originalDark).commit()
        }
    }
}
