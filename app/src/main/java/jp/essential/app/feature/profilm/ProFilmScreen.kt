package jp.essential.app.feature.profilm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import jp.essential.app.ui.progressiveItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import jp.essential.app.ui.EssentialMediaPickerContract

/** メーカー別の現像エンジン設定。シーンリニア化後の色変換と階調設計を持つ。 */
private enum class FilmLook(
    val title: String,
    val description: String,
    val monochrome: Boolean = false,
    val saturation: Float = 1f,
    val contrast: Float = 1f,
    val exposure: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val shadowLift: Float = 0f,
    val highlightSoftness: Float = 0f,
    val blackCrush: Float = 0f,
    val redGain: Float = 1f,
    val greenGain: Float = 1f,
    val blueGain: Float = 1f,
    val redAccent: Float = 0f,
    val greenAccent: Float = 0f,
    val blueAccent: Float = 0f,
    val colorMatrix: FloatArray = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f,
    ),
    val toneKnee: Float = 0.78f,
    val shoulder: Float = 1.8f,
    val chromaCompression: Float = 0.04f,
    val microContrast: Float = 0.018f,
    val halation: Float = 0.008f,
    val grain: Float = 0f,
    val vignette: Float = 0f,
) {
    M9(
        "Leica M9", "CCDの色分離と深い赤、締まった中間調を再生成",
        saturation = 1.08f, contrast = 1.12f, exposure = -0.015f, warmth = 0.018f,
        blackCrush = 0.018f, redGain = 1.025f, blueGain = 0.985f,
        redAccent = 0.045f, greenAccent = 0.012f, blueAccent = -0.018f,
        colorMatrix = floatArrayOf(1.08f, -0.035f, -0.045f, -0.015f, 1.04f, -0.025f, 0.01f, -0.03f, 1.03f),
        toneKnee = 0.72f, shoulder = 2.2f, chromaCompression = 0.08f, microContrast = 0.032f, halation = 0.016f,
        grain = 0.012f, vignette = 0.055f,
    ),
    M3(
        "Leica M3", "銀塩モノクロ現像の深い黒と細かな粒状感を再生成",
        monochrome = true, contrast = 1.16f, warmth = 0.016f,
        highlightSoftness = 0.11f, blackCrush = 0.024f,
        toneKnee = 0.68f, shoulder = 2.4f, chromaCompression = 0.02f, microContrast = 0.027f, halation = 0.009f,
        grain = 0.052f, vignette = 0.105f,
    ),
    Xiaomi(
        "Leica (Xiaomi Original)", "Leica Authentic系の自然で繊細な発色を再生成",
        saturation = 0.92f, contrast = 1.095f, exposure = -0.02f, warmth = 0.014f,
        highlightSoftness = 0.13f, blackCrush = 0.016f,
        redGain = 1.012f, blueGain = 0.992f, redAccent = 0.018f,
        colorMatrix = floatArrayOf(1.035f, -0.02f, -0.015f, 0.0f, 1.025f, -0.025f, -0.01f, 0.01f, 1.01f),
        toneKnee = 0.76f, shoulder = 2.0f, chromaCompression = 0.1f, microContrast = 0.021f, halation = 0.012f,
        grain = 0.009f, vignette = 0.075f,
    ),
    Oppo(
        "Hasselblad (OPPO Original)", "Natural Colour系の穏やかで正確な階調を再生成",
        saturation = 0.985f, contrast = 1.025f, exposure = 0.012f, warmth = 0.012f,
        shadowLift = 0.012f, highlightSoftness = 0.14f,
        redGain = 1.008f, greenGain = 1.004f, blueGain = 0.992f,
        redAccent = 0.012f, greenAccent = 0.008f, grain = 0.006f, vignette = 0.025f,
        colorMatrix = floatArrayOf(1.015f, -0.005f, -0.01f, 0.0f, 1.015f, -0.015f, -0.01f, 0.0f, 1.02f),
        toneKnee = 0.8f, shoulder = 1.7f, chromaCompression = 0.13f, microContrast = 0.014f, halation = 0.007f,
    ),
    Huawei(
        "Huawei (Huaweiスマートフォン)", "XMAGE系の明瞭な色とハイライトを再生成",
        saturation = 1.055f, contrast = 1.075f, exposure = 0.018f, warmth = 0.004f,
        highlightSoftness = 0.12f, blackCrush = 0.008f,
        redGain = 1.006f, greenGain = 1.012f, blueGain = 1.006f,
        greenAccent = 0.022f, blueAccent = 0.016f, grain = 0.004f, vignette = 0.015f,
        colorMatrix = floatArrayOf(1.025f, -0.015f, -0.01f, -0.01f, 1.045f, -0.035f, -0.005f, 0.0f, 1.03f),
        toneKnee = 0.77f, shoulder = 2.1f, chromaCompression = 0.06f, microContrast = 0.024f, halation = 0.01f,
    ),
    Vivo(
        "ZEISS (Vivo Original)", "ZEISS Natural Color系の忠実でニュートラルな色を再生成",
        saturation = 0.945f, contrast = 1.015f, exposure = 0.008f, warmth = -0.003f,
        shadowLift = 0.008f, highlightSoftness = 0.1f,
        redGain = 1.002f, greenGain = 1.004f, blueGain = 1.006f,
        grain = 0.003f, vignette = 0.012f,
        colorMatrix = floatArrayOf(1.01f, -0.005f, -0.005f, -0.005f, 1.01f, -0.005f, -0.005f, 0.0f, 1.015f),
        toneKnee = 0.82f, shoulder = 1.55f, chromaCompression = 0.16f, microContrast = 0.012f, halation = 0.006f,
    ),
    Xperia(
        "Sony (Xperia Original)", "Xperia Creative Look系の自然で透明感のある仕上がりを再生成",
        saturation = 0.99f, contrast = 1.045f, exposure = 0.006f, warmth = -0.006f,
        shadowLift = 0.006f, highlightSoftness = 0.09f,
        redGain = 1.003f, greenGain = 1.002f, blueGain = 1.012f,
        blueAccent = 0.014f, grain = 0.004f, vignette = 0.018f,
        colorMatrix = floatArrayOf(1.02f, -0.01f, -0.01f, -0.01f, 1.015f, -0.005f, 0.0f, -0.01f, 1.035f),
        toneKnee = 0.8f, shoulder = 1.8f, chromaCompression = 0.09f, microContrast = 0.02f, halation = 0.008f,
    ),
    Fujifilm(
        "FUJIFILM (PROVIA)", "PROVIA系の記憶色と標準的な階調を再生成",
        saturation = 1.075f, contrast = 1.045f, exposure = 0.008f, warmth = -0.002f,
        tint = 0.004f, shadowLift = 0.005f, highlightSoftness = 0.08f,
        redGain = 0.998f, greenGain = 1.012f, blueGain = 1.012f,
        redAccent = -0.008f, greenAccent = 0.035f, blueAccent = 0.038f,
        colorMatrix = floatArrayOf(1.035f, -0.02f, -0.015f, -0.01f, 1.045f, -0.035f, -0.015f, 0.0f, 1.055f),
        toneKnee = 0.77f, shoulder = 1.9f, chromaCompression = 0.08f, microContrast = 0.026f, halation = 0.014f,
        grain = 0.012f, vignette = 0.02f,
    ),
}

