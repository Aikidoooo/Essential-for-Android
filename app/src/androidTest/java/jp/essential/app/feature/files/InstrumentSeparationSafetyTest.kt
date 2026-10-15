package jp.essential.app.feature.files

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstrumentSeparationSafetyTest {
    @Test fun cancelledRequestCanBeRetried(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File.createTempFile("worker-cancel", ".wav", context.cacheDir)
        AudioStemSeparationEngine.PcmWavWriter(source, 2, 44100).use { it.write(Array(2) { FloatArray(44100) }, 0, 44100) }
        val file = ReferencedFile(Uri.fromFile(source), source.name, "audio/wav", ReferencedMediaType.Audio, source.length())
        val started = CompletableDeferred<Unit>()
        try {
            val task = launch {
                InstrumentSeparationEngine(context).separate(file) { label, _ ->
                    if (label == "分離モデルを読み込み") started.complete(Unit)
                }.close()
            }
            withTimeout(30000) { started.await() }
            task.cancelAndJoin()
            val result = withTimeout(60000) { InstrumentSeparationEngine(context).separate(file) { _, _ -> } }
            try { assertEquals(6, result.files.size) } finally { result.close() }
        } finally { source.delete() }
    }

    @Test fun workerTerminationDoesNotTerminateCaller(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File.createTempFile("worker-safety", ".wav", context.cacheDir)
        AudioStemSeparationEngine.PcmWavWriter(source, 2, 44100).use { it.write(Array(2) { FloatArray(44100) }, 0, 44100) }
        val started = CompletableDeferred<Unit>()
        val callerPid = Process.myPid()
        try {
            val task = async {
                runCatching { InstrumentSeparationEngine(context).separate(
                    ReferencedFile(Uri.fromFile(source), source.name, "audio/wav", ReferencedMediaType.Audio, source.length())) { _, _ ->
                    started.complete(Unit)
                } }
            }
            withTimeout(30000) { started.await() }
            val worker = context.getSystemService(ActivityManager::class.java).runningAppProcesses.first {
                it.processName == context.packageName + ":audio_separation"
            }
            assertNotEquals(callerPid, worker.pid)
            // 検証用の分離プロセスだけを停止し、画面側の生存とエラー通知を確認する。
            Process.killProcess(worker.pid)
            val result = withTimeout(15000) { task.await() }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("プロセスが終了"))
            assertEquals(callerPid, Process.myPid())
        } finally { source.delete() }
    }

    @Test fun reusedPcmBufferClearsPreviousSamples() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File.createTempFile("pcm-reuse", ".wav", context.cacheDir)
        try {
            AudioStemSeparationEngine.PcmWavWriter(source, 2, 44100).use {
                it.write(Array(2) { FloatArray(4) { .5f } }, 0, 4)
            }
            AudioStemSeparationEngine.PcmWavReader(source).use { reader ->
                val buffer = Array(2) { FloatArray(4) }
                reader.readStereoInto(0, 4, buffer)
                assertTrue(buffer.all { it.all { sample -> sample > .49f } })
                reader.readStereoInto(3, 4, buffer)
                assertTrue(buffer.all { it[0] > .49f && it.drop(1).all { sample -> sample == 0f } })
                reader.readStereoInto(4, 4, buffer)
                assertTrue(buffer.all { it.all { sample -> sample == 0f } })
            }
        } finally { source.delete() }
    }
}
