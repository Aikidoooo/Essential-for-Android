package jp.essential.app.feature.schedule

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleDocumentGeneratorInstrumentedTest {
    @Test
    fun 三形式を実ファイルへ生成できる() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outputDirectory = File(context.getExternalFilesDir(null), "schedule-generator-test").apply { mkdirs() }
        val pdfFile = File(outputDirectory, "schedule-preview.pdf")
        val textFile = File(outputDirectory, "schedule-preview.txt")
        val imageFile = File(outputDirectory, "schedule-preview.png")
        val data = previewData()

        ScheduleDocumentGenerator.writePdf(context, Uri.fromFile(pdfFile), data)
        ScheduleDocumentGenerator.writeText(context, Uri.fromFile(textFile), data)
        ScheduleDocumentGenerator.writeImage(context, Uri.fromFile(imageFile), data)

        assertTrue(pdfFile.length() > 1_000L)
        assertEquals("%PDF", pdfFile.readBytes().take(4).map(Byte::toInt).map(Int::toChar).joinToString(""))
        assertTrue(textFile.readText(Charsets.UTF_8).contains("海辺の一日旅行"))
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        assertEquals(1600, bitmap.width)
        assertTrue(bitmap.height >= 2263)
        bitmap.recycle()
    }

    private fun previewData() = ScheduleData(
        title = "海辺の一日旅行",
        purpose = "友人と景色と食事を楽しむ",
        place = "中央駅",
        startDate = "2026/09/20",
        endDate = "2026/09/21",
        attendees = listOf("山田", "佐藤", "鈴木"),
        notes = "歩きやすい靴と雨具を持参。予約時刻の15分前に集合。",
        entries = listOf(
            ScheduleEntry("08:30", "09:15", "45分", "中央駅", listOf("乗換駅"), "港町駅", "快速電車で移動"),
            ScheduleEntry("09:30", "11:00", "1時間30分", "港町駅", emptyList(), "海辺の市場", "朝市を見学して昼食を予約"),
            ScheduleEntry("11:30", "14:00", "2時間30分", "海辺の市場", emptyList(), "海浜公園", "散策と写真撮影"),
            ScheduleEntry("14:30", "16:00", "1時間30分", "海浜公園", emptyList(), "灯台カフェ", "休憩とお土産選び"),
            ScheduleEntry("16:30", "18:00", "1時間30分", "灯台カフェ", listOf("港町駅"), "中央駅", "電車で帰着"),
        ),
    )
}
