package jp.essential.app.feature.downloader

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.core.withMetadataRetriever
import jp.essential.app.feature.files.FfmpegRunner
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class TwitterFixtureInstrumentedTest {
    @Test fun providedSamplesRetainAudioAndLateMotion() {
        // 投稿データはリポジトリへ同梱しない。明示したローカル検証時のみ使用する。
        assumeTrue(InstrumentationRegistry.getArguments().getString("twitterFixtures") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtures = File(context.filesDir, "twitter-repro")
        for ((id, expectedDuration) in listOf("2089651625770323968" to 5505L, "2089931529581719552" to 10033L)) {
            val source = File(fixtures, "$id-fixed.mp4")
            assertTrue("検証用動画が必要です: $id", source.isFile)
            val work = File(context.cacheDir, "twitter-fixture-${System.nanoTime()}").apply { mkdirs() }
            try {
                source.copyTo(File(work, source.name))
                val output = YtDlpDownloadEngine(context).mergeDownloadedStreams(work,
                    DownloaderSelection("https://x.com/i/status/$id", DownloadMediaType.Video))
                FfmpegRunner.execute(context, listOf("-v", "error", "-xerror", "-i", output.absolutePath, "-f", "null", "-"))
                withMetadataRetriever { retriever ->
                    retriever.setDataSource(output.absolutePath)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    assertTrue(kotlin.math.abs(duration - expectedDuration) < 200)
                    assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                    val middle = retriever.getFrameAtTime(2_000_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
                    val late = retriever.getFrameAtTime((expectedDuration - 600) * 1000, MediaMetadataRetriever.OPTION_CLOSEST)!!
                    assertFalse("2秒以降も映像が変化していること", middle.sameAs(late))
                    middle.recycle()
                    late.recycle()
                    println("検証成功: $id duration=$duration 音声あり・終盤フレーム変化あり")
                }
            } finally { work.deleteRecursively() }
        }
    }
}
