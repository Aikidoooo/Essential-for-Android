package jp.essential.app.feature.downloader

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import jp.essential.app.feature.files.FfmpegRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YtDlpMergeInstrumentedTest {
    @Test
    fun 短い音声でも映像終盤のフレームを保持する() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "merge-motion-${System.nanoTime()}").apply { mkdirs() }
        try {
            FfmpegRunner.execute(context, listOf("-f", "lavfi", "-i", "testsrc2=s=160x120:r=15", "-t", "6", "-an", "-c:v", "mpeg4", "-y", File(directory, "video.mp4").absolutePath))
            FfmpegRunner.execute(context, listOf("-f", "lavfi", "-i", "sine=frequency=440", "-t", "1", "-c:a", "aac", "-y", File(directory, "audio.m4a").absolutePath))
            val output = YtDlpDownloadEngine(context).mergeDownloadedStreams(directory, DownloaderSelection("https://x.com/a/status/1", DownloadMediaType.Video))
            jp.essential.app.core.withMetadataRetriever { retriever ->
                retriever.setDataSource(output.absolutePath)
                assertTrue(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() >= 5900)
                val early = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
                val late = retriever.getFrameAtTime(5_000_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
                assertTrue("終盤のフレームが更新されていること", !early.sameAs(late))
                early.recycle()
                late.recycle()
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun 音声付き動画は再結合せず音声なし動画も保存できる() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "merge-muxed-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(directory, "combined.mp4")
            FfmpegRunner.execute(context, listOf("-f", "lavfi", "-i", "testsrc2=s=160x120:r=15", "-f", "lavfi", "-i", "sine=frequency=440", "-t", "3", "-c:v", "mpeg4", "-c:a", "aac", "-y", source.absolutePath))
            val engine = YtDlpDownloadEngine(context)
            val selection = DownloaderSelection("https://x.com/a/status/1", DownloadMediaType.Video)
            assertEquals(source, engine.mergeDownloadedStreams(directory, selection))
            source.delete()
            FfmpegRunner.execute(context, listOf("-f", "lavfi", "-i", "testsrc2=s=160x120:r=15", "-t", "3", "-an", "-c:v", "mpeg4", "-y", File(directory, "silent.mp4").absolutePath))
            assertTrue(engine.mergeDownloadedStreams(directory, selection).length() > 0)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun 分離した映像と音声を一つの動画へ結合できる() {
        assumeTrue(Build.SUPPORTED_ABIS.firstOrNull() in setOf("arm64-v8a", "x86_64"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "merge-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val video = File(directory, "video-only.mp4")
        val audio = File(directory, "audio-only.m4a")
        FfmpegRunner.execute(
            context,
            listOf(
                "-f", "lavfi", "-i", "color=c=orange:s=320x180:r=30",
                "-t", "1", "-an", "-c:v", "mpeg4", "-y", video.absolutePath,
            ),
        )
        FfmpegRunner.execute(
            context,
            listOf(
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
                "-t", "1", "-vn", "-c:a", "aac", "-y", audio.absolutePath,
            ),
        )
        val engine = YtDlpDownloadEngine(context)
        val output = engine.mergeDownloadedStreams(
            directory,
            DownloaderSelection(
                url = "https://example.com/video",
                mediaType = DownloadMediaType.Video,
                videoFormat = VideoFormat.Mp4,
            ),
        )

        assertTrue(output.exists() && output.length() > 0L)
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(output.absolutePath)
            assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
            assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
        }
        directory.deleteRecursively()
    }
}
