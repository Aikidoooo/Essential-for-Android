package jp.essential.app.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class AppProgressRulesTest {
    @Test
    fun `Block Blastは最終スコアの10分の1を四捨五入する`() {
        assertEquals(12, blockBlastXp(123))
        assertEquals(13, blockBlastXp(125))
        assertEquals(0, blockBlastXp(0))
    }

    @Test
    fun `マインスイーパーは盤面サイズごとのXPを返す`() {
        assertEquals(35, minesweeperXp(8, 8))
        assertEquals(42, minesweeperXp(9, 9))
        assertEquals(50, minesweeperXp(12, 12))
        assertEquals(100, minesweeperXp(12, 24))
        assertEquals(0, minesweeperXp(10, 10))
    }
}
