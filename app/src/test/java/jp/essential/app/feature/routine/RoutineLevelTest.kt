package jp.essential.app.feature.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RoutineLevelTest {
    @Test
    fun `指定式から必要XPを四捨五入して求める`() {
        assertEquals(25, requiredRoutineXp(1))
        assertEquals(135, requiredRoutineXp(15))
        assertEquals(158, requiredRoutineXp(16))
        assertEquals(703, requiredRoutineXp(40))
        assertEquals(1760, requiredRoutineXp(50))
        assertEquals(15490, requiredRoutineXp(55))
        assertEquals(22671, requiredRoutineXp(59))
        assertEquals(0, requiredRoutineXp(60))
        assertEquals(0, requiredRoutineXp(61))
    }

    @Test
    fun `合計XPから現在レベルを計算する`() {
        assertEquals(1, calculateRoutineLevel(0).level)
        assertEquals(25, calculateRoutineLevel(0).pointsForNextLevel)
        assertEquals(2, calculateRoutineLevel(requiredRoutineXp(1)).level)
        assertEquals(33, calculateRoutineLevel(requiredRoutineXp(1)).pointsForNextLevel)
    }

    @Test
    fun `レベルは60を超えない`() {
        val result = calculateRoutineLevel(Int.MAX_VALUE)
        assertEquals(60, result.level)
        assertEquals(1f, result.progress)
    }

    @Test
    fun `日次と週次で期間キーを分ける`() {
        val date = LocalDate.of(2026, 9, 7)
        assertEquals("D:2026-09-07", routinePeriodKey(RoutineCadence.Daily, date))
        assertTrue(routinePeriodKey(RoutineCadence.Weekly, date).startsWith("W:2026-"))
    }

    @Test
    fun `イベントミッションは指定期間と開始時刻だけ有効`() {
        val task = RoutineTask(
            id = "event",
            title = "イベント",
            emoji = "★",
            cadence = RoutineCadence.Event,
            points = 10,
            eventStartDate = "2026-09-11",
            eventStartTime = "10:00",
            eventDurationDays = 3,
        )
        assertTrue(!isRoutineTaskActive(task, LocalDate.of(2026, 9, 10), LocalTime.NOON))
        assertTrue(!isRoutineTaskActive(task, LocalDate.of(2026, 9, 11), LocalTime.of(9, 59)))
        assertTrue(isRoutineTaskActive(task, LocalDate.of(2026, 9, 11), LocalTime.of(10, 0)))
        assertTrue(isRoutineTaskActive(task, LocalDate.of(2026, 9, 13), LocalTime.NOON))
        assertTrue(!isRoutineTaskActive(task, LocalDate.of(2026, 9, 14), LocalTime.NOON))
    }
}
