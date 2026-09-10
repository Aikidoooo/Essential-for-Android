package jp.essential.app.feature.profilm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import jp.essential.app.ui.progressiveItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** 公開されている色表現の特徴を基に独自設計した近似プロファイル。 */
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
    val grain: Float = 0f,
    val vignette: Float = 0f,
) {
    M9(
        "Leica M9", "CCDらしい深い赤と締まった中間調",
        saturation = 1.08f, contrast = 1.12f, exposure = -0.015f, warmth = 0.018f,
        blackCrush = 0.018f, redGain = 1.025f, blueGain = 0.985f,
        redAccent = 0.045f, greenAccent = 0.012f, blueAccent = -0.018f,
        grain = 0.012f, vignette = 0.055f,
    ),
    M3(
        "Leica M3", "銀塩モノクロを想定した深い黒と細かな粒状感",
        monochrome = true, contrast = 1.16f, warmth = 0.016f,
        highlightSoftness = 0.11f, blackCrush = 0.024f,
        grain = 0.052f, vignette = 0.105f,
    ),
    Xiaomi(
        "Leica (Xiaomi Original)", "Leica Authenticを意識した自然で繊細な発色",
        saturation = 0.92f, contrast = 1.095f, exposure = -0.02f, warmth = 0.014f,
        highlightSoftness = 0.13f, blackCrush = 0.016f,
        redGain = 1.012f, blueGain = 0.992f, redAccent = 0.018f,
        grain = 0.009f, vignette = 0.075f,
    ),
    Oppo(
        "Hasselblad (OPPO Original)", "Natural Colourを意識した穏やかで正確な色",
        saturation = 0.985f, contrast = 1.025f, exposure = 0.012f, warmth = 0.012f,
        shadowLift = 0.012f, highlightSoftness = 0.14f,
        redGain = 1.008f, greenGain = 1.004f, blueGain = 0.992f,
        redAccent = 0.012f, greenAccent = 0.008f, grain = 0.006f, vignette = 0.025f,
    ),
    Huawei(
        "Huawei (Huaweiスマートフォン)", "XMAGE Originalを意識した明瞭で実景に近い色",
        saturation = 1.055f, contrast = 1.075f, exposure = 0.018f, warmth = 0.004f,
        highlightSoftness = 0.12f, blackCrush = 0.008f,
        redGain = 1.006f, greenGain = 1.012f, blueGain = 1.006f,
        greenAccent = 0.022f, blueAccent = 0.016f, grain = 0.004f, vignette = 0.015f,
    ),
    Vivo(
        "ZEISS (Vivo Original)", "ZEISS Natural Colorを意識した忠実でニュートラルな色",
        saturation = 0.945f, contrast = 1.015f, exposure = 0.008f, warmth = -0.003f,
        shadowLift = 0.008f, highlightSoftness = 0.1f,
        redGain = 1.002f, greenGain = 1.004f, blueGain = 1.006f,
        grain = 0.003f, vignette = 0.012f,
    ),
    Xperia(
        "Sony (Xperia Original)", "Creative Look STを意識した自然で透明感のある仕上がり",
        saturation = 0.99f, contrast = 1.045f, exposure = 0.006f, warmth = -0.006f,
        shadowLift = 0.006f, highlightSoftness = 0.09f,
        redGain = 1.003f, greenGain = 1.002f, blueGain = 1.012f,
        blueAccent = 0.014f, grain = 0.004f, vignette = 0.018f,
    ),
    Fujifilm(
        "FUJIFILM (PROVIA)", "PROVIAの記憶色と標準的な階調を意識した万能仕上げ",
        saturation = 1.075f, contrast = 1.045f, exposure = 0.008f, warmth = -0.002f,
        tint = 0.004f, shadowLift = 0.005f, highlightSoftness = 0.08f,
        redGain = 0.998f, greenGain = 1.012f, blueGain = 1.012f,
        redAccent = -0.008f, greenAccent = 0.035f, blueAccent = 0.038f,
        grain = 0.012f, vignette = 0.02f,
    ),
}

private fun clampChannel(value: Float): Float = value.coerceIn(0f, 1f)

private fun toneChannel(value: Float, look: FilmLook): Float {
    var result = (value - 0.5f) * look.contrast + 0.5f
    result += look.shadowLift * (1f - result).coerceAtLeast(0f).pow(2)
    result -= look.blackCrush * (1f - result).coerceAtLeast(0f).pow(2)
    val highlight = ((result - 0.62f) / 0.38f).coerceAtLeast(0f)
    result -= highlight.pow(2) * look.highlightSoftness * 0.16f
    return clampChannel(result)
}

