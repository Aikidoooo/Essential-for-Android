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
