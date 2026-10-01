package jp.essential.app.feature.textscan

import kotlin.math.roundToInt

/** 認識した文字片と、回転補正後の画像内の位置。 */
internal data class ScanTextPiece(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/** 等幅文字の空白と改行で、行・字下げ・横並びの位置関係を近似する。 */
internal fun formatScanLayout(pieces: List<ScanTextPiece>): String {
    val valid = pieces.filter { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }
    if (valid.isEmpty()) return ""
    val origin = valid.minOf { it.left }
    val widths = valid.map { (it.right - it.left).toFloat() / scanTextColumns(it.text).coerceAtLeast(1) }.sorted()
    val cellWidth = widths[widths.size / 2].coerceAtLeast(1f)
    val heights = valid.map { it.bottom - it.top }.sorted()
    val lineHeight = heights[heights.size / 2].coerceAtLeast(1)
    val rows = mutableListOf<MutableList<ScanTextPiece>>()
    valid.sortedBy { (it.top + it.bottom) / 2 }.forEach { piece ->
        val row = rows.lastOrNull()
        val center = (piece.top + piece.bottom) / 2f
        val anchor = row?.firstOrNull()
        val sameRow = anchor != null && kotlin.math.abs(center - (anchor.top + anchor.bottom) / 2f) <=
            minOf(lineHeight, piece.bottom - piece.top, anchor.bottom - anchor.top) * 0.5f
        if (sameRow) row!!.add(piece) else rows.add(mutableListOf(piece))
    }
    return buildString {
        var previousBottom: Int? = null
        rows.forEach { row ->
            if (previousBottom != null) {
                val gap = row.minOf { it.top } - previousBottom!!
                append("\n".repeat(1 + (gap.toFloat() / (lineHeight * 1.4f)).roundToInt().coerceIn(0, 8)))
            }
            var column = 0
            row.sortedBy { it.left }.forEach { piece ->
                val target = ((piece.left - origin) / cellWidth).roundToInt().coerceIn(0, 512)
                val spaces = (target - column).coerceAtLeast(if (column > 0) 1 else 0)
                append(" ".repeat(spaces))
                append(piece.text)
                column += spaces + scanTextColumns(piece.text)
            }
            previousBottom = row.maxOf { it.bottom }
        }
    }
}

/** 日本語の全角文字を2桁として扱い、英数字と混在した列のずれを抑える。 */
private fun scanTextColumns(text: String): Int = text.codePoints().toArray().sumOf { code ->
    if (code in 0x1100..0x115F || code in 0x2E80..0xA4CF || code in 0xAC00..0xD7A3 ||
        code in 0xF900..0xFAFF || code in 0xFE10..0xFE6F || code in 0xFF01..0xFF60 || code >= 0x1F300) 2 else 1
}
