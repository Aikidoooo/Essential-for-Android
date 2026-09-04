package jp.essential.app.feature.schedule

import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDocumentGeneratorTest {
    @Test
    fun 文章出力に入力項目を含める() {
        val data = ScheduleData(
            title = "京都旅行",
            purpose = "観光",
            place = "京都駅",
            startDate = "2026/09/20",
            endDate = "2026/09/21",
            attendees = listOf("山田", "佐藤", "鈴木"),
            notes = "雨具を持参",
            entries = listOf(
                ScheduleEntry(
                    departureTime = "09:00",
                    arrivalTime = "10:30",
                    duration = "1時間30分",
                    origin = "京都駅",
                    waypoints = listOf("祇園四条"),
                    destination = "清水寺",
                    details = "電車と徒歩で移動",
                ),
            ),
        )

        val output = ScheduleDocumentGenerator.toPlainText(data)

        assertTrue(output.contains("京都旅行"))
        assertTrue(output.contains("京都駅"))
        assertTrue(output.contains("2026/09/20 〜 2026/09/21"))
        assertTrue(output.contains("山田、佐藤、鈴木"))
        assertTrue(output.contains("雨具を持参"))
        assertTrue(output.contains("09:00 → 10:30"))
        assertTrue(output.contains("清水寺"))
        assertTrue(output.contains("京都駅 → 祇園四条 → 清水寺"))
        assertTrue(output.contains("所要 1時間30分"))
        assertTrue(output.contains("電車と徒歩で移動"))
    }
}
