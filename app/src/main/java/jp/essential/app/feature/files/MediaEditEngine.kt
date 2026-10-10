package jp.essential.app.feature.files

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class MediaEditEngine(private val context: Context) {
    suspend fun waveform(file: ReferencedFile): FloatArray = withContext(Dispatchers.IO) {
        val source = File.createTempFile("wave-source", ".bin", context.cacheDir)
        val pcm = File.createTempFile("wave", ".pcm", context.cacheDir)
        try {
            context.contentResolver.openInputStream(file.uri)?.use { input -> source.outputStream().use(input::copyTo) } ?: error("音声を開けませんでした")
            FfmpegRunner.execute(context, listOf("-i", source.absolutePath, "-vn", "-ac", "1", "-ar", "8000", "-f", "s16le", "-y", pcm.absolutePath))
            val peaks = FloatArray(180)
            val samples = pcm.length() / 2
            pcm.inputStream().buffered().use { input ->
                var index = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val low = input.read(); val high = input.read()
                    if (high < 0) break
                    val value = (low or (high shl 8)).toShort().toInt()
                    val bucket = (index * peaks.size / samples.coerceAtLeast(1)).toInt().coerceAtMost(peaks.lastIndex)
                    peaks[bucket] = maxOf(peaks[bucket], abs(value / 32768f))
                    index++
                }
            }
            peaks
        } finally { source.delete(); pcm.delete() }
    }

    suspend fun upscale(file: ReferencedFile, mode: UpscaleMode, progress: (String, Float?) -> Unit): String {
        val bitmap = ImageUpscaleEngine(context).upscale(file, mode, progress)
        return withContext(Dispatchers.IO) {
            val output = File.createTempFile("Essential-upscale", ".png", context.cacheDir)
            try {
                output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                MediaFileEngine(context).publishFile(output, "image/png")
            } finally { bitmap.recycle(); output.delete() }
        }
    }

    suspend fun mix(stems: InstrumentStems, gains: Map<InstrumentStem, Float>, instrumentGain: Float, sourceFile: ReferencedFile? = null): String = withContext(Dispatchers.IO) {
        val output = File.createTempFile("Essential-mixed", ".wav", context.cacheDir)
        var videoSource: File? = null
        var videoOutput: File? = null
        try {
            val args = mutableListOf<String>()
            InstrumentStem.entries.forEach { args += listOf("-i", stems.files.getValue(it).absolutePath) }
            val filters = InstrumentStem.entries.mapIndexed { index, stem ->
                val gain = (gains[stem] ?: 1f) * if (stem == InstrumentStem.Vocals) 1f else instrumentGain
                "[$index:a]volume=${String.format(Locale.US, "%.4f", gain)}[s$index]"
            }.joinToString(";") + ";" + InstrumentStem.entries.indices.joinToString("") { "[s$it]" } +
                "amix=inputs=6:normalize=0,alimiter=limit=0.95:level=false[mix]"
            args += listOf("-filter_complex", filters, "-map", "[mix]", "-c:a", "pcm_s16le", "-y", output.absolutePath)
            FfmpegRunner.execute(context, args)
            if (sourceFile?.type == ReferencedMediaType.Video) {
                val source = File.createTempFile("Essential-video-source", ".bin", context.cacheDir).also { videoSource = it }
                val result = File.createTempFile("Essential-mixed-video", ".mp4", context.cacheDir).also { videoOutput = it }
                context.contentResolver.openInputStream(sourceFile.uri)?.use { input -> source.outputStream().use(input::copyTo) } ?: error("動画を開けませんでした")
                val inputs = listOf("-i", source.absolutePath, "-i", output.absolutePath, "-map", "0:v:0", "-map", "1:a:0")
                val tail = listOf("-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", "-y", result.absolutePath)
                try { FfmpegRunner.execute(context, inputs + listOf("-c:v", "copy") + tail) }
                catch (error: IllegalStateException) {
                    // MP4へ直接格納できない映像だけを互換形式へ変換する。
                    FfmpegRunner.execute(context, inputs + listOf("-c:v", "mpeg4", "-q:v", "2") + tail)
                }
                return@withContext MediaFileEngine(context).publishFile(result, "video/mp4")
            }
            MediaFileEngine(context).publishFile(output, "audio/wav")
        } finally { output.delete(); videoSource?.delete(); videoOutput?.delete() }
    }
}
