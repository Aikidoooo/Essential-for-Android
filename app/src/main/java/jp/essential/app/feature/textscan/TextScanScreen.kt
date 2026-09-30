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
import jp.essential.app.ui.ProgressiveWidget
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

@Composable
fun TextScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val recognizer = remember { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }
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
        onDispose { alive.set(false); recognizer.close() }
    }

    fun recognize(uri: Uri, temporaryFile: File? = null) {
        busy = true
        error = null
        scope.launch {
            try {
                val input = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
                val text = suspendCancellableCoroutine<String> { continuation ->
                    recognizer.process(input)
                        .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text) }
                        .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                        .addOnCompleteListener { temporaryFile?.delete() }
                }
                if (text.isBlank()) {
                    error = "文字が見つかりませんでした。明るさやピントを確認して、もう一度お試しください。"
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
        ProgressiveWidget(0, Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = 0.2f), Color.Transparent, Color.Black.copy(alpha = 0.35f),
            ))))
        }
        ProgressiveWidget(1, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    GlassBackButton(onClick = onBack, size = 48.dp)
                    Surface(color = Color.Black.copy(alpha = 0.54f), contentColor = Color.White, shape = CircleShape) {
                        Text("文字スキャン", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                    }
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
                            Text("文字を読み取り中", color = Color.White)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Surface(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !busy, color = Color.Black.copy(alpha = 0.34f), contentColor = Color.White,
                            shape = CircleShape, modifier = Modifier.width(104.dp).height(48.dp)) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                GalleryIcon()
                                Spacer(Modifier.width(7.dp))
                                Text("写真", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Surface(onClick = {
                            val photoCapture = capture ?: return@Surface
                            busy = true
                            error = null
                            val file = runCatching { File.createTempFile("text-scan-", ".jpg", context.cacheDir) }.getOrElse {
                                busy = false
                                error = "撮影用の空き容量を確保できませんでした"
                                return@Surface
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
                        }, enabled = capture != null && !busy, color = Color.White, contentColor = Color(0xFF122537),
                            shape = CircleShape, modifier = Modifier.size(68.dp).border(4.dp, Color.White.copy(alpha = 0.45f), CircleShape)) {
                            Box(contentAlignment = Alignment.Center) { Text("撮影", style = MaterialTheme.typography.labelLarge) }
                        }
                        Spacer(Modifier.width(104.dp))
                    }
                    if (permissionGranted && camera != null) {
                        ZoomQuickButtons(zoom, minZoom, maxZoom, applyZoom)
                        ZoomRuler(zoom, minZoom, maxZoom, applyZoom, Modifier.fillMaxWidth())
                    }
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
                    Surface(Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = 0.4f), contentColor = Color(0xFFE5F6FF)) {
                        Box(Modifier.fillMaxSize()) {
                            var selectableText by remember(result) { mutableStateOf(TextFieldValue(result.orEmpty())) }
                            BasicTextField(
                                value = selectableText,
                                onValueChange = { selectableText = it },
                                readOnly = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFFE5F6FF), fontFamily = FontFamily.Monospace,
                                ),
                                modifier = Modifier.fillMaxSize().padding(start = 18.dp, top = 18.dp, end = 56.dp, bottom = 18.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
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