private fun clampChannel(value: Float): Float = value.coerceIn(0f, 1f)

private fun srgbToLinear(value: Float): Float {
    val normalized = value.coerceIn(0f, 1f)
    return if (normalized <= 0.04045f) normalized / 12.92f else ((normalized + 0.055f) / 1.055f).pow(2.4f)
}

private fun linearToSrgb(value: Float): Float {
    val normalized = value.coerceAtLeast(0f)
    return if (normalized <= 0.0031308f) normalized * 12.92f else 1.055f * normalized.pow(1f / 2.4f) - 0.055f
}

private fun pixelLuminance(pixel: Int): Float {
    val red = (pixel ushr 16 and 0xFF) / 255f
    val green = (pixel ushr 8 and 0xFF) / 255f
    val blue = (pixel and 0xFF) / 255f
    return red * 0.2126f + green * 0.7152f + blue * 0.0722f
}

/** 現像エンジンごとのトーンカーブでハイライトをロールオフする。 */
private fun applyEngineTone(value: Float, look: FilmLook): Float {
    val safeValue = value.coerceAtLeast(0f)
    val mapped = if (safeValue <= look.toneKnee) {
        safeValue
    } else {
        val span = (1f - look.toneKnee).coerceAtLeast(0.05f)
        look.toneKnee + span * (1f - exp(-(safeValue - look.toneKnee) * look.shoulder))
    }
    var result = mapped + look.shadowLift * (1f - mapped).coerceAtLeast(0f).pow(2)
    result -= look.blackCrush * (1f - result).coerceAtLeast(0f).pow(2)
    val highlight = ((result - 0.62f) / 0.38f).coerceAtLeast(0f)
    result -= highlight.pow(2) * look.highlightSoftness * 0.16f
    return clampChannel((result - 0.5f) * look.contrast + 0.5f)
}

