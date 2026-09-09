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
}
