package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 操作の途中で画面と選択枠が同期することと、速度による演出の切り替えを確認する。 */
class TabNavigationMotionTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun position(tag: String = "main-tabs") = compose.onNodeWithTag(tag)
        .fetchSemanticsNode().config[TabMotionPosition]
    private fun progressive() = compose.onNodeWithTag("main-tabs")
        .fetchSemanticsNode().config[TabMotionProgressive]
    private fun settle() { compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }

    @Test fun fastSwipePlaysVisibleProgressiveMotionAfterSettlement() = withHome {
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("home-background-preview-root").performTouchInput {
                swipe(Offset(width * .75f, height * .42f), Offset(width * .51f, height * .42f), 170)
            }
            val widgets = SemanticsMatcher.keyIsDefined(ProgressiveMotionFraction) and
                hasAnyAncestor(hasTestTag("tab-motion-page-1"))
            fun fractions() = compose.onAllNodes(widgets, useUnmergedTree = true)
                .fetchSemanticsNodes().map { it.config[ProgressiveMotionFraction] }
            compose.mainClock.advanceTimeBy(32)
            assertTrue("整列中は遷移先の入場演出を待機する", fractions().isNotEmpty() && fractions().all { it == 0f })
            var elapsed = 0L
            while (kotlin.math.abs(position() - 1f) >= .001f && elapsed < 2000L) {
                compose.mainClock.advanceTimeBy(16)
                elapsed += 16L
            }
            assertEquals(1f, position(), .001f)
            assertTrue("画面が到着しても入場演出を消費していない", fractions().any { it < 1f })
            compose.mainClock.advanceTimeBy(160)
            assertTrue("到着後に項目が段階的に表示される", fractions().any { it > 0f && it < 1f })
        } finally { compose.mainClock.autoAdvance = true }
        settle()
        compose.onNodeWithContentDescription("機能一覧").assertIsSelected()
    }

    @Test fun directProfileTapSkipsFeaturesInBothDirections() = withHome {
        for (destination in listOf("プロフィール", "ホーム")) {
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithContentDescription(destination).performClick()
                repeat(12) {
                    compose.mainClock.advanceTimeBy(32)
                    compose.onNodeWithTag("tab-motion-page-1", useUnmergedTree = true).assertDoesNotExist()
                }
            } finally { compose.mainClock.autoAdvance = true }
            settle()
            compose.onNodeWithContentDescription(destination).assertIsSelected()
        }
    }

    @Test fun shorterModerateSwipeCommitsBeforeHalfway() = withHome {
        compose.onNodeWithTag("home-background-preview-root").performTouchInput {
            swipe(Offset(width * .75f, height * .42f), Offset(width * .51f, height * .42f), 170)
        }
        settle()
        assertEquals("半分未満の移動でも高速判定で次のタブへ進む", 1f, position(), .001f)
        assertTrue(progressive())
    }

    @Test fun slowScreenDragTracksFingerAndCanReverse() = withHome {
        val root = compose.onNodeWithTag("home-background-preview-root")
        root.performTouchInput {
            down(Offset(width * .8f, height * .42f))
            moveTo(Offset(width * .55f, height * .42f), 600)
        }
        assertTrue("離す前に画面が進む", position() in .2f.. .5f)
        assertEquals("選択枠と画面の同じ進行度", position(), position("liquid-glass-navigation"), .001f)
        assertFalse("低速ドラッグでは入場演出を重ねない", progressive())
        capture("tab-screen-held.png")
        val held = position()
        root.performTouchInput { moveTo(Offset(width * .72f, height * .42f), 500) }
        assertTrue("途中の方向反転へ追従する", position() < held)
        root.performTouchInput { up() }
        settle()
        assertEquals(0f, position(), .001f)
        root.performTouchInput {
            swipe(Offset(width * .86f, height * .42f), Offset(width * .14f, height * .42f), 1800)
        }
        settle()
        assertEquals(1f, position(), .001f)
        assertFalse(progressive())
        compose.onNodeWithContentDescription("機能一覧").assertIsSelected()
    }

    @Test fun slowBarDragMovesScreenAndFastDragUsesProgressiveMotion() = withHome {
        val bar = compose.onNodeWithTag("liquid-glass-navigation")
        bar.performTouchInput {
            down(Offset(width / 6f, height / 2f))
            moveTo(Offset(width * .3f, height / 2f), 600)
        }
        assertTrue("バー内ドラッグで離す前に画面が進む", position() in .2f.. .5f)
        assertEquals(position(), position("liquid-glass-navigation"), .001f)
        assertFalse(progressive())
        capture("tab-bar-held.png")
        bar.performTouchInput { moveTo(Offset(width * .52f, height / 2f), 900); up() }
        settle()
        assertEquals(1f, position(), .001f)
        assertFalse(progressive())
        bar.performTouchInput {
            swipe(Offset(width * .5f, height / 2f), Offset(width * .82f, height / 2f), 80)
        }
        settle()
        assertEquals(2f, position(), .001f)
        assertTrue("高速バー操作で入場演出を有効にする", progressive())
        compose.onNodeWithContentDescription("プロフィール").assertIsSelected()
    }

    @Test fun fastScreenSwipeAndRapidTabTapsKeepFinalSelection() = withHome {
        val root = compose.onNodeWithTag("home-background-preview-root")
        root.performTouchInput {
            swipe(Offset(width * .8f, height * .42f), Offset(width * .2f, height * .42f), 80)
        }
        settle()
        assertEquals("高速スワイプでも隣の一画面へ移る", 1f, position(), .001f)
        assertTrue(progressive())
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("ホーム").performClick()
            compose.mainClock.advanceTimeBy(32)
            val interruptedPosition = position()
            assertTrue("整列の途中を確認する", interruptedPosition > 0f && interruptedPosition < 1f)
            compose.onNodeWithContentDescription("プロフィール").performClick()
            val afterTap = position()
            assertTrue("再タップでも途中位置と慣性を維持する",
                afterTap > 0f && afterTap < 1f && kotlin.math.abs(afterTap - interruptedPosition) < .2f)
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithContentDescription("機能一覧").performClick()
        } finally { compose.mainClock.autoAdvance = true }
        settle()
        assertEquals(1f, position(), .001f)
        assertTrue("タップでは入場演出を有効にする", progressive())
        compose.onNodeWithContentDescription("機能一覧").assertIsSelected()
        compose.onNodeWithContentDescription("ホーム").performClick()
        settle()
        compose.onNodeWithTag("home-artwork-shortcut").assertExists()
    }

    @Test fun screenDragCanInterruptTabTapSettlement() = withHome {
        compose.onNodeWithContentDescription("機能一覧").performClick()
        settle()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("ホーム").performClick()
            compose.mainClock.advanceTimeBy(64)
            assertTrue("タップ後の整列途中をつかむ", position() > .5f && position() < 1f)
            compose.onNodeWithTag("home-background-preview-root").performTouchInput {
                swipe(Offset(width * .75f, height * .42f), Offset(width * .4f, height * .42f), 1600)
            }
        } finally { compose.mainClock.autoAdvance = true }
        settle()
        assertEquals("ドラッグの確定先を優先する", 1f, position(), .001f)
        assertFalse(progressive())
    }

    private fun capture(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private fun withHome(block: () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            compose.onNodeWithContentDescription("ホーム").performClick()
            settle()
            block()
        } finally { scenario.close() }
    }
}