/** 入力をシーンリニアへ戻し、メーカー別の色行列・階調・光学特性で再生成する。 */
private suspend fun regenerateFilmThroughEngine(source: Bitmap, look: FilmLook, strength: Float): Bitmap {
    val width = source.width
    val height = source.height
    val input = IntArray(width * height)
    val output = IntArray(input.size)
    source.getPixels(input, 0, width, 0, 0, width, height)
    val exposureScale = 2.0.pow(look.exposure.toDouble()).toFloat()
    val centerX = (width - 1) / 2f
    val centerY = (height - 1) / 2f
    val radiusX = max(centerX, 1f)
    val radiusY = max(centerY, 1f)

    for (y in 0 until height) {
        if (y % 24 == 0) currentCoroutineContext().ensureActive()
        val normalizedY = (y - centerY) / radiusY
        for (x in 0 until width) {
            val index = y * width + x
            val pixel = input[index]
            val sourceRed = (pixel ushr 16 and 0xFF) / 255f
            val sourceGreen = (pixel ushr 8 and 0xFF) / 255f
            val sourceBlue = (pixel and 0xFF) / 255f
            var red = srgbToLinear(sourceRed) * exposureScale * (1f + look.warmth + look.tint * 0.5f) * look.redGain
            var green = srgbToLinear(sourceGreen) * exposureScale * (1f - look.tint) * look.greenGain
            var blue = srgbToLinear(sourceBlue) * exposureScale * (1f - look.warmth + look.tint * 0.5f) * look.blueGain

            val matrix = look.colorMatrix
            val matrixRed = red * matrix[0] + green * matrix[1] + blue * matrix[2]
            val matrixGreen = red * matrix[3] + green * matrix[4] + blue * matrix[5]
            val matrixBlue = red * matrix[6] + green * matrix[7] + blue * matrix[8]
            red = matrixRed.coerceAtLeast(0f)
            green = matrixGreen.coerceAtLeast(0f)
            blue = matrixBlue.coerceAtLeast(0f)

            if (look.monochrome) {
                val gray = red * 0.36f + green * 0.56f + blue * 0.08f
                red = gray * (1f + look.warmth)
                green = gray
                blue = gray * (1f - look.warmth)
            } else {
                red += (red - max(green, blue)).coerceAtLeast(0f) * look.redAccent
                green += (green - max(red, blue)).coerceAtLeast(0f) * look.greenAccent
                blue += (blue - max(red, green)).coerceAtLeast(0f) * look.blueAccent
                val luminance = red * 0.2126f + green * 0.7152f + blue * 0.0722f
                red = luminance + (red - luminance) * look.saturation
                green = luminance + (green - luminance) * look.saturation
                blue = luminance + (blue - luminance) * look.saturation
                val highlight = ((luminance - 0.55f) / 0.45f).coerceIn(0f, 1f)
                val compressedChroma = 1f - look.chromaCompression * highlight
                red = luminance + (red - luminance) * compressedChroma
                green = luminance + (green - luminance) * compressedChroma
                blue = luminance + (blue - luminance) * compressedChroma
            }

            val preToneLuminance = red * 0.2126f + green * 0.7152f + blue * 0.0722f
            val shadowLift = (1f - preToneLuminance).coerceAtLeast(0f)
            red += look.shadowLift * shadowLift
            green += look.shadowLift * shadowLift
            blue += look.shadowLift * shadowLift
            red = applyEngineTone(red, look)
            green = applyEngineTone(green, look)
            blue = applyEngineTone(blue, look)

            val normalizedX = (x - centerX) / radiusX
            val distance = sqrt((normalizedX * normalizedX + normalizedY * normalizedY) * 0.5f).coerceIn(0f, 1f)
            val vignetteScale = 1f - look.vignette * distance.pow(2)
            val centerLuminance = pixelLuminance(pixel)
            val leftLuminance = pixelLuminance(input[if (x > 0) index - 1 else index])
            val rightLuminance = pixelLuminance(input[if (x + 1 < width) index + 1 else index])
            val topLuminance = pixelLuminance(input[if (y > 0) index - width else index])
            val bottomLuminance = pixelLuminance(input[if (y + 1 < height) index + width else index])
            val detail = (centerLuminance - (leftLuminance + rightLuminance + topLuminance + bottomLuminance) * 0.25f) * look.microContrast
            red = clampChannel(red + detail * 0.9f)
            green = clampChannel(green + detail)
            blue = clampChannel(blue + detail * 1.05f)
            val highlightMask = ((red * 0.2126f + green * 0.7152f + blue * 0.0722f - 0.72f) / 0.28f).coerceIn(0f, 1f)
            red = clampChannel(red + highlightMask * look.halation * 0.08f)
            green = clampChannel(green + highlightMask * look.halation * 0.015f)
            val hash = ((x * 73856093) xor (y * 19349663) xor (look.ordinal * 83492791))
            val noise = ((hash ushr 8 and 0xFF) / 255f - 0.5f) * look.grain
            val shadowNoise = noise * (1.35f - (red + green + blue) / 6f)
            val processedRed = clampChannel(linearToSrgb(red * vignetteScale + shadowNoise))
            val processedGreen = clampChannel(linearToSrgb(green * vignetteScale + shadowNoise))
            val processedBlue = clampChannel(linearToSrgb(blue * vignetteScale + shadowNoise))

            val mixStrength = strength.coerceIn(0f, 1f)
            val mixedRed = sourceRed + (processedRed - sourceRed) * mixStrength
            val mixedGreen = sourceGreen + (processedGreen - sourceGreen) * mixStrength
            val mixedBlue = sourceBlue + (processedBlue - sourceBlue) * mixStrength
            output[index] = (pixel and -0x1000000) or
                ((clampChannel(mixedRed) * 255f + 0.5f).toInt() shl 16) or
                ((clampChannel(mixedGreen) * 255f + 0.5f).toInt() shl 8) or
                (clampChannel(mixedBlue) * 255f + 0.5f).toInt()
        }
    }
    return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
}

