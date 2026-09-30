package jp.essential.app.feature.textscan

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCodeBlockTest {
    @Test
    fun preservesJapaneseLineBreaksAndIndentation() {
        val text = "文字スキャン\n    val count = 10\n\n完了"
        assertEquals("```\n$text\n```", textAsCodeBlock(text))
    }

    @Test
    fun keepsEmbeddedCodeFencesInsideOneBlock() {
        val text = "説明\n```kotlin\nval count = 10\n```"
        assertEquals("````\n$text\n````", textAsCodeBlock(text))
    }
}