/** 8bit画像へ色、階調、粒状感、周辺減光を一括適用する。 */
private suspend fun renderFilm(source: Bitmap, look: FilmLook, strength: Float): Bitmap {
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
            var red = sourceRed
            var green = sourceGreen
            var blue = sourceBlue

            if (look.monochrome) {
                val gray = red * 0.36f + green * 0.56f + blue * 0.08f
                red = gray * (1f + look.warmth)
                green = gray
                blue = gray * (1f - look.warmth)
            } else {
                red *= look.redGain * (1f + look.warmth + look.tint * 0.5f)
                green *= look.greenGain * (1f - look.tint)
                blue *= look.blueGain * (1f - look.warmth + look.tint * 0.5f)
                red += (red - max(green, blue)).coerceAtLeast(0f) * look.redAccent
                green += (green - max(red, blue)).coerceAtLeast(0f) * look.greenAccent
                blue += (blue - max(red, green)).coerceAtLeast(0f) * look.blueAccent
                val luminance = red * 0.2126f + green * 0.7152f + blue * 0.0722f
                red = luminance + (red - luminance) * look.saturation
                green = luminance + (green - luminance) * look.saturation
                blue = luminance + (blue - luminance) * look.saturation
            }

            red = toneChannel(red * exposureScale, look)
            green = toneChannel(green * exposureScale, look)
            blue = toneChannel(blue * exposureScale, look)

            val normalizedX = (x - centerX) / radiusX
            val distance = sqrt((normalizedX * normalizedX + normalizedY * normalizedY) * 0.5f).coerceIn(0f, 1f)
            val vignetteScale = 1f - look.vignette * distance.pow(2)
            val hash = ((x * 73856093) xor (y * 19349663) xor (look.ordinal * 83492791))
            val noise = ((hash ushr 8 and 0xFF) / 255f - 0.5f) * look.grain
            val shadowNoise = noise * (1.35f - (red + green + blue) / 6f)
            red = clampChannel(red * vignetteScale + shadowNoise)
            green = clampChannel(green * vignetteScale + shadowNoise)
            blue = clampChannel(blue * vignetteScale + shadowNoise)

            val mixedRed = sourceRed + (red - sourceRed) * strength
            val mixedGreen = sourceGreen + (green - sourceGreen) * strength
            val mixedBlue = sourceBlue + (blue - sourceBlue) * strength
            output[index] = (pixel and -0x1000000) or
                ((clampChannel(mixedRed) * 255f + 0.5f).toInt() shl 16) or
                ((clampChannel(mixedGreen) * 255f + 0.5f).toInt() shl 8) or
                (clampChannel(mixedBlue) * 255f + 0.5f).toInt()
        }
    }
    return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
}

@Composable
fun ProFilmScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var look by remember { mutableStateOf(FilmLook.M9) }
    var strength by remember { mutableFloatStateOf(1f) }
    var original by remember { mutableStateOf(false) }
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
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
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
                status = "${bitmap.width} × ${bitmap.height} px・端末内で加工（長辺最大2560px）"
            } catch (e: Exception) { status = e.message ?: "写真を読み込めませんでした" }
            finally { busy = false }
        }
    }
    LaunchedEffect(source, look, strength) {
        val bitmap = source ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) { renderFilm(bitmap, look, strength) }
        renderedSettings = look to strength
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        progressiveItem(0) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onBack) { Text("戻る") }
                Text("Pro Film", style = MaterialTheme.typography.headlineLarge)
            }
        }
        progressiveItem(1) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val bitmap = if (original) source else preview
                    bitmap?.let {
                        Image(it.asImageBitmap(), "写真プレビュー", Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height).clip(RoundedCornerShape(24.dp)))
                    }
                    Button(onClick = { pick.launch(arrayOf("image/*")) }, enabled = !busy) { Text("写真を参照") }
                    Text(status)
                    if (busy || (source != null && renderedSettings != (look to strength))) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
        progressiveItem(2) {
            Column {
                Text("フィルムの強度 ${(strength * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
                Slider(value = strength, onValueChange = { strength = it }, enabled = !busy)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = original, onClick = { original = !original }, label = { Text("元の写真と比較") })
                    Button(onClick = { exportImage = preview; save.launch("ProFilm-${look.name}-${System.currentTimeMillis()}.png") }, enabled = preview != null && renderedSettings == (look to strength) && !busy) { Text("保存") }
                }
            }
        }
        FilmLook.entries.forEachIndexed { index, item ->
            progressiveItem(index + 3) {
                Surface(onClick = { look = item }, shape = RoundedCornerShape(24.dp), color = if (look == item) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleLarge)
                        Text(item.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        progressiveItem(FilmLook.entries.size + 3) {
            Text(
                "公開されている各社の色表現を基に、色チャンネル、階調、黒、ハイライト、粒状感、周辺減光を独自調整した近似です。公式LUTや実機ISPの完全再現ではありません。M3は使用フィルムで色が変わるため、モノクロ銀塩の表現例です。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
