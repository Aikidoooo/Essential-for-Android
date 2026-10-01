package jp.essential.app.feature.mannaka

import org.junit.Assert.*
import org.junit.Test

class MeetingGeometryTest {
    @Test fun midpointAcrossDateLine() {
        val point = MeetingGeometry.center(listOf(MeetingPoint("A", 0.0, 179.0), MeetingPoint("B", 0.0, -179.0)))
        assertEquals(180.0, kotlin.math.abs(point.longitude), 0.0001)
        assertEquals(0.0, point.latitude, 0.0001)
    }
    @Test fun repeatedStationStillCountsEachParticipant() {
        val a = MeetingPoint("A", 35.0, 139.0)
        val b = MeetingPoint("B", 35.0, 140.0)
        val center = MeetingGeometry.center(List(100) { a } + b)
        assertTrue(MeetingGeometry.distance(center, a) < MeetingGeometry.distance(center, b))
        assertEquals(0.0, MeetingGeometry.distance(a, a), 0.00001)
    }
    @Test fun balancedMeetingRanksBeforeDistantStation() {
        val origins = listOf(MeetingPoint("A", 35.0, 139.0), MeetingPoint("B", 35.0, 140.0))
        val middle = MeetingPoint("中央", 35.0, 139.5)
        assertEquals(middle, MeetingGeometry.rank(listOf(origins.first(), middle), origins).first())
    }
    @Test fun longestDistanceTakesPriorityOverLargeGroupsAverage() {
        val first = MeetingPoint("近い側", 0.0, 0.0)
        val far = MeetingPoint("遠い側", 0.0, 0.046)
        val balanced = MeetingPoint("最大距離が短い駅", 0.038, 0.023)
        assertEquals(balanced, MeetingGeometry.rank(listOf(first, balanced), List(100) { first } + far).first())
    }
    @Test fun adjacentRepresentationsOfSameStationDoNotFillAllCandidates() {
        val first = MeetingPoint("中央駅", 35.0, 139.5)
        val duplicate = first.copy(name = "中央", latitude = 35.0001)
        val next = first.copy(name = "次の駅", latitude = 35.001)
        val ranked = MeetingGeometry.rank(listOf(first, duplicate, next), listOf(first))
        assertEquals(listOf(first, next), ranked)
    }
}