/** 参照画像の構成に合わせた、左右比較プレビューを表示する。 */
@Composable
private fun FilmComparisonPreview(
    source: Bitmap?,
    preview: Bitmap?,
    position: Float,
    onPositionChange: (Float) -> Unit,
    onPick: () -> Unit,
    enabled: Boolean,
    pickEnabled: Boolean,
) {
    val base = source ?: preview
    val targetAspectRatio = base?.let { (it.width.toFloat() / it.height.toFloat()).coerceIn(0.45f, 2.4f) } ?: 1f
    val animatedAspectRatio by animateFloatAsState(
        targetValue = targetAspectRatio,
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f),
        label = "写真縦横比の変化",
    )
    val shape = RoundedCornerShape(30.dp)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(animatedAspectRatio)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(source, preview) {
                            detectTapGestures { offset ->
                                onPositionChange((offset.x / size.width).coerceIn(0f, 1f))
                            }
                        }
                        .pointerInput(source, preview) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                onPositionChange((change.position.x / size.width).coerceIn(0f, 1f))
                            }
                        }
                } else Modifier
            ),
    ) {
        if (source == null && preview == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("写真を参照", style = MaterialTheme.typography.titleLarge)
                Text("ここに加工前と加工後を表示します", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onPick, enabled = pickEnabled) { Text("写真を選ぶ") }
            }
            return@BoxWithConstraints
        }

        val sourceBitmap = source ?: base
        val previewBitmap = preview
        val animatedPosition by animateFloatAsState(
            targetValue = position.coerceIn(0f, 1f),
            animationSpec = spring(stiffness = 850f),
            label = "比較バー位置",
        )
        if (sourceBitmap != null) {
            Image(
                bitmap = sourceBitmap.asImageBitmap(),
                contentDescription = "元画像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (previewBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedPosition)
                    .clip(RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp)),
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "加工後",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * animatedPosition - 2.dp)
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(Color(0xFFFFD21C)),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * animatedPosition - 20.dp)
                    .size(40.dp),
                shape = CircleShape,
                color = Color(0xFFFFD21C),
                shadowElevation = 6.dp,
            ) {
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    val centerY = size.height / 2f
                    drawLine(Color(0xFF3B3000), Offset(size.width * 0.28f, centerY), Offset(size.width * 0.72f, centerY), strokeWidth = 2.5f)
                    drawLine(Color(0xFF3B3000), Offset(size.width * 0.28f, centerY), Offset(size.width * 0.44f, centerY - size.height * 0.18f), strokeWidth = 2.5f)
                    drawLine(Color(0xFF3B3000), Offset(size.width * 0.28f, centerY), Offset(size.width * 0.44f, centerY + size.height * 0.18f), strokeWidth = 2.5f)
                    drawLine(Color(0xFF3B3000), Offset(size.width * 0.72f, centerY), Offset(size.width * 0.56f, centerY - size.height * 0.18f), strokeWidth = 2.5f)
                    drawLine(Color(0xFF3B3000), Offset(size.width * 0.72f, centerY), Offset(size.width * 0.56f, centerY + size.height * 0.18f), strokeWidth = 2.5f)
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.62f),
            ) { Text("編集後", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.62f),
            ) { Text("元画像", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
        } else {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.52f),
            ) { Text("加工中…", color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }
        }
    }
}

