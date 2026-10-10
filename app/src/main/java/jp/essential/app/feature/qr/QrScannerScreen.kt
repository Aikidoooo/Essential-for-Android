package jp.essential.app.feature.qr

import jp.essential.app.ui.fixedHeader
import jp.essential.app.feature.scanner.scannerSafeArea
import jp.essential.app.ui.FeatureHeader
import jp.essential.app.ui.GlassFeatureTitle

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
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
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
import jp.essential.app.feature.scanner.ScannerCameraControls
import jp.essential.app.feature.scanner.ScannerPreviewTransition
import jp.essential.app.feature.scanner.ScannerModeHeader
import jp.essential.app.feature.scanner.ScannerMotionProgress
import androidx.compose.ui.platform.testTag

@Composable
fun QrScannerScreen(modePosition: Float = 0f, transitionFrame: android.graphics.Bitmap? = null,
    onTransitionFrame: (android.graphics.Bitmap?) -> Unit = {},
    onTransitionFinished: () -> Unit = {}, onModeChange: () -> Unit = {}, onBack: () -> Unit) {
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
    var photoScanInProgress by remember { mutableStateOf(false) }
    val qrPreferences = remember(context) {
        context.getSharedPreferences("qr_scanner", Context.MODE_PRIVATE)
    }
    var autoOpenRecognizedUrls by rememberSaveable {
        mutableStateOf(qrPreferences.getBoolean("auto_open_urls", false))
    }
    val autoOpenUrls by rememberUpdatedState(autoOpenRecognizedUrls)
    val applyZoomRatio: (Float) -> Unit = { requestedRatio ->
        val next = requestedRatio.coerceIn(minZoom, maxZoom)
        zoomRatio = next
        camera?.cameraControl?.setZoomRatio(next)
    }

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
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                CameraPermissionScreen(
                    denied = permissionDenied,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onBack = onBack,
                )
                TextButton(onClick = onModeChange, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)) {
                    Text("文字スキャンへ切り替える")
                }
            }
        }
        return
    }

    val scanner = remember { createQrBarcodeScanner() }
    val photoScanner = remember { createQrBarcodeScanner() }
    val photoAlive = remember { AtomicBoolean(true) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisGate = remember { AtomicBoolean(false) }
    val qrHitTarget = remember { AtomicReference<QrHitTarget?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { imageUri ->
        if (imageUri != null) {
            scannedValue = null
            photoScanInProgress = true
            val inputImage = runCatching { InputImage.fromFilePath(context, imageUri) }
                .getOrElse { error ->
                    photoScanInProgress = false
                    Toast.makeText(
                        context,
                        error.message ?: "写真を読み込めませんでした",
                        Toast.LENGTH_LONG,
                    ).show()
                    null
                }
            if (inputImage != null) {
                val mainExecutor = ContextCompat.getMainExecutor(context)
                photoScanner.process(inputImage)
                    .addOnSuccessListener(mainExecutor) { barcodes ->
                        if (!photoAlive.get()) return@addOnSuccessListener
                        photoScanInProgress = false
                        val value = barcodes
                            .filter { it.rawValue != null }
                            .maxByOrNull { barcode ->
                                barcode.boundingBox?.let { bounds ->
                                    bounds.width().toLong() * bounds.height().toLong()
                                } ?: 0L
                            }
                            ?.rawValue
                        if (value == null) {
                            Toast.makeText(context, "写真にQRコードが見つかりませんでした", Toast.LENGTH_LONG).show()
                        } else {
                            scannedValue = value
                            if (openRecognizedUrl(context, value)) {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            } else {
                                Toast.makeText(context, "QRコードを読み取りました。結果を確認してください", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .addOnFailureListener(mainExecutor) {
                        if (!photoAlive.get()) return@addOnFailureListener
                        photoScanInProgress = false
                        Toast.makeText(context, "写真のQRコードを読み取れませんでした", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
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

        ScannerPreviewTransition(transitionFrame, previewView, onTransitionFinished)

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
                                        val detectedValue = tracker.value
                                        if (detectedValue != null) {
                                            if (autoOpenUrls) {
                                                openRecognizedUrl(context, detectedValue)
                                            }
                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        }
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
                photoAlive.set(false)
                scanner.close()
                photoScanner.close()
                analysisExecutor.shutdownNow()
            }
        }

        ScannerOverlay(1f - modePosition)

        Box(Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .scannerSafeArea()
                        .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("scanner-top-controls"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        jp.essential.app.ui.GlassBackButton(onClick = onBack, size = 48.dp)
                        Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            GlassFeatureTitle("スキャナー")
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

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TextButton(onClick = { requestQrTile(context) }) { Text("クイック設定に追加") }
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
                        if (cameraError != null) {
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(18.dp)) {
                                Text(
                                    cameraError.orEmpty(),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                        if (photoScanInProgress) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Text("写真のQRコードを確認中", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        ScannerCameraControls(
                            modePosition = modePosition,
                            textMode = false, onModeChange = { onTransitionFrame(previewView?.bitmap); onModeChange() },
                            zoom = zoomRatio, minZoom = minZoom, maxZoom = maxZoom, onZoom = applyZoomRatio,
                            onPhoto = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onCapture = {},
                            captureEnabled = camera != null, busy = photoScanInProgress,
                            auto = autoOpenRecognizedUrls,
                            onAutoChange = { enabled ->
                                autoOpenRecognizedUrls = enabled
                                qrPreferences.edit().putBoolean("auto_open_urls", enabled).apply()
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class ZoomPreset(
    val label: String,
    val ratio: Float,
    val available: Boolean,
)

private data class ZoomRulerMark(
    val label: String,
    val ratio: Float,
)

@Composable
internal fun ZoomQuickButtons(
    zoomRatio: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val presets = listOf(
        ZoomPreset("W", minZoom, minZoom < 1f),
        ZoomPreset("1x", 1f, 1f in minZoom..maxZoom),
        ZoomPreset("3.5x", 3.5f, 3.5f in minZoom..maxZoom),
        ZoomPreset("10x", 10f, 10f in minZoom..maxZoom),
    )

    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.28f),
        shape = CircleShape,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.2.dp, vertical = 3.36.dp),
            horizontalArrangement = Arrangement.spacedBy(2.52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            presets.forEach { preset ->
                val selected = preset.available && kotlin.math.abs(zoomRatio - preset.ratio) < 0.06f
                val interaction = remember(preset.label) { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed && preset.available) 0.91f else 1f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
                    label = "QRズームボタン",
                )
                val selectionAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(180),
                    label = "QRズーム選択枠",
                )
                Box(
                    modifier = Modifier
                        .size(38.64.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .background(Color.White.copy(alpha = 0.10f * selectionAlpha), CircleShape)
                        .border(
                            width = 1.5.dp,
                            color = Color.White.copy(alpha = selectionAlpha),
                            shape = CircleShape,
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = preset.available,
                        ) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onZoomChange(preset.ratio)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = preset.label,
                        color = Color.White.copy(alpha = if (preset.available) 1f else 0.38f),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ZoomRuler(
    zoomRatio: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
) {
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val currentZoomRatio by rememberUpdatedState(zoomRatio)
    val curveDepth = 14.dp
    val mainRatios = listOf(1f, 2f, 3f, 5f, 10f, 15f, 20f, 25f, 30f)
    val marks = remember(minZoom, maxZoom) {
        mainRatios.filter { it in minZoom..maxZoom }.map { ZoomRulerMark("${it.toInt()}x", it) }
    }
    // 目盛りを横へ広く配置し、高倍率側でも主目盛りの文字を重ねない。
    val rulerSpan = 2.5f
    val currentFraction = animateFloatAsState(
        targetValue = zoomRatioToFraction(zoomRatio, minZoom, maxZoom),
        animationSpec = tween(100, easing = androidx.compose.animation.core.LinearEasing),
        label = "ズーム目盛りの滑走",
    )
    val tickValues = remember(minZoom, maxZoom) {
        (kotlin.math.ceil(minZoom * 10).toInt()..kotlin.math.floor(maxZoom * 10).toInt()).map { it / 10f }
    }

    BoxWithConstraints(
        modifier = modifier
            .height(82.dp)
            .clipToBounds()
            .semantics {
                contentDescription = "ズーム倍率"
                stateDescription = "${"%.1f".format(zoomRatio)}倍"
                progressBarRangeInfo = ProgressBarRangeInfo(zoomRatio, minZoom..maxZoom)
                setProgress { value ->
                    if (maxZoom <= minZoom) {
                        false
                    } else {
                        currentOnZoomChange(value.coerceIn(minZoom, maxZoom))
                        true
                    }
                }
            }
            .pointerInput(minZoom, maxZoom) {
                var dragFraction = 0.5f
                if (gesturesEnabled) detectHorizontalDragGestures(
                    onDragStart = {
                        dragFraction = zoomRatioToFraction(currentZoomRatio, minZoom, maxZoom)
                    },
                ) { change, dragAmount ->
                    change.consume()
                    if (maxZoom > minZoom && size.width > 0) {
                        dragFraction = (dragFraction - dragAmount / (size.width * rulerSpan)).coerceIn(0f, 1f)
                        currentOnZoomChange(fractionToZoomRatio(dragFraction, minZoom, maxZoom))
                    }
                }
            }
            .pointerInput(minZoom, maxZoom) {
                if (gesturesEnabled) detectTapGestures { position ->
                    if (maxZoom > minZoom && size.width > 0) {
                        val fraction = currentFraction.value
                        val selected = (fraction + (position.x / size.width - 0.5f) / rulerSpan).coerceIn(0f, 1f)
                        currentOnZoomChange(fractionToZoomRatio(selected, minZoom, maxZoom))
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerDip = curveDepth.toPx()
            val minorTickHeight = 5.dp.toPx()
            val mediumTickHeight = 9.dp.toPx()
            val majorTickHeight = 14.dp.toPx()
            fun arcBaseline(fraction: Float): Float {
                val normalized = fraction * 2f - 1f
                val curve = 1f - normalized * normalized
                return size.height * 0.48f + centerDip * curve
            }

            // 指定の主目盛りに加え、0.1倍刻みの短い補助目盛りを描く。
            for (ratio in tickValues) {
                val fraction = zoomRatioToFraction(ratio, minZoom, maxZoom)
                val screenFraction = 0.5f + (fraction - currentFraction.value) * rulerSpan
                if (screenFraction !in -0.02f..1.02f) continue
                val x = size.width * screenFraction
                val index = kotlin.math.round(ratio * 10).toInt()
                val major = mainRatios.any { kotlin.math.abs(ratio - it) < 0.001f }
                val medium = index % 5 == 0
                val tickHeight = when {
                    major -> majorTickHeight
                    medium -> mediumTickHeight
                    else -> minorTickHeight
                }
                val baseline = arcBaseline(screenFraction)
                drawLine(
                    color = Color.White.copy(alpha = if (major) 0.92f else if (medium) 0.68f else 0.38f),
                    start = Offset(x, baseline - tickHeight / 2f),
                    end = Offset(x, baseline + tickHeight / 2f),
                    strokeWidth = if (major) 1.7.dp.toPx() else 1.15.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val markerX = size.width * 0.5f
            val markerBaseline = arcBaseline(0.5f)
            val markerWidth = 5.dp.toPx()
            val markerHeight = 34.dp.toPx()
            drawRoundRect(
                color = Color(0xFFFF3948),
                topLeft = Offset(markerX - markerWidth / 2f, markerBaseline - 22.dp.toPx()),
                size = Size(markerWidth, markerHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(markerWidth / 2f),
            )
        }

        val labelWidth = 38.dp
        marks.forEach { mark ->
            val fraction = zoomRatioToFraction(mark.ratio, minZoom, maxZoom)
            Text(
                text = mark.label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(labelWidth).graphicsLayer {
                    val screenFraction = 0.5f + (fraction - currentFraction.value) * rulerSpan
                    val normalized = screenFraction * 2f - 1f
                    val curve = 1f - normalized * normalized
                    translationX = maxWidth.toPx() * screenFraction - labelWidth.toPx() / 2f
                    translationY = maxHeight.toPx() * 0.48f + curveDepth.toPx() * curve - 34.dp.toPx()
                    // 端と中央の倍率表示へ近づくと、文字を滑らかに薄くする。
                    val edgeFade = minOf(screenFraction, 1f - screenFraction) / 0.06f
                    val centerFade = (kotlin.math.abs(screenFraction - 0.5f) - 0.06f) / 0.05f
                    alpha = minOf(edgeFade, centerFade).coerceIn(0f, 1f)
                },
            )
        }
        Text(
            text = String.format(java.util.Locale.US, "%.1fx", zoomRatio),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(x = maxWidth / 2 - labelWidth / 2, y = maxHeight * 0.48f + curveDepth - 34.dp)
                .width(labelWidth),
        )
    }
}

internal fun zoomRatioToFraction(value: Float, minZoom: Float, maxZoom: Float): Float {
    if (minZoom <= 0f || maxZoom <= minZoom) return 0.5f
    return (
        kotlin.math.ln(value.coerceIn(minZoom, maxZoom) / minZoom) /
            kotlin.math.ln(maxZoom / minZoom)
        ).coerceIn(0f, 1f)
}

internal fun fractionToZoomRatio(fraction: Float, minZoom: Float, maxZoom: Float): Float {
    if (minZoom <= 0f || maxZoom <= minZoom) return minZoom
    return (minZoom * kotlin.math.exp(kotlin.math.ln(maxZoom / minZoom) * fraction.coerceIn(0f, 1f)))
        .coerceIn(minZoom, maxZoom)
}

private fun requestQrTile(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val manager = context.getSystemService(StatusBarManager::class.java)
        val component = ComponentName(context, QrScannerTileService::class.java)
        manager.requestAddTileService(
            component,
            "スキャナー",
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
        Toast.makeText(context, "クイック設定の編集画面からスキャナーを追加してください", Toast.LENGTH_LONG).show()
    }
}

private fun createQrBarcodeScanner(): BarcodeScanner {
    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    return BarcodeScanning.getClient(options)
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
internal fun GalleryIcon() {
    Canvas(Modifier.size(20.dp)) {
        val strokeWidth = 1.7.dp.toPx()
        val inset = 2.dp.toPx()
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawCircle(
            color = Color.White,
            radius = 1.5.dp.toPx(),
            center = Offset(size.width * 0.68f, size.height * 0.34f),
        )
        val landscape = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.73f)
            lineTo(size.width * 0.43f, size.height * 0.47f)
            lineTo(size.width * 0.58f, size.height * 0.62f)
            lineTo(size.width * 0.72f, size.height * 0.51f)
            lineTo(size.width * 0.84f, size.height * 0.73f)
        }
        drawPath(
            path = landscape,
            color = Color.White,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@Composable
internal fun ScannerOverlay(visibility: Float = 1f) {
    val progress = visibility.coerceIn(0f, 1f)
    // 同じ進行度から影と枠を描き、切り替えや方向反転でも位置を連続させる。
    val shadeProgress = progress * progress * (3f - 2f * progress)
    val safePadding = jp.essential.app.feature.scanner.scannerSafePadding()
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    Canvas(modifier = Modifier.fillMaxSize().testTag("scanner-qr-frame")
        .semantics { this[ScannerMotionProgress] = progress }
        ) {
        val safeLeft = safePadding.calculateLeftPadding(layoutDirection).toPx()
        val safeRight = safePadding.calculateRightPadding(layoutDirection).toPx()
        val safeTop = safePadding.calculateTopPadding().toPx()
        val safeBottom = safePadding.calculateBottomPadding().toPx()
        val availableWidth = (size.width - safeLeft - safeRight).coerceAtLeast(1f)
        val availableHeight = (size.height - safeTop - safeBottom).coerceAtLeast(1f)
        val baseWidth = availableWidth * 0.72f
        val frameWidth = baseWidth * (0.96f + 0.04f * progress)
        val frameHeight = frameWidth
        val left = safeLeft + (availableWidth - frameWidth) / 2f
        val top = safeTop + (availableHeight - baseWidth) * 0.42f + (baseWidth - frameHeight) / 2f
        val shade = Color.Black.copy(alpha = 0.28f * shadeProgress)
        drawRect(shade, topLeft = Offset.Zero, size = Size(size.width, top))
        drawRect(shade, topLeft = Offset(0f, top + frameHeight), size = Size(size.width, size.height - top - frameHeight))
        drawRect(shade, topLeft = Offset(0f, top), size = Size(left, frameHeight))
        drawRect(shade, topLeft = Offset(left + frameWidth, top), size = Size(size.width - left - frameWidth, frameHeight))
        val corner = frameWidth * 0.14f
        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        val color = Color(0xFFC4F45A).copy(alpha = progress)
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
internal fun ScannerCircleButton(
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
            .scannerSafeArea()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FeatureHeader("スキャナー", onBack)
        Spacer(Modifier.weight(1f))
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
        Spacer(Modifier.weight(1f))
    }
}
