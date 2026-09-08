package jp.essential.app.feature.qr

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.graphics.drawable.Icon
import android.graphics.Rect
import android.graphics.RectF
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import jp.essential.app.device.DeviceOptimizer
import jp.essential.app.R
import jp.essential.app.ui.ProgressiveWidget

@Composable
fun QrScannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val optimization = remember { DeviceOptimizer.current() }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var torchEnabled by remember { mutableStateOf(false) }
    var scannedValue by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!permissionGranted) {
        ProgressiveWidget(0, Modifier.fillMaxSize()) {
            CameraPermissionScreen(
                denied = permissionDenied,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onBack = onBack,
            )
        }
        return
    }

    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisGate = remember { AtomicBoolean(false) }
    val qrHitTarget = remember { AtomicReference<QrHitTarget?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        ProgressiveWidget(0, Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        }

        DisposableEffect(previewView, lifecycleOwner) {
            val view = previewView
            if (view == null) {
                onDispose { }
            } else {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                val active = AtomicBoolean(true)
                val tracker = QrDetectionTracker()
                var boundAnalysis: ImageAnalysis? = null
                val listener = Runnable {
                    if (!active.get()) return@Runnable
                    runCatching {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = view.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setResolutionSelector(
                                ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        ResolutionStrategy(
                                            optimization.qrAnalysisSize,
                                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                        ),
                                    )
                                    .build(),
                            )
                            .build()
                        boundAnalysis = analysis
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            analyzeQrFrame(
                                imageProxy = imageProxy,
                                scanner = scanner,
                                gate = analysisGate,
                                active = active,
                                onDetected = { target ->
                                    qrHitTarget.set(target)
                                    if (tracker.update(target?.value, android.os.SystemClock.elapsedRealtime())) {
                                        scannedValue = tracker.value
                                        if (tracker.value != null) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    }
                                },
                            )
                        }
                        provider.unbindAll()
                        val boundCamera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                        camera = boundCamera
                        boundCamera.cameraInfo.zoomState.value?.let { state ->
                            minZoom = state.minZoomRatio
                            maxZoom = minOf(30f, state.maxZoomRatio).coerceAtLeast(minZoom)
                            zoomRatio = state.zoomRatio
                        }

                        val scaleDetector = ScaleGestureDetector(
                            context,
                            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                override fun onScale(detector: ScaleGestureDetector): Boolean {
                                    val next = (zoomRatio * detector.scaleFactor).coerceIn(minZoom, maxZoom)
                                    zoomRatio = next
                                    boundCamera.cameraControl.setZoomRatio(next)
                                    return true
                                }
                            },
                        )
                        view.setOnTouchListener { _, event ->
                            scaleDetector.onTouchEvent(event)
                            if (event.action == android.view.MotionEvent.ACTION_UP && !scaleDetector.isInProgress) {
                                val target = qrHitTarget.get()
                                val tappedQr = target?.let { qrTarget ->
                                    mapQrBoundsToPreview(qrTarget, view).contains(event.x, event.y)
                                } == true
                                if (tappedQr && openRecognizedUrl(context, target?.value.orEmpty())) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                } else {
                                    val point = view.meteringPointFactory.createPoint(event.x, event.y)
                                    val action = FocusMeteringAction.Builder(point)
                                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                        .build()
                                    boundCamera.cameraControl.startFocusAndMetering(action)
                                }
                            }
                            true
                        }
                    }.onFailure { error ->
                        cameraError = error.message ?: "カメラを開始できませんでした"
                    }
                }
                providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
                onDispose {
                    active.set(false)
                    boundAnalysis?.clearAnalyzer()
                    if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
                    view.setOnTouchListener(null)
                    camera = null
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                scanner.close()
                analysisExecutor.shutdownNow()
            }
        }

        ProgressiveWidget(1, Modifier.fillMaxSize()) { ScannerOverlay() }

        ProgressiveWidget(2, Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScannerCircleButton("‹", "戻る", onBack)
                Surface(
                    color = Color.Black.copy(alpha = 0.54f),
                    shape = CircleShape,
                ) {
                    Text(
                        optimization.label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                ScannerCircleButton(
                    text = if (torchEnabled) "●" else "○",
                    description = "ライト",
                    onClick = {
                        torchEnabled = !torchEnabled
                        camera?.cameraControl?.enableTorch(torchEnabled)
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = Color.Black.copy(alpha = 0.58f),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { requestQrTile(context) },
                ) {
                    Text(
                        "クイック設定に追加",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                if (cameraError != null) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(18.dp)) {
                        Text(
                            cameraError.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
                if (maxZoom > minZoom) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.58f),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("倍率（上限 ${"%.1f".format(maxZoom)}×）", color = Color.White, style = MaterialTheme.typography.labelLarge)
                                Text("${"%.1f".format(zoomRatio)}×", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                            Slider(
                                value = zoomRatio,
                                onValueChange = { value ->
                                    zoomRatio = value
                                    camera?.cameraControl?.setZoomRatio(value)
                                },
                                valueRange = minZoom..maxZoom,
                            )
                            Text("最大30×・カメラが対応する範囲で利用できます", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                AnimatedVisibility(
                    visible = scannedValue != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                ) {
                    ScanResultCard(
                        value = scannedValue.orEmpty(),
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("QRコード", scannedValue))
                        },
                        onOpen = {
                            val value = scannedValue.orEmpty()
                            openRecognizedUrl(context, value)
                        },
                    )
                }
            }
        }
        }
    }
}

private fun requestQrTile(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val manager = context.getSystemService(StatusBarManager::class.java)
        val component = ComponentName(context, QrScannerTileService::class.java)
        manager.requestAddTileService(
            component,
            "QRスキャナー",
            Icon.createWithResource(context, R.drawable.ic_qr_tile),
            ContextCompat.getMainExecutor(context),
        ) { result ->
            if (result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED &&
                result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                Toast.makeText(context, "クイック設定の編集画面から追加できます", Toast.LENGTH_LONG).show()
            }
        }
    } else {
        Toast.makeText(context, "クイック設定の編集画面からQRスキャナーを追加してください", Toast.LENGTH_LONG).show()
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
private fun analyzeQrFrame(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    gate: AtomicBoolean,
    active: AtomicBoolean,
    onDetected: (QrHitTarget?) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null || !active.get() || !gate.compareAndSet(false, true)) {
        imageProxy.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    try {
    scanner.process(input)
        .addOnSuccessListener { barcodes ->
            // 複数コードがあるときも、画面中央に最も近いコードを優先する。
            val barcode = barcodes.filter { it.rawValue != null }.minByOrNull { barcode ->
                val bounds = barcode.boundingBox
                if (bounds == null) Float.MAX_VALUE else {
                    val rotated = input.rotationDegrees % 180 != 0
                    val dx = bounds.exactCenterX() - (if (rotated) input.height else input.width) / 2f
                    val dy = bounds.exactCenterY() - (if (rotated) input.width else input.height) / 2f
                    dx * dx + dy * dy
                }
            }
            val rotated = input.rotationDegrees % 180 != 0
            val target = barcode?.let {
                QrHitTarget(
                    value = it.rawValue.orEmpty(),
                    bounds = Rect(it.boundingBox ?: return@let null),
                    imageWidth = if (rotated) input.height else input.width,
                    imageHeight = if (rotated) input.width else input.height,
                )
            }
            if (active.get()) onDetected(target)
        }
        .addOnCompleteListener {
            gate.set(false)
            imageProxy.close()
        }
    } catch (_: Exception) {
        gate.set(false)
        imageProxy.close()
    }
}

private data class QrHitTarget(
    val value: String,
    val bounds: Rect,
    val imageWidth: Int,
    val imageHeight: Int,
)

/** FILL_CENTERで中央クロップされた解析画像の座標を、実際のPreviewView座標へ変換する。 */
private fun mapQrBoundsToPreview(target: QrHitTarget, view: PreviewView): RectF {
    if (target.imageWidth <= 0 || target.imageHeight <= 0 || view.width <= 0 || view.height <= 0) {
        return RectF()
    }
    val scale = maxOf(
        view.width.toFloat() / target.imageWidth,
        view.height.toFloat() / target.imageHeight,
    )
    val offsetX = (view.width - target.imageWidth * scale) / 2f
    val offsetY = (view.height - target.imageHeight * scale) / 2f
    val touchPadding = 18f * view.resources.displayMetrics.density
    return RectF(
        target.bounds.left * scale + offsetX - touchPadding,
        target.bounds.top * scale + offsetY - touchPadding,
        target.bounds.right * scale + offsetX + touchPadding,
        target.bounds.bottom * scale + offsetY + touchPadding,
    )
}

private fun openRecognizedUrl(context: Context, value: String): Boolean {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    }.getOrDefault(false)
}

@Composable
private fun ScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val frameWidth = size.width * 0.72f
        val frameHeight = frameWidth
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) * 0.42f
        val shade = Color.Black.copy(alpha = 0.28f)
        drawRect(shade, topLeft = Offset.Zero, size = Size(size.width, top))
        drawRect(shade, topLeft = Offset(0f, top + frameHeight), size = Size(size.width, size.height - top - frameHeight))
        drawRect(shade, topLeft = Offset(0f, top), size = Size(left, frameHeight))
        drawRect(shade, topLeft = Offset(left + frameWidth, top), size = Size(size.width - left - frameWidth, frameHeight))
        val corner = frameWidth * 0.14f
        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        val color = Color(0xFFC4F45A)
        drawLine(color, Offset(left, top + corner), Offset(left, top), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(left, top), Offset(left + corner, top), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(left + frameWidth - corner, top), Offset(left + frameWidth, top), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(left + frameWidth, top), Offset(left + frameWidth, top + corner), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(left, top + frameHeight - corner), Offset(left, top + frameHeight), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(left, top + frameHeight), Offset(left + corner, top + frameHeight), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(left + frameWidth - corner, top + frameHeight), Offset(left + frameWidth, top + frameHeight), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(left + frameWidth, top + frameHeight), Offset(left + frameWidth, top + frameHeight - corner), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun ScannerCircleButton(
    text: String,
    description: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "QR操作ボタン押下",
    )
    Surface(
        onClick = onClick,
        color = Color.Black.copy(alpha = 0.58f),
        contentColor = Color.White,
        shape = CircleShape,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = true
                shape = CircleShape
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ScanResultCard(
    value: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("読み取り結果", style = MaterialTheme.typography.titleMedium)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCopy, modifier = Modifier.weight(1f)) { Text("コピー") }
                if (value.startsWith("https://") || value.startsWith("http://")) {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("開く") }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionScreen(
    denied: Boolean,
    onRequest: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("QRスキャナー", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            if (denied) "カメラ権限が拒否されました。QRコードの読み取りには権限が必要です。" else "カメラを準備しています。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        if (denied) {
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("カメラ権限を許可") }
            Spacer(Modifier.height(10.dp))
        } else {
            CircularProgressIndicator()
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("戻る") }
    }
}
