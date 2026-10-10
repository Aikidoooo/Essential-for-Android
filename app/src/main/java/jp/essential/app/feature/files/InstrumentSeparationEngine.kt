package jp.essential.app.feature.files

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class InstrumentStem(val label: String) {
    Drums("ドラム"), Bass("ベース"), Other("その他"), Vocals("ボーカル"), Guitar("ギター"), Piano("キーボード")
}

internal data class InstrumentStems(val files: Map<InstrumentStem, File>) {
    fun close() { files.values.forEach(File::delete) }
}

/** 6ステムを重なり付きチャンクで処理し、長い音声も一括で保持しない。 */
internal class InstrumentSeparationEngine(private val context: Context) {
    suspend fun separate(file: ReferencedFile, progress: (String, Float?) -> Unit): InstrumentStems {
        require(file.type != ReferencedMediaType.Image)
        val model = MediaAiModels.obtain(context, MediaAiModels.stems) { progress("分離モデルをダウンロード", it) }
        return withContext(Dispatchers.Default) {
            val source = File.createTempFile("stems-source", ".bin", context.cacheDir)
            val normalized = File.createTempFile("stems-normalized", ".wav", context.cacheDir)
            val files = InstrumentStem.entries.associateWith { File.createTempFile("stem-${it.name}", ".wav", context.cacheDir) }
            val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
            try {
                progress("音声を準備", null)
                context.contentResolver.openInputStream(file.uri)?.use { input -> source.outputStream().use(input::copyTo) } ?: error("音声を読み込めませんでした")
                FfmpegRunner.execute(context, listOf("-i", source.absolutePath, "-vn", "-ac", "2", "-ar", "44100", "-c:a", "pcm_s16le", "-y", normalized.absolutePath))
                OrtEnvironment.getEnvironment().createSession(model.absolutePath, options).use { session ->
                    AudioStemSeparationEngine.PcmWavReader(normalized).use { reader ->
                        val writers = files.mapValues { AudioStemSeparationEngine.PcmWavWriter(it.value, 2, 44100) }
                        try {
                            val size = 343980
                            val overlap = size / 4
                            val stride = size - overlap
                            val carry = Array(6) { Array(2) { FloatArray(overlap) } }
                            val carryWeights = FloatArray(overlap)
                            var start = 0L
                            while (start < reader.frameCount) {
                                currentCoroutineContext().ensureActive()
                                val length = minOf(size.toLong(), reader.frameCount - start).toInt()
                                val inputAudio = reader.readStereo(start, length)
                                val input = FloatArray(2 * size)
                                for (channel in 0..1) inputAudio[channel].copyInto(input, channel * size)
                                val last = start + size >= reader.frameCount
                                val emitted = if (last) length else stride
                                val block = Array(6) { Array(2) { FloatArray(emitted) } }
                                OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(input), longArrayOf(1, 2, size.toLong())).use { tensor ->
                                    session.run(mapOf("mix" to tensor)).use { result ->
                                        val output = result[0] as OnnxTensor
                                        check(output.info.shape.contentEquals(longArrayOf(1, 6, 2, size.toLong()))) { "分離モデルの出力形式が一致しません" }
                                        val values = output.floatBuffer
                                        for (sample in 0 until length) {
                                            val weight = when {
                                                start > 0 && sample < overlap -> sample.toFloat() / overlap
                                                !last && sample >= stride -> (size - sample).toFloat() / overlap
                                                else -> 1f
                                            }
                                            val previousWeight = if (sample < overlap) carryWeights[sample] else 0f
                                            for (stem in 0..5) for (channel in 0..1) {
                                                val value = values.get((stem * 2 + channel) * size + sample) * weight
                                                if (sample < emitted) {
                                                    val previous = if (sample < overlap) carry[stem][channel][sample] else 0f
                                                    block[stem][channel][sample] = (value + previous) / (weight + previousWeight).coerceAtLeast(.000001f)
                                                } else carry[stem][channel][sample - stride] = value
                                            }
                                        }
                                        if (!last) for (sample in 0 until overlap) carryWeights[sample] = (overlap - sample).toFloat() / overlap
                                    }
                                }
                                InstrumentStem.entries.forEachIndexed { index, stem -> writers.getValue(stem).write(block[index], 0, emitted) }
                                progress("ボーカル・楽器を分離", ((start + emitted).toFloat() / reader.frameCount).coerceIn(0f, 1f))
                                if (last) break
                                start += stride
                            }
                        } finally { writers.values.forEach { it.close() } }
                    }
                }
                InstrumentStems(files)
            } catch (error: Throwable) { files.values.forEach(File::delete); throw error }
            finally { options.close(); source.delete(); normalized.delete() }
        }
    }
}
