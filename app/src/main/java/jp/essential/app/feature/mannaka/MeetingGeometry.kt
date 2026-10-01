package jp.essential.app.feature.mannaka

import kotlin.math.*

data class MeetingPoint(val name: String, val latitude: Double, val longitude: Double, val id: String = "", val detail: String = "", val category: String = "")

object MeetingGeometry {
    fun center(points: List<MeetingPoint>): MeetingPoint {
        require(points.isNotEmpty())
        val x = points.sumOf { cos(Math.toRadians(it.latitude)) * cos(Math.toRadians(it.longitude)) }
        val y = points.sumOf { cos(Math.toRadians(it.latitude)) * sin(Math.toRadians(it.longitude)) }
        val z = points.sumOf { sin(Math.toRadians(it.latitude)) }
        return MeetingPoint("みんなの中間地点", Math.toDegrees(atan2(z, sqrt(x * x + y * y))), Math.toDegrees(atan2(y, x)))
    }

    fun distance(a: MeetingPoint, b: MeetingPoint): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        return 6371.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun rank(stations: List<MeetingPoint>, origins: List<MeetingPoint>): List<MeetingPoint> {
        require(origins.isNotEmpty())
        // 距離を比較のたびに再計算せず、各駅の評価を一度だけ求める。
        val ranked = stations.map { station ->
            var maximum = 0.0
            var total = 0.0
            origins.forEach { origin ->
                val kilometers = distance(origin, station)
                maximum = max(maximum, kilometers)
                total += kilometers
            }
            Triple(station, maximum, total / origins.size)
        }.sortedWith(compareBy<Triple<MeetingPoint, Double, Double>> { it.second }.thenBy { it.third })
        return buildList {
            for ((station) in ranked) {
                // 同じ駅の別路線やOSM要素が候補の枠を占めることを避ける。
                if (none { it.name.removeSuffix("駅") == station.name.removeSuffix("駅") && distance(it, station) < 0.2 }) add(station)
                if (size == 3) break
            }
        }
    }
}
