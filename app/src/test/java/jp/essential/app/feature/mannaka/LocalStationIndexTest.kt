package jp.essential.app.feature.mannaka

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.StringReader

class LocalStationIndexTest {
    private fun fixture() = LocalStationIndex.read(StringReader(
        "1\t町屋\t35.742\t139.781\t東京メトロ\n" +
        "2\t町屋二丁目\t35.743\t139.776\t都電\n" +
        "3\tＡ駅前\t35.72\t139.79\t試験路線\n" +
        "4\t札幌\t43.068\t141.351\t北海道\n"
    ))

    @Test fun normalizedSearchRanksExactBeforePrefix() {
        val index = fixture()
        assertEquals("町屋駅", index.suggest(" 町屋駅 ").first().name)
        assertEquals(2, index.suggest("町屋").size)
        assertEquals("Ａ駅前駅", index.suggest("A駅前").single().name)
        assertTrue(index.suggest(" ").isEmpty())
    }

    @Test fun nearestCandidatesWorkOutsideTokyo() {
        assertEquals("札幌駅", fixture().nearby(MeetingPoint("中心", 43.06, 141.35)).first().name)
    }

    @Test fun bundledDatasetContainsNationwideAndReferenceStations() {
        val file = listOf(File("src/main/assets/mannaka/stations.tsv"), File("app/src/main/assets/mannaka/stations.tsv"))
            .first { it.isFile }
        assertTrue(file.length() < 2_000_000)
        val index = file.reader(Charsets.UTF_8).use(LocalStationIndex::read)
        listOf("町屋", "両国", "札幌", "博多", "那覇空港", "新函館北斗").forEach {
            assertFalse("駅データに $it がありません。", index.exact(it).isEmpty())
        }
        val origins = index.exact("町屋").take(1) + index.exact("両国").take(1)
        val candidates = MeetingGeometry.rank(index.nearby(MeetingGeometry.center(origins)), origins)
        assertEquals(3, candidates.size)
        assertTrue(candidates.all { it.id.startsWith("mlit/") })
    }
}
