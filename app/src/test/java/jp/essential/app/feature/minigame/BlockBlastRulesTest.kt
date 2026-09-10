package jp.essential.app.feature.minigame

import org.junit.Assert.*
import org.junit.Test

class BlockBlastRulesTest {
    @Test fun clearsCrossingRowAndColumnTogether() {
        val board = MutableList(64) { 0 }
        for (i in 1..7) { board[i] = 1; board[i * 8] = 1 }
        val (result, lines) = BlockBlastRules.place(board, 0, 0, 0)
        assertEquals(2, lines)
        assertTrue(result.all { it == 0 })
        assertEquals(14, board.count { it != 0 })
    }
    @Test fun rejectsOverflowOverlapAndUsedPieces() {
        val board = MutableList(64) { 0 }
        board[0] = 1
        assertFalse(BlockBlastRules.fits(board, 0, 0, 0))
        assertFalse(BlockBlastRules.fits(board, 1, 7, 0))
        assertFalse(BlockBlastRules.fits(board, -1, 2, 2))
        assertTrue(BlockBlastRules.fits(board, 5, 6, 6))
    }
    @Test fun detectsWhenNoRemainingPieceFits() {
        val board = MutableList(64) { 1 }
        board[63] = 0
        assertFalse(BlockBlastRules.canPlay(board, listOf(1, 5, -1)))
        assertTrue(BlockBlastRules.canPlay(board, listOf(-1, 0, -1)))
    }

    @Test fun snapsToNearestAvailableGap() {
        val board = MutableList(64) { 1 }
        board[3 * 8 + 3] = 0
        assertEquals(3 to 3, BlockBlastRules.nearestFit(board, 0, 4, 3))
        assertNull(BlockBlastRules.nearestFit(board, 0, 7, 7, maxDistance = 1f))
    }

    @Test fun appliesTimedMultiplierAfterConsecutiveClears() {
        val first = BlockBlastRules.scoreMove(1, 1, 0, 1, 0L, 1_000L)
        assertEquals(1, first.multiplier)
        assertEquals(11, first.points)

        val second = BlockBlastRules.scoreMove(1, 1, first.clearStreak, first.multiplier, first.multiplierEndsAt, 2_000L)
        assertEquals(2, second.multiplier)
        assertEquals(22, second.points)
        assertEquals(12_000L, second.multiplierEndsAt)

        val third = BlockBlastRules.scoreMove(1, 1, second.clearStreak, second.multiplier, second.multiplierEndsAt, 3_000L)
        assertEquals(3, third.multiplier)
        assertEquals(33, third.points)
        assertEquals(13_000L, third.multiplierEndsAt)

        val noClear = BlockBlastRules.scoreMove(2, 0, third.clearStreak, third.multiplier, third.multiplierEndsAt, 4_000L)
        assertEquals(0, noClear.clearStreak)
        assertEquals(3, noClear.multiplier)
        assertEquals(6, noClear.points)
    }
}
