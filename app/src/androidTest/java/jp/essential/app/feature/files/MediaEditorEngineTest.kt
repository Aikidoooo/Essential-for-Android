package jp.essential.app.feature.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class MediaEditorEngineTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun audio(): ReferencedFile {
        val file = File(context.cacheDir, "editor-test.wav")
        AudioStemSeparationEngine.PcmWavWriter(file, 2, 44100).use { writer ->
            val samples = Array(2) { FloatArray(44100) { (.15 * sin(it * 2 * Math.PI * 440 / 44100)).toFloat() } }
            writer.write(samples, 0, 44100)
        }
        return ReferencedFile(Uri.fromFile(file), file.name, "audio/wav", ReferencedMediaType.Audio, file.length(), 1000)
    }
    private fun image(width: Int = 12, height: Int = 8): ReferencedFile {
        val file = File(context.cacheDir, "editor-image.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(110, 60, 190))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        return ReferencedFile(Uri.fromFile(file), file.name, "image/png", ReferencedMediaType.Image, file.length())
    }
    private fun saved(name: String): Uri {
        context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf("_id"), "_display_name=?", arrayOf(name), null)!!.use { cursor ->
            check(cursor.moveToFirst())
            return android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0))
        }
    }
    @Test fun threeRealModelsAnd2048InputLimit(): Unit = runBlocking {
        val big = image(2050, 100)
        val shrunk = loadMediaImage(context, big, 2048)
        assertEquals(2048, shrunk.width); assertTrue(shrunk.height <= 100); shrunk.recycle()
        val file = image()
        for (mode in UpscaleMode.entries) {
            val bitmap = ImageUpscaleEngine(context).upscale(file, mode) { _, _ -> }
            assertEquals(12 * mode.scale, bitmap.width)
            assertEquals(8 * mode.scale, bitmap.height)
            assertEquals(255, Color.alpha(bitmap.getPixel(0, 0)))
            bitmap.recycle()
        }
        File(file.uri.path!!).delete()
        Unit
    }
    @Test fun modelDownloadReportsRealByteProgress(): Unit = runBlocking {
        val model = UpscaleMode.Fast.model.copy(id = "download-progress-test-${System.nanoTime()}")
        val values = mutableListOf<Float?>()
        val result = MediaAiModels.obtain(context, model) { values += it }
        try {
            assertEquals(4866429L, result.length())
            assertTrue(values.any { it != null && it > 0f && it < 1f })
            assertEquals(1f, values.last()!!, 0f)
        } finally { result.delete() }
    }
    @Test fun waveformAndSampleAccurateTrim() = runBlocking {
        val file = audio()
        val peaks = MediaEditEngine(context).waveform(file)
        assertEquals(180, peaks.size)
        assertTrue(peaks.all { it in .1f.. .2f })
        val name = MediaFileEngine(context).trimAudio(file, 200, 750)
        val uri = saved(name)
        val output = File(context.cacheDir, "editor-trim-check.wav")
        try {
            context.contentResolver.openInputStream(uri)!!.use { input -> output.outputStream().use(input::copyTo) }
            AudioStemSeparationEngine.PcmWavReader(output).use { assertEquals(24255L, it.frameCount) }
        } finally { output.delete(); context.contentResolver.delete(uri, null, null); File(file.uri.path!!).delete() }
    }
    @Test fun sixStemInferenceMuteMixAndPlayback() = runBlocking {
        val file = audio()
        val stems = InstrumentSeparationEngine(context).separate(file) { _, _ -> }
        try {
            assertEquals(6, stems.files.size)
            stems.files.values.forEach { source -> AudioStemSeparationEngine.PcmWavReader(source).use { assertEquals(44100L, it.frameCount) } }
            val name = MediaEditEngine(context).mix(stems, InstrumentStem.entries.associateWith { 0f }, 1f)
            val uri = saved(name)
            val check = File(context.cacheDir, "editor-mix-check.wav")
            try {
                context.contentResolver.openInputStream(uri)!!.use { input -> check.outputStream().use(input::copyTo) }
                AudioStemSeparationEngine.PcmWavReader(check).use { reader -> assertTrue(reader.readStereo(0, 44100).all { channel -> channel.all { it == 0f } }) }
            } finally { check.delete(); context.contentResolver.delete(uri, null, null) }
            val video = File(context.cacheDir, "editor-video.mp4")
            FfmpegRunner.execute(context, listOf("-f", "lavfi", "-i", "color=c=blue:s=160x90:r=10", "-i", stems.files.getValue(InstrumentStem.Vocals).absolutePath, "-c:v", "mpeg4", "-c:a", "aac", "-shortest", "-y", video.absolutePath))
            val referencedVideo = ReferencedFile(Uri.fromFile(video), video.name, "video/mp4", ReferencedMediaType.Video, video.length(), 1000)
            val videoName = MediaEditEngine(context).mix(stems, InstrumentStem.entries.associateWith { 0f }, 1f, referencedVideo)
            val videoUri = saved(videoName)
            try {
                val inspected = MediaFileEngine(context).inspect(videoUri)
                assertEquals(ReferencedMediaType.Video, inspected.type)
                assertTrue(inspected.durationMillis >= 900)
            } finally { context.contentResolver.delete(videoUri, null, null); video.delete() }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val player = StemPreviewPlayer(stems, scope)
            try {
                player.play(); delay(350)
                player.gains = FloatArray(6)
                player.seekTo(500); delay(150)
                assertNull(player.error)
                assertTrue(player.positionMillis > 0)
                player.pause()
            } finally { player.close(); scope.cancel(); delay(100) }
        } finally { stems.close(); File(file.uri.path!!).delete() }
    }
}
