package jp.essential.app.feature.files

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/** 分離済みの一時WAV。音量調整後の保存は別途行う。 */
data class AudioStemFiles(
    val vocals: File,
    val accompaniment: File,
    val baseName: String,
)

/** Spleeter 2-stemを端末内で実行する音声分離エンジン。 */
class AudioStemSeparationEngine(private val context: Context) {
    suspend fun separate(
        file: ReferencedFile,
        onProgress: (Float) -> Unit = {},
    ): AudioStemFiles = withContext(Dispatchers.Default) {
        require(file.type == ReferencedMediaType.Audio) { "音声ファイルだけを分離できます" }
        val source = copyToCache(file, "source")
        val normalized = tempFile("normalized", "wav")
        val vocals = tempFile("vocals", "wav")
        val accompaniment = tempFile("accompaniment", "wav")
        val baseName = file.name.substringBeforeLast('.', file.name)
            .replace(Regex("[^A-Za-z0-9ぁ-んァ-ヶ一-龠._-]"), "_")
            .ifBlank { "Essential-audio" }

        try {
            FfmpegRunner.execute(
                context,
                listOf(
                    "-i", source.absolutePath,
                    "-vn", "-ac", CHANNELS.toString(),
                    "-ar", SAMPLE_RATE.toString(),
                    "-c:a", "pcm_s16le", "-f", "wav", "-y", normalized.absolutePath,
                ),
            )
            PcmWavReader(normalized).use { reader ->
                check(reader.sampleRate == SAMPLE_RATE && reader.channels == CHANNELS) {
                    "音声を44.1kHzステレオへ変換できませんでした"
                }
                val vocalModel = copyModel("vocals.fp16.onnx")
                val accompanimentModel = copyModel("accompaniment.fp16.onnx")
                val environment = OrtEnvironment.getEnvironment()
                val sessionOptions = OrtSession.SessionOptions()
                val vocalSession = environment.createSession(vocalModel.absolutePath, sessionOptions)
                val accompanimentSession = environment.createSession(
                    accompanimentModel.absolutePath,
                    sessionOptions,
                )
                try {
                    PcmWavWriter(vocals, CHANNELS, SAMPLE_RATE).use { vocalWriter ->
                        PcmWavWriter(accompaniment, CHANNELS, SAMPLE_RATE).use { accompanimentWriter ->
                            processChunks(
                                reader = reader,
                                vocalSession = vocalSession,
                                accompanimentSession = accompanimentSession,
                                vocalWriter = vocalWriter,
                                accompanimentWriter = accompanimentWriter,
                                onProgress = onProgress,
                            )
                        }
                    }
                } finally {
                    vocalSession.close()
                    accompanimentSession.close()
                    sessionOptions.close()
                }
            }
            return@withContext AudioStemFiles(vocals, accompaniment, baseName)
        } catch (error: Throwable) {
            vocals.delete()
            accompaniment.delete()
            throw error
        } finally {
            source.delete()
            normalized.delete()
        }
    }

