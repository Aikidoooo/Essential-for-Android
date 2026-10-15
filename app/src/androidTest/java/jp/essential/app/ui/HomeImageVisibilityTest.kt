package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import jp.essential.app.profile.ProfileStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 画像を消さずに表示を切り替え、再起動後にも設定が残ることを確認する。 */
class HomeImageVisibilityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun settle() { compose.mainClock.advanceTimeBy(1600); compose.waitForIdle() }

    @Test fun togglePersistsAndPreservesSelectedImage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ProfileStore(context)
        val originalEnabled = store.loadHomeBackgroundEnabled()
        val originalPath = store.loadHomeBackgroundPath()
        store.saveHomeBackgroundEnabled(true)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                settle()
                compose.onNodeWithTag("home-background-artwork").assertExists()
                fun openSwitch() {
                    compose.onNodeWithContentDescription("プロフィール").performClick()
                    settle()
                    compose.onNodeWithText("設定", substring = false).performClick()
                    settle()
                    compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("home-background-enabled"))
                }
                openSwitch()
                compose.onNodeWithTag("home-background-enabled").assertIsOn().performClick().assertIsOff()
                assertFalse(store.loadHomeBackgroundEnabled())
                assertEquals(originalPath, store.loadHomeBackgroundPath())
                compose.onNodeWithContentDescription("ホーム").performClick()
                settle()
                compose.onNodeWithTag("home-background-artwork").assertDoesNotExist()
                scenario.recreate()
                settle()
                compose.onNodeWithTag("home-background-artwork").assertDoesNotExist()
                openSwitch()
                compose.onNodeWithTag("home-background-enabled").assertIsOff().performClick().assertIsOn()
                compose.onNodeWithContentDescription("ホーム").performClick()
                settle()
                compose.onNodeWithTag("home-background-artwork").assertExists()
                assertEquals(originalPath, store.loadHomeBackgroundPath())
            }
        } finally { store.saveHomeBackgroundEnabled(originalEnabled) }
    }
}
