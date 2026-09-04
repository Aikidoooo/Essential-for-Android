package jp.essential.app.feature.schedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.OutputStreamWriter
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScheduleEntry(
    val departureTime: String = "",
    val arrivalTime: String = "",
    val duration: String = "",
    val origin: String = "",
    val waypoints: List<String> = emptyList(),
    val destination: String = "",
    val details: String = "",
)

data class ScheduleData(
    val title: String,
    val purpose: String,
    val place: String,
    val startDate: String,
    val endDate: String,
    val attendees: List<String>,
    val notes: String,
    val entries: List<ScheduleEntry> = emptyList(),
)

object ScheduleDocumentGenerator {
    suspend fun writeText(context: Context, uri: Uri, data: ScheduleData) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer -> writer.write(toPlainText(data)) }
        } ?: error("文章の保存先を開けませんでした")
    }

    suspend fun writePdf(context: Context, uri: Uri, data: ScheduleData) = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            val entryPages = normalizedEntries(data).chunked(7)
            entryPages.forEachIndexed { pageIndex, entries ->
                val pageInfo = PdfDocument.PageInfo.Builder(1240, 1754, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                drawTablePage(
                    page.canvas,
                    pageInfo.pageWidth,
                    pageInfo.pageHeight,
                    data,
                    entries,
                    pageIndex + 1,
                    entryPages.size,
                )
                document.finishPage(page)
            }
            context.contentResolver.openOutputStream(uri, "w")?.use(document::writeTo)
                ?: error("PDFの保存先を開けませんでした")
        } finally {
            document.close()
        }
    }

    suspend fun writeImage(context: Context, uri: Uri, data: ScheduleData) = withContext(Dispatchers.IO) {
        val entries = normalizedEntries(data)
        val imageHeight = max(2263, 770 + entries.size * 245 + if (data.notes.isBlank()) 120 else 360)
            .coerceAtMost(7200)
        val bitmap = Bitmap.createBitmap(1600, imageHeight, Bitmap.Config.ARGB_8888)
        try {
            drawVerticalImage(Canvas(bitmap), bitmap.width, bitmap.height, data, entries)
            context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    error("画像の書き出しに失敗しました")
                }
            } ?: error("画像の保存先を開けませんでした")
        } finally {
            bitmap.recycle()
        }
    }

    fun toPlainText(data: ScheduleData): String = buildString {
        appendLine(data.title.ifBlank { "行程表" })
        appendLine("=".repeat(28))
        appendLine("目的: ${data.purpose.ifBlank { "未入力" }}")
        appendLine("集合場所・主な場所: ${data.place.ifBlank { "未入力" }}")
        appendLine("日付範囲: ${dateRange(data)}")
        appendLine("参加者: ${data.attendees.joinToString("、").ifBlank { "未入力" }}")
        appendLine()
        appendLine("行程表")
        normalizedEntries(data).forEachIndexed { index, entry ->
            val time = listOf(entry.departureTime, entry.arrivalTime)
                .filter(String::isNotBlank)
                .joinToString(" → ")
                .ifBlank { "時刻未入力" }
            val duration = entry.duration.takeIf(String::isNotBlank)?.let { "（所要 $it）" }.orEmpty()
            appendLine("${index + 1}. $time $duration")
            appendLine("   ${routeText(entry)}")
            appendLine("   ${entry.details.ifBlank { "内容なし" }}")
        }
        appendLine()
        appendLine("注意事項・メモ")
        appendLine(data.notes.ifBlank { "なし" })
        appendLine()
        appendLine("Essential 行程表ジェネレーター")
    }

    private fun normalizedEntries(data: ScheduleData): List<ScheduleEntry> {
        val entered = data.entries.filterNot {
            it.departureTime.isBlank() && it.arrivalTime.isBlank() && it.duration.isBlank() &&
                it.origin.isBlank() && it.waypoints.all(String::isBlank) &&
                it.destination.isBlank() && it.details.isBlank()
        }
        return entered.ifEmpty {
            listOf(
                ScheduleEntry(
                    origin = data.place,
                    destination = data.place,
                    details = data.purpose,
                ),
            )
        }
    }

    private fun drawTablePage(
        canvas: Canvas,
        width: Int,
        height: Int,
        data: ScheduleData,
        entries: List<ScheduleEntry>,
        pageNumber: Int,
        pageCount: Int,
    ) {
        val scale = width / 1240f
        val margin = 78f * scale
        val contentRight = width - margin
        val regular = textPaint(28f * scale, Color.rgb(28, 28, 27))
        val bold = textPaint(30f * scale, Color.rgb(20, 20, 19), true)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(80, 79, 73)
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
        }
        canvas.drawColor(Color.WHITE)

        bold.textSize = 48f * scale
        drawCenteredText(canvas, data.title.ifBlank { "行程表" }, width / 2f, 112f * scale, bold)
        regular.textSize = 25f * scale
        bold.textSize = 25f * scale
        canvas.drawText("日付", margin, 190f * scale, bold)
        canvas.drawText(dateRange(data), margin + 92f * scale, 190f * scale, regular)
        canvas.drawText("参加者", width * 0.53f, 190f * scale, bold)
        canvas.drawText(data.attendees.joinToString("、").ifBlank { "未入力" }, width * 0.65f, 190f * scale, regular)
        canvas.drawText("目的", margin, 245f * scale, bold)
        canvas.drawText(data.purpose.ifBlank { "未入力" }, margin + 92f * scale, 245f * scale, regular)
        canvas.drawText("集合", margin, 300f * scale, bold)
        canvas.drawText(data.place.ifBlank { "未入力" }, margin + 92f * scale, 300f * scale, regular)

        val tableTop = 365f * scale
        val headerHeight = 72f * scale
        val rowHeight = 150f * scale
        val timeWidth = 220f * scale
        val destinationWidth = 340f * scale
        val x1 = margin + timeWidth
        val x2 = x1 + destinationWidth
        val tableBottom = tableTop + headerHeight + rowHeight * entries.size
        canvas.drawRect(margin, tableTop, contentRight, tableBottom, line)
        canvas.drawLine(x1, tableTop, x1, tableBottom, line)
        canvas.drawLine(x2, tableTop, x2, tableBottom, line)
        canvas.drawLine(margin, tableTop + headerHeight, contentRight, tableTop + headerHeight, line)
        entries.indices.forEach { index ->
            val y = tableTop + headerHeight + rowHeight * (index + 1)
            canvas.drawLine(margin, y, contentRight, y, line)
        }

        bold.textSize = 27f * scale
        drawCenteredText(canvas, "発着時間", margin + timeWidth / 2f, tableTop + 46f * scale, bold)
        drawCenteredText(canvas, "目的地", x1 + destinationWidth / 2f, tableTop + 46f * scale, bold)
        drawCenteredText(canvas, "備考・内容", x2 + (contentRight - x2) / 2f, tableTop + 46f * scale, bold)
        entries.forEachIndexed { index, entry ->
            val top = tableTop + headerHeight + rowHeight * index
            regular.textSize = 26f * scale
            val time = listOf(entry.departureTime, entry.arrivalTime)
                .filter(String::isNotBlank)
                .joinToString("\n↓\n")
                .ifBlank { "未入力" }
            val timeWithDuration = listOf(time, entry.duration.takeIf(String::isNotBlank)?.let { "所要 $it" })
                .filterNotNull().joinToString("\n")
            drawWrappedText(canvas, timeWithDuration, margin + 18f * scale, top + 42f * scale, timeWidth - 36f * scale, regular, 1.12f, 4)
            drawWrappedText(
                canvas,
                routeText(entry),
                x1 + 18f * scale,
                top + 42f * scale,
                destinationWidth - 36f * scale,
                regular,
                1.2f,
                3,
            )
            drawWrappedText(
                canvas,
                entry.details.ifBlank { "なし" },
                x2 + 18f * scale,
                top + 42f * scale,
                contentRight - x2 - 36f * scale,
                regular,
                1.2f,
                4,
            )
        }

        if (pageNumber == pageCount) {
            var notesY = tableBottom + 65f * scale
            bold.textSize = 28f * scale
            canvas.drawText("注意事項・メモ", margin, notesY, bold)
            notesY += 42f * scale
            regular.textSize = 25f * scale
            drawWrappedText(canvas, data.notes.ifBlank { "なし" }, margin, notesY, contentRight - margin, regular, 1.3f, 5)
        }
        regular.textSize = 20f * scale
        regular.color = Color.rgb(100, 100, 96)
        drawCenteredText(canvas, "Essential 行程表ジェネレーター  $pageNumber / $pageCount", width / 2f, height - 45f * scale, regular)
    }

    private fun drawVerticalImage(
        canvas: Canvas,
        width: Int,
        height: Int,
        data: ScheduleData,
        entries: List<ScheduleEntry>,
    ) {
        val scale = width / 1600f
        val margin = 96f * scale
        val right = width - margin
        val text = textPaint(34f * scale, Color.rgb(71, 69, 62))
        val bold = textPaint(38f * scale, Color.rgb(52, 50, 45), true)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(191, 187, 173)
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
        }
        canvas.drawColor(Color.rgb(249, 247, 240))
        canvas.drawRect(48f * scale, 48f * scale, width - 48f * scale, height - 72f * scale, border)
        drawCornerMarks(canvas, width, height, scale, border)

        var y = 150f * scale
        bold.textSize = 66f * scale
        y = drawWrappedText(canvas, data.title.ifBlank { "行程表" }, margin, y, right - margin, bold, 1.18f, 2)
        y += 30f * scale
        text.textSize = 28f * scale
        text.color = Color.rgb(116, 111, 99)
        canvas.drawText(
            listOf(dateRange(data), data.attendees.joinToString("、")).filter(String::isNotBlank).joinToString("  ・  "),
            margin,
            y,
            text,
        )
        y += 72f * scale
        if (data.purpose.isNotBlank()) {
            text.textSize = 32f * scale
            text.color = Color.rgb(82, 79, 70)
            y = drawWrappedText(canvas, data.purpose, margin, y, right - margin, text, 1.25f, 3)
            y += 42f * scale
        }

        val colors = intArrayOf(
            Color.rgb(188, 221, 81), Color.rgb(255, 176, 74), Color.rgb(125, 190, 210),
            Color.rgb(231, 142, 106), Color.rgb(156, 171, 225),
        )
        entries.forEachIndexed { index, entry ->
            val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[index % colors.size] }
            canvas.drawCircle(margin + 24f * scale, y - 8f * scale, 24f * scale, markerPaint)
            val numberPaint = textPaint(24f * scale, Color.WHITE, true).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText("${index + 1}", margin + 24f * scale, y, numberPaint)
            bold.textSize = 38f * scale
            bold.color = Color.rgb(64, 62, 56)
            y = drawWrappedText(
                canvas,
                routeText(entry),
                margin + 76f * scale,
                y,
                right - margin - 76f * scale,
                bold,
                1.18f,
                2,
            )
            y += 14f * scale
            text.textSize = 28f * scale
            text.color = Color.rgb(126, 120, 106)
            val time = listOf(entry.departureTime, entry.arrivalTime)
                .filter(String::isNotBlank)
                .joinToString("  →  ")
                .ifBlank { "時刻未入力" }
            val timeWithDuration = entry.duration.takeIf(String::isNotBlank)?.let { "$time  ・  所要 $it" } ?: time
            canvas.drawText(timeWithDuration, margin + 76f * scale, y, text)
            y += 48f * scale
            text.textSize = 32f * scale
            text.color = Color.rgb(87, 83, 74)
            y = drawWrappedText(
                canvas,
                entry.details.ifBlank { "内容なし" },
                margin + 76f * scale,
                y,
                right - margin - 76f * scale,
                text,
                1.25f,
                3,
            )
            y += 76f * scale
        }
        if (data.notes.isNotBlank() && y < height - 250f * scale) {
            bold.textSize = 32f * scale
            canvas.drawText("注意事項・メモ", margin, y, bold)
            y += 48f * scale
            text.textSize = 29f * scale
            text.color = Color.rgb(91, 87, 77)
            drawWrappedText(canvas, data.notes, margin, y, right - margin, text, 1.3f, 6)
        }
        text.textSize = 22f * scale
        text.color = Color.rgb(142, 136, 122)
        text.textAlign = Paint.Align.CENTER
        canvas.drawText("Essentialで作成しました", width / 2f, height - 100f * scale, text)
    }

    private fun dateRange(data: ScheduleData): String = when {
        data.startDate.isBlank() -> "未入力"
        data.endDate.isBlank() || data.startDate == data.endDate -> data.startDate
        else -> "${data.startDate} 〜 ${data.endDate}"
    }

    private fun routeText(entry: ScheduleEntry): String = buildList {
        if (entry.origin.isNotBlank()) add(entry.origin)
        addAll(entry.waypoints.filter(String::isNotBlank))
        if (entry.destination.isNotBlank()) add(entry.destination)
    }.joinToString(" → ").ifBlank { "経路未入力" }

    private fun drawCornerMarks(canvas: Canvas, width: Int, height: Int, scale: Float, paint: Paint) {
        val inset = 38f * scale
        val length = 34f * scale
        listOf(
            floatArrayOf(inset, inset + length, inset, inset, inset + length, inset),
            floatArrayOf(width - inset - length, inset, width - inset, inset, width - inset, inset + length),
            floatArrayOf(inset, height - inset - length, inset, height - inset, inset + length, height - inset),
            floatArrayOf(width - inset - length, height - inset, width - inset, height - inset, width - inset, height - inset - length),
        ).forEach { points ->
            canvas.drawLine(points[0], points[1], points[2], points[3], paint)
            canvas.drawLine(points[2], points[3], points[4], points[5], paint)
        }
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun drawCenteredText(canvas: Canvas, value: String, centerX: Float, y: Float, paint: Paint) {
        val original = paint.textAlign
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(value, centerX, y, paint)
        paint.textAlign = original
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineSpacing: Float,
        maxLines: Int = Int.MAX_VALUE,
    ): Float {
        val lines = mutableListOf<String>()
        text.lineSequence().forEach { paragraph ->
            var current = ""
            paragraph.forEach { character ->
                val next = current + character
                if (paint.measureText(next) > maxWidth && current.isNotEmpty()) {
                    lines += current
                    current = character.toString()
                } else {
                    current = next
                }
            }
            lines += current.ifBlank { " " }
        }
        val visibleLines = lines.take(maxLines)
        val lineHeight = paint.textSize * lineSpacing
        visibleLines.forEachIndexed { index, line ->
            val rendered = if (index == maxLines - 1 && lines.size > maxLines) "$line…" else line
            canvas.drawText(rendered, x, startY + index * lineHeight, paint)
        }
        return startY + visibleLines.size * lineHeight
    }
}
