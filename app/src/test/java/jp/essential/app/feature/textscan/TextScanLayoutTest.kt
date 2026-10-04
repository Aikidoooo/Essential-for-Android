package jp.essential.app.feature.textscan

import org.junit.Assert.assertEquals
import org.junit.Test

class TextScanLayoutTest {
    @Test fun restoresIndentationAndColumnsRegardlessOfRecognitionOrder() {
        val pieces = listOf(
            ScanTextPiece("next", 40, 30, 80, 50),
            ScanTextPiece("right", 100, 0, 150, 20),
            ScanTextPiece("left", 0, 0, 40, 20),
        )
        assertEquals("left right\nnext", formatScanLayout(pieces))
    }

    @Test fun preservesBlankRowsAndMixedJapaneseColumns() {
        assertEquals("日本語 ABC\n\nend", formatScanLayout(listOf(
            ScanTextPiece("日本語", 0, 0, 60, 20),
            ScanTextPiece("ABC", 80, 0, 110, 20),
            ScanTextPiece("end", 40, 60, 70, 80),
        )))
    }

    @Test fun ignoresMissingOrEmptyBounds() {
        assertEquals("", formatScanLayout(listOf(ScanTextPiece("", 0, 0, 0, 0))))
    }

    @Test fun joinsTouchingPiecesAndNormalizesSpaces() {
        assertEquals("日本語 A B", formatScanLayout(listOf(
            ScanTextPiece("日本", 0, 0, 20, 20),
            ScanTextPiece("語　A  B", 20, 0, 80, 20),
        )))
    }
}
