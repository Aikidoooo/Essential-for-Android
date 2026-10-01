package jp.essential.app.feature.mannaka

import java.io.Reader
import java.text.Normalizer

/** 同梱した全国駅データを検索する。通信や端末への展開は行わない。 */
internal class LocalStationIndex(private val stations: List<MeetingPoint>) {
    private val names = stations.map { normalize(it.name) }

    fun exact(input: String): List<MeetingPoint> {
        val query = normalize(input)
        if (query.isEmpty()) return emptyList()
        return stations.indices.asSequence().filter { names[it] == query }.take(8).map { stations[it] }.toList()
    }

    fun suggest(input: String): List<MeetingPoint> {
        val query = normalize(input)
        if (query.isEmpty()) return emptyList()
        return stations.indices.asSequence().filter { names[it].contains(query) }
            .sortedWith(compareBy<Int> { names[it] != query }.thenBy { !names[it].startsWith(query) }
                .thenBy { names[it].length }.thenBy { stations[it].detail })
            .take(8).map { stations[it] }.toList()
    }

    fun nearby(center: MeetingPoint): List<MeetingPoint> = stations.asSequence()
        .map { it to MeetingGeometry.distance(center, it) }
        .sortedBy { it.second }.take(128).map { it.first }.toList()

    companion object {
        fun read(reader: Reader): LocalStationIndex {
            val points = reader.buffered().useLines { lines -> lines.filter { it.isNotBlank() }.map { line ->
                val fields = line.split('\t', limit = 5)
                require(fields.size == 5) { "駅データの形式が不正です。" }
                val latitude = fields[2].toDouble()
                val longitude = fields[3].toDouble()
                require(latitude in 20.0..46.0 && longitude in 122.0..154.0)
                MeetingPoint(fields[1] + "駅", latitude, longitude, "mlit/" + fields[0], fields[4], "駅")
            }.toList() }
            require(points.isNotEmpty()) { "駅データがありません。" }
            return LocalStationIndex(points)
        }

        private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim().removeSuffix("駅").lowercase(java.util.Locale.ROOT)
    }
}
