package jp.essential.app.feature.minigame

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.MainActivity
import jp.essential.app.profile.AppProgressStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MiniGameImprovementsTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun playTimeAwardsOnceAndStopsWhenInactive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AppProgressStore(context)
        val original = store.loadXp()
        val preferences = context.getSharedPreferences("game_play_xp", 0)
        val key = "test-play-time"
        preferences.edit().putLong(key, 29000L).commit()
        var active by mutableStateOf(true)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            scenario.onActivity { it.setContent { GamePlayXp(key, active, 90) } }
            compose.waitUntil(5000) { store.loadXp() == original + 90 }
            compose.runOnIdle { active = false }
            Thread.sleep(1500)
            assertEquals(original + 90, store.loadXp())
        } finally {
            scenario.close()
            store.setXp(original)
            preferences.edit().remove(key).commit()
        }
    }
    @Test fun blockHandAndMineGridScreenshots() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_FEATURE, "mini_game"))
        fun capture(name: String) {
            compose.waitForIdle()
            Thread.sleep(700)
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        try {
            compose.onNodeWithText("Block Blast").performClick()
            capture("block-hand-fixed.png")
        } finally { scenario.close() }
        val mines = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_FEATURE, "mini_game"))
        try {
            compose.onNodeWithText("マインスイーパー").performClick()
            compose.onNodeWithText("8 × 8").performClick()
            compose.onAllNodesWithContentDescription("未開封").onFirst().assertExists()
            capture("mine-soft-grid.png")
        } finally { mines.close() }
    }
}