    private suspend fun processChunks(
        reader: PcmWavReader,
        vocalSession: OrtSession,
        accompanimentSession: OrtSession,
        vocalWriter: PcmWavWriter,
        accompanimentWriter: PcmWavWriter,
        onProgress: (Float) -> Unit,
    ) {
        val totalFrames = reader.frameCount
        check(totalFrames > 0L) { "音声にサンプルがありません" }
        var coreStart = 0L
        while (coreStart < totalFrames) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val coreLength = min(CHUNK_SAMPLES.toLong(), totalFrames - coreStart).toInt()
            val contextFrames = min(CONTEXT_SAMPLES.toLong(), coreStart).toInt()
            val afterContext = min(
                CONTEXT_SAMPLES.toLong(),
                totalFrames - coreStart - coreLength,
            ).toInt()
            val inputStart = coreStart - contextFrames
            val inputLength = contextFrames + coreLength + afterContext
            val input = reader.readStereo(inputStart, inputLength)
            val separated = separateChunk(input, vocalSession, accompanimentSession)
            vocalWriter.write(separated.vocals, contextFrames, coreLength)
            accompanimentWriter.write(separated.accompaniment, contextFrames, coreLength)
            coreStart += coreLength
            onProgress((coreStart.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f))
        }
    }

    private fun separateChunk(
        input: Array<FloatArray>,
        vocalSession: OrtSession,
        accompanimentSession: OrtSession,
    ): SeparationChunk {
        val left = createSpectrogram(input[0])
        val right = createSpectrogram(input[1])
        check(left.frameCount == right.frameCount) { "左右チャンネルのフレーム数が一致しません" }
        val splits = ceil(left.frameCount / MODEL_FRAMES.toDouble()).toInt().coerceAtLeast(1)
        val inputValues = FloatArray(CHANNELS * splits * MODEL_FRAMES * MODEL_BINS)
        packMagnitude(left, inputValues, 0, splits)
        packMagnitude(right, inputValues, splits * MODEL_FRAMES * MODEL_BINS, splits)
        val vocalMaskSource = runModel(vocalSession, inputValues, splits)
        val accompanimentMaskSource = runModel(accompanimentSession, inputValues, splits)
        val outputLength = input[0].size
        return SeparationChunk(
            vocals = arrayOf(
                reconstruct(left, vocalMaskSource, accompanimentMaskSource, 0, splits, outputLength, false),
                reconstruct(right, vocalMaskSource, accompanimentMaskSource, 1, splits, outputLength, false),
            ),
            accompaniment = arrayOf(
                reconstruct(left, vocalMaskSource, accompanimentMaskSource, 0, splits, outputLength, true),
                reconstruct(right, vocalMaskSource, accompanimentMaskSource, 1, splits, outputLength, true),
            ),
        )
    }

    private fun packMagnitude(
        spectrogram: Spectrogram,
        destination: FloatArray,
        channelOffset: Int,
        splits: Int,
    ) {
        for (frame in 0 until spectrogram.frameCount) {
            val destinationOffset = channelOffset + frame * MODEL_BINS
            val sourceOffset = frame * FULL_BINS
            for (bin in 0 until MODEL_BINS) {
                val real = spectrogram.real[sourceOffset + bin]
                val imaginary = spectrogram.imaginary[sourceOffset + bin]
                destination[destinationOffset + bin] = sqrt(real * real + imaginary * imaginary)
            }
        }
    }

    private fun runModel(
        session: OrtSession,
        inputValues: FloatArray,
        splits: Int,
    ): FloatArray {
        val inputName = session.inputNames.first()
        val shape = longArrayOf(CHANNELS.toLong(), splits.toLong(), MODEL_FRAMES.toLong(), MODEL_BINS.toLong())
        OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(inputValues), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                val buffer = output.floatBuffer
                val values = FloatArray(buffer.remaining())
                buffer.get(values)
                return values
            }
        }
    }

    private fun reconstruct(
        spectrogram: Spectrogram,
        vocalOutput: FloatArray,
        accompanimentOutput: FloatArray,
        channel: Int,
        splits: Int,
        outputLength: Int,
        accompaniment: Boolean,
    ): FloatArray {
        val expectedSize = CHANNELS * splits * MODEL_FRAMES * MODEL_BINS
        check(vocalOutput.size >= expectedSize && accompanimentOutput.size >= expectedSize) {
            "分離モデルの出力サイズが想定外です"
        }
        val paddedLength = spectrogram.paddedLength
        val result = FloatArray(paddedLength)
        val weights = FloatArray(paddedLength)
        val frameReal = FloatArray(FFT_SIZE)
        val frameImaginary = FloatArray(FFT_SIZE)
        for (frame in 0 until spectrogram.frameCount) {
            java.util.Arrays.fill(frameReal, 0f)
            java.util.Arrays.fill(frameImaginary, 0f)
            val sourceOffset = frame * FULL_BINS
            val modelOffset = channel * splits * MODEL_FRAMES * MODEL_BINS + frame * MODEL_BINS
            for (bin in 0 until FULL_BINS) {
                val mask = if (bin < MODEL_BINS) {
                    val vocalValue = vocalOutput[modelOffset + bin]
                    val accompanimentValue = accompanimentOutput[modelOffset + bin]
                    val denominator = vocalValue * vocalValue + accompanimentValue * accompanimentValue + EPSILON
                    val numerator = if (accompaniment) {
                        accompanimentValue * accompanimentValue + EPSILON / 2f
                    } else {
                        vocalValue * vocalValue + EPSILON / 2f
                    }
                    (numerator / denominator).coerceIn(0f, 1f)
                } else if (accompaniment) {
                    1f
                } else {
                    0f
                }
                frameReal[bin] = spectrogram.real[sourceOffset + bin] * mask
                frameImaginary[bin] = spectrogram.imaginary[sourceOffset + bin] * mask
                if (bin in 1 until FFT_SIZE / 2) {
                    frameReal[FFT_SIZE - bin] = frameReal[bin]
                    frameImaginary[FFT_SIZE - bin] = -frameImaginary[bin]
                }
            }
            FastFft.transform(frameReal, frameImaginary, inverse = true)
            val frameStart = frame * HOP_SIZE
            for (sample in 0 until FFT_SIZE) {
                val window = window(sample)
                result[frameStart + sample] += frameReal[sample] * window
                weights[frameStart + sample] += window * window
            }
        }
        for (index in 0 until outputLength) {
            val weight = weights[index]
            result[index] = if (weight > EPSILON) result[index] / weight else 0f
        }
        return result.copyOf(outputLength)
    }

    private fun copyModel(name: String): File {
        val target = File(context.noBackupFilesDir, "audio-separation/spleeter-2stems-fp16/$name")
        target.parentFile?.mkdirs()
        val assetPath = "models/audio_separation/spleeter-2stems-fp16/$name"
        val expectedSize = context.assets.open(assetPath).use { it.available().toLong() }
        if (target.length() != expectedSize) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    private fun copyToCache(file: ReferencedFile, label: String): File {
        val extension = file.name.substringAfterLast('.', "bin")
            .replace(Regex("[^A-Za-z0-9]"), "")
            .ifBlank { "bin" }
        val target = tempFile(label, extension)
        context.contentResolver.openInputStream(file.uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("参照ファイルを開けませんでした")
        check(target.length() > 0L) { "参照ファイルが空です" }
        return target
    }

    private fun tempFile(label: String, extension: String): File =
        File(context.cacheDir, "Essential-$label-${System.nanoTime()}.$extension")

    private data class SeparationChunk(
        val vocals: Array<FloatArray>,
        val accompaniment: Array<FloatArray>,
    )

    private data class Spectrogram(
        val real: FloatArray,
        val imaginary: FloatArray,
        val frameCount: Int,
        val paddedLength: Int,
    )

    private fun createSpectrogram(samples: FloatArray): Spectrogram {
        val frameCount = if (samples.size <= FFT_SIZE) {
            1
        } else {
            ceil((samples.size - FFT_SIZE).toDouble() / HOP_SIZE).toInt() + 1
        }
        val paddedLength = FFT_SIZE + (frameCount - 1) * HOP_SIZE
        val real = FloatArray(frameCount * FULL_BINS)
        val imaginary = FloatArray(frameCount * FULL_BINS)
        val frameReal = FloatArray(FFT_SIZE)
        val frameImaginary = FloatArray(FFT_SIZE)
        for (frame in 0 until frameCount) {
            val start = frame * HOP_SIZE
            for (sample in 0 until FFT_SIZE) {
                frameReal[sample] = if (start + sample < samples.size) samples[start + sample] * window(sample) else 0f
                frameImaginary[sample] = 0f
            }
            FastFft.transform(frameReal, frameImaginary, inverse = false)
            val offset = frame * FULL_BINS
            for (bin in 0 until FULL_BINS) {
                real[offset + bin] = frameReal[bin]
                imaginary[offset + bin] = frameImaginary[bin]
            }
        }
        return Spectrogram(real, imaginary, frameCount, paddedLength)
    }

    private fun window(index: Int): Float =
        (0.5 - 0.5 * cos(2.0 * Math.PI * index / FFT_SIZE)).toFloat()

    private class PcmWavReader(private val file: File) : Closeable {
        private val randomAccessFile = RandomAccessFile(file, "r")
        var sampleRate: Int = 0
            private set
        var channels: Int = 0
            private set
        var frameCount: Long = 0L
            private set
        private var dataOffset: Long = 0L
        private var bytesPerFrame: Int = 0

        init {
            parseHeader()
        }

        fun readStereo(startFrame: Long, frameLength: Int): Array<FloatArray> {
            val stereo = Array(CHANNELS) { FloatArray(frameLength) }
            val validStart = max(0L, startFrame)
            val validEnd = min(frameCount, startFrame + frameLength)
            if (validStart >= validEnd) return stereo
            val validFrames = (validEnd - validStart).toInt()
            val bytes = ByteArray(validFrames * bytesPerFrame)
            randomAccessFile.seek(dataOffset + validStart * bytesPerFrame)
            randomAccessFile.readFully(bytes)
            val destinationOffset = (validStart - startFrame).toInt()
            var cursor = 0
            for (frame in 0 until validFrames) {
                for (channel in 0 until channels) {
                    val sample = readShort(bytes, cursor) / 32768f
                    cursor += 2
                    val outputChannel = channel.coerceAtMost(CHANNELS - 1)
                    stereo[outputChannel][destinationOffset + frame] = sample
                }
            }
            return stereo
        }

        private fun parseHeader() {
            val riff = ByteArray(12)
            randomAccessFile.readFully(riff)
            check(String(riff, 0, 4, Charsets.US_ASCII) == "RIFF") { "WAVヘッダーが不正です" }
            check(String(riff, 8, 4, Charsets.US_ASCII) == "WAVE") { "WAV形式ではありません" }
            var bitsPerSample = 0
            while (randomAccessFile.filePointer + 8 <= randomAccessFile.length()) {
                val chunkId = ByteArray(4)
                randomAccessFile.readFully(chunkId)
                val size = readIntLE(randomAccessFile)
                val chunkStart = randomAccessFile.filePointer
                when (String(chunkId, Charsets.US_ASCII)) {
                    "fmt " -> {
                        val format = readUnsignedShortLE(randomAccessFile)
                        channels = readUnsignedShortLE(randomAccessFile)
                        sampleRate = readIntLE(randomAccessFile)
                        randomAccessFile.skipBytes(6)
                        bitsPerSample = if (format == 1) readUnsignedShortLE(randomAccessFile) else 0
                    }
                    "data" -> {
                        dataOffset = chunkStart
                        frameCount = size.toLong() / (channels.coerceAtLeast(1) * 2L)
                    }
                }
                randomAccessFile.seek(chunkStart + size + (size and 1))
            }
            check(channels > 0 && sampleRate > 0 && bitsPerSample == 16 && dataOffset > 0L) {
                "16bit PCM WAVへ変換できませんでした"
            }
            bytesPerFrame = channels * 2
        }

        override fun close() {
            randomAccessFile.close()
        }
    }

    private class PcmWavWriter(
        private val file: File,
        private val channels: Int,
        private val sampleRate: Int,
    ) : Closeable {
        private val randomAccessFile = RandomAccessFile(file, "rw")
        private var dataBytes = 0L

        init {
            randomAccessFile.setLength(0L)
            randomAccessFile.write(ByteArray(44))
            randomAccessFile.seek(0L)
            randomAccessFile.writeBytes("RIFF")
            writeIntLE(36)
            randomAccessFile.writeBytes("WAVEfmt ")
            writeIntLE(16)
            writeShortLE(1)
            writeShortLE(channels)
            writeIntLE(sampleRate)
            writeIntLE(sampleRate * channels * 2)
            writeShortLE(channels * 2)
            writeShortLE(16)
            randomAccessFile.writeBytes("data")
            writeIntLE(0)
        }

        fun write(stereo: Array<FloatArray>, start: Int, length: Int) {
            for (frame in start until start + length) {
                for (channel in 0 until channels) {
                    val sample = stereo[channel][frame].coerceIn(-1f, 1f)
                    randomAccessFile.writeShortLE((sample * 32767f).toInt())
                    dataBytes += 2L
                }
            }
        }

        override fun close() {
            randomAccessFile.seek(4L)
            writeIntLE((36L + dataBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            randomAccessFile.seek(40L)
            writeIntLE(dataBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            randomAccessFile.close()
        }

        private fun writeIntLE(value: Int) {
            randomAccessFile.write(value and 0xff)
            randomAccessFile.write((value ushr 8) and 0xff)
            randomAccessFile.write((value ushr 16) and 0xff)
            randomAccessFile.write((value ushr 24) and 0xff)
        }

        private fun writeShortLE(value: Int) {
            randomAccessFile.write(value and 0xff)
            randomAccessFile.write((value ushr 8) and 0xff)
        }
    }

    private object FastFft {
        fun transform(real: FloatArray, imaginary: FloatArray, inverse: Boolean) {
            var j = 0
            for (index in 1 until real.size) {
                var bit = real.size shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (index < j) {
                    val realValue = real[index]
                    real[index] = real[j]
                    real[j] = realValue
                    val imaginaryValue = imaginary[index]
                    imaginary[index] = imaginary[j]
                    imaginary[j] = imaginaryValue
                }
            }
            var length = 2
            while (length <= real.size) {
                val angle = (if (inverse) 2.0 else -2.0) * Math.PI / length
                val stepReal = cos(angle).toFloat()
                val stepImaginary = sin(angle).toFloat()
                var offset = 0
                while (offset < real.size) {
                    var currentReal = 1f
                    var currentImaginary = 0f
                    val half = length / 2
                    for (index in 0 until half) {
                        val evenIndex = offset + index
                        val oddIndex = evenIndex + half
                        val oddReal = real[oddIndex] * currentReal - imaginary[oddIndex] * currentImaginary
                        val oddImaginary = real[oddIndex] * currentImaginary + imaginary[oddIndex] * currentReal
                        real[oddIndex] = real[evenIndex] - oddReal
                        imaginary[oddIndex] = imaginary[evenIndex] - oddImaginary
                        real[evenIndex] += oddReal
                        imaginary[evenIndex] += oddImaginary
                        val nextReal = currentReal * stepReal - currentImaginary * stepImaginary
                        currentImaginary = currentReal * stepImaginary + currentImaginary * stepReal
                        currentReal = nextReal
                    }
                    offset += length
                }
                length = length shl 1
            }
            if (inverse) {
                val scale = 1f / real.size
                for (index in real.indices) {
                    real[index] *= scale
                    imaginary[index] *= scale
                }
            }
        }
    }

    private companion object {
        private const val SAMPLE_RATE = 44_100
        private const val CHANNELS = 2
        private const val FFT_SIZE = 4_096
        private const val HOP_SIZE = 1_024
        private const val FULL_BINS = FFT_SIZE / 2 + 1
        private const val MODEL_BINS = 1_024
        private const val MODEL_FRAMES = 512
        private const val CHUNK_SAMPLES = SAMPLE_RATE * 8
        private const val CONTEXT_SAMPLES = SAMPLE_RATE
        private const val EPSILON = 1e-10f
    }
}

private fun RandomAccessFile.writeShortLE(value: Int) {
    write(value and 0xff)
    write((value ushr 8) and 0xff)
}

private fun readIntLE(file: RandomAccessFile): Int =
    file.read() or (file.read() shl 8) or (file.read() shl 16) or (file.read() shl 24)

private fun readUnsignedShortLE(file: RandomAccessFile): Int =
    file.read() or (file.read() shl 8)

private fun readShort(bytes: ByteArray, offset: Int): Short =
    ((bytes[offset].toInt() and 0xff) or (bytes[offset + 1].toInt() shl 8)).toShort()
