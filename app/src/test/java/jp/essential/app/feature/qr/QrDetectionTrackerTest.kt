package jp.essential.app.feature.qr

import org.junit.Assert.*
import org.junit.Test

class QrDetectionTrackerTest {
    @Test fun 新しいコードは即反映して同一コードは通知しない() {
        val tracker = QrDetectionTracker()
        assertTrue(tracker.update("A", 1000))
        assertFalse(tracker.update("A", 1010))
        assertTrue(tracker.update("B", 1020))
        assertEquals("B", tracker.value)
        assertTrue(tracker.update("A", 1030))
    }

    @Test fun 短い検出抜けを吸収し消失後は再認識できる() {
        val tracker = QrDetectionTracker()
        tracker.update("A", 1000)
        assertFalse(tracker.update(null, 1100))
        assertEquals("A", tracker.value)
        assertTrue(tracker.update(null, 1350))
        assertNull(tracker.value)
        assertTrue(tracker.update("A", 1360))
    }
}
