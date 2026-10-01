package jp.essential.app.feature.textscan

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import jp.essential.app.feature.qr.GalleryIcon
import jp.essential.app.feature.qr.ScannerCircleButton
import jp.essential.app.feature.qr.ZoomQuickButtons
import jp.essential.app.feature.qr.ZoomRuler
import jp.essential.app.ui.GlassBackButton
import jp.essential.app.feature.scanner.ScannerCameraControls
import jp.essential.app.feature.scanner.ScannerPreviewTransition
import jp.essential.app.feature.scanner.ScannerModeHeader
import jp.essential.app.feature.qr.ScannerOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// 本文中のバッククォートより長い囲みを使い、認識文字をそのまま保持する。
internal fun textAsCodeBlock(text: String): String {
    val longest = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
    val fence = "`".repeat(maxOf(3, longest + 1))
    return "$fence\n$text\n$fence"
}

private suspend fun <T> Task<T>.awaitScan(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

@Composable
fun TextScanScreen(modePosition: Float = 1f, transitionFrame: android.graphics.Bitmap? = null,
    onTransitionFrame: (android.graphics.Bitmap?) -> Unit = {},
    onTransitionFinished: () -> Unit = {}, onModeChange: () -> Unit = {}, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val recognizer = remember { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }
    val qrScanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    var qrUrls by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var scanWarning by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var torch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val alive = remember { AtomicBoolean(true) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    LaunchedEffect(Unit) { if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA) }
    DisposableEffect(Unit) {
        onDispose { alive.set(false); recognizer.close(); qrScanner.close() }
    }

    fun recognize(uri: Uri, temporaryFile: File? = null) {
        busy = true
        error = null
        scope.launch {
            try {
                val input = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
                val textTask = recognizer.process(input)
                val qrTask = qrScanner.process(input)
                // 両方の認識が画像を読み終えた後に、一時撮影ファイルを回収する。
                Tasks.whenAllComplete(textTask, qrTask).addOnCompleteListener { temporaryFile?.delete() }
                val recognized = runCatching { textTask.awaitScan() }.getOrElse {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    null
                }
                val codes = runCatching { qrTask.awaitScan() }.getOrElse {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    null
                }
                val pieces = recognized?.textBlocks.orEmpty().flatMap { it.lines }.flatMap { line ->
                    line.elements.mapNotNull { element ->
                        element.boundingBox?.let { bounds ->
                            ScanTextPiece(element.text, bounds.left, bounds.top, bounds.right, bounds.bottom)
                        }
                    }
                }
                val text = withContext(Dispatchers.Default) { formatScanLayout(pieces) }
                    .ifBlank { recognized?.text.orEmpty() }
                qrUrls = ArrayList(codes.orEmpty().mapNotNull { code ->
                    (code.url?.url ?: code.rawValue)?.takeIf { value ->
                        val parsed = Uri.parse(value)
                        parsed.scheme?.lowercase() in setOf("http", "https") && !parsed.host.isNullOrBlank()
                    }
                }.distinct())
                scanWarning = when {
                    recognized == null -> "文字の読み取りに失敗しました。"
                    codes == null -> "QRコードの読み取りに失敗しました。"
                    else -> null
                }
                if (text.isBlank() && qrUrls.isEmpty()) {
                    error = "文字やURLのQRコードが見つかりませんでした。明るさやピントを確認して、もう一度お試しください。"
                } else {
                    result = text
                    copied = false
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                temporaryFile?.delete()
                error = "画像の文字を読み取れませんでした。別の写真でお試しください。"
            } finally { busy = false }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !busy) recognize(uri)
    }
    val applyZoom: (Float) -> Unit = {
        zoom = it.coerceIn(minZoom, maxZoom)
        camera?.cameraControl?.setZoomRatio(zoom)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF101623))) {
        if (permissionGranted) {
            AndroidView(factory = { viewContext ->
                PreviewView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            }, modifier = Modifier.fillMaxSize())
        }
        ScannerPreviewTransition(transitionFrame, previewView, onTransitionFinished)
        ScannerOverlay(1f - modePosition)

        DisposableEffect(previewView, permissionGranted, lifecycleOwner) {
            val view = previewView
            val active = AtomicBoolean(true)
            val future = if (view != null && permissionGranted) ProcessCameraProvider.getInstance(context) else null
            var provider: ProcessCameraProvider? = null
            var boundPreview: Preview? = null
            var boundCapture: ImageCapture? = null
            future?.addListener({
                if (active.get()) {
                    runCatching {
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        val preview = Preview.Builder().build().apply { surfaceProvider = view!!.surfaceProvider }
                        val photoCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                        boundPreview = preview
                        boundCapture = photoCapture
                        val boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, photoCapture)
                        camera = boundCamera
                        capture = photoCapture
                        boundCamera.cameraInfo.zoomState.value?.let {
                            minZoom = it.minZoomRatio
                            maxZoom = minOf(30f, it.maxZoomRatio).coerceAtLeast(minZoom)
                            zoom = it.zoomRatio
                        }
                        val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                applyZoom(zoom * detector.scaleFactor)
                                return true
                            }
                        })
                        view!!.setOnTouchListener { _, event ->
                            detector.onTouchEvent(event)
                            if (event.action == android.view.MotionEvent.ACTION_UP && !detector.isInProgress) {
                                val point = view.meteringPointFactory.createPoint(event.x, event.y)
                                boundCamera.cameraControl.startFocusAndMetering(
                                    FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build(),
                                )
                            }
                            true
                        }
                    }.onFailure { error = "カメラを開始できませんでした。写真からも読み取れます。" }
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                active.set(false)
                view?.setOnTouchListener(null)
                boundPreview?.let { provider?.unbind(it) }
                boundCapture?.let { provider?.unbind(it) }
                camera = null
                capture = null
                torch = false
            }
        }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = 0.2f), Color.Transparent, Color.Black.copy(alpha = 0.35f),
            ))))
        }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    GlassBackButton(onClick = onBack, size = 48.dp)
                    ScannerModeHeader(true, modePosition)
                    ScannerCircleButton(text = if (torch) "●" else "○", description = "ライト", onClick = {
                        if (camera?.cameraInfo?.hasFlashUnit() == true) {
                            torch = !torch
                            camera?.cameraControl?.enableTorch(torch)
                        }
                    })
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!permissionGranted) {
                        Surface(color = Color.Black.copy(alpha = 0.65f), contentColor = Color.White, shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("撮影にはカメラ権限が必要です")
                                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("カメラを許可") }
                            }
                        }
                    }
                    error?.let { message ->
                        Surface(color = Color(0xFF35232C).copy(alpha = 0.94f), contentColor = Color.White, shape = RoundedCornerShape(20.dp)) {
                            Text(message, Modifier.padding(16.dp))
                        }
                    }
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Text("文字とQRコードを読み取り中", color = Color.White)
                        }
                    }
                    ScannerCameraControls(
                            modePosition = modePosition,
                        textMode = true, onModeChange = { onTransitionFrame(previewView?.bitmap); onModeChange() },
                        zoom = zoom, minZoom = minZoom, maxZoom = maxZoom, onZoom = applyZoom,
                        onPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onCapture = {
                            val photoCapture = capture ?: return@ScannerCameraControls
                            busy = true
                            error = null
                            val file = runCatching { File.createTempFile("text-scan-", ".jpg", context.cacheDir) }.getOrElse {
                                busy = false
                                error = "撮影用の空き容量を確保できませんでした"
                                return@ScannerCameraControls
                            }
                            photoCapture.targetRotation = previewView?.display?.rotation ?: android.view.Surface.ROTATION_0
                            photoCapture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        if (alive.get()) recognize(Uri.fromFile(file), file) else file.delete()
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        file.delete()
                                        if (alive.get()) { busy = false; error = "撮影できませんでした。もう一度お試しください。" }
                                    }
                                })

                        }, captureEnabled = capture != null, busy = busy,
                    )
                }
            }
        }
        AnimatedVisibility(visible = result != null, enter = fadeIn() + slideInVertically { it / 3 }) {
            BackHandler { result = null }
            Surface(Modifier.fillMaxSize(), color = Color(0xFF131D30), contentColor = Color.White) {
                Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF20394B), Color(0xFF221D36)))).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GlassBackButton(onClick = { result = null })
                        Text("読み取り結果", style = MaterialTheme.typography.titleLarge)
                    }
                    if (qrUrls.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.1f)) {
                            Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("QRコードのURL", color = Color(0xFFAAE6F3), style = MaterialTheme.typography.titleSmall)
                                SelectionContainer { Text(qrUrls.joinToString("\n"), color = Color.White) }
                            }
                        }
                    }
                    scanWarning?.let { Text(it, color = Color(0xFFFFD9A8)) }
                    Surface(Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = 0.4f), contentColor = Color(0xFFE5F6FF)) {
                        Box(Modifier.fillMaxSize()) {
                            var selectableText by remember(result) { mutableStateOf(TextFieldValue(result.orEmpty())) }
                            Box(Modifier.fillMaxSize().padding(start = 18.dp, top = 56.dp, end = 18.dp, bottom = 18.dp)
                                .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                            BasicTextField(
                                value = selectableText,
                                onValueChange = { selectableText = it },
                                readOnly = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFFE5F6FF), fontFamily = FontFamily.Monospace,
                                ),
                                modifier = Modifier.widthIn(min = 260.dp),
                            )
                            }
                            IconButton(
                                onClick = {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .setPrimaryClip(ClipData.newPlainText("コードブロック", textAsCodeBlock(result.orEmpty())))
                                    copied = true
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                },
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                    .semantics { contentDescription = if (copied) "コピーしました" else "コードブロックをコピー" },
                            ) {
                                Canvas(Modifier.size(20.dp)) {
                                    val stroke = Stroke(width = 1.5.dp.toPx())
                                    val tint = if (copied) Color(0xFFAAE6F3) else Color.White
                                    drawRoundRect(tint, Offset(size.width * 0.34f, size.height * 0.08f),
                                        Size(size.width * 0.56f, size.height * 0.64f), CornerRadius(3.dp.toPx()), style = stroke)
                                    drawRoundRect(tint, Offset(size.width * 0.1f, size.height * 0.3f),
                                        Size(size.width * 0.56f, size.height * 0.64f), CornerRadius(3.dp.toPx()), style = stroke)
                                }
                            }
                        }
                    }
                    TextButton(onClick = { result = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("もう一度スキャン", color = Color.White)
                    }
                }
            }
        }
    }
}
