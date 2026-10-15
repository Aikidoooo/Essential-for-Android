package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import jp.essential.app.profile.AppProgressStore
import jp.essential.app.profile.calculateAppLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 保存XPを変更せず、新しいプロフィールの表示位置とおしらせの往復を確認する。 */
class ProfileLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun unreadDotsClearOnOpeningAndStayReadAfterRestart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AppProgressStore(context)
        val xp = store.loadXp()
        val preferences = context.getSharedPreferences("app_progress", Context.MODE_PRIVATE)
        val keys = listOf("reward_notice_levels_v1", "reward_notice_read_v1", "xp_notice_read_v1")
        val original = keys.associateWith { preferences.getStringSet(it, null)?.toSet() }
        val originalXpNotices = preferences.getString("xp_notices_v1", null)
        try {
            // XPや報酬は変更せず、通知キーだけへ試験用の未読通知を追加する。
            val xpNotices = org.json.JSONArray()
                .put(org.json.JSONObject().put("id", "update:test").put("kind", "update")
                    .put("points", 1200).put("time", 1L))
                .put(org.json.JSONObject().put("id", "qr:test").put("kind", "qr")
                    .put("points", 500).put("time", 2L))
            preferences.edit().putStringSet(keys[0], setOf("16")).putStringSet(keys[1], emptySet())
                .putStringSet(keys[2], emptySet()).putString("xp_notices_v1", xpNotices.toString()).commit()
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                compose.onNodeWithTag("profile-tab-unread", useUnmergedTree = true).assertExists()
                compose.onNodeWithContentDescription("プロフィール").performClick()
                compose.onNodeWithTag("profile-bell-unread", useUnmergedTree = true).assertExists()
                capture(context, "profile-unread-dots.png")
                compose.onNodeWithContentDescription("おしらせ").performClick()
                compose.onNodeWithText("Anemo Essentialを獲得しました").assertIsDisplayed()
                compose.onNodeWithText("アップデートのXPを獲得しました").assertIsDisplayed()
                compose.onNodeWithText("QRコードのXPを獲得しました").assertIsDisplayed()
                compose.onNodeWithText("1200 XP").assertIsDisplayed()
                compose.onNodeWithText("500 XP").assertIsDisplayed()
                compose.onNodeWithTag("profile-tab-unread", useUnmergedTree = true).assertDoesNotExist()
                assertTrue(store.loadNotices().all { it.read })
                capture(context, "profile-reward-notices.png")
                capture(context, "profile-xp-notices.png")
                compose.onNodeWithContentDescription("戻る").performClick()
                compose.onNodeWithTag("profile-bell-unread", useUnmergedTree = true).assertDoesNotExist()
                it.recreate()
                compose.onNodeWithTag("profile-tab-unread", useUnmergedTree = true).assertDoesNotExist()
                compose.onNodeWithContentDescription("プロフィール").performClick()
                compose.onNodeWithContentDescription("おしらせ").performClick()
                compose.onNodeWithText("Anemo Essentialを獲得しました").assertIsDisplayed()
                assertEquals(xp, store.loadXp())
            }
        } finally {
            val editor = preferences.edit()
            original.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putStringSet(key, value) }
            if (originalXpNotices == null) editor.remove("xp_notices_v1")
            else editor.putString("xp_notices_v1", originalXpNotices)
            editor.commit()
        }
    }

    @Test fun levelDetailsAndNoticesKeepProfileActionsUsable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            compose.onNodeWithContentDescription("プロフィール").performClick()
            val totalXp = AppProgressStore(context).loadXp()
            val level = calculateAppLevel(totalXp)
            val levelLabel = "LV ${level.level}"
            compose.onAllNodesWithText(levelLabel).assertCountEquals(1)
            compose.onNodeWithText("レベル報酬").assertDoesNotExist()
            compose.onNodeWithText("総XP $totalXp").assertIsDisplayed()
            compose.onNodeWithText(if (level.level >= 60) "次のLVまで 0 XP（最大レベル）"
                else "次のLVまで ${level.pointsForNextLevel - level.pointsInLevel} XP").assertIsDisplayed()
            val levelBounds = compose.onNodeWithText(levelLabel).fetchSemanticsNode().boundsInRoot
            val editBounds = compose.onNodeWithText("プロフィール編集").fetchSemanticsNode().boundsInRoot
            val bell = compose.onNodeWithContentDescription("おしらせ")
            assertTrue("ベルは編集ボタンより上、レベルは編集ボタンより下に配置する",
                bell.fetchSemanticsNode().boundsInRoot.bottom < editBounds.top && levelBounds.top > editBounds.bottom)
            assertEquals(level.progress, compose.onNodeWithTag("profile-xp-progress")
                .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .001f)
            capture(context, "profile-level-layout.png")
            bell.performClick()
            if (AppProgressStore(context).loadNotices().isEmpty()) {
                compose.onNodeWithText("おしらせはありません").assertIsDisplayed()
            } else {
                compose.onNodeWithTag("reward-notices-list").assertExists()
            }
            capture(context, "profile-notices-empty.png")
            compose.onNodeWithContentDescription("戻る").performClick()
            compose.onNodeWithText(levelLabel).assertIsDisplayed()
            compose.onNodeWithText("プロフィール編集").performClick()
            compose.onNodeWithText("表示名").assertExists()
            assertEquals("表示と画面移動で保存XPを変更しない", totalXp, AppProgressStore(context).loadXp())
        }
    }

    private fun capture(context: Context, name: String) {
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
