package jp.essential.app.feature.files

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*

/** 6音源を同じサンプル位置で混ぜ、音量の変更を次の音声ブロックから反映する。 */
internal class StemPreviewPlayer(private val stems: InstrumentStems, private val scope: CoroutineScope) {
    @Volatile var gains = FloatArray(6) { 1f }
    @Volatile var positionMillis = 0L
        private set
    @Volatile var playing = false
        private set
    private val seek = AtomicLong(-1L)
    private var job: Job? = null
    private var track: AudioTrack? = null
    @Volatile var error: String? = null
        private set
    fun seekTo(millis: Long) { positionMillis = millis; seek.set(millis * 44100 / 1000) }
    fun pause() { playing = false; runCatching { track?.pause() } }
    fun play() {
        if (playing) return
        playing = true
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, cause -> error = cause.message ?: "再生に失敗しました"; playing = false }) {
            val readers = InstrumentStem.entries.map { AudioStemSeparationEngine.PcmWavReader(stems.files.getValue(it)) }
            val bufferBytes = maxOf(AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT), 4096)
            val output = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(bufferBytes).setTransferMode(AudioTrack.MODE_STREAM).build()
            track = output
            try {
                var frame = positionMillis * 44100 / 1000
                val mixed = ShortArray(2048)
                while (isActive) {
                    val target = seek.getAndSet(-1)
                    if (target >= 0) { frame = target; output.pause(); output.flush() }
                    if (!playing) { delay(30); continue }
                    if (frame >= readers.first().frameCount) { playing = false; frame = 0; positionMillis = 0; continue }
                    if (output.playState != AudioTrack.PLAYSTATE_PLAYING) output.play()
                    val length = minOf(1024L, readers.first().frameCount - frame).toInt()
                    val blocks = readers.map { it.readStereo(frame, length) }
                    val volumes = gains
                    for (sample in 0 until length) for (channel in 0..1) {
                        var value = 0f
                        for (stem in blocks.indices) value += blocks[stem][channel][sample] * volumes[stem]
                        mixed[sample * 2 + channel] = (value.coerceIn(-.95f, .95f) * 32767).toInt().toShort()
                    }
                    output.write(mixed, 0, length * 2, AudioTrack.WRITE_BLOCKING)
                    frame += length
                    positionMillis = frame * 1000 / 44100
                }
            } finally {
                runCatching { output.stop() }; output.release(); track = null
                readers.forEach { it.close() }; playing = false
            }
        }
    }
    fun close() { playing = false; job?.cancel(); runCatching { track?.pause() } }
}
