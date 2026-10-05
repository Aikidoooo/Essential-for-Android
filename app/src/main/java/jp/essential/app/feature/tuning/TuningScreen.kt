package jp.essential.app.feature.tuning

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.essential.app.feature.subscriptions.SubscriptionGlass
import jp.essential.app.ui.FeatureHeader
import jp.essential.app.ui.fixedHeader
import kotlinx.coroutines.*
import kotlin.math.abs

@Composable
fun TuningScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("tuning", android.content.Context.MODE_PRIVATE) }
    var presetIndex by rememberSaveable { mutableStateOf(preferences.getInt("preset", 0).coerceIn(tuningPresets.indices)) }
    var referenceText by rememberSaveable { mutableStateOf(preferences.getInt("reference", 440).toString()) }
    val reference = referenceText.toIntOrNull()?.takeIf { it in 400..480 }
    var manualNote by rememberSaveable { mutableStateOf<Int?>(null) }
    var running by rememberSaveable { mutableStateOf(false) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var message by remember { mutableStateOf<String?>(null) }
    var frequency by remember { mutableStateOf<Double?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!foreground) { running = false; frequency = null }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        running = granted
        message = if (granted) null else "マイクの許可が必要です。許可できない場合は端末のアプリ設定で変更してください。"
    }
    LaunchedEffect(running, foreground, permission) {
        if (!running || !foreground || !permission) { frequency = null; return@LaunchedEffect }
        try {
            withContext(Dispatchers.IO) {
                val sampleRate = 22050
                val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minBuffer > 0) { "この端末でマイクを開始できません。" }
                val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 16384))
                try {
                    check(recorder.state == AudioRecord.STATE_INITIALIZED) { "マイクを初期化できません。" }
                    recorder.startRecording()
                    check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "マイクを使用できません。" }
                    val samples = ShortArray(4096)
                    var filled = 0
                    val recent = ArrayDeque<Double>()
                    while (isActive) {
                        val read = recorder.read(samples, filled, samples.size - filled, AudioRecord.READ_NON_BLOCKING)
                        check(read >= 0) { "マイクからの読み取りに失敗しました。" }
                        filled += read
                        if (filled == samples.size) {
                            val pitch = detectPitch(samples, sampleRate)
                            if (pitch == null) recent.clear() else {
                                if (recent.isNotEmpty() && abs(centsFrom(pitch, recent.last())) > 100) recent.clear()
                                recent.addLast(pitch)
                                if (recent.size > 3) recent.removeFirst()
                            }
                            val stable = recent.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
                            withContext(Dispatchers.Main) { frequency = stable }
                            filled = 0
                        }
                        delay(12)
                    }
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { running = false; frequency = null; message = "マイクを使用できません。他の録音アプリを閉じて、もう一度開始してください。" }
    }
    val preset = tuningPresets[presetIndex]
    val targetNote = manualNote ?: frequency?.let { measured -> preset.midiNotes.minByOrNull { abs(centsFrom(measured, noteFrequency(it, reference ?: 440))) } }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var directionRevision by remember { mutableIntStateOf(0) }
    fun selectPreset(index: Int) {
        presetIndex = index
        manualNote = tuningPresets[index].midiNotes.first()
        frequency = null
        preferences.edit().putInt("preset", index).apply()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        fixedHeader { FeatureHeader("チューニング", onBack) }
        item {
            TuningPanel(preset, presetIndex, targetNote, manualNote, frequency, reference ?: 440, running, reference != null,
                onPreset = ::selectPreset, onNote = { manualNote = it },
                onToggle = {
                    if (running) running = false else {
                        message = null
                        permission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (permission) running = true else launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }, clockwiseToRaise = { midi ->
                    directionRevision
                    val key = "raise_clockwise_${presetIndex}_$midi"
                    if (preferences.contains(key)) preferences.getBoolean(key, true) else null
                }, onDirectionChange = { midi, clockwise ->
                    preferences.edit().putBoolean("raise_clockwise_${presetIndex}_$midi", clockwise).apply()
                    directionRevision++
                })
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        item { TextButton(onClick = { settingsOpen = !settingsOpen }) { Text(if (settingsOpen) "詳細設定を閉じる" else "詳細設定・その他のプリセット") } }
        if (settingsOpen) item { SubscriptionGlass {
            OutlinedTextField(referenceText, { value ->
                referenceText = value
                value.toIntOrNull()?.takeIf { it in 400..480 }?.let { preferences.edit().putInt("reference", it).apply() }
            }, label = { Text("基準音 A4（400〜480 Hz）") }, singleLine = true,
                isError = reference == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            tuningPresets.forEachIndexed { index, option ->
                FilterChip(selected = index == presetIndex, onClick = { selectPreset(index) },
                    label = { Text("${option.name} · ${option.midiNotes.joinToString(" ") { noteLabel(it) }}") })
            }
        } }
    }
}