/** 参照画像の紫色レールと、右から左へ流れる泡を一体化した強度コントロール。 */
@Composable
private fun BubblyStrengthSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "強度の泡")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2400, easing = LinearEasing)),
        label = "泡の流れ",
    )
    val bubbles = remember {
        listOf(
            0.04f to 0.16f,
            0.18f to 0.10f,
            0.31f to 0.13f,
            0.47f to 0.08f,
            0.63f to 0.12f,
            0.78f to 0.07f,
            0.92f to 0.11f,
        )
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        val railHeight = 28.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(railHeight)
                .clip(RoundedCornerShape(railHeight / 2))
                .background(Color(0xFFE6D8FF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .background(Color(0xFFB77BF4)),
            )
            Canvas(Modifier.matchParentSize()) {
                clipRect(right = size.width * value.coerceIn(0f, 1f)) {
                    bubbles.forEachIndexed { index, (offset, sizeFactor) ->
                        val travel = (phase + offset) % 1f
                        val x = size.width * (1f - travel)
                        val wave = sin((travel * Math.PI * 2.0 + index * 1.7)).toFloat()
                        val y = size.height * (0.5f + wave * 0.18f)
                        val radius = size.height * (sizeFactor + sin((travel * Math.PI * 2.0 + index).toFloat()) * 0.025f)
                        val alpha = (0.16f + (1f - travel) * 0.38f).coerceIn(0f, 0.58f)
                        drawCircle(Color.White.copy(alpha = alpha), radius.coerceAtLeast(1f), Offset(x, y))
                    }
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (maxWidth - 28.dp) * value.coerceIn(0f, 1f))
                .size(28.dp)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
fun ProFilmScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var look by remember { mutableStateOf(FilmLook.M9) }
    var strength by remember { mutableFloatStateOf(1f) }
    var comparisonPosition by remember { mutableFloatStateOf(0.5f) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("写真を選んで、色の違いを楽しもう") }
    var exportImage by remember { mutableStateOf<Bitmap?>(null) }
    var renderedSettings by remember { mutableStateOf<Pair<FilmLook, Float>?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = exportImage
        if (uri != null && bitmap != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        ?: error("保存先を開けませんでした")
                }
                status = "加工した写真を保存しました"
            } catch (e: Exception) { status = e.message ?: "保存に失敗しました" }
            finally { busy = false; exportImage = null }
        } else exportImage = null
    }
    val pick = rememberLauncherForActivityResult(EssentialMediaPickerContract()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
                    val decoded = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: error("写真を読み込めませんでした")
                    val orientation = context.contentResolver.openInputStream(uri)?.use {
                        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                    } ?: 1
                    val matrix = Matrix().apply {
                        when (orientation) {
                            2 -> setScale(-1f, 1f)
                            3 -> setRotate(180f)
                            4 -> setScale(1f, -1f)
                            5 -> { setRotate(90f); postScale(-1f, 1f) }
                            6 -> setRotate(90f)
                            7 -> { setRotate(-90f); postScale(-1f, 1f) }
                            8 -> setRotate(-90f)
                        }
                    }
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                }
                source = bitmap
                preview = null
                renderedSettings = null
                comparisonPosition = 0.5f
                status = "${bitmap.width} × ${bitmap.height} px・${look.title}現像エンジンで再生成（長辺最大2560px）"
            } catch (e: Exception) { status = e.message ?: "写真を読み込めませんでした" }
            finally { busy = false }
        }
    }
    LaunchedEffect(source, look, strength) {
        val bitmap = source ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) { regenerateFilmThroughEngine(bitmap, look, strength) }
        status = "${bitmap.width} × ${bitmap.height} px・${look.title}現像エンジンで再生成（長辺最大2560px）"
        renderedSettings = look to strength
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        progressiveItem(0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    onClick = onBack,
                    modifier = Modifier.size(46.dp).semantics { contentDescription = "戻る" },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                ) { Box(contentAlignment = Alignment.Center) { Text("‹", style = MaterialTheme.typography.headlineMedium) } }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pro Film", style = MaterialTheme.typography.headlineMedium)
                    Text("写真をカメラフィルム風に加工", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(
                    onClick = { exportImage = preview; save.launch("ProFilm-${look.name}-${System.currentTimeMillis()}.png") },
                    enabled = preview != null && renderedSettings == (look to strength) && !busy,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Text("保存")
                }
            }
        }
        progressiveItem(1) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                shape = RoundedCornerShape(32.dp),
                tonalElevation = 4.dp,
                modifier = Modifier.animateContentSize(
                    animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f),
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilmComparisonPreview(
                        source = source,
                        preview = preview,
                        position = comparisonPosition,
                        onPositionChange = { comparisonPosition = it },
                        onPick = { pick.launch(arrayOf("image/*")) },
                        enabled = source != null && preview != null && !busy,
                        pickEnabled = !busy,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        if (source != null && preview != null) {
                            Text("バーを左右にスライド", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (busy || (source != null && renderedSettings != (look to strength))) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
        progressiveItem(2) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("フィルター", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilmLook.entries.forEach { item ->
                        FilterChip(
                            selected = look == item,
                            onClick = { look = item },
                            label = { Text(item.title, maxLines = 1) },
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("現像エンジン  •  ${look.description}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
        progressiveItem(3) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 2.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("設定", style = MaterialTheme.typography.titleLarge)
                        Text("${(strength * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = Color(0xFF7B3FE4))
                    }
                    BubblyStrengthSlider(value = strength, onValueChange = { strength = it }, enabled = !busy)
                }
            }
        }
    }
}
