package jp.essential.app.feature.files

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** ネイティブ推論が終了しても画面のプロセスを巻き込まない専用サービス。 */
class InstrumentSeparationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { request ->
        if (request.what == START) {
            job?.cancel()
            val reply = request.replyTo
            val data = request.data
            job = scope.launch { workMutex.withLock {
                var result: InstrumentStems? = null
                fun send(kind: Int, data: Bundle) = reply.send(Message.obtain(null, kind).apply { this.data = data })
                try {
                    val file = ReferencedFile(Uri.parse(data.getString("uri")), data.getString("name")!!,
                        data.getString("mime")!!, ReferencedMediaType.valueOf(data.getString("type")!!), 0)
                    result = InstrumentSeparationEngine(this@InstrumentSeparationService).separateLocally(file) { label, value ->
                        send(PROGRESS, Bundle().apply { putString("stage", label); value?.let { putFloat("progress", it) } })
                    }
                    currentCoroutineContext().ensureActive()
                    send(SUCCESS, Bundle().apply {
                        InstrumentStem.entries.forEach { putString(it.name, result!!.files.getValue(it).absolutePath) }
                    })
                    result = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Throwable) {
                    runCatching { send(FAILURE, Bundle().apply { putString("error", if (error is OutOfMemoryError)
                        "音声分離に必要なメモリが不足しました。他のアプリを閉じて再実行してください" else error.message ?: "音声分離に失敗しました") }) }
                } finally { result?.close() }
            } }
        }
        true
    })
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onUnbind(intent: Intent): Boolean { job?.cancel(); return false }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        // サービスの再生成中も、中断待ちのネイティブ推論との重複を防ぐ。
        private val workMutex = Mutex()
        internal const val START = 1
        internal const val PROGRESS = 2
        internal const val SUCCESS = 3
        internal const val FAILURE = 4
    }
}

/** 同時分離を直列化し、離脱・失敗時は接続を必ず解放する。 */
internal object InstrumentSeparationClient {
    private val mutex = Mutex()
    suspend fun separate(context: Context, file: ReferencedFile, progress: (String, Float?) -> Unit): InstrumentStems = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            val app = context.applicationContext
            var bound = false
            lateinit var connection: ServiceConnection
            try {
                suspendCancellableCoroutine { continuation ->
                    val reply = Messenger(Handler(Looper.getMainLooper()) { response ->
                        val data = response.data
                        when (response.what) {
                            InstrumentSeparationService.PROGRESS -> if (continuation.isActive) progress(data.getString("stage")!!,
                                if (data.containsKey("progress")) data.getFloat("progress") else null)
                            InstrumentSeparationService.SUCCESS -> {
                                val result = InstrumentStems(InstrumentStem.entries.associateWith { File(data.getString(it.name)!!) })
                                if (continuation.isActive) continuation.resume(result) { _, value, _ -> value.close() }
                                else result.close()
                            }
                            InstrumentSeparationService.FAILURE -> if (continuation.isActive)
                                continuation.resumeWithException(IllegalStateException(data.getString("error")))
                        }
                        true
                    })
                    fun failed() {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException(
                            "音声分離プロセスが終了しました。メモリ不足の可能性があります。他のアプリを閉じて再実行してください"))
                    }
                    connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                            if (!continuation.isActive) return
                            try {
                                Messenger(binder).send(Message.obtain(null, InstrumentSeparationService.START).apply {
                                    replyTo = reply
                                    data = Bundle().apply {
                                        putString("uri", file.uri.toString()); putString("name", file.name)
                                        putString("mime", file.mimeType); putString("type", file.type.name)
                                    }
                                })
                            } catch (_: RemoteException) { failed() }
                        }
                        override fun onServiceDisconnected(name: ComponentName) = failed()
                        override fun onBindingDied(name: ComponentName) = failed()
                        override fun onNullBinding(name: ComponentName) = failed()
                    }
                    bound = app.bindService(Intent(app, InstrumentSeparationService::class.java), connection, Context.BIND_AUTO_CREATE)
                    if (!bound) failed()
                }
            } finally { if (bound) app.unbindService(connection) }
        }
    }
}
