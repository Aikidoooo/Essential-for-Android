package jp.essential.app.feature.tuning

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class PitchDetectorTest {
    @Test fun detectsInstrumentRangeAndDetunedNotes() {
        listOf(41.203, 73.416, 82.407, 110.0, 261.626, 329.628, 440.0, 659.255, 880.0).forEach { frequency ->
            val samples = ShortArray(4096) { (12000 * sin(2 * PI * frequency * it / 22050)).toInt().toShort() }
            val actual = detectPitch(samples, 22050)
            assertNotNull("音程 $frequency", actual)
            assertTrue("$frequency -> $actual", abs(centsFrom(actual!!, frequency)) < 3)
        }
    }
    @Test fun rejectsSilenceAndNoise() {
        assertNull(detectPitch(ShortArray(4096), 22050))
        val random = Random(42)
        assertNull(detectPitch(ShortArray(4096) { random.nextInt(-12000, 12000).toShort() }, 22050))
    }
    @Test fun findsFundamentalWithHarmonics() {
        val frequency = 82.407
        val samples = ShortArray(4096) {
            val phase = 2 * PI * frequency * it / 22050
            (4000 * sin(phase) + 9000 * sin(2 * phase) + 5000 * sin(3 * phase)).toInt().toShort()
        }
        val actual = detectPitch(samples, 22050)
        assertNotNull(actual)
        assertTrue(abs(centsFrom(actual!!, frequency)) < 5)
    }
    @Test fun presetsAndReferencePitch() {
        assertEquals(440.0, noteFrequency(69), .001)
        assertEquals(442.0, noteFrequency(69, 442), .001)
        assertEquals("E2", noteLabel(40))
        assertEquals("G4", noteLabel(tuningPresets[3].midiNotes.first()))
        assertEquals(100.0, centsFrom(noteFrequency(70), noteFrequency(69)), .001)
    }
}
