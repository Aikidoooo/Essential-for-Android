package jp.essential.app.feature.mannaka

import org.junit.Assert.assertEquals
import org.junit.Test

class MeetingTimeTest {
    @Test fun shiftsWrapWithinOneDay() {
        assertEquals(23 * 60 + 55, shiftMeetingTime(0, -5))
        assertEquals(25, shiftMeetingTime(23 * 60 + 55, 30))
        assertEquals(19 * 60 + 5, shiftMeetingTime(19 * 60, 5))
    }

    @Test fun formatsMidnightAndSingleDigits() {
        assertEquals("00:00", meetingTimeLabel(0))
        assertEquals("09:05", meetingTimeLabel(9 * 60 + 5))
        assertEquals("19:00", meetingTimeLabel(19 * 60))
    }
}
