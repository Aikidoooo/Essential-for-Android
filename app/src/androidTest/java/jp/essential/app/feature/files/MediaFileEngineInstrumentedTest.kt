package jp.essential.app.feature.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaFileEngineInstrumentedTest {
    @Test
    fun ffmpegで音声を切り取り保存できる() = runBlocking {
        assumeTrue(Build.SUPPORTED_ABIS.firstOrNull() in setOf("arm64-v8a", "x86_64"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "engine-test.wav")
        writeTestWave(source)
        val engine = MediaFileEngine(context)
        val referenced = ReferencedFile(
            uri = Uri.fromFile(source),
            name = source.name,
            mimeType = "audio/wav",
            type = ReferencedMediaType.Audio,
            sizeBytes = source.length(),
            durationMillis = 1_000L,
        )

        val savedName = engine.trimAudio(referenced, 100L, 700L)

        assertTrue(savedName.endsWith(".wav"))
        source.delete()
        Unit
    }

    @Test
    fun 動画からフレームとmp3とgifを生成できる() = runBlocking {
        assumeTrue(Build.SUPPORTED_ABIS.firstOrNull() in setOf("arm64-v8a", "x86_64"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "engine-video-test.mp4")
        FfmpegRunner.execute(
            context,
            listOf(
                "-f", "lavfi", "-i", "color=c=orange:s=320x180:r=30",
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
                "-t", "1", "-c:v", "mpeg4", "-c:a", "aac", "-shortest", "-y", source.absolutePath,
            ),
        )
        val referenced = ReferencedFile(
            uri = Uri.fromFile(source),
            name = source.name,
            mimeType = "video/mp4",
            type = ReferencedMediaType.Video,
            sizeBytes = source.length(),
            durationMillis = 1_000L,
            frameRate = 30f,
        )
        val engine = MediaFileEngine(context)

        val frame = engine.extractFrame(referenced, 500L)
        val mp3 = engine.convertVideoToMp3(referenced)
        val gif = engine.makeGif(referenced, GifPreset.Compact)

        assertTrue(frame.endsWith(".png"))
        assertTrue(mp3.endsWith(".mp3"))
        assertTrue(gif.endsWith(".gif"))
        source.delete()
        Unit
    }

    @Test
    fun aiで画像の背景を透明化できる() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "segmentation-test.png")
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawCircle(256f, 240f, 150f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 120, 30) })
            drawRect(180f, 330f, 332f, 480f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 70, 75) })
        }
        FileOutputStream(source).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val referenced = ReferencedFile(
            uri = Uri.fromFile(source),
            name = source.name,
            mimeType = "image/png",
            type = ReferencedMediaType.Image,
            sizeBytes = source.length(),
        )

        val savedName = MediaFileEngine(context).removeBackground(referenced)

        assertTrue(savedName.endsWith(".png"))
        source.delete()
        Unit
    }

    @Test
    fun 動画を目標サイズへ圧縮できる() = runBlocking {
        assumeTrue(Build.SUPPORTED_ABIS.firstOrNull() in setOf("arm64-v8a", "x86_64"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "compression-test.mp4")
        FfmpegRunner.execute(
            context,
            listOf(
                "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=30",
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
                "-t", "4", "-c:v", "mpeg4", "-b:v", "8M", "-c:a", "aac", "-y", source.absolutePath,
            ),
        )
        assertTrue(source.length() > 1024L * 1024L)
        val referenced = ReferencedFile(
            uri = Uri.fromFile(source),
            name = source.name,
            mimeType = "video/mp4",
            type = ReferencedMediaType.Video,
            sizeBytes = source.length(),
            durationMillis = 4_000L,
            frameRate = 30f,
        )

        val savedName = MediaFileEngine(context).compress(referenced, 1)

        val savedSize = context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.SIZE),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(savedName),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L
        assertTrue(savedSize in 1..(1024L * 1024L))
        source.delete()
        Unit
    }

    private fun writeTestWave(file: File) {
        val sampleRate = 8_000
        val sampleCount = sampleRate
        val dataSize = sampleCount * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVEfmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(dataSize)
        }.array()
        FileOutputStream(file).use { output ->
            output.write(header)
            repeat(sampleCount) { index ->
                val sample = (sin(index * 2.0 * Math.PI * 440.0 / sampleRate) * Short.MAX_VALUE * 0.2).toInt().toShort()
                output.write(sample.toInt() and 0xff)
                output.write((sample.toInt() shr 8) and 0xff)
            }
        }
    }
}
