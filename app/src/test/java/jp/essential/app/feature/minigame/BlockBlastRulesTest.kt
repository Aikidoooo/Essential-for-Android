package jp.essential.app.feature.minigame

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockBlastRulesTest {
    @Test
    fun `直近5ターン以内のクリアで倍率が上がる`() {
        val first = BlockBlastRules.scoreMove(
            shapeSize = 1,
            clearedLines = 1,
            previousClearStreak = 0,
            currentMultiplier = 1,
            currentMultiplierTurnsRemaining = 0,
            previousTurnsSinceLastClear = 6,
        )
        var state = first
        repeat(5) {
            state = BlockBlastRules.scoreMove(
                shapeSize = 1,
                clearedLines = 0,
                previousClearStreak = state.clearStreak,
                currentMultiplier = state.multiplier,
                currentMultiplierTurnsRemaining = state.multiplierTurnsRemaining,
                previousTurnsSinceLastClear = state.turnsSinceLastClear,
            )
        }
        val second = BlockBlastRules.scoreMove(
            shapeSize = 1,
            clearedLines = 1,
            previousClearStreak = state.clearStreak,
            currentMultiplier = state.multiplier,
            currentMultiplierTurnsRemaining = state.multiplierTurnsRemaining,
            previousTurnsSinceLastClear = state.turnsSinceLastClear,
        )

        assertEquals(2, second.multiplier)
        assertEquals(5, second.multiplierTurnsRemaining)
        assertEquals(2 * (1 + 10), second.points)
    }

    @Test
    fun `6ターン空くと倍率条件が切れる`() {
        val result = BlockBlastRules.scoreMove(
            shapeSize = 1,
            clearedLines = 1,
            previousClearStreak = 1,
            currentMultiplier = 1,
            currentMultiplierTurnsRemaining = 0,
            previousTurnsSinceLastClear = 6,
        )

        assertEquals(1, result.multiplier)
        assertEquals(1, result.clearStreak)
    }
}
